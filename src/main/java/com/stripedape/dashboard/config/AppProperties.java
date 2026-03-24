package com.stripedape.dashboard.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name = "stripedApe-Dasboard";
    private String encryptionKey = "stripedape-dev-key";
    private final List<SeedDatabase> seedDatabases = new ArrayList<>();
    private final BootstrapUser bootstrapUsers = new BootstrapUser();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public List<SeedDatabase> getSeedDatabases() {
        return seedDatabases;
    }

    public BootstrapUser getBootstrapUsers() {
        return bootstrapUsers;
    }

    public static class SeedDatabase {
        private String name;
        private String jdbcUrl;
        private String username;
        private String password;
        private boolean active = true;

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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static class BootstrapUser {
        private String adminUsername = "admin";
        private String adminPassword = "Admin@1234!";
        private String managerUsername = "manager";
        private String managerPassword = "Manager@1234!";
        private String viewerUsername = "viewer";
        private String viewerPassword = "Viewer@1234!";

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getManagerUsername() {
            return managerUsername;
        }

        public void setManagerUsername(String managerUsername) {
            this.managerUsername = managerUsername;
        }

        public String getManagerPassword() {
            return managerPassword;
        }

        public void setManagerPassword(String managerPassword) {
            this.managerPassword = managerPassword;
        }

        public String getViewerUsername() {
            return viewerUsername;
        }

        public void setViewerUsername(String viewerUsername) {
            this.viewerUsername = viewerUsername;
        }

        public String getViewerPassword() {
            return viewerPassword;
        }

        public void setViewerPassword(String viewerPassword) {
            this.viewerPassword = viewerPassword;
        }
    }
}

