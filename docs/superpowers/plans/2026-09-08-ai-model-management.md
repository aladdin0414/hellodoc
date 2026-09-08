# AI 模型后台独立管理与多模型切换实施计划 (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在系统后台构建独立的 AI 大模型配置管理模块并单独建表，支持维护多个 OpenAI 兼容大模型（含 URL、API Key、Model Name、温度、Agent 定位提示词、激活/默认控制），并在前台编辑器浮窗与独立 AI 助理抽屉中支持自由选择已激活的大模型进行流式交互。

**Architecture:** 
1. **持久层**：PostgreSQL 新建 `sys_ai_model` 表，JPA 映射 `SysAiModel`，索引覆盖激活状态与默认标志；
2. **服务层**：`AiModelService` 负责模型 CRUD、原子级默认互斥切换、连通性探测；`AiService` 根据前台入参 `modelId` 实现动态解析与参数注入（fallback 至系统默认模型）；
3. **接口层**：`AdminAiModelController`（管理员专属）与 `AiController`（公开激活模型列表与补全接口）；
4. **前端**：后台新增「AI 模型管理」看板组件；前台抽象 `useAiModels` 状态组合式函数并在编辑器浮窗及新增的全局 AI 助理抽屉中实现模型切换。

**Tech Stack:** 
- 后端：Spring Boot 3, Spring Data JPA, PostgreSQL, Jackson, RestTemplate / HttpURLConnection
- 前端：Vue 3, TypeScript, Tailwind CSS, Vite, Lucide 图标库

