package com.ai.rts.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Accepts Neon/Render-style {@code postgres://} URLs in addition to JDBC URLs.
 */
@Configuration
@Profile("prod")
public class DataSourceUrlConfig {

    @Bean
    @Primary
    @ConditionalOnExpression(
            "'${SPRING_DATASOURCE_URL:}'.isEmpty() && ('${DATABASE_URL:}'.startsWith('postgres://') || '${DATABASE_URL:}'.startsWith('postgresql://'))")
    public DataSource dataSourceFromDatabaseUrl(@Value("${DATABASE_URL}") String databaseUrl) {
        if (databaseUrl.startsWith("jdbc:")) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(databaseUrl);
            return new HikariDataSource(config);
        }
        ParsedUrl parsed = parsePostgresUrl(databaseUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parsed.jdbcUrl());
        config.setUsername(parsed.username());
        config.setPassword(parsed.password());
        return new HikariDataSource(config);
    }

    static ParsedUrl parsePostgresUrl(String url) {
        String normalized = url;
        if (normalized.startsWith("postgres://")) {
            normalized = "postgresql://" + normalized.substring("postgres://".length());
        }
        URI uri = URI.create(normalized);
        String userInfo = uri.getUserInfo();
        String username = "";
        String password = "";
        if (userInfo != null && !userInfo.isBlank()) {
            String[] parts = userInfo.split(":", 2);
            username = decode(parts[0]);
            if (parts.length > 1) {
                password = decode(parts[1]);
            }
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String query = uri.getQuery();
        String jdbc = "jdbc:postgresql://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                + "/" + path
                + (query == null || query.isBlank() ? "" : "?" + query);
        return new ParsedUrl(jdbc, username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    record ParsedUrl(String jdbcUrl, String username, String password) {}
}
