# MCP 同目录排序实施计划

**目标：** 让 AI 对知识库根目录或任意文件夹的直接子文档、子文件夹进行混合排序，并与网页显示和并发操作保持一致。

**架构：** 以知识库行为并发序列化点，维护知识库级目录修订号；所有会改变目录顺序、成员或可见性的写操作先获取同一锁并递增修订号。MCP 先分页读取一致快照，再以完整 ID 列表和修订号提交事务性排序；网页排序使用同一后端批量操作。

**技术栈：** Java、Spring Boot、Spring Data JPA、PostgreSQL/H2、Flyway、Vue/TypeScript、MCP Java SDK、Node MCP bridge。

**设计依据：** 2026-11-04 对话中确认的同目录完整排序方案；不支持跨目录移动，不修改正文或发布状态。

## 契约与约束

- 根目录统一使用 `parentId: 0`；实际数据库根节点为 null。嵌套目录使用正整数 ID。
- `list_folder_children` 的第一页返回不透明字符串 `snapshot`；续页增加 `expectedSnapshot`，不匹配返回 `ORDER_CONFLICT`，不得继续拼接页面。原有调用仍可省略此参数，但排序工具说明要求使用该参数。
- 新工具 `reorder_folder_children` 必填 `knowledgeBaseId`、`parentId`、`expectedSnapshot`、`orderedIds`。`orderedIds` 可为空（空目录），每个 ID 为正整数；服务端限制最大条目数并在工具描述中明确上限。首次实现上限为 10,000，超限返回 `DIRECTORY_TOO_LARGE`，不执行任何修改。
- 严格要求目录中所有未删除直接子项均可通过现有 MCP 读取且具备编辑权限；目录或子项不可用、加密或权限不足时统一返回 `DIRECTORY_UNAVAILABLE`，不暴露隐藏条目数量或名称。根目录还需知识库编辑权限；文件夹需文件夹编辑权限及全部直接子项编辑权限。
- `orderedIds` 必须是当前全部直接子项的无重复排列，包含文件和文件夹。重复、遗漏、额外、跨库或非直接子项返回 `INVALID_ORDER`，不修改数据。
- 目录修订号发生变化时返回 `ORDER_CONFLICT`。唯一例外：在持锁、重新验证权限和完整集合后，当前顺序已等于目标顺序则返回成功 `changed: false`，支持超时重试；其他情况不得自动覆盖。
- 排序结果使用唯一递增 orderNum；10,000 项以内采用从 10,000 开始、间隔 10,000 的整数，避免整型溢出。空目录成功且不修改修订号。
- 同级排序的权威比较规则为 `orderNum ASC NULLS LAST, id ASC`；前端、MCP 和批量排序采用同一规则。仅排序不更新文档正文版本。
- 修订号是知识库级保守失效：同一知识库任意目录或相关权限变化均可导致冲突，跨知识库互不影响；不对修订号连续性作业务承诺。
- 对所有目录写入路径与权限变更统一锁顺序：先按知识库 ID 升序锁知识库行，再按文档 ID 升序锁文档行；跨库复制需先锁两个知识库。避免先锁文档再锁知识库。锁必须在校验和写入所属的同一数据库事务内持有。

## 任务 1：快照与统一排序基础

**修改：** `hellodoc-server/src/main/java/com/nopkg/hellodoc/entities/KbKnowledgeBase.java`、知识库 repository、`KbDocumentRepository.java`、`mcp/McpReadService.java`；新增两个数据库厂商的 Flyway 迁移；修改 `hellodoc-client/src/utils/documentTree.ts`。

- [ ] 为 `kb_knowledge_base` 增加非空 `tree_revision BIGINT DEFAULT 0`；分别在 `db/migration/h2` 和 `db/migration/postgresql` 使用各自下一可用版本，检查现有迁移序号并保持两端一致。为 repository 添加按 ID 悲观写锁方法。
- [ ] 创建集中式目录锁和修订服务，明确 `lock(kbId)`、`currentRevision(kbId)`、`bump(kbId)`；将快照编码为不透明版本字符串。读取第一页在一致事务中读取修订号和页面，续页在读取前后确保版本未改变，避免读到混合页。不要把仅靠应用内同步当作数据库并发保护。
- [ ] MCP 原有可见性过滤保持不变，新增快照字段和可选 `expectedSnapshot`；非根目录也遵循相同分页契约。统一 null 排序和 ID 平局处理；前端 `sortDocuments` 同步相同规则。
- [ ] 在 `McpReadServiceTest` 和前端目录排序测试中覆盖同 orderNum、null、混合类型、超过 50 项、翻页间新增/重排导致冲突和根/嵌套目录；确保旧调用仍可读取。

## 任务 2：封闭全部目录写入路径

**修改：** `DocumentServiceImpl.java`、`mcp/McpMoveService.java`、`mcp/AiItemService.java`、相关权限服务，以及实际写入目录的其他服务；检查仓库内所有 `setParent`、`setOrderNum`、删除/恢复和批量 SQL。

