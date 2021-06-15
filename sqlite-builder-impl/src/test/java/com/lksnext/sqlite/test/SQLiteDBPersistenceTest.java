package com.lksnext.sqlite.test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FilenameUtils;
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
import com.lksnext.sqlite.SQLiteDBPersistManager;
import com.lksnext.sqlite.metadata.SQLiteDBMetadata;
import com.lksnext.sqlite.metadata.SQLiteDBMetadataManager;
import com.lksnext.sqlite.test.config.SQliteBuilderTestConfig;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@ContextHierarchy(@ContextConfiguration(classes = { SQliteBuilderTestConfig.class }))
public class SQLiteDBPersistenceTest {

	@Autowired
	SQLiteDBPersistManager sqLiteDBPersistManager;

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

	private void populateFileSystem() throws IOException, URISyntaxException {

		URI testUri = new URI("jimfs://databases/test/");
		Path testPath = Files.createDirectories(Paths.get(testUri));
		URI tmpUri = new URI("jimfs://databases/tmp/");
		Files.createDirectories(Paths.get(tmpUri));
		Path dbpath = Files.createDirectory(testPath.resolve("existingdb"));
		Path metadataPath = dbpath.resolve("metadata.json");
		URI metadata = getFileFromResource("assets/existingdb/metadata.json");
		Files.copy(metadata.toURL().openStream(), metadataPath);

		copyDbFromAssets("dummy.db", "newdb.db");
	}

	private void copyDbFromAssets(String sourceFileName, String targetFileName)
			throws MalformedURLException, IOException, URISyntaxException {
		URI tmpUri = new URI("jimfs://databases/tmp/");
		Path databasePath = Paths.get(tmpUri).resolve(targetFileName);
		URI database = getFileFromResource("assets/" + sourceFileName);
		Files.copy(database.toURL().openStream(), databasePath);
	}

	@Test
	public void persistTest() throws IOException, URISyntaxException {
		populateFileSystem();
		sqLiteDBPersistManager.persist("owner", "newdb", true);

		SQLiteDBMetadata metadata = sqliteDBMetadataManager.loadMetadata("newdb");
		Assert.assertNotNull(metadata.getCurrent());
		Assert.assertTrue(metadata.getCurrent().getMd5().equals("a19582bb2bdb5df45d0a1b01371c6c38"));
		Assert.assertTrue(metadata.getPrevious().isEmpty());

		URI currentURI = new URI(metadata.getCurrent().getFile());
		URI testUri = new URI("jimfs://databases/test/newdb/");
		Path latestdb = Paths.get(testUri).resolve("latest.db");
		Assert.assertTrue(Files.exists(latestdb));
		Assert.assertTrue(Files.isSymbolicLink(latestdb));
		Path target = Files.readSymbolicLink(latestdb);
		Assert.assertTrue(Files.isSameFile(Paths.get(currentURI), target));

		Path latestzip = Paths.get(testUri).resolve("latest.zip");
		Assert.assertTrue(Files.exists(latestzip));

		long patchCount = Files.find(Paths.get(testUri), 1, (p, a) -> {
			return FilenameUtils.getExtension(p.getFileName().toString()).equalsIgnoreCase("patch");
		}).count();
		Assert.assertEquals(0, patchCount);

		copyDbFromAssets("dummy1.db", "newdb.db");
		sqLiteDBPersistManager.persist("owner", "newdb", true);

		metadata = sqliteDBMetadataManager.loadMetadata("newdb");
		Assert.assertNotNull(metadata.getCurrent());
		Assert.assertTrue(metadata.getCurrent().getMd5().equals("18c99512510ea73dc1970c7b7bf91efa"));
		Assert.assertTrue(metadata.getPrevious().size() == 1);

		Path patch0 = Paths.get(testUri).resolve("a19582bb2bdb5df45d0a1b01371c6c38.patch");
		Assert.assertTrue(Files.exists(patch0));

		patchCount = Files.find(Paths.get(testUri), 1, (p, a) -> {
			return FilenameUtils.getExtension(p.getFileName().toString()).equalsIgnoreCase("patch");
		}).count();
		Assert.assertEquals(1, patchCount);

		copyDbFromAssets("dummy2.db", "newdb.db");
		sqLiteDBPersistManager.persist("owner", "newdb", true);

		metadata = sqliteDBMetadataManager.loadMetadata("newdb");
		Assert.assertNotNull(metadata.getCurrent());
		Assert.assertTrue(metadata.getCurrent().getMd5().equals("229da98785fdcec109e5ebf5c4eb5180"));
		Assert.assertTrue(metadata.getPrevious().size() == 2);

		Assert.assertTrue(Files.exists(patch0));
		Path patch1 = Paths.get(testUri).resolve("18c99512510ea73dc1970c7b7bf91efa.patch");
		Assert.assertTrue(Files.exists(patch1));

		patchCount = Files.find(Paths.get(testUri), 1, (p, a) -> {
			return FilenameUtils.getExtension(p.getFileName().toString()).equalsIgnoreCase("patch");
		}).count();
		Assert.assertEquals(2, patchCount);

	}
}
