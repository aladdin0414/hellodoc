---
name: release-notes-generator
description: Generate bilingual Chinese and English release notes or a changelog from Git history for a release or GitHub Release.
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

每次发版说明均须包含中文和英文，英文逐条对应中文，顺序、含义、类型前缀和升级步骤保持一致。基于实际 diff 撰写，不遗漏任一语言；保留 MCP、API 等技术名称。每条采用 `feat:`、`fix:`、`refactor:`、`docs:`、`chore:`、`style:` 等合适的类型前缀。使用以下结构：

```markdown
### 更新说明

- feat: 中文更新内容
- fix: 中文修复内容

### Changelog

- feat: corresponding English update
- fix: corresponding English fix
```

过滤发版自循环提交（如 `chore: 发布版本 vX.Y.Z`）和默认 Merge 节点；有破坏性变更时在两个语言版本中分别说明迁移步骤。

### 4. 写入文件与验证

1. 将生成的 Markdown 保存至 `.local/RELEASE_NOTES_<version>.md`。
2. 检查两种语言的条目数量、顺序、含义和前缀一致。
3. 向用户汇报结果，提供超链接指向生成的 `.md` 文件，并在回答中展示或提示用户可直接复制到 GitHub Release 界面使用。
