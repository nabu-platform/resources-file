package be.nabu.libs.resources.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URLConnection;
import java.util.Date;

import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

public class FileItem extends FileResource implements ReadableResource, WritableResource, FiniteResource, TimestampedResource {

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
		return IOUtils.wrap(new FileOutputStream(getFile()));
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws FileNotFoundException {
		return IOUtils.wrap(new FileInputStream(getFile()));
	}

	@Override
	public long getSize() {
		return getFile().length();
	}

	@Override
	public Date getLastModified() {
		return new Date(getFile().lastModified());
	}

}
