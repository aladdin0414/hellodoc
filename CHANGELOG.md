# Changelog

## [v3.6.6] - 2026-11-09

### 更新说明

- fix: 修复中文加粗在标点边缘及末尾空格时的解析渲染，支持粘贴 HTML 中的 Markdown 标题和加粗内容转换为对应富文本。
- fix: 为 CI 构建的 Gradle 下载增加 SHA-256 校验和镜像备用源，保障依赖下载可靠性。

### Changelog

- fix: Correct Chinese bold parsing beside punctuation and trailing spaces, and render pasted plain Markdown headings and bold text as rich text.
- fix: Add SHA-256 checksum verification and mirror fallback sources for Gradle downloads in CI workflows.

## [v3.6.5] - 2026-11-09

### 更新说明

- feat: MCP bridge 支持通过 `HELLODOC_MCP_TOKEN` 认证，同时保留用户名和密码登录方式。
- docs: 补充发版后 CI 跟踪约定及本机隔离测试环境说明。

### Changelog

- feat: Support authentication with `HELLODOC_MCP_TOKEN` in the MCP bridge while retaining username and password login.
- docs: Document the post-release CI tracking convention and local isolated test environment.

## [v3.6.4] - 2026-11-09

### 更新说明

- feat: 将 AI 知识助理对话按用户和文档保存到数据库，重新打开时恢复，清空时同步清除历史。
- fix: 修复粘贴 HTML 时混合正文和图片、Base64 图片可能丢失，以及表格单元格换行或代码中的竖线导致保存后错行、错列的问题。
- fix: 加固外部图片转存的 DNS 地址校验，禁止自动跟随重定向；依赖重定向的图片会保留原始链接。
- fix: 修复目录右键菜单打开时再次右键弹出浏览器菜单，并避免移动端宽度模式覆盖桌面偏好。
- style: 将原生列表编号格式下拉选项设为左对齐。
- docs: 更新中英文项目介绍、Docker 部署说明和界面截图。
- chore: 升级前请备份数据库和数据目录；服务启动时会自动执行 AI 对话记录数据库迁移。

### Changelog

- feat: Store AI knowledge assistant conversations in the database per user and document, restore them when reopened, and clear stored history when requested.
- fix: Preserve text and images when pasting HTML, including Base64 images, and prevent table rows or columns from breaking after saving cells with line breaks or pipes in code.
- fix: Validate DNS addresses used to fetch external images and disable automatic redirects; images requiring redirects retain their original URLs.
- fix: Prevent the browser context menu from appearing on a second right-click while the directory menu is open, and preserve desktop page-width preferences on mobile.
- style: Left-align options in the native list-numbering format dropdown.
- docs: Update the Chinese and English project overview, Docker setup instructions, and interface screenshots.
- chore: Back up the database and data directories before upgrading; the AI conversation database migration runs automatically when the service starts.

## [v3.6.3] - 2026-11-06

### 更新说明

- style: 统一编辑页与发布页的知识库侧栏、目录层级轨道、状态标记及顶部导航，优化笔记编辑界面的视觉层级。
- style: 统一桌面和移动端弹窗的遮罩、圆角及操作样式，优化知识库设置、切换器和右键菜单。
- fix: 修复右键菜单在窗口边缘被裁切，以及目录自定义颜色遮挡图标内部线条的问题。
- fix: 修复批量删除、复制和创建副本时父子项目重复处理及部分失败反馈不清的问题，并保留失败操作的重试能力。
- fix: 复制或创建文件夹副本时逐项校验子项权限；跨知识库复制至公开知识库时提示副本保留发布状态。

### Changelog

- style: Align the knowledge base sidebar, directory hierarchy guides, status markers, and top navigation across the editor and published view, improving visual hierarchy while editing notes.
- style: Unify overlays, corner radii, and action styles in desktop and mobile dialogs, and refine knowledge base settings, the switcher, and context menus.
- fix: Prevent context menus from being clipped at viewport edges and preserve internal icon lines when custom directory colors are applied.
- fix: Avoid processing selected parent and child items twice during batch deletion, copying, and duplication; clarify partial failures and retain retry support for failed operations.
- fix: Check permissions for each descendant when copying or duplicating folders, and warn that publication status is retained when copying to a public knowledge base.

## [v3.6.2] - 2026-11-05

### 更新说明

- fix: 统一富文本编辑、Markdown 源码预览和发布阅读页的正文排版样式，修复表格、标题和段落显示不一致的问题。
- fix: 保留 Markdown 表格的列对齐设置，统一亮色和暗色主题下的表格行高与边框样式。

### Changelog

