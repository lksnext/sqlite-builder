package com.lksnext.sqlite.config;

import java.net.URI;

public interface SQLitePropertyConfig {

    URI getDatabasePath();

    URI getTemporalPath();

    int getMaxDBCopyNumber();

    int getMaxPatchNumber();

	boolean isMoveDisabled();
	
	boolean isSymLinkDisabled();
}
