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
    @Autowired com.nopkg.hellodoc.services.DocumentService documents;
    @Autowired McpReadService directoryReads;

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
        assertTrue(listResult.body().contains("整篇内容放入代码块"), listResult.body());
        assertTrue(listResult.body().contains("search_documents"), listResult.body());
        assertTrue(listResult.body().contains("read_document"), listResult.body());
        assertTrue(listResult.body().contains("reorder_folder_children"), listResult.body());
        var kb = new com.nopkg.hellodoc.entities.KbKnowledgeBase(); kb.setTitle("mcp integration"); kb.setOwnerId(user.getId());
        kb = knowledgeBases.save(kb);
        String staleList = "{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"tools/call\",\"params\":{\"name\":\"list_folder_children\",\"arguments\":{\"knowledgeBaseId\":" + kb.getId() + ",\"offset\":50,\"expectedSnapshot\":\"99999\"}}}";
        var staleListReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(staleList)).build();
        assertTrue(client.send(staleListReq, HttpResponse.BodyHandlers.ofString()).body().contains("ORDER_CONFLICT"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, "unused"));
        var a = new com.nopkg.hellodoc.web.dto.kb.DocCreateDTO(); a.setName("sort-a"); a.setType(com.nopkg.hellodoc.enums.DocType.FILE);
        var b = new com.nopkg.hellodoc.web.dto.kb.DocCreateDTO(); b.setName("sort-b"); b.setType(com.nopkg.hellodoc.enums.DocType.FOLDER);
        var first = documents.create(user.getId(), kb.getId(), a);
        var second = documents.create(user.getId(), kb.getId(), b);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var snapshot = directoryReads.listFolderChildren(user.getId(), kb.getId(), null, 0).get("snapshot");
        String reorder = "{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\",\"params\":{\"name\":\"reorder_folder_children\",\"arguments\":{\"knowledgeBaseId\":" + kb.getId() + ",\"parentId\":0,\"expectedSnapshot\":\"" + snapshot + "\",\"orderedIds\":[" + first.getId() + "," + second.getId() + "]}}}";
        var reorderReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(reorder)).build();
        var reorderResult = client.send(reorderReq, HttpResponse.BodyHandlers.ofString());
        assertTrue(reorderResult.body().contains("\\\"changed\\\":true"), reorderResult.body());
        var sorted = (java.util.List<?>) directoryReads.listFolderChildren(user.getId(), kb.getId(), null, 0).get("children");
        assertEquals(first.getId(), ((java.util.Map<?, ?>) sorted.get(0)).get("documentId"));
        String reorderWeb = "{\"parentId\":0,\"expectedIds\":[" + first.getId() + "," + second.getId() + "],\"orderedIds\":[" + second.getId() + "," + first.getId() + "]}";
        var webReq = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/kb/" + kb.getId() + "/documents/reorder"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(reorderWeb)).build();
        assertTrue(client.send(webReq, HttpResponse.BodyHandlers.ofString()).body().contains("\"code\":0"));
        String call = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_items\",\"arguments\":{\"knowledgeBaseId\":" + kb.getId() + "}}}";
        var callReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(call)).build();
        var callResult = client.send(callReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, callResult.statusCode(), callResult.body());
        assertFalse(callResult.body().contains("无法列出事项"), callResult.body());
        assertTrue(callResult.body().contains("[]"), callResult.body());
        var otherKb = new com.nopkg.hellodoc.entities.KbKnowledgeBase(); otherKb.setTitle("second kb"); otherKb.setOwnerId(user.getId());
        otherKb = knowledgeBases.save(otherKb);
        var dto = new com.nopkg.hellodoc.web.dto.kb.DocCreateDTO(); dto.setName("部署指南");
        dto.setType(com.nopkg.hellodoc.enums.DocType.FILE); dto.setContent("独特关键词部署说明");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, "unused"));
        var doc = documents.create(user.getId(), otherKb.getId(), dto);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        String search = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"search_documents\",\"arguments\":{\"query\":\"独特关键词\"}}}";
        var searchReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(search)).build();
        var found = client.send(searchReq, HttpResponse.BodyHandlers.ofString());
        assertTrue(found.body().contains("部署指南"), found.body());
        assertTrue(found.body().contains("\\\"type\\\":\\\"file\\\""), found.body());
        assertTrue(found.body().contains("draft"), found.body());
        String read = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"read_document\",\"arguments\":{\"documentId\":" + doc.getId() + "}}}";
        var readReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(read)).build();
        var bodyResult = client.send(readReq, HttpResponse.BodyHandlers.ofString());
        assertTrue(bodyResult.body().contains("独特关键词部署说明"), bodyResult.body());
        String update = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"update_document\",\"arguments\":{\"documentId\":" + doc.getId() + ",\"expectedVersion\":" + doc.getCurrentVersion() + ",\"mode\":\"append\",\"markdown\":\"追加内容\",\"requestId\":\"http-update-once\"}}}";
        var updateReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(update)).build();
        var updateResult = client.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertTrue(updateResult.body().contains("\"isError\":false"), updateResult.body());
        var retryResult = client.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertTrue(retryResult.body().contains("\"isError\":false"), retryResult.body());
        assertEquals("独特关键词部署说明追加内容", documents.getContent(doc.getId()));

        String staleRead = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"read_document\",\"arguments\":{\"documentId\":" + doc.getId() + ",\"offset\":1,\"expectedVersion\":99999}}}";
        var staleReadReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(staleRead)).build();
        var staleReadResult = client.send(staleReadReq, HttpResponse.BodyHandlers.ofString());
        assertTrue(staleReadResult.body().contains("VERSION_CONFLICT"), staleReadResult.body());
        assertFalse(staleReadResult.body().contains("关键词部署说明"), staleReadResult.body());

        var folderDto = new com.nopkg.hellodoc.web.dto.kb.DocCreateDTO(); folderDto.setName("MCP测试文件夹");
        folderDto.setType(com.nopkg.hellodoc.enums.DocType.FOLDER);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, "unused"));
        var folder = documents.create(user.getId(), otherKb.getId(), folderDto);
        var childDto = new com.nopkg.hellodoc.web.dto.kb.DocCreateDTO(); childDto.setName("文件夹内文档");
        childDto.setType(com.nopkg.hellodoc.enums.DocType.FILE); childDto.setParentId(folder.getId()); childDto.setContent("正文");
        documents.create(user.getId(), otherKb.getId(), childDto);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        String folderCall = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"read_document\",\"arguments\":{\"documentId\":" + folder.getId() + "}}}";
        var folderReq = HttpRequest.newBuilder(uri).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(folderCall)).build();
        var folderResult = client.send(folderReq, HttpResponse.BodyHandlers.ofString());
        assertTrue(folderResult.body().contains("这是文件夹，没有正文"), folderResult.body());
        assertTrue(folderResult.body().contains("文件夹内文档"), folderResult.body());


    }
    @Autowired McpAccessTokenService accessTokens;
    @Test void dedicatedTokenOnlyWorksForMcpAndCanBeRevoked() throws Exception {
        var user = new SysUser(); user.setNickname("mcp token http"); user.setStatus((short) 0); user = users.save(user);
        String username = "mcp-http-token-" + user.getId();
        var auth = new SysUserAuth(); auth.setUser(user); auth.setIdentityType("PASSWORD"); auth.setIdentifier(username); auth.setCredential("unused"); identities.save(auth);
        var token = accessTokens.create(user.getId(), username, "HTTP test");
        var client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port;
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        var request = HttpRequest.newBuilder(URI.create(base + "/api/mcp")).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token.token()).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var initialized = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, initialized.statusCode());
        String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
        String call = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"list_knowledge_bases\",\"arguments\":{}}}";
        var discover = HttpRequest.newBuilder(URI.create(base + "/api/mcp")).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").header("Authorization", "Bearer " + token.token()).header("Mcp-Session-Id", session).POST(HttpRequest.BodyPublishers.ofString(call)).build();
        var discovery = client.send(discover, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, discovery.statusCode());
        assertTrue(discovery.body().contains("knowledgeBases"), discovery.body());
        var other = HttpRequest.newBuilder(URI.create(base + "/api/mcp-tokens")).header("Authorization", "Bearer " + token.token()).GET().build();
        assertEquals(401, client.send(other, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertTrue(accessTokens.revoke(user.getId(), token.id()));
        assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401, client.send(discover, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void signedInUserCanCreateListAndRevokeToken() throws Exception {
        var user = new SysUser(); user.setNickname("token management"); user.setStatus((short) 0); user = users.save(user);
        String username = "mcp-manage-" + user.getId();
        var auth = new SysUserAuth(); auth.setUser(user); auth.setIdentityType("PASSWORD"); auth.setIdentifier(username); auth.setCredential("unused"); identities.save(auth);
        String jwt = tokens.generateToken(User.withUsername(username).password("unused").authorities("ROLE_USER").build());
        var client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port + "/api/mcp-tokens";
        var create = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer " + jwt).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Codex\"}")).build();
        var issued = client.send(create, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, issued.statusCode(), issued.body());
        assertTrue(issued.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(issued.body()).get("data");
        String id = json.get("id").asText(), secret = json.get("token").asText();
        var list = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer " + jwt).GET().build();
        String listed = client.send(list, HttpResponse.BodyHandlers.ofString()).body();
        assertTrue(listed.contains(id)); assertFalse(listed.contains(secret));
        var revoke = HttpRequest.newBuilder(URI.create(base + "/" + id)).header("Authorization", "Bearer " + jwt).DELETE().build();
        assertEquals(200, client.send(revoke, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertTrue(accessTokens.authenticate(secret).isEmpty());
    }

}
