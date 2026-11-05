package com.nopkg.hellodoc.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELLODOC_FRESH_TEST_DB_URL", matches = ".+")
class FreshDatabaseMigrationPostgresTest {
    @Test
    void migratesFreshDatabaseAndCanRestart() throws Exception {
        String url = System.getenv("HELLODOC_FRESH_TEST_DB_URL");
        String user = System.getenv("HELLODOC_FRESH_TEST_DB_USER");
        String password = System.getenv("HELLODOC_FRESH_TEST_DB_PASSWORD");
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration/postgresql").baselineOnMigrate(true).load();
        FreshDatabaseMigrationStrategy.migrate(flyway);
        FreshDatabaseMigrationStrategy.migrate(flyway);
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT to_regclass('public.sys_user') IS NOT NULL AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='kb_knowledge_base' AND column_name='tree_revision')")) {
            assertTrue(result.next());
            assertTrue(result.getBoolean(1));
        }
    }
}
