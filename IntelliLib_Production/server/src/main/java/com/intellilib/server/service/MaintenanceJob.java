package com.intellilib.server.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceJob {
    private final JdbcTemplate jdbc;
    public MaintenanceJob(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 60_000)
    public void maintainStates() {
        jdbc.update("UPDATE borrow_requests SET status='EXPIRED' WHERE status='APPROVED' AND expires_at IS NOT NULL AND expires_at<NOW()");
        jdbc.update("UPDATE loans SET status='OVERDUE' WHERE status='ISSUED' AND returned_at IS NULL AND due_at<NOW()");
    }
}
