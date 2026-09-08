# Changelog

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

