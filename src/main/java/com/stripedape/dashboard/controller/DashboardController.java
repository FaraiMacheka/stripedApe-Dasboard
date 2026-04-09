package com.stripedape.dashboard.controller;

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
    public String dashboard(Model model) {
        populateDashboard(model);
        return "dashboard";
    }

    @GetMapping("/app-ports")
    public String appPorts(Model model) {
        populateDashboard(model);
        return "app-ports";
    }

    private void populateDashboard(Model model) {
        List<?> databases = databaseAdminService.listAll();
        AppPortsService.AppPortsSnapshot appPorts = appPortsService.loadSnapshot();
        model.addAttribute("databases", databases);
        model.addAttribute("databaseCount", databases.size());
        model.addAttribute("activeCount", databaseAdminService.listActive().size());
        model.addAttribute("userCount", userAdminService.listUsers().size());
        model.addAttribute("appPorts", appPorts);
        model.addAttribute("appPortsCount", appPorts.entries().size());
        model.addAttribute("activeAppsCount", appPorts.activeCount());
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
