# MCP Document Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely update documents across editable knowledge bases, improve keyword search, and make Agent Markdown saves verifiable.

**Architecture:** Coordinate all existing-body writes on the document row and persist idempotent MCP update operations in the same transaction as the revision. Add stable version-aware reads and MCP transport, optimize PostgreSQL search while preserving existing permission semantics, and update Agent instructions and bridge behavior.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, PostgreSQL, Flyway, MCP Java SDK, Node.js MCP bridge, JUnit, Node test runner.

**Spec:** `docs/superpowers/specs/2026-11-03-mcp-document-reliability-design.md`

## Global Constraints

- Update all editable knowledge bases; existing creation tools remain restricted to the configured KB.
- Existing web and third-party body writes require expectedVersion; missing values must be rejected with VERSION_REQUIRED and upgrade guidance; metadata-only updates remain compatible. Desktop and mobile editors must send versions and preserve unsaved drafts on conflict.
- A document row lock must be held before reading its prior body/version and through commit in every existing-body write path; revisions, body, version and operation record commit or roll back together.
- `replace` accepts empty content; `append` concatenates exact input and rejects empty content. Both enforce 2 MiB UTF-8 input and final content limit.
- Only unencrypted, undeleted FILE documents are writable. Nonexistent and inaccessible documents have indistinguishable responses.
- Search remains keyword-based, excludes deleted/encrypted/unauthorized documents, includes drafts, and filters permissions before truncating results.
- No PDF parsing, external embeddings or vector database; preserving user work and existing MCP write scope is mandatory.

## File map

- `KbDocumentRepository.java`: pessimistic document lock lookup shared by all body writers.
- `DocumentServiceImpl.java`, `RevisionServiceImpl.java`: acquire the shared lock before update, revision creation and restore; keep current version and body synchronized.
- `McpReadService.java`: consistent versioned body reads and pagination; `McpWriteService.java` / new update service: validated cross-KB updates.
- `McpConfiguration.java`: schemas, error codes, agent instructions and response mapping.
- `db/migration/V2__mcp_update_operations.sql`, operation entity/repository: transactional idempotency with unique user/document/request ID and no plaintext body.
- `SearchService.java` and targeted repository SQL: indexed candidates, batched body/permission lookup, stable ranking and correct fallbacks.
- `hellodoc-mcp-bridge/index.mjs`, `scope.mjs`: expose global update and preserve existing write scoping; attach source URLs.
- MCP guide and targeted Java/Node tests: contracts, concurrency, permissions and integration.

## Workstream A: Versioned reads and transactional updates

**Compatibility decision:** The user explicitly chose to reject legacy body writes that omit a version. Update desktop and mobile editors and revision-restore requests before enforcing the new API contract in a release; keep metadata-only requests compatible. Test stale drafts and conflict handling.

### Task 1: Establish a shared row-lock boundary

**Files:** `hellodoc-server/src/main/java/com/nopkg/hellodoc/repositories/KbDocumentRepository.java`, `hellodoc-server/src/main/java/com/nopkg/hellodoc/services/impl/DocumentServiceImpl.java`, `hellodoc-server/src/main/java/com/nopkg/hellodoc/services/impl/RevisionServiceImpl.java`, and PostgreSQL integration tests under `hellodoc-server/src/test/java/com/nopkg/hellodoc/mcp/`.

**Interfaces:** Repository `Optional<KbDocument> findByIdForUpdate(Long id)` uses JPA `PESSIMISTIC_WRITE`. The lock is held by a surrounding transaction until commit. Existing document updates, manual revisions and restore obtain it before reading old version/content. Audit `rg -n 'updateContent\\(|contentRepository\\.(save|updateContent)|setCurrentVersion\\(' hellodoc-server/src/main/java` and enumerate each call site in the test class comments.

