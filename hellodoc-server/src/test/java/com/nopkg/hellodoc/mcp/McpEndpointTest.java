package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.userdetails.User;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpEndpointTest {
    @LocalServerPort int port;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired UserAuthRepository identities;
    @Autowired com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository knowledgeBases;

    @Test
    void endpointRequiresLoginAndNegotiatesMcp() throws Exception {
        var user = new SysUser(); user.setNickname("mcp endpoint"); user.setStatus((short) 0); user = users.save(user);
        String username = "mcp-endpoint-" + user.getId();
        var auth = new SysUserAuth(); auth.setUser(user); auth.setIdentityType("PASSWORD"); auth.setIdentifier(username); auth.setCredential("unused");
        identities.save(auth);
        String token = tokens.generateToken(User.withUsername(username).password("unused").authorities("ROLE_USER").build());
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"integration-test\",\"version\":\"1\"}}}";
        var client = HttpClient.newHttpClient();
        var uri = URI.create("http://127.0.0.1:" + port + "/api/mcp");
        var plain = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var authorized = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertTrue(client.send(plain, HttpResponse.BodyHandlers.ofString()).statusCode() >= 400);
        var response = client.send(authorized, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("protocolVersion"), response.body());
        String session = response.headers().firstValue("Mcp-Session-Id").orElseThrow();
        String list = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        var listReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(list)).build();
        var listResult = client.send(listReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResult.statusCode(), listResult.body());
        assertTrue(listResult.body().contains("list_items"), listResult.body());
        assertTrue(listResult.body().contains("create_item"), listResult.body());
        assertTrue(listResult.body().contains("create_document"), listResult.body());
        var kb = new com.nopkg.hellodoc.entities.KbKnowledgeBase(); kb.setTitle("mcp integration"); kb.setOwnerId(user.getId());
        kb = knowledgeBases.save(kb);
        String call = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_items\",\"arguments\":{\"knowledgeBaseId\":" + kb.getId() + "}}}";
        var callReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(call)).build();
        var callResult = client.send(callReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, callResult.statusCode(), callResult.body());
        assertFalse(callResult.body().contains("无法列出事项"), callResult.body());
        assertTrue(callResult.body().contains("[]"), callResult.body());
    }
}
