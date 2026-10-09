package com.ivan_naumenko.repository_analyzer.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalMigrationTest {
    @TempDir Path directory;

    @Test void upgradesExistingReportsWithoutLosingContentOrPeriodAndAllowsAnotherRevision() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("upgrade.db");
        Flyway.configure().dataSource(url, "", "").target("8").load().migrate();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("INSERT INTO analysis_task VALUES (7, 'https://github.com/example/project.git', 'main', 'COMPLETED', '2026-07-20', '2026-07-20')");
            statement.execute("INSERT INTO analysis_report (id, analysis_task_id, audience, period_type, period_days, period_key, status, total_commits, processed_commits, content, created_at, updated_at) VALUES (11, 7, 'DEVELOPER', 'LAST_DAYS', 30, 'DAYS:30', 'COMPLETED', 5, 5, 'Old report', '2026-07-20', '2026-07-20')");
        }
        Flyway.configure().dataSource(url, "", "").load().migrate();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            try (var rows = statement.executeQuery("SELECT * FROM analysis_report WHERE id = 11")) {
                assertTrue(rows.next());
                assertEquals("Old report", rows.getString("content"));
                assertEquals("DAYS:30", rows.getString("period_key"));
                assertEquals(30, rows.getInt("period_days"));
                assertEquals(0, rows.getInt("analysis_revision"));
                assertEquals(5, rows.getInt("processed_commits"));
            }
            statement.execute("INSERT INTO analysis_report (id, analysis_task_id, audience, period_key, analysis_revision, status, created_at, updated_at) VALUES (12, 7, 'DEVELOPER', 'DAYS:30', 1, 'NEW', '2026-07-21', '2026-07-21')");
            try (var rows = statement.executeQuery("PRAGMA foreign_key_check")) { assertFalse(rows.next()); }
            statement.execute("DELETE FROM analysis_task WHERE id = 7");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM analysis_report")) { assertEquals(0, rows.getInt(1)); }
        }
    }
}
