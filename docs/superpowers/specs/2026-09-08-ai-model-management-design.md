# AI 模型后台独立管理与多模型切换设计规范 (Design Spec)

- **创建日期**：2026-09-08
- **状态**：已评审通过 (Approved)
- **范围**：后台管理独立 AI 模型模块、数据库独立建表、OpenAI 兼容协议配置、激活/默认状态控制、编辑器浮窗与独立 AI 助理侧边栏多模型动态选择与调用

---

## 1. 背景与目标

### 1.1 背景
当前系统的 AI 配置散落在全局系统配置表（`sys_config`）中，仅支持单一的大模型参数配置，缺少多模型管理、动态切换以及模型专属 Agent 定位与参数微调能力。

### 1.2 建设目标
1. **后台管理独立模块**：在系统管理后台新增「AI 模型管理」独立 Tab 模块，与用户管理、系统设置并列。
2. **独立建表与多模型配置**：采用单独数据表（`sys_ai_model`）存储多个 OpenAI 兼容大模型配置，支持自由扩展。
3. **独立参数覆盖**：每个模型均包含独立配置：
   - 基础参数：模型别名、服务商标识、OpenAI 协议请求端点（`baseUrl`）、`apiKey`、模型名称（`modelName`）；
   - 生成调控：采样温度（`temperature`，0.0 ~ 2.0）；
   - Agent 定位：专属系统角色提示词（`agentPrompt`）；
   - 纯净推理：是否禁用深度思考标签（`disableThinking`）。
4. **默认与激活机制**：
   - **激活状态（`isEnabled`）**：只有处于激活状态的模型，前台普通用户才可见可选；
   - **默认模型（`isDefault`）**：全系统同一时刻有且仅有一个默认模型，当用户未主动选择时系统自动使用该模型；默认模型必须保持激活状态。
5. **前台双交互场景支持**：
   - 场景一：编辑器内选中文本的 AI 快捷浮窗（`EditorAiContextMenu.vue`）支持在激活模型间切换；
   - 场景二：右侧可唤起的独立 AI 对话助理抽屉（AI Assistant Drawer），支持多轮对话并记忆当前选择的模型。

---

## 2. 数据库设计 (PostgreSQL)

数据表名称：`sys_ai_model`

```sql
CREATE TABLE IF NOT EXISTS sys_ai_model (
    id VARCHAR(64) PRIMARY KEY,                    -- 模型唯一ID（UUID）
    name VARCHAR(100) NOT NULL,                    -- 显示名称（例如：DeepSeek 满血版、GPT-4o）
    provider VARCHAR(50) DEFAULT 'custom',          -- 服务商标识（openai, deepseek, qwen, custom等）
    base_url VARCHAR(500) NOT NULL,                 -- OpenAI协议请求端点
    api_key VARCHAR(500) NOT NULL,                  -- API Key（敏感信息）
    model_name VARCHAR(100) NOT NULL,               -- 实际模型名（如 deepseek-chat, gpt-4o）
    temperature NUMERIC(3, 2) DEFAULT 0.70,         -- 采样温度（0.00 ~ 2.00）
    agent_prompt TEXT,                              -- Agent定位/系统提示词
    is_default BOOLEAN DEFAULT FALSE,               -- 是否系统默认模型（全局唯一）
    is_enabled BOOLEAN DEFAULT TRUE,                -- 是否激活（前台可见）
    disable_thinking BOOLEAN DEFAULT TRUE,          -- 是否禁用思考标签
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_model_status ON sys_ai_model(is_enabled, is_default);
```

---

## 3. 后端架构设计

### 3.1 实体与数据访问层
- 实体：`com.nopkg.hellodoc.entities.SysAiModel`
- Repository：`com.nopkg.hellodoc.repositories.AiModelRepository`
  - `Optional<SysAiModel> findByIsDefaultTrue();`
  - `List<SysAiModel> findByIsEnabledTrueOrderByCreatedAtDesc();`
  - `List<SysAiModel> findAllByOrderByCreatedAtDesc();`

