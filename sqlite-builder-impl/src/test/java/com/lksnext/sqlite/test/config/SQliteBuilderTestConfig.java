package com.lksnext.sqlite.test.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import com.lksnext.sqlite.impl.config.SqliteBuilderImplConfig;

@Import({ SqliteBuilderImplConfig.class, JDBC4TestConfig.class })
@ComponentScan({ "com.lksnext.sqlite" })
public class SQliteBuilderTestConfig {

}
