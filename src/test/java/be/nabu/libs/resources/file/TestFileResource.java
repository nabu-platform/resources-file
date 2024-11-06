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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;

import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.Container;
import junit.framework.TestCase;

public class TestFileResource extends TestCase {
	
	public void testFileResource() throws IOException, URISyntaxException {
		String tmpDirectory = System.getProperty("java.io.tmpdir");
		assertNotNull("There is no temporary directory", tmpDirectory);
		System.out.println("Using " + tmpDirectory + " as temporary directory");
		
		Resource root = ResourceFactory.getInstance().resolve(new URI("file:" + tmpDirectory.replaceAll("\\\\", "/")), null);
		assertNotNull("Could not resolve the temporary directory", root);
		assertTrue("Temporary directory not manageable", root instanceof ManageableContainer);

		// test file creation in root
		testFileCreation((ManageableContainer<?>) root, "testing.txt", "test");
		
		// test dir creation
		ManageableContainer<?> dir = createDirectory((ManageableContainer<?>) root, "test");
		try {
			testFileCreation(dir, "testing.something.xml", "<test/>");
		}
		finally {
			((ManageableContainer<?>) root).delete(dir.getName());
		}
	}
	
	public ManageableContainer<?> createDirectory(ManageableContainer<?> directory, String dirName) throws IOException {
		Resource dir = directory.create(dirName, Resource.CONTENT_TYPE_DIRECTORY);
		assertNotNull("Directory could not be created", dir);
		assertTrue("Directory not manageable", dir instanceof ManageableContainer);
		assertEquals(directory, dir.getParent());
		return (ManageableContainer<?>) dir;
	}
	
	public void testFileCreation(ManageableContainer<?> directory, String fileName, String testContent) throws IOException {
		Resource file = directory.create(fileName, URLConnection.guessContentTypeFromName(fileName));
		assertNotNull("File could not be created", file);
		assertTrue("Temporary file is not writable", file instanceof WritableResource);
		assertTrue("Temporary file is not readable", file instanceof ReadableResource);
		assertEquals(directory, file.getParent());
		Container<ByteBuffer> container = IOUtils.wrap(((ReadableResource) file).getReadable(), ((WritableResource) file).getWritable());
		try {
			container.write(IOUtils.wrap(testContent.getBytes(), true));
			container.flush();
			assertEquals(testContent, new String(IOUtils.toBytes(container)));
		}
		finally {
			container.close();
		}
		directory.delete(fileName);
	}
}