### 3.2 业务逻辑层（`AiModelService` & `AiService`）
- **模型维护能力**：
  - 增删改查：创建模型时若勾选默认，则自动将其他模型设为非默认；修改为默认时同理；
  - 切换激活：若将默认模型设为停用，系统抛出业务异常阻止，提示“默认模型不可停用，请先指定其他模型为默认”；
  - 设为默认：通过 `@Transactional` 保证原子性，批量重置其他记录的 `isDefault = false`，将目标记录置为 `isDefault = true` 且 `isEnabled = true`；
  - 连通性测试：向目标模型的 `baseUrl + /chat/completions` 发送简单 ping 消息，返回响应时延与成功状态。
- **调用中转解析链（`AiService`）**：
  - `getCompletion` 与 `streamCompletion` 接收 `String modelId`；
  - 解析优先级：
    1. 用户指定的有效激活模型（`modelId`）；
    2. 系统默认模型（`isDefault = true`）；
    3. 兜底全局系统配置或内置默认值；
  - 动态应用该模型专属的 `baseUrl`、`apiKey`、`modelName`、`temperature`、`agentPrompt`（若为空使用标准内置写作助手 Prompt）。

### 3.3 控制层与接口规范
1. **管理端接口**（`@PreAuthorize` / `requiresAdmin`）：
   - `GET /api/admin/ai/models`：获取全部模型
   - `POST /api/admin/ai/models`：新建模型
   - `PUT /api/admin/ai/models/{id}`：修改模型
   - `DELETE /api/admin/ai/models/{id}`：删除模型
   - `PUT /api/admin/ai/models/{id}/default`：设为默认模型
   - `PUT /api/admin/ai/models/{id}/toggle-active`：切换激活状态
   - `POST /api/admin/ai/models/test`：连通性测试
2. **前台用户端接口**（普通登录用户）：
   - `GET /api/ai/models/active`：获取所有激活模型，返回字段：`id`, `name`, `provider`, `modelName`, `temperature`, `isDefault`（严格屏蔽 `apiKey` 和敏感 `baseUrl`）。
   - `POST /api/ai/completion` & `POST /api/ai/completion/stream`：请求体增加 `modelId` 可选字段。

---

## 4. 前端交互设计

### 4.1 管理后台（`AdminAiModelManagement.vue`）
- 位于 `AdminManagement.vue` 侧边栏新增 Tab；
- 头部提供“+ 新增模型”按钮及统计信息（共 X 个，已激活 Y 个）；
- 核心卡片网格列表：
  - 卡片顶部：模型名称、Provider 徽标、默认模型金标、激活/停用 Switch；
  - 卡片内容：模型标识（`modelName`）、端点域名缩写、温度值；
  - 卡片底部：一键设为默认按钮、编辑按钮、删除按钮；
- 新建/编辑模态框（`BaseDialog`）：
  - 表单项：名称、Base URL、API Key（明密文切换）、Model Name、Temperature 滑块、Agent 定位提示词（多行文本）、是否默认、是否激活；
  - 底部左侧设【测试连接】按钮，点击实时显示连通结果。

### 4.2 前台用户交互
1. **模型状态 Store/Composable（`useAiModel`）**：
   - 负责缓存与获取激活模型列表；
   - 维护当前选中的 `selectedModelId`，读写 `localStorage`；
   - 自动兜底降级至系统标记为 `isDefault` 的模型。
2. **编辑器 AI 快捷浮窗（`EditorAiContextMenu.vue`）**：
   - 浮窗上方集成微型模型切换器，展示所有激活模型，并在发起请求时传参 `modelId`。
3. **独立 AI 对话助理抽屉（`AiAssistantDrawer.vue`）**：
   - 右侧悬浮球或顶部工具栏唤起；
   - 抽屉顶部提供模型下拉选择框；
   - 支持多轮会话、Markdown 排版流式渲染与一键插入当前光标文档。

---

## 5. 质量与验证计划
1. **后端验证**：
   - 单元与集成测试：验证模型 CRUD、唯一默认互斥约束、未激活无法调用的降级逻辑；
   - 接口权限验证：非 admin 用户无法访问管理端接口。
2. **前端与集成验证**：
   - 新建模型并测试连通性；
   - 切换激活状态与设为默认，验证前台模型列表实时响应；
   - 前台切换不同模型进行 AI 补全和流式对话，抓包验证后端正确加载该模型的专属参数和提示词。
