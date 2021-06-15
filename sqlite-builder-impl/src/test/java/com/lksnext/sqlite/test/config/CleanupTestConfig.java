package com.lksnext.sqlite.test.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.lksnext.sqlite.SQLiteDBCleanupStrategy;
import com.lksnext.sqlite.metadata.SQLiteDBFileInfo;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;

@TestConfiguration
@ComponentScan({ "com.lksnext.sqlite" })
public class CleanupTestConfig {

	@Bean
	public SQLiteDBCleanupStrategy cleanupStrategy() {

		return new SQLiteDBCleanupStrategy() {

			@Override
			public List<SQLiteDBFileInfo> selectDbsToCleanup(SQLiteDBMetadata sqliteDBMetadata, String owner,
					String database) {
				SQLiteDBFileInfo fakedb = new SQLiteDBFileInfo();
				fakedb.setFile("fakedb");
				fakedb.setMd5("fakedb-hash");

				return Arrays.<SQLiteDBFileInfo>asList(fakedb);
			}

		};

	}

}
