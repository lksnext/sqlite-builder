package com.lksnext.sqlite.impl.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.lksnext.sqlite.config.SQLitePropertyConfig;

@Component
@ConfigurationProperties(prefix = "sqlite.builder.location")
public class SQLiteConfigImpl implements SQLitePropertyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQLiteConfigImpl.class);

    private String databasePath;

    private URI databasePathURI;

    private String temporalPath;

    private URI temporalPathURI;

    private int maxDBCopyNumber;

    private int maxPatchNumber;

    public void setDatabasePath(String path) {
        this.databasePath = path;
    }

    public void setTemporalPath(String temporalPath) {
        this.temporalPath = temporalPath;
    }

    @Override
    public URI getDatabasePath() {

        if (databasePathURI == null && databasePath != null) {
            try {
                databasePathURI = new URI(this.databasePath);
            } catch (URISyntaxException e) {
                LOGGER.error("'{}' is not a valid value for param 'location.sqlite.databasePath'.", databasePath, e);
            }
        }
        return databasePathURI;
    }

    @Override
    public URI getTemporalPath() {

        if (temporalPathURI == null && temporalPath != null) {
            try {
                temporalPathURI = new URI(this.temporalPath);
            } catch (URISyntaxException e) {
                LOGGER.error("'{}' is not a valid value for param 'location.sqlite.temporalPath'.", temporalPath, e);
            }
        }
        return temporalPathURI;
    }

    @Override
    public int getMaxDBCopyNumber() {
        return maxDBCopyNumber;
    }

    public void setMaxDBCopyNumber(int maxDBCopyNumber) {
        this.maxDBCopyNumber = maxDBCopyNumber;
    }

    @Override
    public int getMaxPatchNumber() {
        return maxPatchNumber;
    }

    public void setMaxPatchNumber(int maxPatchNumber) {
        this.maxPatchNumber = maxPatchNumber;
    }

    
}
