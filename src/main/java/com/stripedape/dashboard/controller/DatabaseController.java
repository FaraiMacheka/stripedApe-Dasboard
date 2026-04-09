package com.stripedape.dashboard.controller;

import com.stripedape.dashboard.domain.DatabaseConnection;
import com.stripedape.dashboard.service.DatabaseAdminService;
import com.stripedape.dashboard.service.DatabaseBackupService;
import com.stripedape.dashboard.service.DatabaseConnectionForm;
import com.stripedape.dashboard.service.DatabaseDiscoveryService;
import com.stripedape.dashboard.service.DynamicCrudService;
import com.stripedape.dashboard.service.DynamicCrudService.PagedRows;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/databases")
public class DatabaseController {

    private final DatabaseAdminService databaseAdminService;
    private final DynamicCrudService crudService;
    private final DatabaseDiscoveryService discoveryService;
    private final DatabaseBackupService backupService;

    public DatabaseController(
            DatabaseAdminService databaseAdminService,
            DynamicCrudService crudService,
            DatabaseDiscoveryService discoveryService,
            DatabaseBackupService backupService
    ) {
        this.databaseAdminService = databaseAdminService;
        this.crudService = crudService;
        this.discoveryService = discoveryService;
        this.backupService = backupService;
    }

    @GetMapping
    public String list(Model model) {
        List<DatabaseConnection> databases = databaseAdminService.listAll();
        model.addAttribute("databases", databases);
        model.addAttribute("discoveredCount", databases.stream().filter(DatabaseConnection::isAutoDiscovered).count());
        return "databases";
    }

    @PostMapping("/discover")
    @PreAuthorize("hasRole('ADMIN')")
    public String discover(RedirectAttributes redirectAttributes) {
        DatabaseDiscoveryService.DiscoverySummary summary = discoveryService.discoverNow();
        StringBuilder message = new StringBuilder("Discovery completed. Scanned ")
                .append(summary.scannedSources())
                .append(" source connection(s).");
        if (summary.discoveredCount() > 0) {
            message.append(" Added ")
                    .append(summary.discoveredCount())
                    .append(" new database(s): ")
                    .append(String.join(", ", summary.newlyAdded()))
                    .append('.');
        } else {
            message.append(" No new databases were found.");
        }
        if (!summary.failedSources().isEmpty()) {
            message.append(" Some sources could not be scanned: ")
                    .append(String.join(", ", summary.failedSources()))
                    .append('.');
        }
        redirectAttributes.addFlashAttribute("message", message.toString());
        return "redirect:/databases";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String create(Model model) {
        model.addAttribute("form", new DatabaseConnectionForm());
        return "database-form";
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@ModelAttribute("form") DatabaseConnectionForm form) {
        DatabaseConnection saved = databaseAdminService.save(form);
        return "redirect:/databases/" + saved.getId() + "/tables";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id) {
        return "redirect:/databases/" + id + "/tables";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String edit(@PathVariable Long id, Model model) {
        DatabaseConnection connection = databaseAdminService.getRequired(id);
        DatabaseConnectionForm form = new DatabaseConnectionForm();
        form.setId(connection.getId());
        form.setName(connection.getName());
        form.setJdbcUrl(connection.getJdbcUrl());
        form.setUsername(connection.getUsername());
        form.setActive(connection.isActive());
        form.setDescription(connection.getDescription());
        model.addAttribute("form", form);
        return "database-form";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id) {
        databaseAdminService.delete(id);
        return "redirect:/databases";
    }

    @PostMapping("/{id}/backup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> backup(@PathVariable Long id) throws IOException {
        DatabaseConnection connection = databaseAdminService.getRequired(id);
        DatabaseBackupService.BackupArtifact artifact = backupService.backup(connection);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"")
                .contentLength(artifact.path().toFile().length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(artifact.resource());
    }

    @GetMapping("/{id}/tables")
    public String tables(@PathVariable Long id, Model model) {
        DatabaseConnection connection = databaseAdminService.getRequired(id);
        model.addAttribute("connection", connection);
        model.addAttribute("tables", crudService.listTables(id));
        return "tables";
    }

    @GetMapping("/{id}/tables/{tableName}")
    public String rows(
            @PathVariable Long id,
            @PathVariable String tableName,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String direction,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Model model
    ) {
        DatabaseConnection connection = databaseAdminService.getRequired(id);
        var details = crudService.describeTable(id, tableName);
        PagedRows pageData = crudService.pageRows(id, tableName, query, sort, direction, page, size);
        List<RowView> rows = pageData.rows().stream()
                .map(row -> new RowView(row, rowIdFor(details.primaryKeys(), row)))
                .collect(Collectors.toList());
        model.addAttribute("connection", connection);
        model.addAttribute("details", details);
        model.addAttribute("tableName", tableName);
        model.addAttribute("pageData", pageData);
        model.addAttribute("rows", rows);
        model.addAttribute("searchQuery", query);
        return "table-rows";
    }

    @GetMapping("/{id}/tables/{tableName}/new")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String newRow(@PathVariable Long id, @PathVariable String tableName, Model model) {
        var details = crudService.describeTable(id, tableName);
        model.addAttribute("connection", databaseAdminService.getRequired(id));
        model.addAttribute("details", details);
        model.addAttribute("tableName", tableName);
        model.addAttribute("form", crudService.buildRowForm(id, tableName, null));
        model.addAttribute("rowId", "");
        return "row-form";
    }

    @GetMapping("/{id}/tables/{tableName}/{rowId}/edit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String editRow(@PathVariable Long id, @PathVariable String tableName, @PathVariable String rowId, Model model) {
        Map<String, Object> existing = crudService.fetchRow(id, tableName, rowId);
        var details = crudService.describeTable(id, tableName);
        model.addAttribute("connection", databaseAdminService.getRequired(id));
        model.addAttribute("details", details);
        model.addAttribute("tableName", tableName);
        model.addAttribute("form", crudService.buildRowForm(id, tableName, existing));
        model.addAttribute("rowId", rowId);
        return "row-form";
    }

    @PostMapping("/{id}/tables/{tableName}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String saveRow(@PathVariable Long id, @PathVariable String tableName, @RequestParam Map<String, String> params) {
        String rowId = params.getOrDefault("rowId", "");
        Map<String, String> payload = new java.util.LinkedHashMap<>(params);
        payload.remove("_csrf");
        payload.remove("rowId");
        crudService.saveRow(id, tableName, payload, rowId);
        return "redirect:/databases/" + id + "/tables/" + tableName;
    }

    @PostMapping("/{id}/tables/{tableName}/{rowId}/delete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String deleteRow(@PathVariable Long id, @PathVariable String tableName, @PathVariable String rowId) {
        crudService.deleteRow(id, tableName, rowId);
        return "redirect:/databases/" + id + "/tables/" + tableName;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @GetMapping(value = "/{id}/tables/{tableName}/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long id,
            @PathVariable String tableName,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String direction
    ) {
        byte[] csv = crudService.exportCsv(id, tableName, query, sort, direction);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + tableName + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private String rowIdFor(List<String> primaryKeys, Map<String, Object> row) {
        if (primaryKeys == null || primaryKeys.isEmpty()) {
            return "";
        }
        List<Object> values = primaryKeys.stream().map(row::get).toList();
        return crudService.encodeRowId(values);
    }

    public record RowView(Map<String, Object> values, String rowId) {}
}
