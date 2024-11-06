/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.resources.file;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;

import be.nabu.libs.resources.api.LocatableResource;
import be.nabu.libs.resources.api.RenameableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;

abstract public class FileResource implements Resource, Closeable, LocatableResource, RenameableResource {

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
	
	public File getFile() {
		return file;
	}

	@Override
	public void close() throws IOException {
		// no system resources
	}

	@Override
	public URI getUri() {
		if (uri == null) {
			uri = this.file.toURI();
		}
		return uri;
	}
	
	@Override
	public String toString() {
		return getUri().toString();
	}

	@Override
	public void rename(String name) throws IOException {
		File newFile = new File(getFile().getParentFile(), name);
		if (newFile.exists()) {
			throw new IOException("Target file already exists: " + newFile);
		}
		// make sure we have a file directory parent before actually renaming
		FileDirectory parent = (FileDirectory) getParent();
		if (!getFile().renameTo(newFile)) {
			throw new IOException("Could not rename " + getFile() + " to " + newFile);
		}
		if (parent != null) {
			parent.rename(file.getName(), newFile.getName());
		}
		this.file = newFile;
	}
	
}

