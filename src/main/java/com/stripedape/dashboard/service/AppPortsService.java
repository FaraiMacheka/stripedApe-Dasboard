package com.stripedape.dashboard.service;

import com.stripedape.dashboard.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppPortsService {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[(.+?)]\\((.+?)\\)");

    private final AppProperties properties;

    public AppPortsService(AppProperties properties) {
        this.properties = properties;
    }

    public AppPortsSnapshot loadSnapshot() {
        String configuredPath = properties.getDashboard().getAppPortsFile();
        if (!StringUtils.hasText(configuredPath)) {
            return AppPortsSnapshot.empty();
        }

        Path path = Paths.get(configuredPath);
        if (!Files.exists(path)) {
            return AppPortsSnapshot.missing(configuredPath);
        }

        try {
            List<String> lines = Files.readAllLines(path);
            List<AppPortEntry> entries = parseEntries(lines);
            FileTime lastModified = Files.getLastModifiedTime(path);
            return new AppPortsSnapshot(path.toString(), entries, lastModified.toMillis(), false);
        } catch (IOException ex) {
            return AppPortsSnapshot.missing(path.toString());
        }
    }

    public List<AppPortEntry> filterEntries(List<AppPortEntry> entries, String query) {
        if (!StringUtils.hasText(query)) {
            return entries;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(entry -> contains(entry.app(), normalized)
                        || contains(entry.stack(), normalized)
                        || contains(entry.status(), normalized)
                        || contains(entry.frontendPort(), normalized)
                        || contains(entry.backendPort(), normalized)
                        || contains(entry.notes(), normalized))
                .toList();
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private List<AppPortEntry> parseEntries(List<String> lines) {
        List<AppPortEntry> entries = new ArrayList<>();
        boolean insideTable = false;

        for (String line : lines) {
            if (!line.startsWith("|")) {
                continue;
            }

            if (line.contains("| App | Stack | Status | Frontend Port | Backend Port | Notes |")) {
                insideTable = true;
                continue;
            }

            if (!insideTable || line.contains("| --- |")) {
                continue;
            }

            String[] parts = line.split("\\|");
            if (parts.length < 7) {
                continue;
            }

            MarkdownCell appCell = parseMarkdownCell(parts[1].trim());
            entries.add(new AppPortEntry(
                    appCell.label(),
                    appCell.link(),
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parts[5].trim(),
                    parts[6].trim()
            ));
        }

        return Collections.unmodifiableList(entries);
    }

    private MarkdownCell parseMarkdownCell(String value) {
        Matcher matcher = MARKDOWN_LINK.matcher(value);
        if (matcher.matches()) {
            return new MarkdownCell(matcher.group(1), matcher.group(2));
        }
        return new MarkdownCell(value, null);
    }

    public record AppPortsSnapshot(String sourcePath, List<AppPortEntry> entries, long lastModifiedEpochMs, boolean missing) {
        public static AppPortsSnapshot empty() {
            return new AppPortsSnapshot(null, List.of(), 0L, true);
        }

        public static AppPortsSnapshot missing(String sourcePath) {
            return new AppPortsSnapshot(sourcePath, List.of(), 0L, true);
        }

        public int activeCount() {
            return (int) entries.stream().filter(entry -> "Active".equalsIgnoreCase(entry.status())).count();
        }
    }

    public record AppPortEntry(
            String app,
            String url,
            String stack,
            String status,
            String frontendPort,
            String backendPort,
            String notes
    ) {
    }

    private record MarkdownCell(String label, String link) {
    }
}
