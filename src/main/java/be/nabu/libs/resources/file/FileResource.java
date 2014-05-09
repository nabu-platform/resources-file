package be.nabu.libs.resources.file;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.ResourceRoot;

abstract public class FileResource implements Resource, ResourceRoot {

	private File file;
	private ResourceContainer<?> parent;
	
	public FileResource(ResourceContainer<?> parent, File file) {
		this.parent = parent;
		this.file = file;
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
		return getFile().toURI();
	}	
	
}

