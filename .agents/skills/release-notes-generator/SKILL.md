---
name: release-notes-generator
description: Compare git main branch against the latest tag (or specified base tag), generate a structured Markdown release note formatted for GitHub Releases, and save it to the .local directory. Use when user asks to generate release notes, write changelog, compare main branch with tag, or create release notes in .local directory.
---

# Release Notes Generator

此 Skill 用于自动对比 Git `main` 分支（或当前分支）与上一个最新 Tag（或指定基线 Tag），提取变更日志并自动分类整理成适合 GitHub Release 直接使用的 Markdown 格式发版说明，最后保存到项目根目录下的 `.local/RELEASE_NOTES_<version>.md` 文件中。

## 🛠️ 执行流程 (Workflow)

### 1. 获取 Git 版本差异与基线 Tag
1. 获取最新的 Tag 列表（按版本倒序）：
   ```bash
   git tag -l --sort=-v:refname | head -n 5
   ```
2. **确认基线 Tag (base-tag) 与目标范围 (target)**：
   - 若用户指定了基线 Tag（如 `v2.0.7`），则以该 Tag 为基底。
   - 若当前 `HEAD` 已打 Tag（例如当前已打 `v2.0.8`），则取上一个 Tag（`v2.0.7`）作为基线 Tag，比较范围为 `v2.0.7..v2.0.8`。
   - 若 `HEAD` 处于最新 Tag 之后的提交，默认比较范围为 `<latest-tag>..HEAD`。
3. 获取目标范围内的 Commit 提交列表：
   ```bash
   git log <base-tag>..<target> --oneline
   ```
4. 查看文件变动统计以辅助理解变更范围：
   ```bash
   git diff --stat <base-tag>..<target>
   ```
5. 获取远程 GitHub / Git 仓库地址（用于构建 Full Changelog 链接）：
   ```bash
   git remote get-url origin
   ```

### 2. 识别目标版本号与输出路径
1. 从根目录 `package.json`、`build.gradle`、`pom.xml` 或最新 Tag 推导当前/即将发版的版本号（格式如 `v2.0.8`）。
2. 确保项目根目录下的 `.local` 目录存在。
3. 目标输出文件路径命名规则：
   `.local/RELEASE_NOTES_<version>.md` （例如 `.local/RELEASE_NOTES_v2.0.8.md`）。

### 3. 生成 GitHub Release 标准 Markdown 内容

撰写更新说明时，必须遵循以下结构规范：

```markdown
## 🎉 <项目名> <版本号> 发版说明

<项目名> **<版本号>** 正式发布！<一两句简短高亮总结本次更新的核心价值与重磅变更>。

### ⚠️ 破坏性变更与迁移指南 (BREAKING CHANGES) <!-- 仅在存在破坏性变更时保留 -->

* **<模块/配置项>**
  * <具体的改动说明与升级迁移建议>

### 🚀 新特性与优化 (Features & Enhancements)

* **<模块/主题 1>**
  * <具体的改动说明，保留英文技术术语，重要组件或文件名使用行内代码如 `DocTreeNode`，切勿包含本地绝对路径 `file:///...`>
  * <具体的改动说明>

* **<模块/主题 2>**
  * <具体的改动说明>

### 📝 Commit 提交明细

* `<commit-hash-1>` <commit message>
* `<commit-hash-2>` <commit message>

**Full Changelog**: https://github.com/<owner>/<repo>/compare/<base-tag>...<target-version>
```

#### 分类指引 (Categorization Guidelines)：
按照提交记录与 Diff 语义，将改动划分为以下常用的几类（无改动项的分类可省略）：
- ⚠️ **破坏性变更与迁移指南 (BREAKING CHANGES)**（配置兼容性调整、废弃 API、依赖破坏性升级等）
- 🚀 **新特性与优化 (Features & Enhancements)**（包含部署、架构解耦、网络、性能等）
- 🛠️ **后端服务与代码重构 (Server & Refactoring)**
- 🐛 **缺陷修复 (Bug Fixes)**
- 📖 **文档与规范升级 (Documentation)**
- 📦 **依赖与构建工具 (Chore & Dependencies)**

#### Commit 明细过滤规则：
在生成 `📝 Commit 提交明细` 时：
- **自动过滤**发版自循环提交（如 `chore: 发布版本 vX.Y.Z`、`chore: bump version`）和简单的 Merge branch/pull request 默认节点，保持明细干净利炼。

### 4. 写入文件与验证
1. 使用 `write_to_file` 工具将生成的 Markdown 保存至 `.local/RELEASE_NOTES_<version>.md`。
2. 检查生成的 Markdown 文件内容完整无误。
3. 向用户汇报结果，提供超链接指向生成的 `.md` 文件，并在回答中展示或提示用户可直接复制到 GitHub Release 界面使用。

