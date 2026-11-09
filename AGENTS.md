# 发版说明

每次发布版本、创建或更新 GitHub Release、生成发版说明时，使用中英文双语说明：先列 `### 更新说明`，再列 `### Changelog`。两部分条目数量、顺序、含义和 `feat:`、`fix:` 等类型前缀一一对应；保留技术名称和升级步骤，避免只翻译部分内容。发布前核对 GitHub Release 正文与版本变更一致。发版说明生成流程见 `.agents/skills/release-notes-generator/SKILL.md`。

发版完成后默认不跟踪 CI、Docker 镜像构建或推送状态；仅在用户明确要求时跟踪。

# 本机测试环境

在本机启动 HelloDoc 测试服务、使用测试数据库或进行浏览器验证前，若存在 `.local/test-environment.md`，先阅读并遵循其中的隔离与数据保护说明。该文件只记录本机环境，不随仓库分发。

本机隔离测试环境账号：`ui_review_20261105`，密码：`ReviewPass1234`。仅用于 `.local/test-environment.md` 所述测试数据库，不得用于群晖正式服务或其他环境。
