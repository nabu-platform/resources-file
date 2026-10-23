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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;

import be.nabu.libs.resources.api.AccessTrackingResource;
import be.nabu.libs.resources.api.AppendableResource;
import be.nabu.libs.resources.api.DetachableResource;
import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.RevisionedResource;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

public class FileItem extends FileResource implements ReadableResource, AppendableResource, FiniteResource, TimestampedResource, AccessTrackingResource, DetachableResource, RevisionedResource {
	private Path revisionPath;

	public FileItem(ResourceContainer<?> parent, File file, boolean allowUpwardResolving) {
		super(parent, file, allowUpwardResolving);
		revisionPath = FileRevisions.observe(file);
	}

	@Override
	public String getContentType() {
		return URLConnection.guessContentTypeFromName(getName());
	}

	@Override
	public WritableContainer<ByteBuffer> getWritable() throws FileNotFoundException {
		return openWriter(false);
	}

	@Override
	public String getRevision() {
		return FileRevisions.get(revisionPath);
	}

	private WritableContainer<ByteBuffer> openWriter(boolean append) throws FileNotFoundException {
		File file = getFile();
		if (!file.exists() && !file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}
		FileRevisions.begin(file);
		try {
			return new RevisionWriter(IOUtils.wrap(new BufferedOutputStream(new FileOutputStream(file, append))), file);
		}
		catch (RuntimeException | FileNotFoundException | Error e) {
			FileRevisions.end(file);
			throw e;
		}
	}

	private static class RevisionWriter implements WritableContainer<ByteBuffer> {
		private final WritableContainer<ByteBuffer> delegate;
		private final File file;
		private boolean closed;

		private RevisionWriter(WritableContainer<ByteBuffer> delegate, File file) {
			this.delegate = delegate;
			this.file = file;
		}

		@Override
		public long write(ByteBuffer buffer) throws IOException {
			try {
				return delegate.write(buffer);
			}
			finally {
				FileRevisions.invalidate(file);
			}
		}

		@Override
		public void flush() throws IOException {
			try {
				delegate.flush();
			}
			finally {
				FileRevisions.invalidate(file);
			}
		}

		@Override
		public synchronized void close() throws IOException {
			if (!closed) {
				closed = true;
				try {
				delegate.close();
				}
				finally {
					FileRevisions.end(file);
				}
			}
		}
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws FileNotFoundException {
		try {
			Files.setAttribute(getFile().toPath(), "lastAccessTime", FileTime.fromMillis(new Date().getTime()));
		}
		catch (IOException e) {
			// ignore, if we do not have permission to update file but we do to read it, this should still be possible
		}
		return IOUtils.wrap(new BufferedInputStream(new FileInputStream(getFile())));
	}

	@Override
	public long getSize() {
		return getFile().length();
	}

	@Override
	public Date getLastModified() {
		return new Date(getFile().lastModified());
	}

	@Override
	public Date getLastAccessed() {
		try {
			BasicFileAttributes readAttributes = Files.readAttributes(getFile().toPath(), BasicFileAttributes.class);
			return new Date(readAttributes.lastAccessTime().toMillis());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public WritableContainer<ByteBuffer> getAppendable() throws IOException {
		return openWriter(true);
	}

	@Override
	public void rename(String name) throws IOException {
		super.rename(name);
		revisionPath = FileRevisions.observe(getFile());
	}

	@Override
	public Resource detach() {
		return new FileItem(null, getFile(), false);
	}

}
