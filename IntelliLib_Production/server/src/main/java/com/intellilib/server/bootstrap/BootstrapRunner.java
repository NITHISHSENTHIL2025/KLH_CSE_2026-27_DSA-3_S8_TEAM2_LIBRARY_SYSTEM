package com.intellilib.server.bootstrap;

import com.intellilib.server.search.SearchEngine;
import com.intellilib.server.service.AdminService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(10)
public class BootstrapRunner implements CommandLineRunner {
    private final AdminService admin;
    private final SearchEngine search;

    public BootstrapRunner(AdminService admin, SearchEngine search) {
        this.admin = admin;
        this.search = search;
    }

    @Override
    public void run(String... args) {
        // Production starts clean: only the administrator is bootstrapped.
        // Real books/students are imported by the administrator.
        admin.bootstrapAdmin();
        search.rebuild();
    }
}