**Spec:** [`docs/superpowers/specs/2026-09-08-ai-model-management-design.md`](file:///Users/liyc/code/github-me/hellodoc/docs/superpowers/specs/2026-09-08-ai-model-management-design.md)

## Global Constraints
- 仅已激活（`is_enabled = true`）的模型才对前台普通用户可见可选；
- 默认模型（`is_default = true`）在系统内同一时刻唯一，且默认模型必须保持激活状态；
- 普通用户调用后端 AI 接口，所有敏感数据（API Key、请求 Base URL）完全由后端封装，绝不暴露至前端；
- 注释与文档使用中文，Git 提交信息遵循 `<type>: <中文描述>` 规范。

---

### Task 1: 数据库结构与 JPA 实体定义

**Files:**
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/entities/SysAiModel.java`
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/repositories/AiModelRepository.java`
- Modify: `hellodoc-server/src/main/resources/schema.sql`

**Interfaces:**
- Produces: `SysAiModel` 实体类及 `AiModelRepository` 数据访问接口。

- [ ] **Step 1: 在 schema.sql 中补充建表脚本**
  包含 `id`, `name`, `provider`, `base_url`, `api_key`, `model_name`, `temperature`, `agent_prompt`, `is_default`, `is_enabled`, `disable_thinking`, `created_at`, `updated_at` 字段及状态索引。

- [ ] **Step 2: 创建 JPA 实体 SysAiModel.java**
  使用 Lombok `@Data` / `@Entity` 注解，精确映射字段名与默认值。

- [ ] **Step 3: 创建 AiModelRepository.java**
  定义 `findByIsDefaultTrue()`、`findByIsEnabledTrueOrderByCreatedAtDesc()` 及 `findAllByOrderByCreatedAtDesc()`。

- [ ] **Step 4: 编译并校验 JPA 映射**
  运行 Gradle build 确保无编译与符号错误。

---

### Task 2: 后端 DTO 与 AiModelService 业务服务开发

**Files:**
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/web/dto/ai/AiModelCreateReq.java`
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/web/dto/ai/AiModelUpdateReq.java`
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/web/dto/ai/AiModelResp.java`
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/web/dto/ai/AiModelTestReq.java`
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/services/AiModelService.java`

**Interfaces:**
- Consumes: `AiModelRepository`
- Produces:
  - `List<AiModelResp> getAllModels()`
  - `List<AiModelResp> getActiveModels()`
  - `AiModelResp createModel(AiModelCreateReq req)`
  - `AiModelResp updateModel(String id, AiModelUpdateReq req)`
  - `void deleteModel(String id)`
  - `void setDefault(String id)`
  - `void toggleActive(String id)`
  - `boolean testConnection(AiModelTestReq req)`

- [ ] **Step 1: 编写 DTO 数据传输类**
  包含入参校验与安全出参（`AiModelResp` 在前台模式下脱敏 `apiKey`）。

- [ ] **Step 2: 编写 AiModelService 核心业务逻辑**
  实现原子互斥设为默认、停用默认模型保护拦截、连通性探测发送轻量 OpenAI Ping 逻辑。

- [ ] **Step 3: 编写数据初始化/迁移检查**
  在系统启动时，若 `sys_ai_model` 为空且旧 `sys_config` 存在模型配置，自动平滑迁移一条默认模型记录。

---

### Task 3: 控制器层与安全接口发布

**Files:**
- Create: `hellodoc-server/src/main/java/com/nopkg/hellodoc/controllers/AdminAiModelController.java`
- Modify: `hellodoc-server/src/main/java/com/nopkg/hellodoc/controllers/AiController.java`

**Interfaces:**
- Produces:
  - 管理端 REST 端点：`/api/admin/ai/models/**`（需要 ADMIN 角色）
  - 前台 REST 端点：`GET /api/ai/models/active`（所有已登录用户）

- [ ] **Step 1: 创建 AdminAiModelController.java**
  注入 `AiModelService`，声明列表查询、增删改、设为默认、切换激活和连通性测试端点。

- [ ] **Step 2: 扩展 AiController.java**
  增加 `getActiveModels()` 端点，返回当前所有激活状态的模型。

- [ ] **Step 3: 运行 Gradle 编译与接口检查**
  确保路由注解与 Swagger 文档注解正确加载。

---

### Task 4: 改造 AiService 动态模型路由与调用中转

**Files:**
- Modify: `hellodoc-server/src/main/java/com/nopkg/hellodoc/web/dto/ai/AiCompletionReq.java`
- Modify: `hellodoc-server/src/main/java/com/nopkg/hellodoc/services/AiService.java`

**Interfaces:**
- Consumes: `AiModelRepository`, `AiModelService`
- Produces:
  - `String getCompletion(String modelId, String context, String prompt, String lang)`
  - `void streamCompletion(String modelId, String context, String prompt, String lang, Consumer<String> onChunk)`

- [ ] **Step 1: 在 AiCompletionReq 中增加 modelId 字段**
- [ ] **Step 2: 改造 AiService 模型动态解析链**
  根据入参 `modelId` 查找激活模型；若无匹配则 fallback 到 `findByIsDefaultTrue()`；若仍无则 fallback 到系统配置。
- [ ] **Step 3: 动态组装大模型请求参数**
  提取目标模型的 `baseUrl`, `apiKey`, `modelName`, `temperature`, `agentPrompt`, `disableThinking`，并发起 HTTP 流式与非流式调用。

---

### Task 5: 前端后台管理「AI 模型配置」模块开发

**Files:**
- Create: `hellodoc-client/src/api/aiModel.ts`
- Create: `hellodoc-client/src/components/admin/AdminAiModelManagement.vue`
- Modify: `hellodoc-client/src/components/AdminManagement.vue`
- Modify: `hellodoc-client/src/i18n/locales/zh-CN/common.ts`
- Modify: `hellodoc-client/src/i18n/locales/en-US/common.ts`

**Interfaces:**
- Produces: 后台管理页面新增第三个独立 Tab 「AI 模型配置」。

- [ ] **Step 1: 编写 aiModel.ts 请求接口封装**
  包含 CRUD、设为默认、切换激活、测试连接的前端调用方法。
- [ ] **Step 2: 构建 AdminAiModelManagement.vue 视图**
  设计现代感卡片网格看板，展示模型标识、状态徽章、操作栏；集成新建/编辑模态框与实时连通性测试组件。
- [ ] **Step 3: 在 AdminManagement.vue 中注册 AI 模型 Tab**
  添加 Tab 项与对应图标，实现无缝切换。
- [ ] **Step 4: 补全多语言字典**
  中文与英文国际化词条同步补充。

---

### Task 6: 前台编辑器浮窗与独立 AI 助理抽屉多模型集成

**Files:**
- Create: `hellodoc-client/src/composables/useAiModels.ts`
- Modify: `hellodoc-client/src/api/ai.ts`
- Modify: `hellodoc-client/src/components/editor/EditorAiContextMenu.vue`
- Create: `hellodoc-client/src/components/ai/AiAssistantDrawer.vue`
- Modify: `hellodoc-client/src/components/DocumentEditor.vue`

**Interfaces:**
- Consumes: `useAiModels` 提供的激活模型列表与持久化选中的 `currentModelId`。

- [ ] **Step 1: 封装 useAiModels.ts**
  自动拉取已激活模型，响应式管理当前选中的模型 ID，结合 `localStorage` 实现持久记忆，自动容错降级。
- [ ] **Step 2: 改造 EditorAiContextMenu.vue 快捷浮窗**
  在标题栏右侧加入模型下拉选择控件，发起流式调用时传入选中的 `modelId`。
- [ ] **Step 3: 编写独立 AI 助理抽屉 AiAssistantDrawer.vue**
  支持随时呼出/收起，顶部提供模型切换，支持与 AI 进行自由多轮对话、流式打字效果、以及将 AI 内容一键插入至文档。
- [ ] **Step 4: 在编辑器界面集成呼出入口**
  在 DocumentEditor 侧边或顶部提供智能助手快捷唤起按钮。

---

### Task 7: 端到端联调与系统验收

- [ ] **Step 1: 数据库与后台模型增删改查验收**
  添加多个模型（如 DeepSeek、OpenAI、通义千问等），切换默认模型测试互斥性，验证连通性测试。
- [ ] **Step 2: 激活与停用状态前台过滤验收**
  停用某个模型后，前台用户端无法在下拉列表中看到该模型；若原先选中了该模型，自动优雅回退至默认模型。
- [ ] **Step 3: AI 流式交互与定制 Agent 提示词验收**
  分别测试编辑器上下文浮窗与独立 AI 助理抽屉，验证不同模型在各自温度与 Agent 提示词下的流式输出效果。