- [ ] 为创建、复制、删除、恢复、移动、重排及直接更新 parent/order 的所有入口先获取知识库锁；变更后在同一事务递增修订。权限、加密状态及影响可见性的变更同样锁定并递增。无实际变化不递增。
- [ ] 审计跨库复制、子树删除/恢复、根目录和事项创建；跨库操作按知识库 ID 升序获取锁。调整现有先锁文档的路径，保证所有路径遵守全局锁顺序。
- [ ] 保留现有接口兼容性；不要直接复用当前缺少权限和父目录校验的 `DocumentServiceImpl.reorder`。删除该不安全入口或将其改为调用新的安全服务，并确认没有遗留调用者。
- [ ] 用 PostgreSQL 并发测试让两个事务分别执行排序与创建、移动、删除及权限撤销；验证最终状态可串行化、无死锁、修订更新随事务回滚。测试跨目录及跨知识库操作的锁顺序。

## 任务 3：事务性排序服务与 MCP 工具

**新增：** `hellodoc-server/src/main/java/com/nopkg/hellodoc/mcp/McpReorderService.java` 和对应异常、测试；**修改：** `McpConfiguration.java`、`McpReadService.java`。

- [ ] 实现 `reorder(userId, knowledgeBaseId, parentId, expectedSnapshot, orderedIds)`：持锁后验证知识库、目录、完整可见性与编辑权限；计算当前权威顺序；按契约处理已达目标、修订冲突和无效排列；最后一次性更新全部排序号并递增修订。
- [ ] 返回 `{knowledgeBaseId, parentId, changed, snapshot, orderedIds}`；失败统一使用 `INVALID_ARGUMENT`、`DIRECTORY_UNAVAILABLE`、`DIRECTORY_TOO_LARGE`、`INVALID_ORDER`、`ORDER_CONFLICT`、`REORDER_FAILED`，不返回隐藏节点信息。
- [ ] 在 MCP schema 禁止额外参数，验证整数边界、字符串长度和数组上限。描述要求先读取所有页面、续页带快照、提交完整混合列表；遇冲突重新读取且不覆盖并发修改。
- [ ] 测试根目录、深层目录、空目录、文件/文件夹交错、超过 50 项、重复/缺失/额外/跨库 ID、加密或权限不足、删除和权限并发、相同请求重试、异常回滚及正文版本不变。PostgreSQL 中验证两个并发排序仅一个生效或另一个明确冲突。

## 任务 4：网页排序与桥接

**修改：** `DocumentController.java`、`DocumentService`、`hellodoc-client/src/composables/editor/useDocumentTreeActions.ts`、相关客户端 API、`hellodoc-mcp-bridge/scope.mjs`、`index.mjs` 和 bridge 测试。

- [ ] 新增需认证的同目录批量排序 HTTP 入口，调用相同服务和事务规则。网页拖拽、上移、下移使用单次批量请求，避免当前逐项更新造成中间状态；冲突时刷新目录并提示用户，不能盲目重放旧排列。保留跨目录移动流程并接入任务 2 的锁和修订。
- [ ] Bridge 将 `reorder_folder_children` 加入暴露工具；与 `list_folder_children` 一样允许调用者提供知识库 ID，否则注入配置的默认知识库 ID。转发原样保留 snapshot、列表和错误，不将排序工具当作固定知识库的强制覆盖。
- [ ] 测试网页批量请求、冲突刷新、相同 orderNum 的呈现规则；bridge 覆盖默认/显式知识库、根和嵌套目录、错误透传与工具发现。

## 任务 5：文档与验收

**修改：** `docs/mcp-guide.md`、MCP endpoint 测试和 PostgreSQL HTTP 集成测试；按项目惯例更新用户文档。

- [ ] 用真实工具调用展示读取第一页、续页使用快照、提交完整 ID 列表、处理 `ORDER_CONFLICT` 并重新读取；说明排序权限、条目数上限、根目录、文件夹混排和安全重试。
- [ ] 在服务端执行 `cd hellodoc-server && ./gradlew test`；PostgreSQL 专项按现有测试的环境要求启动数据库后运行 `./gradlew test --tests '*McpHttpPostgresTest' --tests '*DocumentWriteConcurrencyPostgresTest'`。执行 `cd hellodoc-mcp-bridge && npm test`；客户端执行其 package.json 定义的测试和类型检查。
- [ ] 在隔离知识库建立根目录与两层嵌套目录，包含文件和文件夹；用 MCP 重排、刷新网页核对顺序，再在网页调整后重新用 MCP 读取。测试 51 项分页、权限不足和并发冲突；不得在真实用户知识库中创建或删除测试数据。
- [ ] 审查兼容性、查询性能、锁等待与 10,000 项上限；确认所有失败均为原子回滚。记录实际执行命令、测试结果及未验证项。
