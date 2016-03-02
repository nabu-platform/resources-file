package be.nabu.libs.resources.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.features.CacheableResource;

public class FileDirectory extends FileResource implements ManageableContainer<FileResource>, CacheableResource {

	private Map<String, FileResource> children;
	private boolean isCaching = true;
	
	public FileDirectory(ResourceContainer<?> parent, File file) {
		super(parent, file);
	}
	
	@Override
	public String getContentType() {
		return Resource.CONTENT_TYPE_DIRECTORY;
	}

	@Override
	public FileResource getChild(String name) {
		Map<String, FileResource> children = getChildren();
		if (!children.containsKey(name)) {
			synchronized(this) {
				if (!children.containsKey(name)) {
					File child = new File(getFile(), name);
					if (child.exists()) {
						if (child.isFile())
							children.put(name, new FileItem(this, child));
						else if (child.isDirectory())
							children.put(name, new FileDirectory(this, child));
						else
							children.put(name, null);
					}
				}
			}
		}
		return children.get(name);
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
		if (children == null || !isCaching) {
			synchronized(this) {
				if (children == null || !isCaching) {
					loadChildren();
				}
			}
		}
		return children;
	}

	private void loadChildren() {
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
	
	@Override
	public Iterator<FileResource> iterator() {
		return getChildren().values().iterator();
	}
	
	@Override
	public String toString() {
		return getFile().getAbsolutePath();
	}

	@Override
	public void resetCache() throws IOException {
		synchronized(this) {
			loadChildren();
		}
	}

	@Override
	public void setCaching(boolean cache) {
		this.isCaching = cache;
	}

	@Override
	public boolean isCaching() {
		return isCaching;
	}
}
