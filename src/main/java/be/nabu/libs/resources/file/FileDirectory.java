package be.nabu.libs.resources.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;

public class FileDirectory extends FileItem implements ManageableContainer<FileResource> {

	public FileDirectory(ResourceContainer<?> parent, File file) {
		super(parent, file);
	}
	
	@Override
	public String getContentType() {
		return Resource.CONTENT_TYPE_DIRECTORY;
	}

	@Override
	public FileResource getChild(String name) {
		File child = new File(getFile(), name);
		if (child.exists()) {
			if (child.isFile())
				return new FileItem(this, child);
			else if (child.isDirectory())
				return new FileDirectory(this, child);
			else
				return null;
		}
		return null;
	}

	@Override
	public FileResource create(String name, String contentType) throws IOException {
		File target = new File(getFile(), name);
		if (contentType.equals(Resource.CONTENT_TYPE_DIRECTORY)) {
			if (!target.mkdir())
				throw new IOException("Could not create directory: " + target);
			return new FileDirectory(this, target);
		}
		else {
			target.createNewFile();
			return new FileItem(this, target);
		}
	}

	@Override
	public void delete(String name) throws IOException {
		File target = new File(getFile(), name);
		// boolean is not always accurate
		target.delete();
		// perform extra check
		if (target.exists())
			throw new IOException("Could not delete file: " + target);
	}
	
	public List<FileResource> getChildren() {
		List<FileResource> children = new ArrayList<FileResource>();
		for (File child : getFile().listFiles()) {
			if (child.isFile())
				children.add(new FileItem(this, child));
			else if (child.isDirectory())
				children.add(new FileDirectory(this, child));
		}
		return children;
	}
	
	@Override
	public Iterator<FileResource> iterator() {
		return getChildren().iterator();
	}

}