- fix: Unify document typography across the visual editor, Markdown source preview, and published reading view, fixing inconsistent tables, headings, and paragraphs.
- fix: Preserve Markdown table column alignment and unify row heights and borders in light and dark themes.

## [v3.6.1] - 2026-11-05

### 更新说明

- fix: 修复管理员转移知识库所有权时弹窗被遮挡的问题。
- fix: 改进通用弹窗的滚动、键盘焦点和 Escape 关闭行为，修复小屏幕下内容无法访问的问题。
- fix: 统一链接输入、MCP 令牌撤销和冲突草稿处理的对话框，并支持在修改密码时按 Enter 提交。
- fix: 补齐共享知识库列表视图的管理入口，改善键盘和触屏操作，并修复系统设置在窄屏下被裁切的问题。
- fix: 改进移动端目录操作面板和键盘导航，防止重复创建文档或目录，并允许页面缩放。
- fix: 修复编辑器 AI 提示词输入框的换行及输入法确认行为。

### Changelog

- fix: Fix the ownership transfer dialog being obscured when opened by an administrator.
- fix: Improve scrolling, keyboard focus, and Escape handling in shared dialogs, and make their content accessible on small screens.
- fix: Use consistent dialogs for link entry, MCP token revocation, and conflicting drafts, and allow submitting password changes with Enter.
- fix: Add management actions to the shared knowledge base list, improve keyboard and touch access, and prevent system settings from being clipped on narrow screens.
- fix: Improve the mobile directory action sheet and keyboard navigation, prevent duplicate document or folder creation, and allow page zooming.
- fix: Correct line breaks and input method confirmation in the editor AI prompt field.

## [v3.6.0] - 2026-11-05

### 更新说明

- feat: 新增知识库所有权转让功能，支持所有者安全交接管理权并更新成员身份。
- feat: 新增知识库访客留言开关配置，配套数据库 V6 迁移脚本，支持按库启用或禁用留言板。
- feat: 新增知识库设置中的 MCP Token 快捷管理入口，优化 Token 交互体验。
- fix: 强化知识库及私有文档的成员鉴权逻辑，修复未发布文档在特定公开场景下的越权风险。
- docs: 补充全面的 API 接口文档，更新项目说明与 MCP 使用指南。

### Changelog

- feat: add knowledge base ownership transfer to allow owners to safely hand over administration and update membership.
- feat: add guestbook toggle for knowledge bases with V6 database migrations to enable or disable guestbooks per base.
- feat: add shortcut modal for MCP token management in knowledge base settings to streamline token interaction.
- fix: strengthen member authorization for knowledge bases and private documents to prevent unauthorized access to unpublished documents.
- docs: add comprehensive API documentation and update project guides and MCP documentation.

## [v3.5.3] - 2026-11-05

### 更新说明

- fix: 修复文档协作 WebSocket 连接及权限校验，恢复多人会话的连接与锁状态同步。
- fix: 串行化文档抢锁，并在保存、MCP 写入及版本恢复时校验有效编辑锁，防止其他会话绕过持锁者写入。
- fix: 修复协作同步失败和断线重连处理；编辑器在失去编辑权时保持只读并保留本地草稿。
- chore: 自定义客户端在持锁编辑时，更新正文或创建版本需提交 `collabSessionId`，恢复版本时需提供同名参数；部署时请同时升级前后端。

### Changelog

- fix: restore collaborative WebSocket connections, permission checks, and synchronization of session lock state.
- fix: serialize document lock acquisition and validate active edit locks during saves, MCP writes, and revision restores to prevent other sessions from bypassing the lock owner.
- fix: handle collaboration synchronization failures and reconnection correctly; keep the editor read-only and preserve local drafts when edit access is lost.
- chore: custom clients editing with a lock must provide `collabSessionId` when updating content or creating a revision, and the parameter of the same name when restoring a revision; upgrade the frontend and backend together.

## [v3.5.2] - 2026-11-05

### 更新说明

- fix: 完善 AI 助手、模型配置、错误提示和移动端创建知识库的中英文显示，修复缺失词条。
- fix: 修复移动端语言偏好保存与跨端同步，并使页面标题和日期格式跟随语言切换。
- fix: 修复服务端消息、通知和 AI 流式错误的语言选择，补齐文档版本冲突等提示。
- chore: 增加前后端国际化词条、语言切换和错误处理检查，并将客户端国际化检查加入镜像发布流程。

### Changelog

- fix: improve Chinese and English text for the AI assistant, model configuration, error messages, and mobile knowledge base creation, including missing translations.
- fix: save and synchronize mobile language preferences across clients, and update page titles and date formatting when the language changes.
- fix: correct language selection for server messages, notifications, and AI streaming errors, and add document version conflict messages.
- chore: add frontend and backend checks for translations, language switching, and error handling, and run client internationalization checks during image publishing.

