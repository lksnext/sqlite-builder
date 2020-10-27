package com.lksnext.sqlite;

import java.util.List;

import com.lksnext.sqlite.metadata.SQLiteDBFileInfo;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;

public interface SQLiteDBCleanupStrategy {

	List<SQLiteDBFileInfo> selectDbsToCleanup(SQLiteDBMetadata sqliteDBMetadata, String owner, String database);

}
