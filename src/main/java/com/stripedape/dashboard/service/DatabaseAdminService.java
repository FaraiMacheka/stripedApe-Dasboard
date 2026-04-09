package com.stripedape.dashboard.service;

import com.stripedape.dashboard.config.AppProperties;
import com.stripedape.dashboard.domain.DatabaseConnection;
import com.stripedape.dashboard.repository.DatabaseConnectionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DatabaseAdminService {

    private final DatabaseConnectionRepository repository;
    private final CryptoService cryptoService;
    private final AppProperties properties;
    private final DynamicDataSourceRegistry dynamicDataSourceRegistry;

    public DatabaseAdminService(
            DatabaseConnectionRepository repository,
            CryptoService cryptoService,
            AppProperties properties,
            DynamicDataSourceRegistry dynamicDataSourceRegistry
    ) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.dynamicDataSourceRegistry = dynamicDataSourceRegistry;
    }

    @javax.annotation.PostConstruct
    @Transactional
    public void seedConnections() {
        if (repository.count() > 0) {
            return;
        }
        for (AppProperties.SeedDatabase seed : properties.getSeedDatabases()) {
            DatabaseConnection connection = new DatabaseConnection();
            connection.setName(seed.getName());
            connection.setJdbcUrl(normalizeJdbcUrl(seed.getJdbcUrl()));
            connection.setUsername(seed.getUsername());
            connection.setEncryptedPassword(cryptoService.encrypt(seed.getPassword()));
            connection.setActive(seed.isActive());
            connection.setAutoDiscovered(false);
            connection.setLastSeenAt(Instant.now());
            repository.save(connection);
        }
    }

    public List<DatabaseConnection> listAll() {
        return repository.findAll().stream()
                .sorted(java.util.Comparator.comparing(DatabaseConnection::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<DatabaseConnection> listActive() {
        return repository.findAllByActiveTrueOrderByNameAsc();
    }

    public List<DatabaseConnection> listActiveFiltered(String query) {
        return filterConnections(listActive(), query);
    }

    public List<DatabaseConnection> listAutoDiscovered() {
        return repository.findAllByAutoDiscoveredTrueOrderByNameAsc();
    }

    public DatabaseConnection getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Database connection not found"));
    }

    public List<DatabaseConnection> filterConnections(List<DatabaseConnection> connections, String query) {
        if (!StringUtils.hasText(query)) {
            return connections;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return connections.stream()
                .filter(connection -> contains(connection.getName(), normalized)
                        || contains(connection.getJdbcUrl(), normalized)
                        || contains(connection.getUsername(), normalized)
                        || contains(connection.getDescription(), normalized))
                .toList();
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DatabaseConnection save(DatabaseConnectionForm form) {
        DatabaseConnection connection = form.getId() == null
                ? new DatabaseConnection()
                : getRequired(form.getId());
        connection.setName(form.getName());
        connection.setJdbcUrl(normalizeJdbcUrl(form.getJdbcUrl()));
        connection.setUsername(form.getUsername());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            connection.setEncryptedPassword(cryptoService.encrypt(form.getPassword()));
        }
        connection.setActive(form.isActive());
        connection.setDescription(form.getDescription());
        connection.setAutoDiscovered(false);
        connection.setLastSeenAt(Instant.now());
        DatabaseConnection saved = repository.save(connection);
        dynamicDataSourceRegistry.evict(saved.getId());
        return saved;
    }

    @Transactional
    public DatabaseConnection saveDiscovered(DatabaseConnection connection) {
        DatabaseConnection saved = repository.save(connection);
        dynamicDataSourceRegistry.evict(saved.getId());
        return saved;
    }

    @Transactional
    public void markSeen(DatabaseConnection connection, Instant seenAt) {
        connection.setLastSeenAt(seenAt);
        repository.save(connection);
    }

    @Transactional
    public void markBackedUp(DatabaseConnection connection, Instant backupAt) {
        connection.setLastBackupAt(backupAt);
        repository.save(connection);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        repository.deleteById(id);
        dynamicDataSourceRegistry.evict(id);
    }

    public String decryptPassword(DatabaseConnection connection) {
        return cryptoService.decrypt(connection.getEncryptedPassword());
    }

    public String normalizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        if (jdbcUrl.contains("?schema=")) {
            return jdbcUrl.replace("?schema=", "?currentSchema=");
        }
        if (jdbcUrl.contains("&schema=")) {
            return jdbcUrl.replace("&schema=", "&currentSchema=");
        }
        return jdbcUrl;
    }
}
