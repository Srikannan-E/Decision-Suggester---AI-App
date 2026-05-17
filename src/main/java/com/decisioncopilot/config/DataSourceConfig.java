package com.decisioncopilot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:5432/decision_copilot";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        DbConfig config = resolveConfig(env);

        log.info("Database URL: {}", maskCredentials(config.jdbcUrl()));

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.jdbcUrl());
        ds.setUsername(config.username());
        ds.setPassword(config.password());
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        return ds;
    }

    private static DbConfig resolveConfig(Environment env) {
        String springUrl = firstNonBlank(
            env.getProperty("SPRING_DATASOURCE_URL"),
            env.getProperty("spring.datasource.url"));
        String springUser = firstNonBlank(
            env.getProperty("SPRING_DATASOURCE_USERNAME"),
            env.getProperty("spring.datasource.username"));
        String springPassword = firstNonBlank(
            env.getProperty("SPRING_DATASOURCE_PASSWORD"),
            env.getProperty("spring.datasource.password"));

        if (springUrl != null) {
            return new DbConfig(toJdbcUrl(springUrl), springUser != null ? springUser : DEFAULT_USER,
                springPassword != null ? springPassword : DEFAULT_PASSWORD);
        }

        String databaseUrl = firstNonBlank(System.getenv("DATABASE_URL"), env.getProperty("DATABASE_URL"));
        if (databaseUrl != null) {
            return parseDatabaseUrl(databaseUrl);
        }

        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String db = System.getenv("PGDATABASE");
        String user = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");
        if (host != null && port != null && db != null && user != null && password != null) {
            return new DbConfig("jdbc:postgresql://" + host + ":" + port + "/" + db, user, password);
        }

        log.warn("No database env vars found; using local defaults ({})", DEFAULT_JDBC_URL);
        return new DbConfig(DEFAULT_JDBC_URL, DEFAULT_USER, DEFAULT_PASSWORD);
    }

    private static DbConfig parseDatabaseUrl(String databaseUrl) {
        try {
            URI uri = URI.create(databaseUrl.replace("postgres://", "postgresql://"));
            String userInfo = uri.getUserInfo();
            String username = DEFAULT_USER;
            String password = DEFAULT_PASSWORD;
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts = userInfo.split(":", 2);
                username = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (parts.length > 1) {
                    password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
            String path = uri.getPath();
            String db = (path == null || path.length() <= 1) ? "decision_copilot" : path.substring(1);
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + db;
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                jdbcUrl += "?" + uri.getQuery();
            }
            return new DbConfig(jdbcUrl, username, password);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid DATABASE_URL: " + maskCredentials(databaseUrl), e);
        }
    }

    private static String toJdbcUrl(String url) {
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            return parseDatabaseUrl(url).jdbcUrl();
        }
        return url;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String maskCredentials(String url) {
        return url == null ? "" : url.replaceAll("://([^:]+):([^@]+)@", "://***:***@");
    }

    private record DbConfig(String jdbcUrl, String username, String password) {}
}