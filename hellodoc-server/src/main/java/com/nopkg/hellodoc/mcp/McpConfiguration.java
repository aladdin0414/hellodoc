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
                            McpIdentity identity, ObjectMapper mapper, McpWriteService writes, McpReadService reads, McpUpdateService updates,
                            McpKnowledgeBaseService knowledgeBases, McpMoveService moves, McpSourceLinks sourceLinks) {
        var listSchema = new McpSchema.JsonSchema("object", Map.of(
                "knowledgeBaseId", Map.of("type", "integer", "minimum", 1),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)),
                List.of("knowledgeBaseId"), false, null, null);
        var listTool = McpSchema.Tool.builder().name("list_items")
                .description("先通过 list_knowledge_bases 选择知识库，再分页列出该知识库的事项；请继续查询 nextOffset 直到没有更多结果，再判断匹配。多个候选无法区分时请询问用户。")
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
                "parentId", Map.of("type", "integer", "minimum", 1),
                "name", Map.of("type", "string", "minLength", 1, "maxLength", 255),
                "markdown", Map.of("type", "string"),
                "requestId", Map.of("type", "string", "minLength", 1, "maxLength", 128)),
                List.of("knowledgeBaseId", "name", "markdown", "requestId"), false, null, null);
        var itemTool = McpSchema.Tool.builder().name("create_item")
                .description("先通过 list_knowledge_bases 选择知识库，查询候选后没有合适事项时在该知识库根目录新建事项。requestId 在超时重试时必须保持不变。")
                .inputSchema(itemSchema).build();
        var documentTool = McpSchema.Tool.builder().name("create_document")
                .description("保存 Markdown 前先用 list_knowledge_bases、search_documents 和 list_folder_children 查找相关目录；用户指定位置或相关目录明确时使用该目录的 parentId，不要默认保存到根目录。多个候选无法区分时询问用户；仅用户指定根目录或没有合适目录时省略 parentId。可指定有编辑权限的 knowledgeBaseId；旧版 itemId 仍支持事项，不要同时提供 itemId 和 parentId。不要将整篇内容放入代码块；PDF 未识别内容须说明。超时重试必须使用相同 requestId。")
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
                "offset", Map.of("type", "integer", "minimum", 0),
                "expectedVersion", Map.of("type", "integer", "minimum", 0)), List.of("documentId"), false, null, null);
        var searchTool = McpSchema.Tool.builder().name("search_documents")
                .description("跨所有有权访问的知识库搜索文档和文件夹（包含草稿），结果 type 标识类型。从问题提取关键词，必要时换词重试；读取相关内容后引用来源。")
                .inputSchema(searchSchema).build();
        var readTool = McpSchema.Tool.builder().name("read_document")
                .description("读取有权访问的文档正文或列出文件夹子项。文档返回 version 和 currentParentId（0 表示根目录）；续读正文须同时提交第一页的 expectedVersion，文档变更时从头读取。文档 offset 为 Unicode 字符偏移，文件夹 offset 为可访问子项偏移；hasMore 为 true 时用 nextOffset 继续读取，并引用来源。")
                .inputSchema(readSchema).build();
        var folderSchema = new McpSchema.JsonSchema("object", Map.of(
                "knowledgeBaseId", Map.of("type", "integer", "minimum", 1),
                "parentId", Map.of("type", "integer", "minimum", 1),
                "offset", Map.of("type", "integer", "minimum", 0)), List.of("knowledgeBaseId"), false, null, null);
        var folderTool = McpSchema.Tool.builder().name("list_folder_children")
                .description("分页浏览知识库目录；省略 parentId 浏览根目录，否则列出指定文件夹内有权查看的子项。使用 nextOffset 继续查询。")
                .inputSchema(folderSchema).build();
        var kbSchema = new McpSchema.JsonSchema("object", Map.of(
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)), List.of(), false, null, null);
        var kbTool = McpSchema.Tool.builder().name("list_knowledge_bases")
                .description("分页列出账号可访问的知识库；canBrowseRoot 为 false 表示仅有部分文档权限，请通过搜索查找共享文档。")
                .inputSchema(kbSchema).build();
        var updateSchema = new McpSchema.JsonSchema("object", Map.of(
                "documentId", Map.of("type", "integer", "minimum", 1),
                "expectedVersion", Map.of("type", "integer", "minimum", 0),
                "mode", Map.of("type", "string", "enum", List.of("replace", "append")),
                "markdown", Map.of("type", "string"),
                "requestId", Map.of("type", "string", "minLength", 1, "maxLength", 128)),
                List.of("documentId", "expectedVersion", "mode", "markdown", "requestId"), false, null, null);
        var updateTool = McpSchema.Tool.builder().name("update_document")
                .description("跨所有有编辑权限的知识库更新普通未加密文档；先读取正文和版本，使用 replace 替换或 append 原样追加。VERSION_CONFLICT 时重新读取，不得自动覆盖；REQUEST_ID_CONFLICT 时使用新请求 ID；超时重试使用原 requestId。Markdown 要使用标题、列表和表格，不要把整篇内容放进代码块。")
                .inputSchema(updateSchema).build();
        var moveSchema = new McpSchema.JsonSchema("object", Map.of(
                "documentId", Map.of("type", "integer", "minimum", 1),
                "expectedParentId", Map.of("type", "integer", "minimum", 0),
                "parentId", Map.of("type", "integer", "minimum", 0)),
                List.of("documentId", "expectedParentId", "parentId"), false, null, null);
        var moveTool = McpSchema.Tool.builder().name("move_document")
                .description("在同一知识库内移动有编辑权限的普通文档；先读取 currentParentId 并用作 expectedParentId，再通过目录浏览确定目标 parentId；0 表示根目录。若位置已改变返回 LOCATION_CONFLICT，应重新读取并确认；重复相同移动可安全重试。不支持移动文件夹或跨知识库移动。")
                .inputSchema(moveSchema).build();
        return McpServer.sync(transport)
                .serverInfo("hellodoc", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(moveTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        long doc = integerArg(args.get("documentId"), 1, Long.MAX_VALUE);
                        long expected = integerArg(args.get("expectedParentId"), 0, Long.MAX_VALUE);
                        long parent = integerArg(args.get("parentId"), 0, Long.MAX_VALUE);
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        var result = identity.run(auth, user -> moves.move(user, doc, expected, parent));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(result))).build();
                    } catch (McpMoveException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent(e.getMessage()).build();
                    } catch (IllegalArgumentException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("INVALID_ARGUMENT").build();
                    } catch (Exception e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("MOVE_FAILED").build();
                    }
                })
                .toolCall(kbTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        int offset = args.containsKey("offset") ? (int) integerArg(args.get("offset"), 0, Integer.MAX_VALUE) : 0;
                        int limit = args.containsKey("limit") ? (int) integerArg(args.get("limit"), 1, 100) : 50;
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        var result = identity.run(auth, user -> knowledgeBases.list(user, offset, limit));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(result))).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("无法列出知识库，请检查权限与参数。").build();
                    }
                })
                .toolCall(folderTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        long kb = integerArg(args.get("knowledgeBaseId"), 1, Long.MAX_VALUE);
                        Long parent = args.containsKey("parentId") ? integerArg(args.get("parentId"), 1, Long.MAX_VALUE) : null;
                        int offset = args.containsKey("offset") ? (int) integerArg(args.get("offset"), 0, Integer.MAX_VALUE) : 0;
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        var result = identity.run(auth, user -> reads.listFolderChildren(user, kb, parent, offset));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(result))).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("目录不可访问，请检查知识库、目录和权限。").build();
                    }
                })
                .toolCall(updateTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        long id = integerArg(args.get("documentId"), 1, Long.MAX_VALUE);
                        int version = (int) integerArg(args.get("expectedVersion"), 0, Integer.MAX_VALUE);
                        if (!(args.get("mode") instanceof String mode) || !(args.get("markdown") instanceof String markdown)
                                || !(args.get("requestId") instanceof String requestId)) throw new IllegalArgumentException();
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        var result = identity.run(auth, user -> updates.update(user, id, version, mode, markdown, requestId));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(result))).build();
                    } catch (McpUpdateException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent(e.getMessage()).build();
                    } catch (IllegalArgumentException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("INVALID_ARGUMENT").build();
                    } catch (Exception e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("UPDATE_FAILED").build();
                    }
                })
                .toolCall(searchTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        if (!(args.get("query") instanceof String query) || query.isBlank()) throw new IllegalArgumentException();
                        int limit = (int) integerArg(args.getOrDefault("limit", 20), 1, 100);
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        var output = identity.run(auth, user -> reads.search(user, query, limit));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(Map.of("results", output)))).build();
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
                        Integer expected = args.containsKey("expectedVersion") ? (int) integerArg(args.get("expectedVersion"), 0, Integer.MAX_VALUE) : null;
                        var output = identity.run(auth, user -> reads.read(user, id, offset, expected));
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(output))).build();
                    } catch (McpVersionConflictException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("VERSION_CONFLICT：文档已更改，请从头读取。 ").build();
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
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(output))).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("无法查询操作结果，请检查权限与请求参数。").build();
                    }
                })
                .toolCall(documentTool, (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        var auth = (Authentication) exchange.transportContext().get("authentication");
                        long kb = ((Number) args.get("knowledgeBaseId")).longValue();
                        if (args.containsKey("itemId") && args.containsKey("parentId")) throw new IllegalArgumentException();
                        Long item = args.containsKey("itemId") ? integerArg(args.get("itemId"), 1, Long.MAX_VALUE) : null;
                        Long parent = args.containsKey("parentId") ? integerArg(args.get("parentId"), 1, Long.MAX_VALUE) : null;
                        String name = (String) args.get("name");
                        String markdown = (String) args.get("markdown");
                        String requestId = (String) args.get("requestId");
                        var output = identity.run(auth, user -> {
                            long id = item != null ? writes.createDocument(user, kb, item, name, markdown, requestId)
                                    : writes.createDocumentInFolder(user, kb, parent, name, markdown, requestId);
                            var read = reads.read(user, id, 0, null);
                            var data = new java.util.LinkedHashMap<String, Object>();
                            data.put("documentId", id); if (item != null) data.put("itemId", item); data.put("parentId", item != null ? item : parent); data.put("name", name);
                            data.put("path", "/kb/" + kb + "/" + id); data.put("version", read.get("version"));
                            data.put("updatedAt", read.get("updatedAt")); return data;
                        });
                        return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(sourceLinks.enrich(output))).build();
                    } catch (Exception error) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("文档未确认保存成功。请检查目录和权限；重试时使用相同 requestId。").build();
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
