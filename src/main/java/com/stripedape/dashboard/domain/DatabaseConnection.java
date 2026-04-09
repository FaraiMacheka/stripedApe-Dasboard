package com.stripedape.dashboard.domain;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "database_connections")
public class DatabaseConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 1200)
    private String jdbcUrl;

    @Column(nullable = false, length = 120)
    private String username;

    @Column(nullable = false, length = 500)
    private String encryptedPassword;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean autoDiscovered = false;

    @Column(length = 400)
    private String description;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant lastSeenAt;

    @Column
    private Instant lastBackupAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAutoDiscovered() {
        return autoDiscovered;
    }

    public void setAutoDiscovered(boolean autoDiscovered) {
        this.autoDiscovered = autoDiscovered;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getLastBackupAt() {
        return lastBackupAt;
    }

    public void setLastBackupAt(Instant lastBackupAt) {
        this.lastBackupAt = lastBackupAt;
    }
}
