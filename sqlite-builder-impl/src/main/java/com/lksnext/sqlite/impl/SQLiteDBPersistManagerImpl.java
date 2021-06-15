package com.lksnext.sqlite.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.davidehrmann.vcdiff.VCDiffEncoderBuilder;
import com.lksnext.sqlite.SQLiteDBPersistManager;
import com.lksnext.sqlite.config.SQLitePropertyConfig;
import com.lksnext.sqlite.file.FileManager;
import com.lksnext.sqlite.impl.util.SQLitePathUtils;
import com.lksnext.sqlite.metadata.SQLiteDBFileInfo;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;
import com.lksnext.sqlite.metadata.SQLiteDBMetadataManager;

@Service
public class SQLiteDBPersistManagerImpl implements SQLiteDBPersistManager {

	private static final Logger LOG = LoggerFactory.getLogger(SQLiteDBPersistManagerImpl.class);

	@Autowired
	private FileManager fileManager;

	@Autowired
	private SQLitePropertyConfig sqliteConfig;

	@Autowired
	private SQLiteDBMetadataManager sqliteDBMetadataManager;

	@Override
	public void persist(String owner, String database) throws URISyntaxException, IOException {
		persist(owner, database, true);
	}

	@Override
	public void persist(String owner, String database, boolean createDiffPatches)
			throws URISyntaxException, IOException {

		String newMD5 = calculateMD5(sqliteConfig.getTemporalPath(), database);
		SQLiteDBMetadata metadata = sqliteDBMetadataManager.loadMetadata(database);
		SQLiteDBFileInfo current = metadata.getCurrent();

		if (current == null || !current.getMd5().equals(newMD5)) {
			LOG.info("New database generated {} {} {}", owner, database, newMD5);
			URI srcURI = SQLitePathUtils.getTemporalDBPath(sqliteConfig.getTemporalPath(), database);
			URI destURI = SQLitePathUtils.getMasterdataDBPath(sqliteConfig.getDatabasePath(), database, newMD5);

			long fileWriteTime = System.currentTimeMillis();
			fileManager.moveFile(srcURI, destURI);
			fileWriteTime = System.currentTimeMillis() - fileWriteTime;
			LOG.info("Database {} file written in {} ms", database, fileWriteTime);

			LOG.info("Start addDBtoMetadata for Database {}", database);
			long addDbToMetadataTime = System.currentTimeMillis();
			List<SQLiteDBFileInfo> dbsToDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, owner, database,
					destURI.toString(), newMD5);
			// Paths.get(destURI).toString()
			addDbToMetadataTime = System.currentTimeMillis() - addDbToMetadataTime;
			LOG.info("addDBtoMetadata for Database {} finished in {} ms", database, addDbToMetadataTime);

			if (!sqliteConfig.isSymLinkDisabled()) {
				LOG.info("Start creatingSymLink for Database {}", database);
				long symLinkTime = System.currentTimeMillis();
				updateOrCreateSymLinkToLatest(metadata, sqliteConfig.getDatabasePath(), database);
				symLinkTime = System.currentTimeMillis() - symLinkTime;
				LOG.info("SymLink created for Database {} in {} ms", database, symLinkTime);
			}

			if (dbsToDelete != null) {
				for (SQLiteDBFileInfo db : dbsToDelete) {
					LOG.info("Start removing file {}", db.getFile());
					long removeTime = System.currentTimeMillis();
					fileManager.removeFile(Paths.get(db.getFile()).toUri());
					removeTime = System.currentTimeMillis() - removeTime;
					LOG.info("File {} removed in {} ms", database, removeTime);
				}
			}
			sqliteDBMetadataManager.saveMetadata(metadata, database);

			saveZippedLatestDb(database, destURI);

			deletePatches(database);

			if (createDiffPatches) {
				createPatches(metadata, database);
			}
		}
		purgeOrphanedDatabaseFiles(metadata, database);
	}

	@Override
	public void fixSymbolicLinks(String database) throws IOException {
		SQLiteDBMetadata metadata = null;
		try {
			metadata = sqliteDBMetadataManager.loadMetadata(database);

		} catch (URISyntaxException e) {
			LOG.error("Error loading metadata", e);
			return;
		} catch (IOException e) {
			LOG.error("Error loading metadata", e);
			return;
		}
		Path currentPath = Paths.get(metadata.getCurrent().getFile());

		Path latestLink = Paths
				.get(SQLitePathUtils.getMasterdataLatestDBPath(sqliteConfig.getDatabasePath(), database));

		if (!Files.exists(latestLink, LinkOption.NOFOLLOW_LINKS)) {
			Files.createSymbolicLink(latestLink, currentPath);
			LOG.info("Latest link for {} does not exists. fixed", database);
			return;
		}

		Path latestFile = Files.readSymbolicLink(latestLink);

		if (Files.exists(latestFile) && Files.isSameFile(currentPath, latestFile)) {
			LOG.info("Latest link for {} is correct", database);
		} else {
			if (!Files.isSymbolicLink(latestLink)) {
				LOG.warn("latest.db for {} is not a symbolic link ", database);
			}

			fileManager.removeFile(latestLink.toUri());
			Files.createSymbolicLink(latestLink, currentPath);
			LOG.warn("Latest link for {} fixed", database);
		}
	}

	private void purgeOrphanedDatabaseFiles(SQLiteDBMetadata metadata, String database) throws IOException {
		URI folderURI = SQLitePathUtils.getMasterdataDBFolderPath(sqliteConfig.getDatabasePath(), database);
		Path basePath = Paths.get(folderURI);
		List<String> filenames = fileManager.getFolderFilenames(folderURI);
		for (String filename : filenames) {
			if ("db".equals(FilenameUtils.getExtension(filename))) {
				final Path db = Paths.get(basePath.toString(), filename);
				SQLiteDBFileInfo info = metadata.getPrevious().stream().filter(m -> isSameFile(m, db)).findFirst()
						.orElse(null);
				if (info == null && !isSameFile(metadata.getCurrent(), db)) {
					fileManager.removeFile(db.toUri());
				}
			}
		}
	}

	private boolean isSameFile(SQLiteDBFileInfo info, Path db) {
		try {
			URI file = new URI(info.getFile());
			return Files.isSameFile(Paths.get(file), db);
		} catch (Exception e) {
			return false;
		}
	}

	private String calculateMD5(URI tempDir, String database) throws URISyntaxException, IOException {
		URI dbPath = SQLitePathUtils.getTemporalDBPath(tempDir, database);
		String md5 = "";
		try (InputStream fis = Files.newInputStream(Paths.get(dbPath))) {
			md5 = DigestUtils.md5Hex(fis);
		}
		return md5;
	}

	private void updateOrCreateSymLinkToLatest(SQLiteDBMetadata sqliteDBMetadata, URI targetDir, String database) {
		try {
			fileManager.removeFile(SQLitePathUtils.getMasterdataLatestDBPath(targetDir, database));
			URI dbfile = new URI(sqliteDBMetadata.getCurrent().getFile());
			Files.createSymbolicLink(Paths.get(SQLitePathUtils.getMasterdataLatestDBPath(targetDir, database)),
					Paths.get(dbfile));
		} catch (Exception x) {
			LOG.error("Error deleting or updating last db sym link.", x);
		}
	}

	private void deletePatches(String database) {
		LOG.info("Deleting all patches for database {}", database);
		long deletePatchesTime = System.currentTimeMillis();
		try {

			URI masterdataFolderURI = SQLitePathUtils.getMasterdataDBFolderPath(sqliteConfig.getDatabasePath(),
					database);
			Files.list(Paths.get(masterdataFolderURI)).forEach(p -> {
				String fileName = p.getFileName().toString();
				if (FilenameUtils.getExtension(fileName).equalsIgnoreCase(".patch")) {
					long patchTime = System.currentTimeMillis();
					try {
						Files.deleteIfExists(p);
					} catch (IOException e) {
						LOG.error("Error deleting file {}", p, e);
					}
					patchTime = System.currentTimeMillis() - patchTime;
					LOG.info("Patch {} deleted for database {} in {} ms", fileName, database, patchTime);
				}
			});

		} catch (Exception e) {
			LOG.error("Error deleting patches for center {}", database, e);
		}

		deletePatchesTime = System.currentTimeMillis() - deletePatchesTime;
		LOG.info("All patches deleted for database {} in {} ms", database, deletePatchesTime);
	}

	private void createPatches(SQLiteDBMetadata sqliteDBMetadata, String database) throws URISyntaxException {

		int patchCount = 0;
		while (patchCount < sqliteConfig.getMaxPatchNumber() && patchCount < sqliteDBMetadata.getPrevious().size()) {

			URI prev = new URI(sqliteDBMetadata.getPrevious().get(patchCount).getFile());
			URI current = new URI(sqliteDBMetadata.getCurrent().getFile());
			try {
				long patchCreationTime = System.currentTimeMillis();
				createPatch(prev, current, sqliteDBMetadata.getPrevious().get(patchCount).getMd5(), database);
				patchCreationTime = System.currentTimeMillis() - patchCreationTime;
				LOG.info("Patch {} for database {} created in {} ms",
						sqliteDBMetadata.getPrevious().get(patchCount).getMd5(), database, patchCreationTime);
			} catch (Exception e) {
				LOG.error("Error generating patch {} for center {}", patchCount, database, e);
			}
			patchCount++;
		}

	}

	@Override
	public void createPatchForMD5(SQLiteDBMetadata sqliteDBMetadata, String database, String md5) {

		if (md5 == null) {
			return;
		}

		List<SQLiteDBFileInfo> prev = sqliteDBMetadata.getPrevious();

		SQLiteDBFileInfo fileInfo = prev.stream().filter(info -> {
			return md5.equals(info.getMd5());
		}).findFirst().orElse(null);

		if (fileInfo != null) {
			try {
				URI previous = new URI(fileInfo.getFile());
				URI current = new URI(sqliteDBMetadata.getCurrent().getFile());

				createPatch(previous, current, fileInfo.getMd5(), database);
			} catch (Exception e) {
				LOG.error("Error generating patch {} for center {}", md5, database, e);
			}
		}
	}

	@Override
	public boolean existsDBForMD5(SQLiteDBMetadata sqliteDBMetadata, String database, String md5) {

		if (md5 == null) {
			return false;
		}

		List<SQLiteDBFileInfo> prev = sqliteDBMetadata.getPrevious();

		SQLiteDBFileInfo fileInfo = prev.stream().filter(info -> {
			return md5.equals(info.getMd5());
		}).findFirst().orElse(null);

		return fileInfo != null;
	}

	private void createPatch(URI previous, URI current, String md5, String database) throws Exception {

		OutputStream out = null;
		OutputStream vcDiffOut = null;

		try (InputStream is = Files.newInputStream(Paths.get(previous));
				InputStream isTarget = Files.newInputStream(Paths.get(current));) {

			byte[] dictionary = IOUtils.toByteArray(is);

			Path patchFilePath = Paths
					.get(SQLitePathUtils.getMasterdataPatchPath(sqliteConfig.getDatabasePath(), database, md5));
			Files.createFile(patchFilePath);

			out = Files.newOutputStream(patchFilePath);

			vcDiffOut = VCDiffEncoderBuilder.builder().withDictionary(dictionary).buildOutputStream(out);

			IOUtils.copyLarge(isTarget, vcDiffOut, new byte[32 * 1024]);

		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (Exception e) {
					// Ignore
				}
			}

			if (vcDiffOut != null) {
				try {
					vcDiffOut.close();
				} catch (Exception e) {
					// Ignore
				}
			}
		}
	}

	private void saveZippedLatestDb(String database, URI file) {
		deleteLatestZippedDb(database);// sqliteConfig.getDatabasePath()

		URI folderURI = SQLitePathUtils.getMasterdataDBFolderPath(sqliteConfig.getDatabasePath(), database);
		URI zip = SQLitePathUtils.getMasterdataLatestZipPath(folderURI);
		Path fileToZip = Paths.get(file);
		Path finalZip = Paths.get(zip);

		try (OutputStream fos = Files.newOutputStream(finalZip);
				ZipOutputStream zipOut = new ZipOutputStream(fos);
				InputStream fis = Files.newInputStream(fileToZip);) {

			ZipEntry zipEntry = new ZipEntry(SQLitePathUtils.LATEST_DB_NAME);
			zipOut.putNextEntry(zipEntry);
			byte[] bytes = new byte[1024];
			int length;
			while ((length = fis.read(bytes)) >= 0) {
				zipOut.write(bytes, 0, length);
			}
		} catch (Exception e) {
			LOG.error("Error creting latest.zip for database {}", e);
		}
	}

	private void deleteLatestZippedDb(String database) {
		try {
			URI folderURI = SQLitePathUtils.getMasterdataDBFolderPath(sqliteConfig.getDatabasePath(), database);
			URI zip = SQLitePathUtils.getMasterdataLatestZipPath(folderURI);
			fileManager.removeFile(zip);
		} catch (Exception e) {
			LOG.error("Error deleting latest.zip for database {}", e);
		}
	}
}
