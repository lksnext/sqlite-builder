package com.lksnext.sqlite.impl.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"com.lksnext.sqlite.impl"})
public class SqliteBuilderImplConfig {

    @Value("${sqlite.builder.url}")
    private String url;

    @Value("${sqlite.builder.username}")
    private String username;

    @Value("${sqlite.builder.password}")
    private String password;

    @Value("${sqlite.builder.driver-class-name}")
    private String driverClassName;

    /*@Value("${sqlite.builder.tomcat.testOnBorrow}")
    private boolean testOnBorrow;

    @Value("${sqlite.builder.tomcat.minIdle}")
    private int minIdle;

    @Value("${sqlite.builder.tomcat.validationQuery}")
    private String validationQuery;*/

    @SuppressWarnings("rawtypes")
	@Bean(name="builderDs")
    public DataSource dataSource() {
    	 DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
         dataSourceBuilder.driverClassName(this.driverClassName);
         dataSourceBuilder.url(this.url);
         dataSourceBuilder.username(this.username);
         dataSourceBuilder.password(this.password);
         return dataSourceBuilder.build();
    }
}