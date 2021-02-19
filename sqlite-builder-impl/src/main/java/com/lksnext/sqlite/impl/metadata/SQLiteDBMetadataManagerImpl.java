package com.lksnext.sqlite.impl.metadata;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.lksnext.sqlite.SQLiteDBCleanupStrategy;
import com.lksnext.sqlite.config.SQLitePropertyConfig;
import com.lksnext.sqlite.file.FileManager;
import com.lksnext.sqlite.impl.util.SQLitePathUtils;
import com.lksnext.sqlite.metadata.SQLiteDBFileInfo;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;
import com.lksnext.sqlite.metadata.SQLiteDBMetadataManager;

@Service
public class SQLiteDBMetadataManagerImpl implements SQLiteDBMetadataManager {
	
	private static final Logger LOG = LoggerFactory.getLogger(SQLiteDBMetadataManagerImpl.class);

	@Autowired
	private FileManager fileManager;

	@Autowired
	private SQLitePropertyConfig sqliteConfig;

	@Autowired(required = false)
	private List<SQLiteDBCleanupStrategy> cleanupStrategies;

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
		LOG.info("Start updating Metadata for Database {}", database);
		long metadataTime = System.currentTimeMillis();
		URI dbsBaseDir = sqliteConfig.getDatabasePath();
		URI metadataPath = SQLitePathUtils.getMasterdataMetadataPath(dbsBaseDir, database);
		try(BufferedWriter writer = Files.newBufferedWriter(Paths.get(metadataPath))) {
			Gson gson = new GsonBuilder().create();
			gson.toJson(sqliteDBMetadata, writer);
		}
		metadataTime = System.currentTimeMillis() - metadataTime;
        LOG.info("Metadata updated for Database {} in {} ms", database, metadataTime);
	}

	@Override
	public List<SQLiteDBFileInfo> addDBtoMetadata(SQLiteDBMetadata sqliteDBMetadata, String owner, String database,
			String file, String md5) throws URISyntaxException, IOException {
		
		SQLiteDBFileInfo current = sqliteDBMetadata.getCurrent();
		
		if (current!=null ) {
			sqliteDBMetadata.getPrevious().add(0, current);
		}
		
		current = new SQLiteDBFileInfo();
		current.setFile(file);
		current.setMd5(md5);
		sqliteDBMetadata.setCurrent(current);

		List<SQLiteDBFileInfo> toDelete = new ArrayList<SQLiteDBFileInfo>();
		if (cleanupStrategies != null) {
			for (SQLiteDBCleanupStrategy strategy : cleanupStrategies) {
				List<SQLiteDBFileInfo> toDeleteFromStrategy = strategy.selectDbsToCleanup(sqliteDBMetadata, owner,
						database);
				toDelete.addAll(toDeleteFromStrategy);
			}
		}

		List<SQLiteDBFileInfo> toDeleteFromMaxFiles = metadataCleanupBasedOnMaxDBs(sqliteDBMetadata);
		return Stream.of(toDelete, toDeleteFromMaxFiles).flatMap(Collection::stream).collect(Collectors.toList());
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

}
