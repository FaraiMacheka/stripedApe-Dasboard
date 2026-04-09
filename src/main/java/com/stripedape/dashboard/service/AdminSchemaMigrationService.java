package com.stripedape.dashboard.service;

import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminSchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public AdminSchemaMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureDatabaseConnectionColumns() {
        jdbcTemplate.execute("ALTER TABLE database_connections ADD COLUMN IF NOT EXISTS auto_discovered BOOLEAN DEFAULT FALSE NOT NULL");
        jdbcTemplate.execute("ALTER TABLE database_connections ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE database_connections ADD COLUMN IF NOT EXISTS last_backup_at TIMESTAMP");
        jdbcTemplate.execute("UPDATE database_connections SET auto_discovered = FALSE WHERE auto_discovered IS NULL");
    }
}
