package com.stripedape.dashboard.service;

import com.stripedape.dashboard.domain.DatabaseConnection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DynamicCrudService {

    private final DatabaseAdminService adminService;
    private final DynamicDataSourceRegistry registry;

    public DynamicCrudService(DatabaseAdminService adminService, DynamicDataSourceRegistry registry) {
        this.adminService = adminService;
        this.registry = registry;
    }

    public List<TableSummary> listTables(Long connectionId) {
        try (java.sql.Connection connection = jdbcTemplate(connectionId).getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<TableSummary> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    String name = rs.getString("TABLE_NAME");
                    if (skip(schema, name)) {
                        continue;
                    }
                    TableSummary summary = new TableSummary(schema, name, rs.getString("REMARKS"));
                    summary.setRowCount(countRows(connectionId, schema, name));
                    tables.add(summary);
                }
            }
            tables.sort((a, b) -> a.tableName().compareToIgnoreCase(b.tableName()));
            return tables;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read database metadata", ex);
        }
    }

    public TableDetails describeTable(Long connectionId, String tableName) {
        try (java.sql.Connection connection = jdbcTemplate(connectionId).getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            TableRef tableRef = resolve(metaData, tableName);
            List<ColumnDetails> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(null, tableRef.schema, tableRef.name, "%")) {
                while (rs.next()) {
                    columns.add(new ColumnDetails(
                            rs.getString("COLUMN_NAME"),
                            rs.getInt("DATA_TYPE"),
                            rs.getString("TYPE_NAME"),
                            rs.getInt("COLUMN_SIZE"),
                            rs.getInt("DECIMAL_DIGITS"),
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")),
                            rs.getString("REMARKS")
                    ));
                }
            }
            List<String> primaryKeys = new ArrayList<>();
            try (ResultSet rs = metaData.getPrimaryKeys(null, tableRef.schema, tableRef.name)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }
            return new TableDetails(tableRef.schema, tableRef.name, columns, primaryKeys);
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to describe table", ex);
        }
    }

    public PagedRows pageRows(Long connectionId, String tableName, String search, String sortBy, String direction, int page, int size) {
        TableDetails details = describeTable(connectionId, tableName);
        TableRef tableRef = new TableRef(details.schema(), details.tableName());
        String orderBy = resolveSortColumn(details, sortBy);
        String orderDirection = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        String where = "";
        List<Object> searchParams = new ArrayList<>();
        if (StringUtils.hasText(search)) {
            List<String> searchable = details.columns().stream()
                    .filter(column -> isSearchable(column.typeCode()))
                    .map(ColumnDetails::name)
                    .toList();
            if (!searchable.isEmpty()) {
                where = " WHERE " + searchable.stream()
                        .map(column -> quote(column) + "::text ILIKE ?")
                        .collect(Collectors.joining(" OR "));
                String term = "%" + search.trim() + "%";
                for (int i = 0; i < searchable.size(); i++) {
                    searchParams.add(term);
                }
            }
        }
        String selectSql = "SELECT * FROM " + tableRef.qualified() + where + " ORDER BY " + quote(orderBy) + " " + orderDirection + " LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) FROM " + tableRef.qualified() + where;
        List<Object> selectParams = new ArrayList<>(searchParams);
        selectParams.add(size);
        selectParams.add(page * size);
        List<Map<String, Object>> rows = jdbcTemplate(connectionId).query(selectSql, selectParams.toArray(), rowMapper(details.columns()));
        Long total = jdbcTemplate(connectionId).queryForObject(countSql, searchParams.toArray(), Long.class);
        return new PagedRows(rows, page, size, total == null ? 0L : total, orderBy, orderDirection, search);
    }

    public Map<String, Object> fetchRow(Long connectionId, String tableName, String rowId) {
        TableDetails details = describeTable(connectionId, tableName);
        if (details.primaryKeys().isEmpty()) {
            throw new IllegalStateException("Table has no primary key");
        }
        String where = details.primaryKeys().stream().map(key -> quote(key) + " = ?").collect(Collectors.joining(" AND "));
        String sql = "SELECT * FROM " + tableRef(details).qualified() + " WHERE " + where;
        return jdbcTemplate(connectionId).queryForMap(sql, decodeRowId(rowId, details.primaryKeys().size()));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void saveRow(Long connectionId, String tableName, Map<String, String> submittedValues, String rowId) {
        TableDetails details = describeTable(connectionId, tableName);
        Map<String, Object> values = normalize(details, submittedValues);
        JdbcTemplate jdbcTemplate = jdbcTemplate(connectionId);
        if (StringUtils.hasText(rowId)) {
            update(details, jdbcTemplate, values, rowId);
        } else {
            insert(details, jdbcTemplate, values);
        }
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deleteRow(Long connectionId, String tableName, String rowId) {
        TableDetails details = describeTable(connectionId, tableName);
        String where = details.primaryKeys().stream().map(key -> quote(key) + " = ?").collect(Collectors.joining(" AND "));
        String sql = "DELETE FROM " + tableRef(details).qualified() + " WHERE " + where;
        jdbcTemplate(connectionId).update(sql, decodeRowId(rowId, details.primaryKeys().size()));
    }

    public byte[] exportCsv(Long connectionId, String tableName, String search, String sortBy, String direction) {
        PagedRows page = pageRows(connectionId, tableName, search, sortBy, direction, 0, Integer.MAX_VALUE);
        TableDetails details = describeTable(connectionId, tableName);
        StringBuilder csv = new StringBuilder();
        csv.append(details.columns().stream().map(ColumnDetails::name).collect(Collectors.joining(","))).append("\n");
        for (Map<String, Object> row : page.rows()) {
            csv.append(details.columns().stream().map(col -> csvEscape(row.get(col.name()))).collect(Collectors.joining(","))).append("\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public List<SearchHit> globalSearch(String query, int limitPerTable) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<SearchHit> results = new ArrayList<>();
        for (DatabaseConnection connection : adminService.listActive()) {
            for (TableSummary table : listTables(connection.getId())) {
                TableDetails details = describeTable(connection.getId(), table.tableName());
                List<String> searchable = details.columns().stream()
                        .filter(column -> isSearchable(column.typeCode()))
                        .map(ColumnDetails::name)
                        .toList();
                if (searchable.isEmpty()) {
                    continue;
                }
                String where = searchable.stream().map(column -> quote(column) + "::text ILIKE ?").collect(Collectors.joining(" OR "));
                String sql = "SELECT * FROM " + tableRef(details).qualified() + " WHERE " + where + " LIMIT " + Math.min(50, limitPerTable);
                List<Object> params = searchable.stream().map(column -> "%" + query.trim() + "%").collect(Collectors.toList());
                try {
                    List<Map<String, Object>> rows = jdbcTemplate(connection.getId()).query(sql, params.toArray(), rowMapper(details.columns()));
                    for (Map<String, Object> row : rows) {
                        results.add(new SearchHit(connection.getId(), connection.getName(), details.tableName(), preview(row, details.columns())));
                    }
                } catch (DataAccessException ignored) {
                }
            }
        }
        return results.stream().limit(200).toList();
    }

    public RowFormModel buildRowForm(Long connectionId, String tableName, Map<String, Object> existingValues) {
        TableDetails details = describeTable(connectionId, tableName);
        List<FormField> fields = new ArrayList<>();
        for (ColumnDetails column : details.columns()) {
            if (column.autoIncrement() && existingValues == null) {
                continue;
            }
            Object value = existingValues == null ? null : existingValues.get(column.name());
            fields.add(new FormField(column.name(), label(column.name()), inputType(column.typeCode()), value == null ? "" : Objects.toString(value, ""), column.nullable(), column.autoIncrement(), details.primaryKeys().contains(column.name())));
        }
        return new RowFormModel(details, fields);
    }

    private void insert(TableDetails details, JdbcTemplate jdbcTemplate, Map<String, Object> values) {
        List<ColumnDetails> insertable = details.columns().stream().filter(col -> !col.autoIncrement()).filter(col -> values.containsKey(col.name())).toList();
        String sql = "INSERT INTO " + tableRef(details).qualified() + " (" + insertable.stream().map(col -> quote(col.name())).collect(Collectors.joining(", ")) + ") VALUES (" + insertable.stream().map(col -> "?").collect(Collectors.joining(", ")) + ")";
        jdbcTemplate.update(sql, insertable.stream().map(col -> values.get(col.name())).toArray());
    }

    private void update(TableDetails details, JdbcTemplate jdbcTemplate, Map<String, Object> values, String rowId) {
        List<ColumnDetails> updateable = details.columns().stream().filter(col -> !col.autoIncrement()).filter(col -> !details.primaryKeys().contains(col.name())).filter(col -> values.containsKey(col.name())).toList();
        String set = updateable.stream().map(col -> quote(col.name()) + " = ?").collect(Collectors.joining(", "));
        String where = details.primaryKeys().stream().map(key -> quote(key) + " = ?").collect(Collectors.joining(" AND "));
        List<Object> params = new ArrayList<>(updateable.stream().map(col -> values.get(col.name())).toList());
        params.addAll(Arrays.asList(decodeRowId(rowId, details.primaryKeys().size())));
        jdbcTemplate.update("UPDATE " + tableRef(details).qualified() + " SET " + set + " WHERE " + where, params.toArray());
    }

    private Map<String, Object> normalize(TableDetails details, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ColumnDetails column : details.columns()) {
            if (!submitted.containsKey(column.name()) && !isBoolean(column.typeCode())) {
                continue;
            }
            String raw = submitted.get(column.name());
            if (isBoolean(column.typeCode())) {
                values.put(column.name(), raw != null && !raw.isBlank());
            } else if (raw == null || raw.isBlank()) {
                values.put(column.name(), null);
            } else {
                values.put(column.name(), convert(column.typeCode(), raw));
            }
        }
        return values;
    }

    private Object convert(int typeCode, String raw) {
        return switch (typeCode) {
            case Types.BIGINT -> Long.valueOf(raw);
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> Integer.valueOf(raw);
            case Types.NUMERIC, Types.DECIMAL -> new java.math.BigDecimal(raw);
            case Types.BOOLEAN, Types.BIT -> Boolean.valueOf(raw);
            default -> raw;
        };
    }

    private boolean isSearchable(int typeCode) {
        return switch (typeCode) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.CLOB, Types.NVARCHAR, Types.LONGNVARCHAR, Types.NCLOB, Types.OTHER -> true;
            default -> false;
        };
    }

    private boolean isBoolean(int typeCode) {
        return typeCode == Types.BOOLEAN || typeCode == Types.BIT;
    }

    private String inputType(int typeCode) {
        return switch (typeCode) {
            case Types.DATE -> "date";
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> "time";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "datetime-local";
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT, Types.BIGINT, Types.DECIMAL, Types.NUMERIC, Types.FLOAT, Types.REAL, Types.DOUBLE -> "number";
            case Types.BOOLEAN, Types.BIT -> "checkbox";
            default -> "text";
        };
    }

    private String resolveSortColumn(TableDetails details, String requested) {
        if (StringUtils.hasText(requested)) {
            return details.columns().stream()
                    .map(ColumnDetails::name)
                    .filter(name -> name.equalsIgnoreCase(requested))
                    .findFirst()
                    .orElseGet(() -> details.primaryKeys().isEmpty() ? (details.columns().isEmpty() ? "1" : details.columns().get(0).name()) : details.primaryKeys().get(0));
        }
        if (!details.primaryKeys().isEmpty()) {
            return details.primaryKeys().get(0);
        }
        return details.columns().isEmpty() ? "1" : details.columns().get(0).name();
    }

    private long countRows(Long connectionId, String schema, String table) {
        return jdbcTemplate(connectionId).queryForObject("SELECT COUNT(*) FROM " + quote(schema) + "." + quote(table), Long.class);
    }

    private JdbcTemplate jdbcTemplate(Long connectionId) {
        DataSource source = registry.get(connectionId);
        return new JdbcTemplate(source);
    }

    private TableRef resolve(DatabaseMetaData metaData, String tableName) throws SQLException {
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.", 2);
            return new TableRef(parts[0], parts[1]);
        }
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String name = rs.getString("TABLE_NAME");
                if (!skip(schema, name)) {
                    return new TableRef(schema, name);
                }
            }
        }
        throw new IllegalArgumentException("Table not found: " + tableName);
    }

    private boolean skip(String schema, String name) {
        return name == null || name.startsWith("pg_") || "information_schema".equalsIgnoreCase(schema) || "pg_catalog".equalsIgnoreCase(schema);
    }

    private TableRef tableRef(TableDetails details) {
        return new TableRef(details.schema(), details.tableName());
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private RowMapper<Map<String, Object>> rowMapper(List<ColumnDetails> columns) {
        return (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (ColumnDetails column : columns) {
                row.put(column.name(), rs.getObject(column.name()));
            }
            return row;
        };
    }

    private String preview(Map<String, Object> row, List<ColumnDetails> columns) {
        return columns.stream().map(ColumnDetails::name).limit(3).map(name -> name + "=" + Objects.toString(row.get(name), "")).collect(Collectors.joining(" | "));
    }

    public String encodeRowId(List<?> values) {
        return values.stream()
                .map(value -> Base64.getUrlEncoder().withoutPadding().encodeToString(Objects.toString(value, "").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .collect(Collectors.joining("~"));
    }
    private String csvEscape(Object value) {
        String text = Objects.toString(value, "");
        String escaped = text.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") ? "\"" + escaped + "\"" : escaped;
    }

    private Object[] decodeRowId(String rowId, int parts) {
        String[] tokens = rowId.split("~", -1);
        if (tokens.length != parts) {
            throw new IllegalArgumentException("Invalid row identifier");
        }
        return Arrays.stream(tokens).map(token -> new String(Base64.getUrlDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8)).toArray();
    }

    private String label(String value) {
        String cleaned = value.replace('_', ' ');
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    public static final class TableSummary {
        private final String schemaName;
        private final String tableName;
        private final String remarks;
        private long rowCount;

        public TableSummary(String schemaName, String tableName, String remarks) {
            this.schemaName = schemaName;
            this.tableName = tableName;
            this.remarks = remarks;
        }

        public String schemaName() { return schemaName; }
        public String tableName() { return tableName; }
        public String remarks() { return remarks; }
        public long rowCount() { return rowCount; }
        public void setRowCount(long rowCount) { this.rowCount = rowCount; }
    }

    public record ColumnDetails(String name, int typeCode, String typeName, int size, int scale, boolean nullable, boolean autoIncrement, String remarks) {}
    public record TableDetails(String schema, String tableName, List<ColumnDetails> columns, List<String> primaryKeys) {}
    public record PagedRows(List<Map<String, Object>> rows, int page, int size, long totalElements, String sortBy, String direction, String search) {
        public long totalPages() {
            return size <= 0 ? 1 : Math.max(1, (long) Math.ceil(totalElements / (double) size));
        }
    }
    public record SearchHit(Long connectionId, String databaseName, String tableName, String preview) {}
    public record RowFormModel(TableDetails tableDetails, List<FormField> fields) {}
    public record FormField(String name, String label, String inputType, String value, boolean nullable, boolean autoIncrement, boolean readOnly) {
        public boolean checked() {
            return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
        }
    }
    public static final class TableRef {
        private final String schema;
        private final String name;
        public TableRef(String schema, String name) { this.schema = schema; this.name = name; }
        public String qualified() { return "\"" + schema.replace("\"", "\"\"") + "\".\"" + name.replace("\"", "\"\"") + "\""; }
    }
}


