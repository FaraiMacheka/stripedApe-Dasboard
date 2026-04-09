package com.stripedape.dashboard.controller;

import com.stripedape.dashboard.domain.DatabaseConnection;
import com.stripedape.dashboard.service.AppPortsService;
import com.stripedape.dashboard.service.DatabaseAdminService;
import com.stripedape.dashboard.service.DynamicCrudService;
import com.stripedape.dashboard.service.UserAdminService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private static final DateTimeFormatter DASHBOARD_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DatabaseAdminService databaseAdminService;
    private final DynamicCrudService crudService;
    private final UserAdminService userAdminService;
    private final AppPortsService appPortsService;

    public DashboardController(
            DatabaseAdminService databaseAdminService,
            DynamicCrudService crudService,
            UserAdminService userAdminService,
            AppPortsService appPortsService
    ) {
        this.databaseAdminService = databaseAdminService;
        this.crudService = crudService;
        this.userAdminService = userAdminService;
        this.appPortsService = appPortsService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(value = "portsQ", required = false) String portsQuery,
            @RequestParam(value = "dbQ", required = false) String databaseQuery,
            Model model
    ) {
        populateDashboard(model, portsQuery, databaseQuery);
        return "dashboard";
    }

    @GetMapping("/app-ports")
    public String appPorts(@RequestParam(value = "q", required = false) String query, Model model) {
        populateDashboard(model, query, null);
        model.addAttribute("portsQuery", query);
        return "app-ports";
    }

    private void populateDashboard(Model model, String portsQuery, String databaseQuery) {
        List<DatabaseConnection> activeDatabases = databaseAdminService.listActive();
        List<DatabaseConnection> filteredDatabases = databaseAdminService.filterConnections(activeDatabases, databaseQuery);
        AppPortsService.AppPortsSnapshot appPorts = appPortsService.loadSnapshot();
        List<AppPortsService.AppPortEntry> filteredPorts = appPortsService.filterEntries(appPorts.entries(), portsQuery);

        model.addAttribute("databases", filteredDatabases);
        model.addAttribute("databaseCount", databaseAdminService.listAll().size());
        model.addAttribute("activeCount", activeDatabases.size());
        model.addAttribute("userCount", userAdminService.listUsers().size());
        model.addAttribute("appPorts", appPorts);
        model.addAttribute("filteredAppPorts", filteredPorts);
        model.addAttribute("appPortsCount", appPorts.entries().size());
        model.addAttribute("filteredAppPortsCount", filteredPorts.size());
        model.addAttribute("activeAppsCount", appPorts.activeCount());
        model.addAttribute("portsQuery", portsQuery);
        model.addAttribute("databaseQuery", databaseQuery);
        model.addAttribute("filteredDatabaseCount", filteredDatabases.size());
        model.addAttribute("appPortsUpdatedAt", appPorts.lastModifiedEpochMs() > 0
                ? DASHBOARD_TIMESTAMP.format(Instant.ofEpochMilli(appPorts.lastModifiedEpochMs()))
                : null);
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String query, Model model) {
        model.addAttribute("query", query);
        model.addAttribute("results", crudService.globalSearch(query, 25));
        return "search";
    }
}
