package com.ai.rts.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataSourceUrlConfigTest {
    @Test
    void parsesNeonStylePostgresUrl() {
        DataSourceUrlConfig.ParsedUrl parsed = DataSourceUrlConfig.parsePostgresUrl(
                "postgresql://user:pa%24ss@ep-cool-123.us-east-2.aws.neon.tech/neondb?sslmode=require");
        assertTrue(parsed.jdbcUrl().contains("jdbc:postgresql://ep-cool-123"));
        assertTrue(parsed.jdbcUrl().contains("sslmode=require"));
        assertEquals("user", parsed.username());
        assertEquals("pa$ss", parsed.password());
    }

    @Test
    void normalizesPostgresAliasScheme() {
        DataSourceUrlConfig.ParsedUrl parsed = DataSourceUrlConfig.parsePostgresUrl(
                "postgres://alice:secret@localhost:5432/airts");
        assertEquals("jdbc:postgresql://localhost:5432/airts", parsed.jdbcUrl());
        assertEquals("alice", parsed.username());
        assertEquals("secret", parsed.password());
    }
}
