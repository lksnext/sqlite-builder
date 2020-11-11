package com.lksnext.sqlite.impl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.sqlite.SQLiteException;

public class SQLiteUtils {

	private static final Logger LOG = LoggerFactory.getLogger(SQLiteUtils.class);

	public static final Connection createNewDatabaseFrom(URI tempDir, String fromDBName, String toDBName)
			throws ClassNotFoundException, URISyntaxException, SQLException {
		LOG.info("Creating new sqlite db {} from {}", toDBName, fromDBName);
		Class.forName("org.sqlite.JDBC");

		String url = SQLitePathUtils.getDBURL(tempDir, toDBName);
		URI destinationDbUri = SQLitePathUtils.getTemporalDBPath(tempDir, toDBName);
		URI sourceDbUri = SQLitePathUtils.getTemporalDBPath(tempDir, fromDBName);
		File soruceDbFile = new File(sourceDbUri);
		Assert.isTrue(soruceDbFile.exists(), "The source file for " + fromDBName + " does not exists");
		LOG.debug("DB file {}.db found", fromDBName);

		File destinationDbFile = new File(destinationDbUri);
		Assert.isTrue(!destinationDbFile.isDirectory(), "Cannot delete: " + destinationDbUri + " is a DIRECTORY!");
		if (FileUtils.deleteQuietly(destinationDbFile)) {
			LOG.debug("Existing sqlite db deleted {}", toDBName);
		}
		// Copy origin db into destination
		try {
			try (FileInputStream input = new FileInputStream(soruceDbFile);
					FileOutputStream output = new FileOutputStream(destinationDbFile);) {
				IOUtils.copy(input, output);
			}
		} catch (Exception e) {
			LOG.error("Error manipulating DB file {}", toDBName, e);
		}
		Connection conn = DriverManager.getConnection(url);
		LOG.debug("New database created {}", toDBName);
		try (Statement stmt = conn.createStatement()) {
			stmt.execute("PRAGMA synchronous=OFF");
			stmt.execute("PRAGMA count_changes=OFF");
			stmt.execute("PRAGMA journal_mode=MEMORY");
			stmt.execute("PRAGMA temp_store=MEMORY");
		}
		conn.setAutoCommit(false);
		return conn;
	}

	public static final Connection createNewDatabase(URI tempDir, String fileName)
			throws URISyntaxException, SQLException, ClassNotFoundException, IOException {
		LOG.info("Creating new sqlite db {}", fileName);

		Class.forName("org.sqlite.JDBC");

		Path dir = Paths.get(tempDir);
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
		}

		URI dbPath = SQLitePathUtils.getTemporalDBPath(tempDir, fileName);
		String url = SQLitePathUtils.getDBURL(tempDir, fileName);

		File dbFile = new File(dbPath);
		Assert.isTrue(!dbFile.isDirectory(), "Cannot delete: " + dbPath + " is a DIRECTORY!");
		if (FileUtils.deleteQuietly(dbFile)) {
			LOG.debug("Existing sqlite db deleted {}", fileName);
		}

		Connection conn = DriverManager.getConnection(url);
		LOG.debug("New database created {}", fileName);

		try (Statement stmt = conn.createStatement()) {
			stmt.execute("PRAGMA synchronous=OFF");
			stmt.execute("PRAGMA count_changes=OFF");
			stmt.execute("PRAGMA journal_mode=MEMORY");
			stmt.execute("PRAGMA temp_store=MEMORY");
		}

		conn.setAutoCommit(false);
		return conn;

	}

	public static final GenericTable importData(DataSource ds, Connection sqliteConnection, String query, String table)
			throws SQLException {
		LOG.info("Importing {}...", table);
		DSLContext create = DSL.using(sqliteConnection, SQLDialect.SQLITE);
		GenericTable tableDefinition = DatabaseUtil.executeQuery(create, ds, query, table);
		sqliteConnection.commit();
		return tableDefinition;
	}

	public static final GenericTable importData(DataSource ds, Connection sqliteConnection, String query, String table,
			String... params) throws SQLException {
		LOG.info("Importing {}...", table);
		DSLContext create = DSL.using(sqliteConnection, SQLDialect.SQLITE);
		GenericTable tableDefinition = DatabaseUtil.executeQuery(create, ds, query, table, params);
		sqliteConnection.commit();
		return tableDefinition;
	}

	public static final GenericTable importData(DataSource ds, Connection sqliteConnection, String query, String table,
			Map<String, String> params) throws SQLException {
		LOG.info("Importing {}...", table);
		DSLContext create = DSL.using(sqliteConnection, SQLDialect.SQLITE);
		GenericTable tableDefinition = DatabaseUtil.executeQuery(create, ds, query, table, params);
		sqliteConnection.commit();
		return tableDefinition;
	}

	public static final GenericTable importUserData(DataSource ds, Connection sqliteConnection, String query,
			String table, String user) throws SQLException {
		LOG.info("Importing {}...", table);
		DSLContext create = DSL.using(sqliteConnection, SQLDialect.SQLITE);
		GenericTable tableDefinition = DatabaseUtil.executeQuery(create, ds, query, table, user);
		sqliteConnection.commit();
		return tableDefinition;
	}

	public static final void cleanupAction(Connection sqliteCon, String sql) throws SQLException {
		try (PreparedStatement stm = sqliteCon.prepareStatement(sql)) {
			int rows = stm.executeUpdate();
			LOG.info("Cleaned {} rows form SQLite DB", rows);
		} catch (SQLiteException e) {
			LOG.warn("Error executing cleanup action in SQLite: {}", e.getMessage());
		}
	}

	public static final void cleanupAction(Connection sqliteCon, String sql, String... params) throws SQLException {
		try (PreparedStatement stm = sqliteCon.prepareStatement(sql)) {

			int paramIdx = 0;
			for (String param : params) {
				paramIdx++;
				stm.setString(paramIdx, param);
			}

			int rows = stm.executeUpdate();
			LOG.info("Cleaned {} rows from SQLite DB", rows);
		} catch (SQLiteException e) {
			LOG.warn("Error executing cleanup action in SQLite: {}", e.getMessage());
		}
	}

	public static final void cleanupAction(Connection sqliteCon, String sql, Map<String, String> params)
			throws SQLException {

		try (NamedParameterStatement stm = new NamedParameterStatement(sqliteCon, sql)) {

			for (Entry<String, String> p : params.entrySet()) {
				stm.setString(p.getKey(), p.getValue());
			}

			int rows = stm.executeUpdate();
			LOG.info("Cleaned {} rows from SQLite DB", rows);
		} catch (SQLiteException e) {
			LOG.warn("Error executing cleanup action in SQLite: {}", e.getMessage());
		}
	}

	public static final void vacuum(Connection sqliteCon) throws SQLException {
		sqliteCon.setAutoCommit(true);
		try (Statement stm = sqliteCon.createStatement()) {
			stm.execute("PRAGMA auto_vacuum = 1");
			stm.execute("VACUUM");
		}
	}

	public static void createIndex(Connection sqliteConnection, String indexPrefix, String tableName,
			List<String> columns) {
		DSLContext create = DSL.using(sqliteConnection, SQLDialect.SQLITE);
		create.createIndex(indexPrefix + tableName).on(tableName, columns).execute();
	}
}
