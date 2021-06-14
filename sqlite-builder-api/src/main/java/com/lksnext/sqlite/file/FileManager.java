package com.lksnext.sqlite.file;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public interface FileManager {

    byte[] getFileContent(String path) throws IOException, URISyntaxException;

    byte[] getFileContent(URI uri) throws IOException, URISyntaxException;

    void saveFile(byte[] content, String path) throws IOException, URISyntaxException;

    void saveFile(byte[] content, URI uri) throws IOException;

    void removeFile(URI uri) throws IOException;

    List<String> getFolderFilenames(URI folderURI) throws IOException;

    void removeFolder(URI uri) throws IOException;

    void computeMD5forFile(URI sourceFile, URI md5File) throws URISyntaxException, IOException;

    void computeMD5forFolder(URI sourceFolder, URI targetFolder) throws IOException;

    List<String> getFolderFolderNames(URI folderURI) throws IOException;

    void createFolder(String destPath) throws URISyntaxException;

    void moveFile(URI source, URI target) throws IOException;

    void touch(URI file) throws IOException;

	Boolean fileExists(URI uri) throws URISyntaxException;
	
	void ensureParentExists(URI uri) throws IOException;

}
