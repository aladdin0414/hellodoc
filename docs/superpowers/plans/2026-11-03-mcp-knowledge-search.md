# MCP Knowledge Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let AI search and read all documents accessible to the authenticated HelloDoc account, including drafts.

**Architecture:** Extend the existing MCP server with search and paginated read tools, backed by existing document permissions. Extend global keyword search with a Chinese substring fallback and make the bridge scope only existing write tools to the configured KB.

**Tech Stack:** Java, Spring, PostgreSQL, MCP Java SDK, Node.js MCP bridge, Gradle, JUnit, Node test runner.

**Spec:** `docs/superpowers/specs/2026-11-03-mcp-knowledge-search-design.md`

## Global Constraints

- Search all accessible knowledge bases, including drafts; enforce document VIEWER access before returning content or applying result limit.
- No vector index or external model. Search is keyword-based; existing writes remain scoped to the configured KB.
- Search default limit 20, maximum 100; reject invalid limits and blank queries.
- Read pages have at most 12000 Unicode code points, using code-point offsets; recheck permission on every read.
- Deleted, missing and inaccessible documents produce the same unavailable error. Do not log document text, credentials or queries.

---

### Task 1: Permission-aware global search and Chinese fallback

**Files:**
- Modify: `hellodoc-server/src/main/java/com/nopkg/hellodoc/services/SearchService.java`
- Modify: `hellodoc-server/src/main/java/com/nopkg/hellodoc/web/dto/search/SearchResultVO.java`
- Test: `hellodoc-server/src/test/java/com/nopkg/hellodoc/services/SearchServiceTest.java`
- Test: `hellodoc-server/src/test/java/com/nopkg/hellodoc/mcp/McpHttpPostgresTest.java`

**Interfaces:** `SearchService.searchAll(Long userId, String query, int limit)` returns accessible results with document ID, title, KB ID/title, status, score and snippet. MCP builds path `/kb/{kbId}/{docId}` from each result.

- [ ] Add tests with accessible and inaccessible documents across two KBs, including a draft and a Chinese substring appearing inside a longer phrase. Assert accessible results fill the requested limit even when higher-ranked inaccessible documents exist; deleted documents never appear. Run `cd hellodoc-server && ./gradlew test --tests '*SearchServiceTest' --tests '*McpHttpPostgresTest'` and verify the new assertions fail.
- [ ] Replace PostgreSQL global search's fixed `safeLimit * 8` prefetch with a candidate strategy that applies document permission checks before final limiting. Merge ranked full-text matches with substring matches over title/content, deduplicating by document ID and keeping full-text results first. Preserve the non-PostgreSQL fallback. Populate status in `SearchResultVO` from the document. Use parameterized SQL and do not log query or content.
- [ ] Run the same Gradle command; confirm all tests pass, including PostgreSQL integration coverage where the project test environment supplies PostgreSQL. Commit the search changes and tests.

### Task 2: Read service and MCP tools

**Files:**
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/mcp/McpReadService.java`
- Modify: `hellodoc-server/src/main/java/com/nopkg/hellodoc/mcp/McpConfiguration.java`
- Create: `hellodoc-server/src/test/java/com/nopkg/hellodoc/mcp/McpReadServiceTest.java`
- Modify: `hellodoc-server/src/test/java/com/nopkg/hellodoc/mcp/McpEndpointTest.java`

**Interfaces:** `McpReadService.search(long userId, String query, int limit)` returns search results with paths. `McpReadService.read(long userId, long documentId, int offset)` returns document and KB metadata, `content`, `hasMore`, and `nextOffset` (null at end). Both tools execute through `McpIdentity.run` using HTTP authentication.

- [ ] Add service tests for blank query, invalid limit, invalid ID and negative offset; missing/deleted/unauthorized documents must share the same unavailable result. Verify permission is checked on every call, including after access is revoked. Verify empty content and offsets past the end. For a body containing more than 12000 code points with an emoji at the page boundary, assert successive pages concatenate exactly to the original and do not split the emoji. Run `cd hellodoc-server && ./gradlew test --tests '*McpReadServiceTest'` and verify failure.
- [ ] Implement the service using `SearchService.searchAll`, `PermissionChecker.checkDocRole(userId, documentId, DocRole.VIEWER)`, `DocumentService.getById`, and `DocumentService.getContent`. Validate before calling dependencies. For paging compute `count = text.codePointCount(0, text.length())`, `start = text.offsetByCodePoints(0, Math.min(offset, count))`, `endOffset = Math.min(count, offset + 12000)` using overflow-safe arithmetic, and `end = text.offsetByCodePoints(0, endOffset)`. Return `text.substring(start, end)` and next offset only when `endOffset < count`. Translate missing, deleted and denied access into the same unavailable error.
- [ ] Register `search_documents` with required string `query` and optional integer `limit`; register `read_document` with required positive integer `documentId` and optional nonnegative integer `offset`. Reject wrong types and invalid values at runtime, not only through JSON schema. Describe keyword retries, paging and citations in tool descriptions. Serialize outputs using the existing `ObjectMapper`, use `isError(true)` for invalid inputs and inaccessible documents, and avoid exception details in responses.
- [ ] Extend endpoint tests to assert tool discovery, authenticated identity, cross-KB results, draft reads, permission denial after search, invalid arguments, and page continuation. Run `cd hellodoc-server && ./gradlew test --tests '*McpReadServiceTest' --tests '*McpEndpointTest' --tests '*McpHttpPostgresTest'`; confirm passes. Commit service, endpoint and tests.

### Task 3: Bridge exposure and documentation

**Files:**
- Modify: `hellodoc-mcp-bridge/index.mjs`
- Modify: `hellodoc-mcp-bridge/scope.mjs`
- Modify: `hellodoc-mcp-bridge/test/scope.test.mjs`
- Modify: `docs/mcp-guide.md`

**Interfaces:** Existing scoped tool names remain `list_items`, `create_item`, `create_document`, `get_operation`; new global names are `search_documents`, `read_document`. Global tool arguments pass unchanged; write tool arguments always receive the configured `knowledgeBaseId`.

- [ ] Add Node tests asserting both new tools are exposed, their schema and arguments do not gain a KB ID, existing tools still hide the KB ID in their schemas, and a supplied conflicting ID cannot override the configured ID. Test that search and read result paths become absolute URLs based on configured service base URL. Run `cd hellodoc-mcp-bridge && npm test` and verify failures.
- [ ] Extend the allowlist. In the request handler branch by tool name: call `scopeArguments` only for the four existing tools and pass global-tool arguments unchanged. Preserve existing create-document URL behavior; transform relative paths in both new tool results into absolute URLs without exposing credentials. Keep MCP error results intact.
- [ ] Update `docs/mcp-guide.md` to describe cross-KB read access, drafts, keyword limitations, pagination and unchanged write scope. Run `cd hellodoc-mcp-bridge && npm test` and `cd hellodoc-server && ./gradlew test`; confirm results and record any unavailable integration prerequisites. Commit bridge, tests and documentation.
