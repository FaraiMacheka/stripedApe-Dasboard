package com.stripedape.dashboard.service;

import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class DataSeeder {

    private final DatabaseAdminService databaseAdminService;

    public DataSeeder(DatabaseAdminService databaseAdminService) {
        this.databaseAdminService = databaseAdminService;
    }

    @PostConstruct
    public void init() {
        databaseAdminService.seedConnections();
    }
}

