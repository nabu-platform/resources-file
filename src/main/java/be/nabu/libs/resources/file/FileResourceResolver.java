package be.nabu.libs.resources.file;

import java.io.File;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceResolver;

public class FileResourceResolver implements ResourceResolver {
	
	private static List<String> defaultSchemes = Arrays.asList(new String [] { "file" });

	@Override
	public Resource getResource(URI uri, Principal principal) {
		File file = new File(uri.getSchemeSpecificPart());
		if (file.isFile())
			return new FileItem(null, file, true);
		else if (file.isDirectory())
			return new FileDirectory(null, file, true);
		else
			return null;
	}

	@Override
	public List<String> getDefaultSchemes() {
		return defaultSchemes;
	}

}
