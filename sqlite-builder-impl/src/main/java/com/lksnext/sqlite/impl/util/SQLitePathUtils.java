package com.lksnext.sqlite.impl.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLitePathUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SQLitePathUtils.class);

    private static String MD5_EXTENSION = ".md5";
    private static String DB_EXTENSION = ".db";
    private static String LOCK_EXTENSION = ".lck";
    private static String PATCH_EXTENSION = ".patch";
    private static String METADATA_FILE_NAME = "metadata.json";
    private static String HYPHEN = "-";
    public static String LATEST_DB_NAME = "latest.db";
    private static String LATEST_ZIPPED_DB_NAME = "latest.zip";

    public static final URI getTemporalDBPath(URI tempDir, String fileName) {
        return getPath(tempDir, fileName, DB_EXTENSION);
    }

    public static final URI getTemporalMD5Path(URI tempDir, String fileName) {
        return getPath(tempDir, fileName, MD5_EXTENSION);
    }

    private static final URI getPath(URI baseURI, String fileName, String extension) {
        Path basePath = Paths.get(baseURI);
        return Paths.get(basePath.toString(), fileName + extension).toUri();
    }

    private static final URI getPath(URI baseURI, String fileName) {
        Path basePath = Paths.get(baseURI);
        return Paths.get(basePath.toString(), fileName).toUri();
    }

    public static final URI getMasterdataDBPath(URI baseUri, String fileName) {
        return getPath(baseUri, fileName, DB_EXTENSION);
    }

    public static final URI getMasterdataMD5Path(URI baseUri, String fileName) {
        return getPath(baseUri, fileName, MD5_EXTENSION);
    }
    
    public static final URI getMasterdataLatestZipPath(URI baseURI) {
    	Path basePath = Paths.get(baseURI);
        return Paths.get(basePath.toString(), LATEST_ZIPPED_DB_NAME).toUri();
    }

    public static final String getDBURL(URI tempDir, String fileName) {
        return "jdbc:sqlite:" + getTemporalDBPath(tempDir, fileName).getPath();
    }

    public static final URI getMasterdataDBFolderPath(URI baseUri, String database) {
        return getPath(baseUri, database.toLowerCase());
    }

    public static final URI getMasterdataMetadataPath(URI baseUri, String database) {
        return getPath(getMasterdataDBFolderPath(baseUri, database), METADATA_FILE_NAME);
    }

    public static final URI getMasterdataDBPath(URI baseUri, String database, String md5) {
        String filename = System.currentTimeMillis() + HYPHEN + database.toLowerCase() + HYPHEN + md5 + DB_EXTENSION;
        return getPath(getMasterdataDBFolderPath(baseUri, database), filename);
    }

    public static final URI getMasterdataLatestDBPath(URI baseUri, String database) {
        return getPath(getMasterdataDBFolderPath(baseUri, database), LATEST_DB_NAME);
    }

    public static final URI getMasterdataPatchPath(URI baseUri, String database, String md5) {
        return getPath(getMasterdataDBFolderPath(baseUri, database), md5, PATCH_EXTENSION);
    }

    public static boolean isMasterdataLocked(URI tempDir, String database) {
        return Files.exists(Paths.get(getPath(tempDir, database.toLowerCase(), LOCK_EXTENSION)));
    }

    public static File createMasterdataLock(URI tempDir, String database) {
    	File centerLock = new File(getPath(tempDir, database.toLowerCase(), LOCK_EXTENSION));
        try {
            FileUtils.touch(centerLock);
            return centerLock;
        } catch (IOException e) {
        	LOG.info("Error creating lock for database {} using touch ", database);
        	LOG.info("centerLock exists: {} ", centerLock.exists());
        	if(!centerLock.exists()) {
        		try {
					if(centerLock.createNewFile()) {
						LOG.info("centerLock created");
						return centerLock;
					}
				} catch (IOException e1) {
					LOG.error("Error creating lock for database {}", database, e);
				}
        	} else {
        		return centerLock;
        	}
            return null;
        }
    }

    public static void releaseMasterdataLock(URI tempDir, String database) {
        try {
            File centerLock = new File(getPath(tempDir, database.toLowerCase(), LOCK_EXTENSION));
            centerLock.delete();
        } catch (Exception e) {
            LOG.error("Error releasing lock for database {}", database, e);
        }
    }

    public static void releaseMasterdataLock(File lock) {
        try {
            lock.delete();
        } catch (Exception e) {
            LOG.error("Error releasing lock for database", e);
        }
    }
}
