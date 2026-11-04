# Changelog

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

