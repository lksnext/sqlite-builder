package com.lksnext.sqlite.impl.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Map.Entry;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class DatabaseUtil {

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseUtil.class);

	public static GenericTable executeQuery(DSLContext create, DataSource ds, String query, String tableName)
			throws SQLException {
		
		Connection con = DataSourceUtils.getConnection(ds);
		try (Statement stmt = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				ResultSet rs = stmt.executeQuery(query);) {
			rs.setFetchSize(10000);
			
			return ResultSetImporter.convertResultSetSQLite(create, rs, tableName);

		} finally {
			DataSourceUtils.releaseConnection(con, ds);
		}
	}

	public static GenericTable executeQuery(DSLContext create, DataSource ds, String query, String tableName,
			String... params) throws SQLException {

		ResultSet rs = null;
		Connection con = DataSourceUtils.getConnection(ds);

		try (PreparedStatement stmt = con.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY)) {
			int idx = 1;
			for (String param : params) {
				stmt.setString(idx, param);
				idx++;
			}
			rs = stmt.executeQuery();
			rs.setFetchSize(10000);

			return ResultSetImporter.convertResultSetSQLite(create, rs, tableName);
		} catch (DataAccessException e) {
			LOG.error("Error importing table {}", tableName, e);
			LOG.error("Query: {}", query);
			throw e;
		} finally {
			if (rs != null) {
				rs.close();
			}
			DataSourceUtils.releaseConnection(con, ds);
		}
	}

	public static GenericTable executeQuery(DSLContext create, DataSource ds, String query, String tableName,
			Map<String, String> params) throws SQLException {

		ResultSet rs = null;
		Connection con = DataSourceUtils.getConnection(ds);

		try (NamedParameterStatement stmt = new NamedParameterStatement(con, query, ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY)) {

			for (Entry<String, String> p : params.entrySet()) {
				stmt.setString(p.getKey(), p.getValue());
			}

			rs = stmt.executeQuery();
			rs.setFetchSize(10000);

			return ResultSetImporter.convertResultSetSQLite(create, rs, tableName);
		} catch (DataAccessException e) {
			LOG.error("Error importing table {}", tableName, e);
			LOG.error("Query: {}", query);
			throw e;
		} finally {
			if (rs != null) {
				rs.close();
			}
			DataSourceUtils.releaseConnection(con, ds);
		}
	}

}
