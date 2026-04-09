package com.stripedape.dashboard.service;

import com.stripedape.dashboard.config.AppProperties;
import com.stripedape.dashboard.domain.DatabaseConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class DatabaseBackupService {

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final AppProperties properties;
    private final DatabaseAdminService databaseAdminService;

    public DatabaseBackupService(AppProperties properties, DatabaseAdminService databaseAdminService) {
        this.properties = properties;
        this.databaseAdminService = databaseAdminService;
    }

    public BackupArtifact backup(DatabaseConnection connection) {
        ConnectionTarget target = ConnectionTarget.from(connection.getJdbcUrl());
        if (target == null) {
            throw new IllegalArgumentException("Only PostgreSQL JDBC URLs are supported for backups.");
        }

        try {
            Path backupDirectory = Paths.get(properties.getBackup().getDirectory());
            Files.createDirectories(backupDirectory);

            String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
            Path outputFile = backupDirectory.resolve(connection.getName() + "-" + timestamp + ".backup");

            List<String> command = new ArrayList<>();
            command.add(properties.getBackup().getPgDumpPath());
            command.add("-h");
            command.add(target.host());
            command.add("-p");
            command.add(String.valueOf(target.port()));
            command.add("-U");
            command.add(connection.getUsername());
            command.add("-F");
            command.add("c");
            command.add("-d");
            command.add(target.database());
            command.add("-f");
            command.add(outputFile.toAbsolutePath().toString());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.environment().put("PGPASSWORD", databaseAdminService.decryptPassword(connection));
            Process process = builder.start();
            String processOutput = readAll(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("pg_dump failed: " + processOutput);
            }

            databaseAdminService.markBackedUp(connection, Instant.now());
            return new BackupArtifact(outputFile, new FileSystemResource(outputFile));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create backup file. Check app.backup.directory and pg_dump availability.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Backup process was interrupted.", ex);
        }
    }

    private String readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    public record BackupArtifact(Path path, Resource resource) {
        public String fileName() {
            return path.getFileName().toString();
        }
    }

    private record ConnectionTarget(String host, int port, String database) {
        static ConnectionTarget from(String jdbcUrl) {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
                return null;
            }
            try {
                URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
                String database = uri.getPath();
                if (database == null || database.isBlank() || "/".equals(database)) {
                    return null;
                }
                int resolvedPort = uri.getPort() > 0 ? uri.getPort() : 5432;
                return new ConnectionTarget(uri.getHost(), resolvedPort, database.substring(1));
            } catch (URISyntaxException ex) {
                return null;
            }
        }
    }
}