- [ ] Write PostgreSQL integration tests using two independent transactions and latches. Hold transaction A's lock, start B's ordinary edit or restore, assert B cannot complete before A commits, then verify the final body and revision sequence. Cover manual revision, restore and collaboration's actual persistence path; identify any direct repository write not covered by those tests.
- [ ] Run `./gradlew test --tests '*DocumentWriteConcurrencyPostgresTest'` with `HELLODOC_TEST_PG_INTEGRATION_URL` pointing to an isolated schema initialized with `schema.sql`; confirm the tests expose the existing race.
- [ ] Add the locked repository query and acquire the lock at each audited existing-document write entry point before reading the document or allocating the next revision. Do not use a self-invoked transactional helper as the only transaction boundary. Ensure newly copied documents receive the version of their initial revision.
- [ ] Rerun the concurrency tests and the existing document/revision tests; confirm they pass. Commit this self-contained change.

### Task 2: Version-aware reads

**Files:** `hellodoc-server/src/main/java/com/nopkg/hellodoc/mcp/McpReadService.java`, `McpConfiguration.java`, `hellodoc-server/src/test/java/com/nopkg/hellodoc/mcp/McpReadServiceTest.java`, `McpEndpointTest.java`.

**Interfaces:** `read(userId, documentId, offset, expectedVersion)` returns `version` and ISO `updatedAt`; file offset > 0 requires `expectedVersion`; folder offset retains current behavior. `VERSION_CONFLICT` means no content returned.

- [ ] Add tests for initial file read, continued read at matching version, missing continuation version, mutation between pages, Unicode boundary and unchanged folder pagination. At the HTTP MCP layer assert conflict is an `isError` result with stable code and no body text.
- [ ] Add optional nonnegative integer `expectedVersion` to the read schema, and runtime validation. Acquire the document lock inside the read transaction, then check permission, version, and read/slice content before releasing it. Interpret null `currentVersion` as 0 and use `doc.getUpdatedAt()` as `updatedAt`.
- [ ] Run `./gradlew test --tests '*McpReadServiceTest' --tests '*McpEndpointTest'` and the PostgreSQL concurrency tests; commit the passing change.

### Task 3: Idempotent cross-KB update

**Files:** `hellodoc-server/src/main/resources/db/migration/V2__mcp_update_operations.sql`, `hellodoc-server/src/main/java/com/nopkg/hellodoc/mcp/McpUpdateService.java`, operation repository/entity, `McpConfiguration.java`, `McpHttpPostgresTest.java`, `McpUpdateServiceTest.java`.

**Interfaces:** `update(userId, documentId, expectedVersion, mode, markdown, requestId)` returns document ID, KB ID, version, updatedAt and path. Stable errors are `VERSION_CONFLICT`, `REQUEST_ID_CONFLICT`, `INVALID_ARGUMENT`, `DOCUMENT_UNAVAILABLE`, `TOO_LARGE`. A table with unique `(user_id, doc_id, request_id)` stores the SHA-256 input fingerprint and resulting version; the document foreign key cascades on permanent deletion.

- [ ] Write tests asserting `replace` including empty text; exact `append`; 2 MiB UTF-8 boundary; unsupported type, encrypted, deleted, inaccessible and viewer-only rejection; editor access to a second KB; request ID mismatch; same-ID retry and concurrent retry; stale version conflict; rollback on injected failure. PostgreSQL tests must run independent transactions and verify body, revision, version and operation record together.
- [ ] Add an idempotent Flyway migration and compatible fresh-database initialization; check Flyway baseline behavior and test H2 compatibility before selecting SQL types. Hash length-delimited mode, expected version and UTF-8 body with SHA-256. Validate user/document/request arguments before accessing data.
- [ ] Implement update transaction: lock document → check EDITOR permission and eligible type → find prior operation → return its result if fingerprint matches or `REQUEST_ID_CONFLICT` otherwise → compare current version → compute final body and size → create revision and write body/version → save operation record → flush; failures roll back all changes. Existing operation lookup must happen before stale-version rejection so retry succeeds.
- [ ] Register MCP schema and map typed errors to non-sensitive results. Add URL in bridge for `update_document`; do not inject configured KB ID. Run targeted tests plus `./gradlew test` and `npm test`; commit only after passing.

