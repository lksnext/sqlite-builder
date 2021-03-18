package com.lksnext.sqlite.impl.file;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.lksnext.sqlite.config.SQLitePropertyConfig;
import com.lksnext.sqlite.file.FileManager;

@Service
public class FileManagerImpl implements FileManager {

    private static final Logger LOG = LoggerFactory.getLogger(FileManagerImpl.class);

    @Autowired
    private SQLitePropertyConfig sqliteConfig;

    @Override
    public byte[] getFileContent(String filePath) throws IOException, URISyntaxException {
        URI uri = new URI(filePath);
        return getFileContent(uri);
    }

    @Override
    public byte[] getFileContent(URI uri) throws IOException {
        LOG.debug("Reading file from uri {}", uri.getPath());
        byte[] content = null;

        Path path = Paths.get(uri);
        if (Files.exists(path)) {
            content = Files.readAllBytes(path);
        } else {
            return null;
        }

        return content;
    }

    @Override
    public void moveFile(URI source, URI target) throws IOException {
        Files.createDirectories(Paths.get(target).getParent());
        if(sqliteConfig.isMoveDisabled()) {
        	Files.copy(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
        	removeFile(source);
        } else {
        	Files.move(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void saveFile(byte[] content, String filePath) throws IOException, URISyntaxException {
        URI uri = new URI(filePath);
        saveFile(content, uri);
    }

    @Override
    public void saveFile(byte[] content, URI uri) throws IOException {
        LOG.debug("Saving file into uri {}", uri.getPath());

        Path path = Paths.get(uri);
        Files.createDirectories(path.getParent());

        OpenOption rewriteOption =
                Files.exists(path) ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.CREATE;

        Files.write(path, content, rewriteOption);
    }

    @Override
    public void removeFile(URI uri) throws IOException {
        LOG.debug("Removing file from uri {}", uri.getPath());
        Path path = Paths.get(uri);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path)) {
                Files.delete(path);
            } else {
                // Force delete
                Files.deleteIfExists(path);
            }
        }
    }

    public void removeFolder(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("The path should be a folder " + path.toAbsolutePath().toString());
        }

        if (!(path.normalize().getNameCount() > 2)) {
            // Protección de borrado de directorios de primer y segundo nivel
            throw new IllegalArgumentException("Protection: The directory to be deleted should have at least 3 steps");
        }
        LOG.info("Removing foder {}", path);
        FileUtils.forceDelete(path.toFile());
    }

    @Override
    public void removeFolder(URI uri) throws IOException {
        Path path = Paths.get(uri);
        removeFolder(path);
    }

    @Override
    public Boolean fileExists(String filePath) throws URISyntaxException {
        URI uri = new URI(filePath);
        Boolean fileExists = Boolean.FALSE;

        Path path = Paths.get(uri);
        fileExists = Files.exists(path);

        return fileExists;
    }

    @Override
    public List<String> getFolderFolderNames(URI folderURI) throws IOException {
        List<String> filenames = new ArrayList<String>();
        Path path = Paths.get(folderURI);
        if (Files.exists(path) && Files.isDirectory(path)) {
            try (Stream<Path> subpaths = Files.list(path)) {
                filenames = subpaths.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
            }
        }
        return filenames;
    }

    public List<String> getFolderFilenames(URI folderURI) throws IOException {
        List<String> filenames = new ArrayList<String>();
        Path path = Paths.get(folderURI);
        if (Files.exists(path) && Files.isDirectory(path)) {
            try (Stream<Path> subpaths = Files.list(path)) {
                filenames = subpaths.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
            }
        }
        return filenames;
    }



    @Override
    public void computeMD5forFile(URI sourceFile, URI md5File) throws IOException {
        Path filePath = Paths.get(sourceFile);
        Path md5Path = Paths.get(md5File);
        LOG.debug("Computing MD5 for file {} into {}", filePath, md5Path);
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            String md5 = DigestUtils.md5DigestAsHex(fis);
            try (FileWriter fw = new FileWriter(md5Path.toFile())) {
                fw.write(md5);
            }
        }
    }

    @Override
    public void computeMD5forFolder(URI sourceFolder, URI targetFolder) throws IOException {
        List<String> filenames = getFolderFilenames(sourceFolder);
        Path source = Paths.get(sourceFolder);
        Path target = Paths.get(targetFolder);
        for (String filename : filenames) {
            if (!"md5".equals(FilenameUtils.getExtension(filename))) {
                LOG.debug("Computing MD5 for file {}", filename);
                String md5Filename =
                        FilenameUtils.getBaseName(filename) + "-" + FilenameUtils.getExtension(filename) + ".md5";
                Path file = Paths.get(source.toString(), filename);
                Path md5File = Paths.get(target.toString(), md5Filename);
                computeMD5forFile(file.toUri(), md5File.toUri());
            }
        }
    }

    @Override
    public void createFolder(String destFolder) throws URISyntaxException {
        Path destPath = Paths.get(new URI(destFolder));
        destPath.getParent().toFile().mkdirs();
    }

    @Override
    public void touch(URI file) throws IOException {
        FileUtils.touch(Paths.get(file).toFile());
    }
}
