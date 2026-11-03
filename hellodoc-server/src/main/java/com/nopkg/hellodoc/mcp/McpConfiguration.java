package com.nopkg.hellodoc.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.List;
import org.springframework.security.core.Authentication;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class McpConfiguration {
    @Bean
    HttpServletStreamableServerTransportProvider mcpTransport() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/api/mcp")
                .contextExtractor(request -> {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    return authentication == null ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of("authentication", authentication));
                })
                .build();
    }

    @Bean(destroyMethod = "close")
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, AiItemService items,
                            McpIdentity identity, ObjectMapper mapper, McpWriteService writes, McpReadService reads) {
        var listSchema = new McpSchema.JsonSchema("object", Map.of(
                "knowledgeBaseId", Map.of("type", "integer", "minimum", 1),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)),
                List.of("knowledgeBaseId"), false, null, null);
        var listTool = McpSchema.Tool.builder().name("list_items")
                .description("分页列出可访问的事项；请继续查询 nextOffset 直到没有更多结果，再判断匹配。多个候选无法区分时请询问用户。")
                .inputSchema(listSchema).build();
        var itemSchema = new McpSchema.JsonSchema("object", Map.of(
                "knowledgeBaseId", Map.of("type", "integer", "minimum", 1),
                "name", Map.of("type", "string", "minLength", 1, "maxLength", 255),
                "description", Map.of("type", "string"),
                "aliases", Map.of("type", "array", "items", Map.of("type", "string")),
                "requestId", Map.of("type", "string", "minLength", 1, "maxLength", 128)),
                List.of("knowledgeBaseId", "name", "requestId"), false, null, null);
        var documentSchema = new McpSchema.JsonSchema("object", Map.of(
                "knowledgeBaseId", Map.of("type", "integer", "minimum", 1),
                "itemId", Map.of("type", "integer", "minimum", 1),
                "name", Map.of("type", "string", "minLength", 1, "maxLength", 255),
                "markdown", Map.of("type", "string"),
                "requestId", Map.of("type", "string", "minLength", 1, "maxLength", 128)),
                List.of("knowledgeBaseId", "itemId", "name", "markdown", "requestId"), false, null, null);
        var itemTool = McpSchema.Tool.builder().name("create_item")
                .description("没有合适事项时新建根目录事项；先查询候选。requestId 在超时重试时必须保持不变。")
                .inputSchema(itemSchema).build();
        var documentTool = McpSchema.Tool.builder().name("create_document")
                .description("将 Markdown 保存到事项；多个候选无法区分时先询问用户。超时重试时 requestId 必须保持不变。")
                .inputSchema(documentSchema).build();
        var operationSchema = new McpSchema.JsonSchema("object", Map.of(
                "knowledgeBaseId", Map.of("type", "integer", "minimum", 1),
                "operation", Map.of("type", "string", "enum", List.of("item", "document")),
                "requestId", Map.of("type", "string", "minLength", 1, "maxLength", 128)),
                List.of("knowledgeBaseId", "operation", "requestId"), false, null, null);
        var operationTool = McpSchema.Tool.builder().name("get_operation")
                .description("根据原 requestId 查询创建结果；未找到时只使用原 requestId 重试。")
                .inputSchema(operationSchema).build();
        var searchSchema = new McpSchema.JsonSchema("object", Map.of(
                "query", Map.of("type", "string", "minLength", 1),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)), List.of("query"), false, null, null);
        var readSchema = new McpSchema.JsonSchema("object", Map.of(
                "documentId", Map.of("type", "integer", "minimum", 1),
                "offset", Map.of("type", "integer", "minimum", 0)), List.of("documentId"), false, null, null);
        var searchTool = McpSchema.Tool.builder().name("search_documents")
                .description("跨所有有权访问的知识库搜索文档和文件夹（包含草稿），结果 type 标识类型。从问题提取关键词，必要时换词重试；读取相关内容后引用来源。")
                .inputSchema(searchSchema).build();
        var readTool = McpSchema.Tool.builder().name("read_document")
                .description("读取有权访问的文档正文或列出文件夹子项。文档 offset 为 Unicode 字符偏移，文件夹 offset 为可访问子项偏移；hasMore 为 true 时用 nextOffset 继续读取，并引用来源。")
                .inputSchema(readSchema).build();
        return McpServer.sync(transport)
                .serverInfo("hellodoc", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(searchTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        if (!(args.get("query") instanceof String query) || query.isBlank()) throw new IllegalArgumentException();
                        int limit = (int) integerArg(args.getOrDefault("limit", 20), 1, 100);
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        var output = identity.run(auth, user -> reads.search(user, query, limit));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(Map.of("results", output))).build();
                    } catch (IllegalArgumentException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("搜索参数无效。").build();
                    } catch (Exception e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("无法搜索文档，请检查登录状态。").build();
                    }
                })
                .toolCall(readTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        long id = integerArg(args.get("documentId"), 1, Long.MAX_VALUE);
                        int offset = (int) integerArg(args.getOrDefault("offset", 0), 0, Integer.MAX_VALUE);
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        var output = identity.run(auth, user -> reads.read(user, id, offset));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(output)).build();
                    } catch (IllegalArgumentException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("读取参数无效。").build();
                    } catch (Exception e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("文档不存在或不可访问。").build();
                    }
                })
                .toolCall(listTool, (exchange, request) -> {
                    try {
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        long kb = ((Number) request.arguments().get("knowledgeBaseId")).longValue();
                        var result = identity.run(auth, user -> items.listItems(user, kb));
                        int offset = ((Number) request.arguments().getOrDefault("offset", 0)).intValue();
                        int limit = ((Number) request.arguments().getOrDefault("limit", 100)).intValue();
                        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid page");
                        int from = Math.min(offset, result.size());
                        int to = (int) Math.min((long) from + limit, result.size());
                        var page = new java.util.LinkedHashMap<String, Object>();
                        page.put("items", result.subList(from, to));
                        if (to < result.size()) page.put("nextOffset", to);
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(page)).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("无法列出事项，请检查知识库 ID 和访问权限。").build();
                    }
                })
                .toolCall(itemTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        long kb = ((Number) args.get("knowledgeBaseId")).longValue();
                        String name = (String) args.get("name");
                        String description = (String) args.getOrDefault("description", "");
                        var rawAliases = (List<?>) args.getOrDefault("aliases", List.of());
                        List<String> aliases = rawAliases.stream().map(String.class::cast).toList();
                        String requestId = (String) args.get("requestId");
                        long id = identity.run(auth, user -> writes.createItem(user, kb, name, description, aliases, requestId));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(Map.of("itemId", id, "name", name))).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("事项未确认创建成功。请检查权限和同名事项；重试时使用相同 requestId。").build();
                    }
                })
                .toolCall(operationTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        long kb = ((Number) args.get("knowledgeBaseId")).longValue();
                        String operation = (String) args.get("operation");
                        String requestId = (String) args.get("requestId");
                        var result = identity.run(auth, user -> writes.findOperation(user, kb, operation, requestId));
                        var output = result.isPresent() ? Map.of("found", true, "id", result.getAsLong()) : Map.of("found", false);
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(output)).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("无法查询操作结果，请检查权限与请求参数。").build();
                    }
                })
                .toolCall(documentTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        long kb = ((Number) args.get("knowledgeBaseId")).longValue();
                        long item = ((Number) args.get("itemId")).longValue();
                        String name = (String) args.get("name");
                        String markdown = (String) args.get("markdown");
                        String requestId = (String) args.get("requestId");
                        long id = identity.run(auth, user -> writes.createDocument(user, kb, item, name, markdown, requestId));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(Map.of("documentId", id, "itemId", item, "name", name, "path", "/kb/" + kb + "/" + id))).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("文档未确认保存成功。请检查事项和权限；重试时使用相同 requestId。").build();
                    }
                })
                .build();
    }

    private static long integerArg(Object value, long min, long max) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException();
        try {
            long parsed = new java.math.BigDecimal(number.toString()).longValueExact();
            if (parsed < min || parsed > max) throw new IllegalArgumentException();
            return parsed;
        } catch (NumberFormatException | ArithmeticException e) { throw new IllegalArgumentException(); }
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport, McpSyncServer server) {
        var registration = new ServletRegistrationBean<>(transport, "/api/mcp");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }
}
