package be.nabu.libs.resources.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process-local opaque revisions. A fingerprint is sampled on construction and completed
 * mutation boundaries, never on revision lookup. Active writes retain the last completed
 * token; new observations during a write stay unknown until completion. External writes preserving both
 * lastModified and size cannot be detected without explicit invalidation.
 * Symlink paths (including symlink ancestors) are unsupported.
 */
public final class FileRevisions {
	private static final Logger LOGGER = Logger.getLogger(FileRevisions.class.getName());
	private static final int LIMIT = 8192;
	private static final Map<Path, State> revisions = new LinkedHashMap<Path, State>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;
		@Override
		protected boolean removeEldestEntry(Map.Entry<Path, State> entry) {
			return size() > LIMIT;
		}
	};
	private static final Map<Path, Integer> writers = new HashMap<>();

	private static final class State {
		private String token;
		private Long modified;
		private Long size;
		private boolean pending;
	}

	private FileRevisions() {
	}

	private static Path path(File file) {
		return file.toPath().toAbsolutePath().normalize();
	}

	private static boolean writing(Path target) {
		for (Path ancestor = target; ancestor != null; ancestor = ancestor.getParent()) {
			if (writers.containsKey(ancestor)) {
				return true;
			}
		}
		return false;
	}

	private static boolean supported(Path target) {
		for (Path ancestor = target; ancestor != null; ancestor = ancestor.getParent()) {
			if (Files.isSymbolicLink(ancestor)) {
				return false;
			}
		}
		return true;
	}

	private static void sample(Path target, State state, boolean force) {
		if (writing(target)) {
			return;
		}
		try {
			if (!supported(target)) {
				state.token = null;
				state.modified = null;
				state.size = null;
				return;
			}
			BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			if (!attributes.isRegularFile()) {
				state.token = null;
				state.modified = null;
				state.size = null;
				return;
			}
			long modified = attributes.lastModifiedTime().toMillis();
			long size = attributes.size();
			if (force || state.token == null || state.modified == null || state.modified != modified || state.size != size) {
				state.token = UUID.randomUUID().toString();
			}
			state.modified = modified;
			state.size = size;
		}
		catch (IOException e) {
			if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
				LOGGER.log(Level.WARNING, "Could not read file revision: " + target, e);
			}
			state.token = null;
			state.modified = null;
			state.size = null;
		}
	}

	/** Resolve support once; an unsupported alias remains unavailable on this instance. */
	static synchronized Path observe(File file) {
		Path target = path(file);
		if (!supported(target)) {
			return null;
		}
		State state = revisions.get(target);
		if (state == null) {
			state = new State();
			revisions.put(target, state);
		}
		sample(target, state, false);
		return target;
	}

	/** Invalidates a path and all observed descendants, including after partial operations. */
	public static synchronized void invalidate(File file) {
		Path target = path(file);
		for (Map.Entry<Path, State> entry : revisions.entrySet()) {
			if (entry.getKey().startsWith(target)) {
				entry.getValue().pending = true;
			}
		}
		publishPending();
	}

	private static void publishPending() {
		for (Map.Entry<Path, State> entry : revisions.entrySet()) {
			State state = entry.getValue();
			if (state.pending && !writing(entry.getKey())) {
				state.pending = false;
				// Known mutations rotate the token even with unchanged metadata.
				sample(entry.getKey(), state, true);
			}
		}
	}

	/** Tracks a subtree mutation; close even on failure to publish its final revision. */
	public static Mutation beginExternalMutation(File directory) {
		begin(directory);
		return new Mutation(directory);
	}

	public static final class Mutation implements AutoCloseable {
		private final File directory;
		private boolean closed;

		private Mutation(File directory) {
			this.directory = directory;
		}

		@Override
		public synchronized void close() {
			if (!closed) {
				closed = true;
				end(directory);
			}
		}
	}

	static synchronized void begin(File file) {
		Path target = path(file);
		writers.put(target, writers.getOrDefault(target, 0) + 1);
		invalidate(file);
	}

	static synchronized void end(File file) {
		Path target = path(file);
		int count = writers.get(target);
		if (count == 1) {
			writers.remove(target);
		}
		else {
			writers.put(target, count - 1);
		}
		invalidate(file);
		publishPending();
	}

	static synchronized String get(Path target) {
		if (target == null) {
			return null;
		}
		State state = revisions.get(target);
		return state == null ? null : state.token;
	}

	static synchronized String get(File file) {
		return get(path(file));
	}
}