## [v3.5.1] - 2026-11-05

### 更新说明

- fix: 修复 PostgreSQL 空数据库首次安装时初始化及迁移顺序错误。
- fix: 修复 Docker 镜像首页及 API 路径，确保可以访问和登录。
- fix: 持久化上传文件，等待数据库就绪，并要求配置数据库密码、管理员密码和 JWT 密钥；数据库默认不再映射到宿主机。
- docs: 更新 Docker 部署、备份恢复和失败首装处理说明；发布流程增加空数据库迁移检查并同步 Docker Hub 页面。

### Changelog

- fix: correct initialization and migration order when installing with an empty PostgreSQL database.
- fix: correct the Docker image's homepage and API paths so users can access and sign in.
- fix: persist uploads, wait for the database, and require database, administrator, and JWT secrets; the database port is no longer exposed to the host by default.
- docs: update Docker deployment, backup, recovery, and failed first-install instructions; add a fresh-database migration check to publishing and synchronize the Docker Hub page.

## [v3.5.0] - 2026-11-04

### 更新说明

- feat: 新增 MCP 创建文件夹工具，支持知识库根目录及嵌套目录，并使用 requestId 安全重试。
- feat: 新增 MCP 移动文件夹工具，支持完整子树移动、权限校验、位置冲突检测和循环目录防护。
- docs: 更新 MCP Bridge 和使用文档；升级后需更新并重启 HelloDoc 服务及 MCP Bridge。

### Changelog

- feat: add an MCP tool to create folders at the knowledge base root or in nested folders, with safe retries using requestId.
- feat: add an MCP tool to move folders and their subtrees, with permission checks, location conflict detection, and protection against directory cycles.
- docs: update the MCP Bridge and documentation; update and restart the HelloDoc service and MCP Bridge after upgrading.

## [v3.4.0] - 2026-11-04

- 新增 MCP 同目录排序，支持根目录及任意层级文件夹中的文档与文件夹混合排序。
- 新增目录快照、权限校验与冲突检测，避免并发修改被覆盖。
- 网页同目录拖拽和上移、下移改为原子批量排序，统一网页与 MCP 的显示顺序。
- 包含 V5 数据库迁移；升级后需重启服务和 MCP Bridge。

## [v3.3.0] - 2026-11-04

- 支持 MCP 客户端通过 Streamable HTTP 直连，无需本地桥接或固定知识库 ID
- 新增可撤销的 MCP 专用访问令牌和个人资料管理入口
- 支持配置公开服务地址，返回完整文档来源链接

## [v3.2.0] - 2026-11-03

- 新增 MCP 文档移动功能，支持权限校验、位置冲突检测和安全重试
- 优化 AI 保存文档时的目录选择指引，减少误存至根目录

## [v3.1.0] - 2026-11-03

- MCP 会话失效时自动重连，避免不确定的写入被重复执行
- 支持向普通多级文件夹及知识库根目录创建文档，兼容既有事项接口
- 新增知识库发现和分页目录浏览，支持跨知识库选择与权限过滤

## [v3.0.1] - 2026-11-03

- 修复发布脚本误提交无关文件和掩盖推送失败的问题
- 端到端测试改用隔离服务，验证版本冲突和文档修订流程

## [v3.0.0] - 2026-11-03

- MCP 支持跨知识库检索和安全更新文档，优化检索性能与结果准确性
- 正文保存需要版本号；旧客户端和第三方集成需升级以避免覆盖新内容
- 桌面及移动端在版本冲突时保留本地草稿，完善 Markdown 文档保存指引

## [v2.3.1] - 2026-11-03

- 修复 MCP 无法区分文件夹和文档的问题，读取文件夹时列出有权限的子文档。

## [v2.3.0] - 2026-11-03

- 新增 MCP 跨知识库检索和文档正文读取，支持草稿、权限校验及来源链接。
- 加密文档不参与全局搜索和 MCP 读取；中文关键词检索支持子串匹配。

## [v2.2.0] - 2026-11-02

- 新增 MCP 接口，支持 AI 按事项归档 Markdown 文档。
- 新增本地 MCP 桥接程序，复用现有账号并支持知识库范围配置。
- 支持并发请求防重、超时结果查询与现有知识库权限校验。

## [v2.1.9] - 2026-11-02

- fix: improve autosave retry logic, local draft preservation, and lifecycle cleanup
- ci: migrate actions to Node 24 and pin Ubuntu runner
- 改进可视化编辑器多级列表格式选择及嵌套列表保存。

## [v2.1.8] - 2026-10-22

