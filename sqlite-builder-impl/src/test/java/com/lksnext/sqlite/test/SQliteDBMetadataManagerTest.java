package com.lksnext.sqlite.test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.lksnext.sqlite.metadata.SQLiteDBFileInfo;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;
import com.lksnext.sqlite.metadata.SQLiteDBMetadataManager;
import com.lksnext.sqlite.test.config.SQliteBuilderTestConfig;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@ContextHierarchy(@ContextConfiguration(classes = { SQliteBuilderTestConfig.class }))
public class SQliteDBMetadataManagerTest {

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

	@Test
	public void dependenciesAreNotNull() {
		Assert.assertNotNull(sqliteDBMetadataManager);
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
	public void loadEmptyDatabaseMetadata() throws IOException {
		try {
			SQLiteDBMetadata metadata = sqliteDBMetadataManager.loadMetadata("dummydb");
			Assert.assertNotNull(metadata);
			Assert.assertNull(metadata.getCurrent());
			Assert.assertTrue(metadata.getPrevious().isEmpty());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.assertTrue(false);
		}
	}

	@Test
	public void existsMissingDatabaseMetadata() throws IOException {
		try {
			Assert.assertFalse(sqliteDBMetadataManager.existsMetadata("dummydb"));
		} catch (Exception e) {
			Assert.assertTrue(false);
		}
	}

	@Test
	public void existsPresentDatabaseMetadata() throws IOException {
		try {
			populateFileSystem();
			Assert.assertTrue(sqliteDBMetadataManager.existsMetadata("existingdb"));
		} catch (Exception e) {
			e.printStackTrace();
			Assert.assertTrue(false);
		}
	}

	@Test
	public void loadExistingDatabaseMetadata() throws IOException, URISyntaxException {
		populateFileSystem();
		SQLiteDBMetadata metadata = sqliteDBMetadataManager.loadMetadata("existingdb");
		Assert.assertNotNull(metadata);
		Assert.assertNotNull(metadata.getCurrent());
		Assert.assertTrue(metadata.getCurrent().getMd5().equals("8d23ed6bde518ab13283d7352a4f91ba"));
	}

	@Test
	public void saveMetadata() throws URISyntaxException, IOException {
		populateFileSystem();
		SQLiteDBMetadata metadata = new SQLiteDBMetadata();
		SQLiteDBFileInfo current = new SQLiteDBFileInfo();
		current.setFile("/a/path/to/dbfile.db");
		current.setMd5("A HASH");
		metadata.setCurrent(current);
		sqliteDBMetadataManager.saveMetadata(metadata, "newdb");

		SQLiteDBMetadata newMetadata = sqliteDBMetadataManager.loadMetadata("newdb");
		Assert.assertNotNull(newMetadata);
		Assert.assertNotNull(newMetadata.getCurrent());
		Assert.assertTrue(newMetadata.getCurrent().getMd5().equals("A HASH"));
		Assert.assertTrue(newMetadata.getCurrent().getFile().equals("/a/path/to/dbfile.db"));
	}

	@Test
	public void addDatabaseToMetadata() throws URISyntaxException, IOException {
		populateFileSystem();
		SQLiteDBMetadata metadata = sqliteDBMetadataManager.loadMetadata("existingdb");
		Assert.assertNotNull(metadata);
		Assert.assertNotNull(metadata.getCurrent());
		Assert.assertTrue(metadata.getCurrent().getMd5().equals("8d23ed6bde518ab13283d7352a4f91ba"));

		sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database", "/a/path/to/dbfile.db", "A HASH");

		Assert.assertTrue(metadata.getCurrent().getMd5().equals("A HASH"));
		Assert.assertTrue(metadata.getCurrent().getFile().equals("/a/path/to/dbfile.db"));
		Assert.assertTrue(metadata.getPrevious().size() == 1);
		Assert.assertTrue(metadata.getPrevious().get(0).getMd5().equals("8d23ed6bde518ab13283d7352a4f91ba"));
	}

	@Test
	public void identifyDatabasesToDelete() throws URISyntaxException, IOException {
		populateFileSystem();
		SQLiteDBMetadata metadata = sqliteDBMetadataManager.loadMetadata("existingdb");
		Assert.assertTrue(metadata.getPrevious().isEmpty());

		List<SQLiteDBFileInfo> toDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database",
				"/a/path/to/dbfile-1.db", "A HASH1");
		Assert.assertTrue(metadata.getPrevious().size() == 1);
		Assert.assertTrue(toDelete.isEmpty());

		toDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database", "/a/path/to/dbfile-2.db",
				"A HASH2");
		Assert.assertTrue(metadata.getPrevious().size() == 2);
		Assert.assertTrue(toDelete.isEmpty());

		toDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database", "/a/path/to/dbfile-3.db",
				"A HASH3");
		Assert.assertTrue(metadata.getPrevious().size() == 2);
		Assert.assertTrue(toDelete.size() == 1);
		Assert.assertTrue(toDelete.get(0).getMd5().equals("8d23ed6bde518ab13283d7352a4f91ba"));

		toDelete = sqliteDBMetadataManager.addDBtoMetadata(metadata, "owner", "database", "/a/path/to/dbfile-4.db",
				"A HASH4");
		Assert.assertTrue(metadata.getPrevious().size() == 2);
		Assert.assertTrue(toDelete.size() == 1);
		Assert.assertTrue(toDelete.get(0).getMd5().equals("A HASH1"));
	}
}
