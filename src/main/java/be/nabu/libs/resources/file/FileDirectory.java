package be.nabu.libs.resources.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;

public class FileDirectory extends FileResource implements ManageableContainer<FileResource> {

	private Map<String, FileResource> children;
	
	public FileDirectory(ResourceContainer<?> parent, File file) {
		super(parent, file);
	}
	
	@Override
	public String getContentType() {
		return Resource.CONTENT_TYPE_DIRECTORY;
	}

	@Override
	public FileResource getChild(String name) {
		if (!getChildren().containsKey(name)) {
			synchronized(this) {
				if (!getChildren().containsKey(name)) {
					File child = new File(getFile(), name);
					if (child.exists()) {
						if (child.isFile())
							getChildren().put(name, new FileItem(this, child));
						else if (child.isDirectory())
							getChildren().put(name, new FileDirectory(this, child));
						else
							getChildren().put(name, null);
					}
				}
			}
		}
		return getChildren().get(name);
	}

	@Override
	public FileResource create(String name, String contentType) throws IOException {
		File target = new File(getFile(), name);
		FileResource resource;
		if (Resource.CONTENT_TYPE_DIRECTORY.equals(contentType)) {
			if (!target.mkdir())
				throw new IOException("Could not create directory: " + target);
			resource = new FileDirectory(this, target);
		}
		else {
			target.createNewFile();
			resource = new FileItem(this, target);
		}
		// add to children
		getChildren().put(name, resource);
		return resource;
	}

	@Override
	public void delete(String name) throws IOException {
		// refresh child
		getChildren().remove(name);
		File target = new File(getFile(), name);
		if (target.isDirectory()) {
			deleteChildren(target);
		}
		// boolean is not always accurate
		target.delete();
		// perform extra check
		if (target.exists()) {
			throw new IOException("Could not delete file: " + target);
		}
	}
	
	private void deleteChildren(File directory) {
		File[] children = directory.listFiles();
		if (children != null) {
			for (File child : children) {
				// refresh child
				getChildren().remove(child.getName());
				
				if (child.isDirectory()) {
					deleteChildren(child);
					child.delete();
				}
				else {
					child.delete();
				}
			}
		}
	}
	
	private Map<String, FileResource> getChildren() {
		if (children == null) {
			synchronized(this) {
				if (children == null) {
					Map<String, FileResource> children = new HashMap<String, FileResource>();
					File [] list = getFile().listFiles();
					if (list != null) {
						for (File child : list) {
							if (child.isFile()) {
								children.put(child.getName(), new FileItem(this, child));
							}
							else if (child.isDirectory()) {
								children.put(child.getName(), new FileDirectory(this, child));
							}
						}
					}
					this.children = children;
				}
			}
		}
		return children;
	}
	
	@Override
	public Iterator<FileResource> iterator() {
		return getChildren().values().iterator();
	}
	
	@Override
	public String toString() {
		return getFile().getAbsolutePath();
	}
}
