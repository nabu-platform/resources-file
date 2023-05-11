package be.nabu.libs.resources.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.libs.resources.api.DetachableResource;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.features.CacheableResource;

public class FileDirectory extends FileResource implements ManageableContainer<FileResource>, CacheableResource, DetachableResource {

	private Map<String, FileResource> children;
	private boolean isCaching = true;
	
	public FileDirectory(ResourceContainer<?> parent, File file, boolean allowUpwardResolving) {
		super(parent, file, allowUpwardResolving);
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
							children.put(name, new FileItem(this, child, true));
						else if (child.isDirectory())
							children.put(name, new FileDirectory(this, child, true));
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
			if (!target.mkdir() && (!target.exists() || !target.isDirectory())) {
				throw new IOException("Could not create directory: " + target);
			}
			resource = new FileDirectory(this, target, true);
		}
		else {
			target.createNewFile();
			resource = new FileItem(this, target, true);
		}
		// add to children
		getChildren().put(name, resource);
		return resource;
	}

	@Override
	public void delete(String name) throws IOException {
		// @2023-04-12: remove from children IF we have loaded them, we don't want the delete to specifically trigger a load of all children
		// in large directories if we want to delete a particular file, we incidentally trigger a full listing, we can't easily change the caching mechanisms without extensive testing, but this change should have the same behavior as before but not trigger a child load if it hasn't already been done
//		getChildren().remove(name);
		if (children != null && children.containsKey(name)) {
			children.remove(name);
		}
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
				// @2023-04-12: see above
//				getChildren().remove(child.getName());
				if (this.children != null && this.children.containsKey(child.getName())) {
					this.children.remove(child.getName());
				}
				
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
	
	protected void rename(String oldName, String newName) {
		// @2023-04-12: see above
		if (children != null && children.containsKey(oldName)) {
			children.put(newName, children.get(oldName));
			children.remove(oldName);
		}
//		Map<String, FileResource> children = getChildren();
//		children.put(newName, children.get(oldName));
//		children.remove(oldName);
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
					children.put(child.getName(), new FileItem(this, child, true));
				}
				else if (child.isDirectory()) {
					children.put(child.getName(), new FileDirectory(this, child, true));
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

	@Override
	public Resource detach() {
		return new FileDirectory(null, getFile(), false);
	}
}
