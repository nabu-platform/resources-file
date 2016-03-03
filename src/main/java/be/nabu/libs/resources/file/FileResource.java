package be.nabu.libs.resources.file;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;

import be.nabu.libs.resources.api.LocatableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;

abstract public class FileResource implements Resource, Closeable, LocatableResource {

	private File file;
	private ResourceContainer<?> parent;
	
	/**
	 * Cached due to overhead
	 */
	private URI uri;
	
	public FileResource(ResourceContainer<?> parent, File file, boolean allowUpwardResolving) {
		this.parent = parent;
		this.file = file;
		if (allowUpwardResolving && parent == null && file.getParentFile() != null) {
			this.parent = new FileDirectory(null, file.getParentFile(), allowUpwardResolving);
		}
	}

	@Override
	public String getName() {
		return file.getName();
	}

	@Override
	public ResourceContainer<?> getParent() {
		return parent;
	}
	
	@Override
	public boolean equals(Object object) {
		return object instanceof FileResource && ((FileResource) object).file.equals(file);
	}
	
	@Override
	public int hashCode() {
		return file.hashCode();
	}
	
	protected File getFile() {
		return file;
	}

	@Override
	public void close() throws IOException {
		// no system resources
	}

	@Override
	public URI getURI() {
		if (uri == null) {
			uri = this.file.toURI();
		}
		return uri;
	}
	
	@Override
	public String toString() {
		return getURI().toString();
	}
	
}

