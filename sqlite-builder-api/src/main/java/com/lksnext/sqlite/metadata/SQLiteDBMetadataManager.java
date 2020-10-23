package com.lksnext.sqlite.metadata;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public interface SQLiteDBMetadataManager {

    public SQLiteDBMetadata loadMetadata(String database) throws URISyntaxException, IOException;

    public void saveMetadata(SQLiteDBMetadata sqliteDBMetadata, String database) throws URISyntaxException, IOException;

    boolean existsMetadata(String database) throws URISyntaxException;

    List<SQLiteDBFileInfo> addDBtoMetadata(SQLiteDBMetadata sqliteDBMetadata, String user, String database, String file,
            String md5) throws URISyntaxException, IOException;

}
