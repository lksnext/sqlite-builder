package com.lksnext.sqlite.test.config;


import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ConfigurationProperties("spring.datasource")
public class JDBC4TestConfig {

    @Value("${spring.datasource.tomcat.validationQuery}")
    private String validationQuery;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.tomcat.testOnBorrow}")
    private boolean testOnBorrow;

    @Value("${spring.datasource.tomcat.minIdle}")
    private int minIdle;

    @Bean
    public NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource());
        return jdbcTemplate;
    }

    @SuppressWarnings("rawtypes")
    @Bean(name="builderDs")
    public DataSource dataSource() {
    	
    	 DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
         dataSourceBuilder.driverClassName(this.driverClassName);
         dataSourceBuilder.url(this.url);
         dataSourceBuilder.username(this.username);
         dataSourceBuilder.password(this.password);
         return dataSourceBuilder.build();
    	
//        DataSource dataSource = new DataSource();
//        dataSource.setUrl(this.url);
//        dataSource.setTestOnBorrow(this.testOnBorrow);
//        dataSource.setValidationQuery(this.validationQuery);
//        dataSource.setDriverClassName(this.driverClassName);
//        dataSource.setMinIdle(this.minIdle);
//        dataSource.setUsername(this.username);
//        dataSource.setPassword(this.password);
//        return dataSource;
    }
}
