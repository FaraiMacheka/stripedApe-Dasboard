package com.stripedape.dashboard.service;

import com.stripedape.dashboard.config.AppProperties;
import com.stripedape.dashboard.domain.DatabaseConnection;
import com.stripedape.dashboard.repository.DatabaseConnectionRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseDiscoveryService {

    private static final Set<String> IGNORED_DATABASES = Set.of("postgres", "template0", "template1");

    private final DatabaseAdminService databaseAdminService;
    private final DatabaseConnectionRepository repository;
    private final AppProperties properties;

    public DatabaseDiscoveryService(
            DatabaseAdminService databaseAdminService,
            DatabaseConnectionRepository repository,
            AppProperties properties
    ) {
        this.databaseAdminService = databaseAdminService;
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.discovery.cron:0 0 0/12 * * *}")
    @Transactional
    public void scheduledDiscovery() {
        if (!properties.getDiscovery().isEnabled()) {
            return;
        }
        discoverNow();
    }

    @Transactional
    public DiscoverySummary discoverNow() {
        Instant seenAt = Instant.now();
        List<DatabaseConnection> existingConnections = repository.findAll();
        Set<String> knownNames = existingConnections.stream()
                .map(DatabaseConnection::getName)
                .map(name -> name == null ? "" : name.toLowerCase())
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> visitedSources = new HashSet<>();
        List<String> newlyAdded = new ArrayList<>();
        List<String> failedSources = new ArrayList<>();
        int scannedSources = 0;

        for (DatabaseConnection source : existingConnections) {
            ConnectionTarget target = ConnectionTarget.from(source.getJdbcUrl());
            if (target == null || !visitedSources.add(source.getUsername() + "@" + target.host() + ":" + target.port())) {
                continue;
            }
            scannedSources++;
            try {
                for (String databaseName : fetchDatabaseNames(source)) {
                    String normalized = databaseName.toLowerCase();
                    if (!knownNames.add(normalized)) {
                        repository.findByNameIgnoreCase(databaseName).ifPresent(connection -> databaseAdminService.markSeen(connection, seenAt));
                        continue;
                    }

                    DatabaseConnection discovered = new DatabaseConnection();
                    discovered.setName(databaseName);
                    discovered.setJdbcUrl(target.withDatabase(databaseName));
                    discovered.setUsername(source.getUsername());
                    discovered.setEncryptedPassword(source.getEncryptedPassword());
                    discovered.setActive(false);
                    discovered.setAutoDiscovered(true);
                    discovered.setDescription("Auto-discovered from PostgreSQL. Review and activate when ready.");
                    discovered.setLastSeenAt(seenAt);
                    databaseAdminService.saveDiscovered(discovered);
                    newlyAdded.add(databaseName);
                }
            } catch (IllegalStateException ex) {
                failedSources.add(source.getName());
            }
        }

        return new DiscoverySummary(scannedSources, newlyAdded, failedSources);
    }

    private List<String> fetchDatabaseNames(DatabaseConnection connection) {
        List<String> names = new ArrayList<>();
        String password = databaseAdminService.decryptPassword(connection);
        try (Connection jdbcConnection = DriverManager.getConnection(connection.getJdbcUrl(), connection.getUsername(), password);
             PreparedStatement statement = jdbcConnection.prepareStatement(
                     "select datname from pg_database where datistemplate = false order by datname"
             );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                if (!IGNORED_DATABASES.contains(name.toLowerCase())) {
                    names.add(name);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to discover PostgreSQL databases from " + connection.getName(), ex);
        }
        return names;
    }

    public record DiscoverySummary(int scannedSources, List<String> newlyAdded, List<String> failedSources) {
        public int discoveredCount() {
            return newlyAdded.size();
        }
    }

    private record ConnectionTarget(String host, int port, String query) {
        static ConnectionTarget from(String jdbcUrl) {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
                return null;
            }
            try {
                URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
                int resolvedPort = uri.getPort() > 0 ? uri.getPort() : 5432;
                return new ConnectionTarget(uri.getHost(), resolvedPort, uri.getQuery());
            } catch (URISyntaxException ex) {
                return null;
            }
        }

        String withDatabase(String databaseName) {
            StringBuilder builder = new StringBuilder("jdbc:postgresql://")
                    .append(host)
                    .append(":")
                    .append(port)
                    .append("/")
                    .append(databaseName);
            if (query != null && !query.isBlank()) {
                builder.append("?").append(query);
            }
            return builder.toString();
        }
    }
}
