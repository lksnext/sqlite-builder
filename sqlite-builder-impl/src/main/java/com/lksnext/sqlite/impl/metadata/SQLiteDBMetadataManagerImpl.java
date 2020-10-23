package com.lksnext.sqlite.impl.metadata;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.lksnext.sqlite.config.SQLitePropertyConfig;
import com.lksnext.sqlite.file.FileManager;
import com.lksnext.sqlite.impl.SQLiteDBPersistManagerImpl;
import com.lksnext.sqlite.impl.util.SQLitePathUtils;
import com.lksnext.sqlite.metadata.SQLiteDBFileInfo;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;
import com.lksnext.sqlite.metadata.SQLiteDBMetadataManager;

@Service
public class SQLiteDBMetadataManagerImpl implements SQLiteDBMetadataManager {
	
	 private static final Logger LOG = LoggerFactory.getLogger(SQLiteDBPersistManagerImpl.class);

	@Autowired
	private FileManager fileManager;

	@Autowired
	private SQLitePropertyConfig sqliteConfig;

	@Override
	public SQLiteDBMetadata loadMetadata(String database) throws URISyntaxException, IOException {
		Gson gson = new Gson();
		URI dbsBaseDir = sqliteConfig.getDatabasePath();
		URI metaDataPath = SQLitePathUtils.getMasterdataMetadataPath(dbsBaseDir, database);
		boolean metadataFileExits = fileManager.fileExists(metaDataPath.toString());
		if (metadataFileExits) {
			try (JsonReader reader = new JsonReader(new FileReader(metaDataPath.getPath()))) {
				return gson.fromJson(reader, SQLiteDBMetadata.class);
			}
		} else {
			return new SQLiteDBMetadata();
		}
	}

	@Override
	public boolean existsMetadata(String database) throws URISyntaxException {
		URI dbsBaseDir = sqliteConfig.getDatabasePath();
		URI centerMetaDataPath = SQLitePathUtils.getMasterdataMetadataPath(dbsBaseDir, database);
		return fileManager.fileExists(centerMetaDataPath.toString());
	}

	@Override
	public void saveMetadata(SQLiteDBMetadata sqliteDBMetadata, String database)
			throws URISyntaxException, IOException {
		URI dbsBaseDir = sqliteConfig.getDatabasePath();
		URI metadataPath = SQLitePathUtils.getMasterdataMetadataPath(dbsBaseDir, database);
		try (Writer writer = new FileWriter(new File(metadataPath))) {
			Gson gson = new GsonBuilder().create();
			gson.toJson(sqliteDBMetadata, writer);
		}
	}

	@Override
	public List<SQLiteDBFileInfo> addDBtoMetadata(SQLiteDBMetadata sqliteDBMetadata, String user, String database,
			String file, String md5) throws URISyntaxException, IOException {
		if (!StringUtils.isEmpty(sqliteDBMetadata.getCurrent().getMd5())) {
			SQLiteDBFileInfo current = new SQLiteDBFileInfo();
			current.setFile(sqliteDBMetadata.getCurrent().getFile());
			current.setMd5(sqliteDBMetadata.getCurrent().getMd5());
			sqliteDBMetadata.getPrevious().add(0, current);
		}
		sqliteDBMetadata.getCurrent().setFile(file);
		sqliteDBMetadata.getCurrent().setMd5(md5);

		List<SQLiteDBFileInfo> toDeleteFromContacts = metadataCleanupBasedOnContacts(sqliteDBMetadata, user, database);
		List<SQLiteDBFileInfo> toDeleteFromMaxFiles = metadataCleanupBasedOnMaxDBs(sqliteDBMetadata);
		return Stream.of(toDeleteFromContacts, toDeleteFromMaxFiles).flatMap(Collection::stream)
				.collect(Collectors.toList());
	}

	private List<SQLiteDBFileInfo> metadataCleanupBasedOnMaxDBs(SQLiteDBMetadata sqliteDBMetadata) {

		List<SQLiteDBFileInfo> previous = sqliteDBMetadata.getPrevious();
		if (previous == null || previous.size() == 0) {
			return Collections.<SQLiteDBFileInfo>emptyList();
		}

		if (previous.size() > sqliteConfig.getMaxDBCopyNumber()) {
			List<SQLiteDBFileInfo> toDelete = previous.subList(sqliteConfig.getMaxDBCopyNumber(), previous.size());
			sqliteDBMetadata.setPrevious(previous.subList(0, sqliteConfig.getMaxDBCopyNumber()));
			return toDelete;
		}

		return Collections.<SQLiteDBFileInfo>emptyList();
	}

	private List<SQLiteDBFileInfo> metadataCleanupBasedOnContacts(SQLiteDBMetadata sqliteDBMetadata, String user,
			String database) {
		try {
			List<SQLiteDBFileInfo> previous = sqliteDBMetadata.getPrevious();
			if (previous == null || previous.size() == 0) {
				return Collections.<SQLiteDBFileInfo>emptyList();
			}

			List<SQLiteDBFileInfo> toKeep = new ArrayList<SQLiteDBFileInfo>();
			List<SQLiteDBFileInfo> toDelete = new ArrayList<SQLiteDBFileInfo>();
			
			//TODO jurkiri
			/*String lastSeendMd5 = syncStatusManger.findLastUserCheckpoint(user, database);
			String lastSentMd5 = syncStatusManger.findLastUserCheckpoint(user, "last-sent-" + database);

			for (SQLiteDBFileInfo info : previous) {
				if (info.getMd5().equals(lastSeendMd5) || info.getMd5().equals(lastSentMd5)) {
					toKeep.add(info);
				} else {
					toDelete.add(info);
				}
			}

			sqliteDBMetadata.setPrevious(toKeep);*/
			return toDelete;
		} catch (Exception e) {
			LOG.error("Error purging based on contacts", e);
			return Collections.<SQLiteDBFileInfo>emptyList();
		}
	}

}