## Workstream B: Keyword search

### Task 4: Baseline and indexed candidates

**Files:** `hellodoc-server/src/main/java/com/nopkg/hellodoc/services/SearchService.java`, search repositories, database migration where supported, `SearchServiceTest.java`, a PostgreSQL integration benchmark fixture.

**Interfaces:** `searchAll(userId, query, limit)` preserves existing result fields and adds no new external dependencies. Rank title hits above content; sort score descending then document ID ascending. Fetch candidates in stable batches until enough authorized results are found or candidates are exhausted.

- [ ] Create an isolated PostgreSQL fixture with multiple KBs, documents, role overrides, public access, drafts, deleted/encrypted documents and a run of unauthorized high-ranking matches. Record dataset size, queries, output IDs, SQL query counts and warm-run timings. Assert Chinese substring and English keyword matches, deterministic ties, and permission-before-limit.
- [ ] Implement batched candidate and body retrieval. Use parameterized SQL and existing permission semantics; if permission batching cannot reproduce `PermissionChecker` exactly, retain its checks for candidates and document the measured cost rather than weakening access. Merge full-text and substring candidates with one deterministic ranking. Never truncate before checking permissions.
- [ ] If `pg_trgm` can be created, add an idempotent optional index migration and test execution plan; if extension creation is unavailable, preserve service startup and use the correct existing fallback. Keep H2/non-PostgreSQL correct. Add tests for both paths and index absence.
- [ ] Rerun the same fixture and report pre/post timings, query counts and result-ID differences; investigate any unauthorized result or lost expected match. Run all search and permission tests; commit the change.

## Workstream C: Agent save experience

### Task 5: Bridge, create result and documentation

**Files:** `hellodoc-server/src/main/java/com/nopkg/hellodoc/mcp/McpConfiguration.java`, `McpWriteService.java`, `hellodoc-mcp-bridge/index.mjs`, `scope.mjs`, bridge tests, `docs/mcp-guide.md`.

**Interfaces:** Creation returns existing fields plus `version` and `updatedAt`; bridge keeps existing KB scoping and converts update paths to URLs. Agent guidance requires readable Markdown and explicit extraction gaps, with file parsing performed outside MCP.

- [ ] Add MCP endpoint tests for create response compatibility and version; Node tests assert `update_document` is exposed, receives no configured KB ID, generates URLs, and old tools cannot override configured KB. Test Markdown instructions contain the expectation to avoid enclosing the whole document in a code fence.
- [ ] Populate create result version/updatedAt from the committed document; preserve original request ID semantics. Extend tool descriptions and the guide with examples, data-source limitations, file conversion caveats, read-after-write verification and conflict/retry behavior. Do not claim automatic completeness detection.
- [ ] Run `cd hellodoc-server && ./gradlew test`, `cd hellodoc-mcp-bridge && npm test`, plus PostgreSQL HTTP/stdio and concurrency tests. Verify clean diff and report benchmark environment and missing prerequisites; commit the passing work.

## Measured search baseline

On 2026-11-03, PostgreSQL 16.15 in an isolated local Docker container, each run used a separate freshly initialized database and the same `SearchBenchmarkPostgresTest` fixture: one accessible KB, 300 empty-body nonmatching documents and one title match, one warm-up and one measured query. Hibernate statistics recorded 907 prepared statements and 201 ms before the search change, versus 6 statements and 6 ms after. The after fixture had no prebuilt `pg_trgm` index or populated plaintext cache, so this comparison primarily measures candidate-first permission filtering. This single title-match microbenchmark does not predict production latency, large bodies, concurrent load, backfill cost, or index effectiveness. Integration tests separately verify Chinese body matches, stale index fallback, access checks and stable ranking.

For the same 301-row fixture, after creating the optional title trigram index and running `ANALYZE`, PostgreSQL's simple title-substring `EXPLAIN` still selected `Seq Scan` (estimated cost `0.00..10.52`); the table is too small to conclude whether the index helps at production scale. The measured 6 ms / 6 statements therefore must not be presented as proof of index usage.
