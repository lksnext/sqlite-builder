package com.lksnext.sqlite.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.lksnext.sqlite.SQLiteDBConfigurableGenerator;
import com.lksnext.sqlite.SQLiteDBPersistManager;
import com.lksnext.sqlite.config.SQLitePropertyConfig;
import com.lksnext.sqlite.impl.definition.DatabaseDefinition;
import com.lksnext.sqlite.impl.definition.SchemaElement;
import com.lksnext.sqlite.impl.util.SQLitePathUtils;
import com.lksnext.sqlite.impl.util.SQLiteUtils;

@Service
public class SQLiteDBConfigurableGeneratorImpl implements SQLiteDBConfigurableGenerator {

	private static final Logger LOG = LoggerFactory.getLogger(SQLiteDBConfigurableGeneratorImpl.class);

	@Autowired
	@Qualifier("builderDs")
	private DataSource dataSource;

	@Autowired
	private SQLitePropertyConfig sqliteConfig;

	@Autowired
	private SQLiteDBPersistManager sqliteDBPersistManager;

	private static final String FINAL = "final";
	private static final String YML_EXT = ".yml";
	private static final String YAML_EXT = ".yaml";

	@Override
	public synchronized void createDatabase(String definition, Map<String, String> context) {
		buildDatabase(definition, context, new HashSet<String>());
	}

	@Override
	public synchronized void createDatabases(String definition, List<Map<String, String>> contexts) {
		LOG.info("START creating databases {} - {}", definition, contexts);
		StopWatch timing = new StopWatch("SQLite database generation");
		AtomicInteger atomicInteger = new AtomicInteger(0);
		int totalDbs = contexts.size();
		Set<String> avalableDatabases = new HashSet<String>();

		for (Map<String, String> context : contexts) {
			String description = context.entrySet().stream().map(x -> {
				return (x.getKey() + "=" + x.getValue());
			}).collect(Collectors.joining(","));

			int idx = atomicInteger.incrementAndGet();
			LOG.info("{}/{} Processing DB {}", idx, totalDbs, description);
			timing.start("Generate db " + description);
			buildDatabase(definition, context, avalableDatabases);
			timing.stop();
		}

		LOG.info("Finish SQLite database generation");
		LOG.info(timing.prettyPrint());
	}

	private String  buildDatabase(String definition, Map<String, String> context, Set<String> availableDatabases) {
		DatabaseDefinition database = getDatabaseDefinition(definition);

		String dbName = StrSubstitutor.replace(database.getDatabase(), context);
		LOG.info("buildDatabase {} ", dbName);

		if (availableDatabases.contains(dbName)) {
			LOG.info("Database {} is already built", dbName);
			return dbName;
		}
		
		
		if (isDBLocked(dbName)) {
			LOG.warn("Database {} is locked. Generation is cancelled", dbName);
			return null;
		}

		String extendsFrom = database.getExtends();
		String extendedDb = null;
		if (StringUtils.isNotEmpty(extendsFrom)) {
			LOG.info("Database {} extends from {}", database.getDatabase(), extendsFrom);
			extendedDb = buildDatabase(extendsFrom, context, availableDatabases);
		}

		long dbCreationTime = System.currentTimeMillis();
		try (Connection sqliteCon = createNewDatabase(extendedDb, database, context)) {
			createLockForDB(dbName);

			for (SchemaElement table : database.getSchema()) {
				populateTableData(sqliteCon, table, context);
			}
			sqliteCon.commit();
			SQLiteUtils.vacuum(sqliteCon);
			sqliteCon.close();
			availableDatabases.add(dbName);
			dbCreationTime = System.currentTimeMillis() - dbCreationTime;
			LOG.info("Database {} created in {} ms", database.getDatabase(), dbCreationTime);
			if (FINAL.equalsIgnoreCase(database.getType())) {
				sqliteDBPersistManager.persist(dbName, dbName);
			}
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("The file name or path is incorrect.", e);
		} catch (IOException | ClassNotFoundException | SQLException e) {
			throw new IllegalStateException("An error ocurred while creating the database", e);
		} finally {
			try {
				releaseLockForDB(dbName);
			} catch (Exception e) {
				LOG.error("Unable to remove lock for db {}", dbName, e);
			}
		}
		
		return dbName;
	}

	private Connection createNewDatabase(String extendedDb, DatabaseDefinition definition, Map<String, String> context)
			throws ClassNotFoundException, URISyntaxException, SQLException, IOException {

		String dbName = StrSubstitutor.replace(definition.getDatabase(), context);

		String extendsFrom = definition.getExtends();
		if (StringUtils.isNotEmpty(extendsFrom)) {
			return SQLiteUtils.createNewDatabaseFrom(sqliteConfig.getTemporalPath(), extendedDb, dbName);
		} else {
			return SQLiteUtils.createNewDatabase(sqliteConfig.getTemporalPath(), dbName);
		}

	}

	private DatabaseDefinition getDatabaseDefinition(String definition) {
		Yaml yaml = new Yaml(new Constructor(DatabaseDefinition.class));
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(definition + YML_EXT);
		if (inputStream == null) {
			inputStream = this.getClass().getClassLoader().getResourceAsStream(definition + YAML_EXT);
		}

		DatabaseDefinition database = yaml.load(inputStream);
		return database;
	}

	private void populateTableData(Connection sqliteCon, SchemaElement table, Map<String, String> context)
			throws SQLException {
		String query = table.getSource();
		LOG.debug("populateTableData query: " + query);
		String cleanup = table.getCleanup();
		if (StringUtils.isNotEmpty(query)) {
			if (context != null) {
				SQLiteUtils.importData(dataSource, sqliteCon, query, table.getTable(), context);
			} else {
				SQLiteUtils.importData(dataSource, sqliteCon, query, table.getTable());
			}
		}

		if (StringUtils.isNotEmpty(cleanup)) {
			LOG.debug("Cleaning table {}...", table.getTable());

			if (context != null) {
				SQLiteUtils.cleanupAction(sqliteCon, cleanup, context);
			} else {
				SQLiteUtils.cleanupAction(sqliteCon, cleanup);
			}

		}
	}

	private void createLockForDB(String dbFilename) throws URISyntaxException, IOException {
		SQLitePathUtils.createMasterdataLock(sqliteConfig.getDatabasePath(), dbFilename);
	}

	private void releaseLockForDB(String dbFilename) throws URISyntaxException, IOException {
		SQLitePathUtils.releaseMasterdataLock(sqliteConfig.getDatabasePath(), dbFilename);
	}

	private boolean isDBLocked(String dbFilename) {
		return SQLitePathUtils.isMasterdataLocked(sqliteConfig.getDatabasePath(), dbFilename);
	}
}
