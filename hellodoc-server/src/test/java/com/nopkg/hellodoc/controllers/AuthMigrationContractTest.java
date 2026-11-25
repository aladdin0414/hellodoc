package com.nopkg.hellodoc.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "app.database.initializer.enabled=false"
})
class AuthMigrationContractTest {
    @DynamicPropertySource
    static void isolatedDataSource(DynamicPropertyRegistry properties) {
        String url = "jdbc:h2:mem:auth_contract_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                + ";INIT=CREATE DOMAIN IF NOT EXISTS ltree AS VARCHAR\\;CREATE DOMAIN IF NOT EXISTS tsvector AS VARCHAR";
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.hikari.jdbc-url", () -> url);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void flywayCannotOpenAnIndependentDatabase() {
        assertTrue(context.getBeansOfType(org.flywaydb.core.Flyway.class).isEmpty());
    }

    @Test
    void isolatedDatabase() throws Exception {
        try (var conn = dataSource.getConnection()) {
            assertTrue(conn.getMetaData().getURL().startsWith("jdbc:h2:mem:auth_contract_"));
            assertEquals("H2", conn.getMetaData().getDatabaseProductName());
            assertEquals("SA", conn.getMetaData().getUserName());
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void authContract() throws Exception {
        ObjectNode contract = mapper.createObjectNode();
        for (String locale : List.of("en", "zh-CN")) {
            ObjectNode cases = contract.putObject(locale);
            ObjectNode credentials = mapper.createObjectNode()
                    .put("username", UUID.randomUUID().toString()).put("password", UUID.randomUUID().toString())
                    .put("nickname", "Migration User");
            JsonNode registered = request(cases, locale, "register.success", credentials, "application/json", false);
            assertTrue(registered.at("/data/id").isIntegralNumber());
            request(cases, locale, "register.duplicate", credentials, "application/json", false);
            JsonNode login = request(cases, locale, "login.success", credentials, "application/json", false);
            ObjectNode refresh = mapper.createObjectNode().put("refreshToken", login.at("/data/refreshToken").textValue());
            JsonNode rotated = request(cases, locale, "refresh-token.success", refresh, "application/json", false);
            refresh.put("refreshToken", rotated.at("/data/refreshToken").textValue());
            request(cases, locale, "logout.success", refresh, "application/json", false);
            JsonNode loggedOut = request(cases, locale, "refresh-token.afterLogout", refresh, "application/json", false);
            assertNotEquals(0, loggedOut.get("code").intValue());
            request(cases, locale, "login.wrongPassword", credentials.deepCopy().put("password","incorrect"), "application/json", false);
            request(cases, locale, "refresh-token.invalid", refresh.put("refreshToken","invalid"), "application/json", false);
            for (String endpoint : List.of("register", "login", "refresh-token", "logout")) {
                request(cases, locale, endpoint + ".missingFields", mapper.createObjectNode(), "application/json", false);
                request(cases, locale, endpoint + ".wrongMedia", mapper.createObjectNode(), "text/plain", false);
                request(cases, locale, endpoint + ".invalidBearer", credentials, "application/json", true);
            }
        }
        assertEquals(mapper.readTree(Files.readString(Path.of("../tests/migration/fixtures/auth_contract.json"))), contract);
    }

    private JsonNode request(ObjectNode cases, String locale, String name, JsonNode body, String media, boolean bearer)
            throws Exception {
        String endpoint = name.substring(0, name.indexOf('.'));
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/" + endpoint))
                .header("Content-Type", media).header("Accept-Language", locale)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (bearer) builder.header("Authorization", "Bearer invalid");
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        var observed = cases.putObject(name).put("status", response.statusCode())
                .put("contentType", response.headers().firstValue("Content-Type").orElse(""));
        ObjectNode raw = (ObjectNode) mapper.readTree(response.body());
        ObjectNode normalized = raw.deepCopy();
        if (normalized.get("data") instanceof ObjectNode data) {
            for (String key : List.of("accessToken", "refreshToken")) {
                if (data.hasNonNull(key)) {
                    assertEquals(3, data.get(key).asText().split("\\.").length);
                    data.put(key, "<jwt>");
                }
            }
            if (data.has("id")) {
                assertTrue(data.get("id").isIntegralNumber() && data.get("id").longValue() > 0);
                data.put("id", "<id>");
            }
            for (String key : List.of("createTime", "updateTime")) {
                if (data.hasNonNull(key)) {
                    Instant.parse(data.get(key).asText());
                    data.put(key, "<instant>");
                }
            }
        }
        observed.set("body", normalized);
        return raw;
    }
}