- fix: use public npm URLs for reproducible CI installs

## [v2.1.7] - 2026-10-22

- fix: secure AI assistant conversations and stream cancellation
- fix: improve asset upload insertion tracking and remove markdown normalization in VisualEditor

## [v2.1.6] - 2026-10-21

- chore: track package-lock.json by updating gitignore and adding lock file

## [v2.1.5] - 2026-10-21

- ci: add steps to capture and upload npm debug logs on workflow failure

## [v2.1.4] - 2026-10-21

- build: update Node.js version, upgrade npm, and use npm ci for reliable client builds

## [v2.1.3] - 2026-10-21

- v2.1.3 版本发布与日常维护

## [v2.1.2] - 2026-09-08

- refactor: remove format brush feature and update toolbar icon styling
- refactor: remove format brush feature and cleanup related components and styles
- refactor: reset scroll position and TOC state on document navigation and ensure stable layout width
- fix: ensure document content is updated and synchronized before triggering auto-save after AI content insertion

## [v2.1.1] - 2026-09-08

- feat: 优化知识库阅读视图排版、TOC平滑导航与目录树体验并修复死循环问题

## [v2.1.0] - 2026-09-08

- style: 统一 AI 大模型配置页面的头部与新增模型按钮样式
- fix: 修复系统设置初始化重复创建导致的 key_exists 报错与列表变空问题
- refactor: 从系统设置中完全移除已弃用的大模型系统配置
- style: 为编辑器中的块级数学公式添加与对话抽屉一致的背景块样式
- fix: 改进 LaTeX 公式渲染为靶向自愈模式，杜绝 \left 等合法宏被误伤
- fix: 修复大模型流式传输空格丢失与 LaTeX 公式宏命令粘连报红问题
- fix: 修复LaTeX定界符被Markdown转义导致中括号与小括号原样暴露的问题
- fix: 修复流式传输误将换行符和空格丢弃导致的Markdown严重排版错乱问题
- fix: 优化模型连通测试，抑制推理模型深度思考并将探测超时放宽至30秒
- fix: 修复插入文档至富文本编辑器时标题与列表项语法未规范化解析问题
- fix: 重构Markdown数学公式渲染占位机制，杜绝下划线加粗歧义与占位符泄漏
- fix: 优化AI对话Markdown渲染，支持KaTeX数学公式与修复列表加粗语法
- feat: AI知识助理对话支持Markdown语法渲染与排版优化
- refactor: 统一在 DatabaseInitializer 中管理 sys_ai_model 数据表自动创建与初始化迁移
- feat: 支持启动时自动检测并创建 sys_ai_model 数据表与索引
- feat: 实现AI模型后台独立管理模块与多模型切换
- chore: update example environment variables configuration
- style: normalize product name to Hellodoc across codebase and documentation
- docs: localize documentation by splitting README into English and Chinese files and updating project language to English
- chore: update dev script environment configuration

## [v2.0.11] - 2026-08-07

- fix(dev): 优化本地环境变量加载及端口占用处理
- refactor(i18n): 使用国际化替换硬编码文本并更新接口文档
- feat(i18n): 增强国际化支持及翻译文本完善
- feat(i18n): 实现全栈多语言国际化支持
- docs: update project documentation in README.md
- chore: update gitignore and document release notes generator skill

## [v2.0.10] - 2026-08-07

- test: 更改构建发布的docker hub账户
- docs: update release notes generator skill documentation

## [v2.0.9] - 2026-08-07

- feat(mobile): 优化移动端知识库页面交互与缓存体验

## [v2.0.8] - 2026-08-06

- refactor: simplify database initialization logic in DatabaseInitializer
- feat: add host gateway mapping and simplify database environment variable configuration
- feat: optimize deployment SSH/SCP performance with connection multiplexing and key authentication support
- chore: improve error handling, add build suggestions, and optimize deployment script in deploy.sh
- refactor: rename NAS configuration variables to generic DEPLOY variables and update deployment commands
- docs: update deployment instructions in README
- docs: update project description and setup instructions in README

All notable changes to HelloDoc will be documented in this file.

## [v2.0.7] - 2026-08-06

- fix: resolve connection leakage by closing database initializer resources properly
- feat: support BCrypt password hashing for backward compatibility and add project development scripts
- refactor: simplify database initialization by removing manual schema migrations and consolidating logic to schema.sql execution
- feat: update database configuration to support environment-specific variables and remove unused schema example file
- refactor: remove client-side Dockerfile and consolidate environment configuration at the project root
- chore: update environment variable documentation in .env.example and README.md
- feat: add support for dynamic edit mode and auto-focus parameters when navigating to document views

