package com.decisioncopilot.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${PGHOST:localhost}")
    private String host;

    @Value("${PGPORT:5432}")
    private String port;

    @Value("${PGDATABASE:decision_copilot}")
    private String db;

    @Value("${PGUSER:postgres}")
    private String user;

    @Value("${PGPASSWORD:postgres}")
    private String password;

    @Bean
    @Primary
    public DataSource dataSource() {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        log.info("Configuring PostgreSQL DataSource:");
        log.info("  Host: {}", host);
        log.info("  Port: {}", port);
        log.info("  Database: {}", db);
        log.info("  User: {}", user);
        log.info("  URL: {}", url);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");

        // Additional HikariCP settings for Railway
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);

        return ds;
    }
}
