package com.decisioncopilot.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Value("${PGHOST}")
    private String host;

    @Value("${PGPORT}")
    private String port;

    @Value("${PGDATABASE}")
    private String db;

    @Value("${PGUSER}")
    private String user;

    @Value("${PGPASSWORD}")
    private String password;

    @Bean
    public DataSource dataSource() {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");

        return ds;
    }
}
