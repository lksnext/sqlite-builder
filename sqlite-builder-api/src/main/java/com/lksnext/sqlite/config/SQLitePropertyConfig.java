package com.lksnext.sqlite.config;

import java.net.URI;

public interface SQLitePropertyConfig {

    public URI getDatabasePath();

    public URI getTemporalPath();

    public int getMaxDBCopyNumber();

    public int getMaxPatchNumber();
}
