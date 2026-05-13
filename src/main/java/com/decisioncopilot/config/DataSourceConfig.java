package com.decisioncopilot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource() {

        // =========================
        // READ FROM ENV (RAILWAY / LOCAL)
        // =========================
        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String db = System.getenv("PGDATABASE");
        String user = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        // =========================
        // FAIL FAST IF MISSING
        // =========================
        if (host == null || port == null || db == null || user == null || password == null) {
            throw new IllegalStateException(
                "Missing required DB environment variables. " +
                "Please set PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD in Railway."
            );
        }

        // =========================
        // BUILD JDBC URL
        // =========================
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        // =========================
        // LOGGING (SAFE FOR DEBUGGING)
        // =========================
        log.info("========== DATABASE CONFIG ==========");
        log.info("Host     : {}", host);
        log.info("Port     : {}", port);
        log.info("Database : {}", db);
        log.info("User     : {}", user);
        log.info("URL      : {}", url);
        log.info("=====================================");

        // =========================
        // HIKARI CONNECTION POOL
        // =========================
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");

        // Recommended tuning for Railway
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);

        return ds;
    }
}