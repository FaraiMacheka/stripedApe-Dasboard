package com.stripedape.dashboard.controller;

import com.stripedape.dashboard.service.DatabaseAdminService;
import com.stripedape.dashboard.service.DynamicCrudService;
import com.stripedape.dashboard.service.UserAdminService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final DatabaseAdminService databaseAdminService;
    private final DynamicCrudService crudService;
    private final UserAdminService userAdminService;

    public DashboardController(
            DatabaseAdminService databaseAdminService,
            DynamicCrudService crudService,
            UserAdminService userAdminService
    ) {
        this.databaseAdminService = databaseAdminService;
        this.crudService = crudService;
        this.userAdminService = userAdminService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<?> databases = databaseAdminService.listAll();
        model.addAttribute("databases", databases);
        model.addAttribute("databaseCount", databases.size());
        model.addAttribute("activeCount", databaseAdminService.listActive().size());
        model.addAttribute("userCount", userAdminService.listUsers().size());
        return "dashboard";
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String query, Model model) {
        model.addAttribute("query", query);
        model.addAttribute("results", crudService.globalSearch(query, 25));
        return "search";
    }
}
