package com.lksnext.sqlite.test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.lksnext.sqlite.SQLiteDBCleanupStrategy;
import com.lksnext.sqlite.metadata.SQLiteDBFileInfo;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;
import com.lksnext.sqlite.metadata.SQLiteDBMetadataManager;
import com.lksnext.sqlite.test.config.SQliteBuilderTestConfig;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@ContextConfiguration(classes = { SQliteBuilderTestConfig.class })
public class SQliteDBMetadataCleanupTest {

	@TestConfiguration
	static class TestConfig {

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

	@Autowired
	SQLiteDBMetadataManager sqliteDBMetadataManager;

	FileSystem fs;

	@Before
	public void setup() {
		fs = Jimfs.newFileSystem("databases", Configuration.unix());
	}

	@After
	public void cleanup() throws IOException {
		fs.close();
	}

	private void populateFileSystem() throws IOException, URISyntaxException {

		URI testUri = new URI("jimfs://databases/test/");
		Path testPath = Files.createDirectories(Paths.get(testUri));
		URI tmpUri = new URI("jimfs://databases/tmp/");
		Files.createDirectories(Paths.get(tmpUri));
		Path dbpath = Files.createDirectory(testPath.resolve("existingdb"));
		Path metadataPath = dbpath.resolve("metadata.json");
		URI metadata = getFileFromResource("assets/existingdb/metadata.json");
		Files.copy(metadata.toURL().openStream(), metadataPath);

	}

	private URI getFileFromResource(String fileName) throws URISyntaxException {

		ClassLoader classLoader = getClass().getClassLoader();
		URL resource = classLoader.getResource(fileName);
		if (resource == null) {
			throw new IllegalArgumentException("file not found! " + fileName);
		} else {
			// failed if files have whitespaces or special characters
			return resource.toURI();
		}

	}

	@Test
	public void identifyDatabasesToDelete() throws URISyntaxException, IOException {
		populateFileSystem();
		SQLiteDBMetadata metadata = sqliteDBMetadataManager.loadMetadata("existingdb");
		Assert.assertTrue(metadata.getPrevious().isEmpty());

		List<SQLiteDBFileInfo> toDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database",
				"/a/path/to/dbfile-1.db", "A HASH1");
		Assert.assertTrue(metadata.getPrevious().size() == 1);
		Assert.assertTrue(toDelete.get(0).getMd5().equals("fakedb-hash"));

		toDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database", "/a/path/to/dbfile-2.db",
				"A HASH2");
		Assert.assertTrue(metadata.getPrevious().size() == 2);
		Assert.assertTrue(toDelete.size() == 1);
		Assert.assertTrue(toDelete.get(0).getMd5().equals("fakedb-hash"));

		toDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database", "/a/path/to/dbfile-3.db",
				"A HASH3");
		Assert.assertTrue(metadata.getPrevious().size() == 2);
		Assert.assertTrue(toDelete.size() == 2);
		Assert.assertTrue( toDelete.stream().filter(info -> { return info.getMd5().equals("8d23ed6bde518ab13283d7352a4f91ba");}).count() == 1);
		Assert.assertTrue( toDelete.stream().filter(info -> { return info.getMd5().equals("fakedb-hash");}).count() == 1);
	}
}
