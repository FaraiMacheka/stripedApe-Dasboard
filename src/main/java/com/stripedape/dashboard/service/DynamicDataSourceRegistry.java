package com.stripedape.dashboard.service;

import com.stripedape.dashboard.domain.DatabaseConnection;
import com.stripedape.dashboard.repository.DatabaseConnectionRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class DynamicDataSourceRegistry {

    private final DatabaseConnectionRepository repository;
    private final CryptoService cryptoService;
    private final Map<Long, HikariDataSource> sources = new ConcurrentHashMap<>();

    public DynamicDataSourceRegistry(DatabaseConnectionRepository repository, CryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    public DataSource get(Long connectionId) {
        DatabaseConnection connection = repository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Database connection not found"));
        return sources.computeIfAbsent(connectionId, id -> create(connection));
    }

    public void refresh(Collection<DatabaseConnection> connections) {
        sources.values().forEach(HikariDataSource::close);
        sources.clear();
        for (DatabaseConnection connection : connections) {
            if (connection.isActive()) {
                sources.put(connection.getId(), create(connection));
            }
        }
    }

    public void evict(Long connectionId) {
        HikariDataSource source = sources.remove(connectionId);
        if (source != null) {
            source.close();
        }
    }

    private HikariDataSource create(DatabaseConnection connection) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("db-" + connection.getId());
        config.setJdbcUrl(connection.getJdbcUrl());
        config.setUsername(connection.getUsername());
        config.setPassword(cryptoService.decrypt(connection.getEncryptedPassword()));
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
        return new HikariDataSource(config);
    }
}

