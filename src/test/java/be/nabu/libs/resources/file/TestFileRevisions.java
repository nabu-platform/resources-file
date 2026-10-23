package be.nabu.libs.resources.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import be.nabu.utils.io.IOUtils;

import be.nabu.libs.resources.api.Resource;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import junit.framework.TestCase;

public class TestFileRevisions extends TestCase {
	public void testWritesAndSharedInstances() throws IOException {
		File directory = Files.createTempDirectory("revision-test").toFile();
		File file = new File(directory, "item.txt");
		try {
			FileDirectory parent = new FileDirectory(null, directory, false);
			FileItem first = (FileItem) parent.create("item.txt", "text/plain");
			FileItem second = new FileItem(null, new File(directory, "./item.txt"), false);
			String initial = first.getRevision();
			assertNotNull(initial);
			assertEquals(initial, second.getRevision());
			WritableContainer<ByteBuffer> one = first.getWritable();
			assertEquals(initial, second.getRevision());
			one.write(IOUtils.wrap(new byte[] { 1 }, true));
			assertEquals(initial, first.getRevision());
			one.flush();
			assertEquals(initial, second.getRevision());
			WritableContainer<ByteBuffer> two = second.getAppendable();
			one.close();
			one.close();
			assertEquals(initial, first.getRevision());
			two.close();
			String written = second.getRevision();
			assertNotNull(written);
			assertFalse(initial.equals(written));
			WritableContainer<ByteBuffer> three = first.getAppendable();
			three.close();
			assertFalse(written.equals(first.getRevision()));
			parent.delete("item.txt");
			assertNull(first.getRevision());
			parent.create("item.txt", "text/plain");
			assertFalse(written.equals(second.getRevision()));
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testRenameDirectoryAndInvalidation() throws IOException {
		File directory = Files.createTempDirectory("revision-rename").toFile();
		try {
			FileDirectory root = new FileDirectory(null, directory, false);
			FileDirectory old = (FileDirectory) root.create("old", Resource.CONTENT_TYPE_DIRECTORY);
			FileItem item = (FileItem) old.create("item.txt", "text/plain");
			FileItem destination = new FileItem(null, new File(directory, "new/item.txt"), false);
			String before = item.getRevision();
			assertNull(destination.getRevision());
			old.rename("new");
			assertNull(item.getRevision());
			assertNotNull(destination.getRevision());
			root.delete("new");
			assertNull(destination.getRevision());
			FileDirectory recreated = (FileDirectory) root.create("new", Resource.CONTENT_TYPE_DIRECTORY);
			FileItem again = (FileItem) recreated.create("item.txt", "text/plain");
			assertFalse(before.equals(again.getRevision()));
			root.delete("new");
		}
		finally {
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testExplicitSubtreeInvalidation() throws IOException {
		File directory = Files.createTempDirectory("revision-hook").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.createFile(file.toPath());
			FileItem item = new FileItem(null, file, false);
			String initial = item.getRevision();
			FileRevisions.invalidate(directory);
			assertFalse(initial.equals(item.getRevision()));
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testExternalMutationScopeAndOverlappingWriters() throws IOException {
		File directory = Files.createTempDirectory("revision-external").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.createFile(file.toPath());
			FileItem item = new FileItem(null, file, false);
			String initial = item.getRevision();
			FileRevisions.Mutation first = FileRevisions.beginExternalMutation(directory);
			FileRevisions.Mutation second = FileRevisions.beginExternalMutation(new File(directory, "."));
			try {
				assertEquals(initial, item.getRevision());
				Files.write(file.toPath(), new byte[] { 1 });
				first.close();
				first.close();
				assertEquals(initial, item.getRevision());
			}
			finally {
				first.close();
				second.close();
			}
			assertNotNull(item.getRevision());
			assertFalse(initial.equals(item.getRevision()));
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testFailedExternalMutationInvalidatesPartialChanges() throws IOException {
		File directory = Files.createTempDirectory("revision-partial").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.createFile(file.toPath());
			FileItem item = new FileItem(null, file, false);
			String initial = item.getRevision();
			try (FileRevisions.Mutation mutation = FileRevisions.beginExternalMutation(directory)) {
				Files.write(file.toPath(), new byte[] { 1 });
				assertEquals(initial, item.getRevision());
				throw new IOException("partial operation");
			}
			catch (IOException expected) {
				java.util.logging.Logger.getLogger(getClass().getName()).info(expected.toString());
				assertEquals("partial operation", expected.getMessage());
			}
			assertFalse(initial.equals(item.getRevision()));
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testNestedMutationDefersPublicationUntilLastScope() throws IOException {
		File directory = Files.createTempDirectory("revision-nested").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.createFile(file.toPath());
			FileItem item = new FileItem(null, file, false);
			String initial = item.getRevision();
			FileRevisions.Mutation parent = FileRevisions.beginExternalMutation(directory);
			FileRevisions.Mutation child = FileRevisions.beginExternalMutation(file);
			try {
				Files.write(file.toPath(), new byte[] { 1 });
				parent.close();
				assertEquals(initial, item.getRevision());
			}
			finally {
				parent.close();
				child.close();
			}
			assertFalse(initial.equals(item.getRevision()));
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testParentScopeRetainsRevisionAfterChildWriterCloses() throws IOException {
		File directory = Files.createTempDirectory("revision-parent").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.createFile(file.toPath());
			FileItem item = new FileItem(null, file, false);
			String initial = item.getRevision();
			try (FileRevisions.Mutation mutation = FileRevisions.beginExternalMutation(directory)) {
				WritableContainer<ByteBuffer> writer = item.getWritable();
				writer.write(IOUtils.wrap(new byte[] { 1 }, true));
				writer.close();
				assertEquals(initial, item.getRevision());
				assertEquals(initial, new FileItem(null, file, false).getRevision());
			}
			assertFalse(initial.equals(item.getRevision()));
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testUnobservedFileDuringMutationRemainsUnknownUntilClose() throws IOException {
		File directory = Files.createTempDirectory("revision-unknown").toFile();
		File file = new File(directory, "item.txt");
		try {
			FileItem item;
			try (FileRevisions.Mutation mutation = FileRevisions.beginExternalMutation(directory)) {
				Files.write(file.toPath(), new byte[] { 1 });
				item = new FileItem(null, file, false);
				assertNull(item.getRevision());
			}
			assertNotNull(item.getRevision());
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testNewInstanceDetectsExternalMetadataChanges() throws IOException {
		File directory = Files.createTempDirectory("revision-metadata").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.write(file.toPath(), new byte[] { 1 });
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(2000000000000L));
			FileItem old = new FileItem(null, file, false);
			String initial = old.getRevision();
			assertNotNull(initial);
			assertEquals(initial, new FileItem(null, file, false).getRevision());

			Files.write(file.toPath(), new byte[] { 2 });
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(2000000002000L));
			String later = new FileItem(null, file, false).getRevision();
			assertFalse(initial.equals(later));
			assertEquals(later, old.getRevision());

			Files.write(file.toPath(), new byte[] { 3 });
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(2000000000000L));
			String earlier = new FileItem(null, file, false).getRevision();
			assertFalse(later.equals(earlier));
			assertEquals(earlier, old.getRevision());

			Files.write(file.toPath(), new byte[] { 4, 5 });
			Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(2000000000000L));
			String resized = new FileItem(null, file, false).getRevision();
			assertFalse(earlier.equals(resized));
			assertEquals(resized, old.getRevision());
			assertEquals(resized, new FileItem(null, file, false).getRevision());
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testUnsupportedPathsRemainUnavailable() throws IOException {
		File directory = Files.createTempDirectory("revision-unsupported").toFile();
		File file = new File(directory, "item.txt");
		File alias = new File(directory, "alias.txt");
		try {
			assertNull(new FileItem(null, file, false).getRevision());
			assertNull(new FileItem(null, directory, false).getRevision());
			Files.createFile(file.toPath());
			Files.createSymbolicLink(alias.toPath(), file.toPath());
			assertNull(new FileItem(null, alias, false).getRevision());
			assertNotNull(new FileItem(null, file, false).getRevision());
		}
		finally {
			Files.deleteIfExists(alias.toPath());
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testRevisionLookupUsesResolvedIdentity() throws IOException {
		File directory = Files.createTempDirectory("revision-lookup").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.createFile(file.toPath());
			FileItem item = new FileItem(null, file, false) {
				@Override
				public File getFile() {
					throw new AssertionError("Revision lookup must not access the file");
				}
			};
			assertNotNull(item.getRevision());
			assertEquals(item.getRevision(), item.getRevision());
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testActiveWriterConstructorCannotBlessPartialMetadata() throws IOException {
		File directory = Files.createTempDirectory("revision-active").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.write(file.toPath(), new byte[] { 1 });
			FileItem first = new FileItem(null, file, false);
			String initial = first.getRevision();
			FileItem during;
			try (FileRevisions.Mutation mutation = FileRevisions.beginExternalMutation(directory)) {
				Files.write(file.toPath(), new byte[] { 2, 3 });
				during = new FileItem(null, file, false);
				assertEquals(initial, during.getRevision());
			}
			String finished = during.getRevision();
			assertNotNull(finished);
			assertFalse(initial.equals(finished));
			assertEquals(finished, first.getRevision());
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testDirectoryRefreshCreatesNewItemAndSamples() throws IOException {
		File directory = Files.createTempDirectory("revision-refresh").toFile();
		File file = new File(directory, "item.txt");
		try {
			Files.write(file.toPath(), new byte[] { 1 });
			FileDirectory root = new FileDirectory(null, directory, false);
			FileItem old = (FileItem) root.getChild("item.txt");
			String initial = old.getRevision();
			Files.write(file.toPath(), new byte[] { 1, 2 });
			root.resetCache();
			FileItem refreshed = (FileItem) root.getChild("item.txt");
			assertNotSame(old, refreshed);
			assertFalse(initial.equals(refreshed.getRevision()));
			assertEquals(refreshed.getRevision(), old.getRevision());
		}
		finally {
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(directory.toPath());
		}
	}

	public void testFailedOpenDoesNotLeaveWriter() throws IOException {
		File directory = Files.createTempDirectory("revision-failure").toFile();
		try {
			FileItem item = new FileItem(null, directory, false);
			try {
				item.getWritable();
				fail("Opening directory for writing should fail");
			}
			catch (IOException expected) {
				assertNull(FileRevisions.get(directory));
			}
			File file = new File(directory, "item.txt");
			Files.createFile(file.toPath());
			assertNotNull(new FileItem(null, file, false).getRevision());
			Files.delete(file.toPath());
		}
		finally {
			Files.deleteIfExists(directory.toPath());
		}
	}
}
