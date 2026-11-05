package com.nopkg.hellodoc.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
public class FreshDatabaseMigrationStrategy {
    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return FreshDatabaseMigrationStrategy::migrate;
    }

    public static void migrate(Flyway flyway) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                try (var statement = connection.createStatement();
                     var tables = statement.executeQuery("SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' LIMIT 1")) {
                    if (!tables.next()) {
                        var sql = new StringBuilder();
                        try (var reader = new BufferedReader(new InputStreamReader(
                                new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String trimmed = line.trim();
                                if (!trimmed.startsWith("\\") && !trimmed.toLowerCase(Locale.ROOT).startsWith("set transaction_timeout")) {
                                    sql.append(line).append('\n');
                                }
                            }
                        }
                        connection.setAutoCommit(false);
                        try {
                            var populator = new ResourceDatabasePopulator(new ByteArrayResource(sql.toString().getBytes(StandardCharsets.UTF_8)));
                            populator.setSeparator(";\n");
                            populator.populate(connection);
                            connection.commit();
                        } catch (Exception e) {
                            connection.rollback();
                            throw e;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize fresh database", e);
        }
        flyway.migrate();
    }
}
