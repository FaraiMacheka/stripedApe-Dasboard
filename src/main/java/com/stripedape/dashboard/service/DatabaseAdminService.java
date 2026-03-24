package com.stripedape.dashboard.service;

import com.stripedape.dashboard.config.AppProperties;
import com.stripedape.dashboard.domain.DatabaseConnection;
import com.stripedape.dashboard.repository.DatabaseConnectionRepository;
import javax.annotation.PostConstruct;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @PostConstruct
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
            repository.save(connection);
        }
    }

    public List<DatabaseConnection> listAll() {
        return repository.findAll();
    }

    public List<DatabaseConnection> listActive() {
        return repository.findAllByActiveTrueOrderByNameAsc();
    }

    public DatabaseConnection getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Database connection not found"));
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
        DatabaseConnection saved = repository.save(connection);
        dynamicDataSourceRegistry.evict(saved.getId());
        return saved;
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


