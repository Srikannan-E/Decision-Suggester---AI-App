package com.decisioncopilot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("!test")
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        DbConfig config = resolveConfig(env);

        log.info("Database target: {}", maskCredentials(config.jdbcUrl()));

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
        String databaseUrl = firstNonBlank(
            System.getenv("DATABASE_URL"),
            System.getenv("DATABASE_PRIVATE_URL"),
            env.getProperty("DATABASE_URL"),
            env.getProperty("DATABASE_PRIVATE_URL"));
        if (databaseUrl != null) {
            return parseDatabaseUrl(databaseUrl);
        }

        String host = firstNonBlank(System.getenv("PGHOST"), env.getProperty("PGHOST"));
        String port = firstNonBlank(System.getenv("PGPORT"), env.getProperty("PGPORT"));
        String db = firstNonBlank(System.getenv("PGDATABASE"), env.getProperty("PGDATABASE"));
        String user = firstNonBlank(System.getenv("PGUSER"), env.getProperty("PGUSER"));
        String password = firstNonBlank(System.getenv("PGPASSWORD"), env.getProperty("PGPASSWORD"));
        if (host != null && port != null && db != null && user != null && password != null) {
            return new DbConfig("jdbc:postgresql://" + host + ":" + port + "/" + db, user, password);
        }

        throw new IllegalStateException(
            "Database is not configured. On Railway, add a PostgreSQL plugin and link it to this service "
                + "so DATABASE_URL or PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD are available.");
    }

    private static DbConfig parseDatabaseUrl(String databaseUrl) {
        try {
            URI uri = URI.create(databaseUrl.replace("postgres://", "postgresql://"));
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                throw new IllegalStateException("Database URL is missing username and password");
            }
            String[] parts = userInfo.split(":", 2);
            String username = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (parts.length < 2 || parts[1].isBlank()) {
                throw new IllegalStateException("Database URL is missing password");
            }
            String password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);

            String path = uri.getPath();
            String db = (path == null || path.length() <= 1) ? "railway" : path.substring(1);
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + db;
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                jdbcUrl += "?" + uri.getQuery();
            }
            return new DbConfig(jdbcUrl, username, password);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid DATABASE_URL: " + maskCredentials(databaseUrl), e);
        }
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
