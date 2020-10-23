package com.lksnext.sqlite;

import java.io.IOException;
import java.net.URISyntaxException;

import com.lksnext.sqlite.metadata.SQLiteDBMetadata;

public interface SQLiteDBPersistManager {

    public void persist(String user,String database) throws URISyntaxException, IOException;

    public void persist(String user,String database, boolean createDiffPatches) throws URISyntaxException, IOException;

    void createPatchForMD5(SQLiteDBMetadata sqliteDBMetadata, String database, String md5);

    boolean existsDBForMD5(SQLiteDBMetadata sqliteDBMetadata, String database, String md5);

    void fixSymbolicLinks(String database) throws IOException;

}
