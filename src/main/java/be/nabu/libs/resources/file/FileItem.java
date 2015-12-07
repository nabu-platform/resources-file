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
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;

import be.nabu.libs.resources.api.AccessTrackingResource;
import be.nabu.libs.resources.api.AppendableResource;
import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

public class FileItem extends FileResource implements ReadableResource, AppendableResource, FiniteResource, TimestampedResource, AccessTrackingResource {

	public FileItem(ResourceContainer<?> parent, File file) {
		super(parent, file);
	}

	@Override
	public String getContentType() {
		return URLConnection.guessContentTypeFromName(getName());
	}

	@Override
	public WritableContainer<ByteBuffer> getWritable() throws FileNotFoundException {
		if (!getFile().exists() && !getFile().getParentFile().exists())
			getFile().getParentFile().mkdirs();
		return IOUtils.wrap(new BufferedOutputStream(new FileOutputStream(getFile())));
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws FileNotFoundException {
		try {
			Files.setAttribute(getFile().toPath(), "lastAccessTime", FileTime.fromMillis(new Date().getTime()));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
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
		if (!getFile().exists() && !getFile().getParentFile().exists()) {
			getFile().getParentFile().mkdirs();
		}
		return IOUtils.wrap(new BufferedOutputStream(new FileOutputStream(getFile(), true)));
	}

}
