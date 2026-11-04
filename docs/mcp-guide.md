# HelloDoc MCP 接入

HelloDoc 提供远程 Streamable HTTP MCP 接口。支持该协议及 Bearer 令牌的客户端可以直接连接，不需要安装 Node.js 或本地桥接程序，也无需预先配置知识库 ID。AI 可列出账号有权访问的知识库，再选择合适的知识库和目录；有多个可能的目标时应询问用户。

## 准备访问令牌

1. 升级并启动包含本功能的 HelloDoc 服务端，数据库会执行相应迁移（目录排序需要 V5）。
2. 登录 HelloDoc，在个人资料的 **MCP 访问令牌**中填写客户端名称并创建令牌。
3. 立即复制令牌：它只在创建时显示一次。服务端只保存令牌哈希；令牌可在同一界面撤销，撤销后立即失效。
4. 在支持 Streamable HTTP 的客户端中配置地址 `https://你的服务地址/api/mcp`，并以 `Authorization: Bearer <令牌>` 认证。令牌继承创建者的文档权限，只能用于 MCP 接口；不要提交到仓库或分享给其他人。

建议使用 HTTPS 保护令牌和文档内容。部署在反向代理后时，确保代理支持 MCP 的流式响应和会话请求。如果希望工具结果包含完整文档链接，在服务端配置 `HELLODOC_PUBLIC_URL`（例如 `https://docs.example.com`，仅填写协议和域名，可带端口）；未配置时结果提供相对路径。

## 配置 Codex

先通过安全方式把令牌放入启动 Codex 进程的环境变量 `HELLODOC_MCP_TOKEN`，然后运行：

```bash
codex mcp add hellodoc --url https://你的服务地址/api/mcp --bearer-token-env-var HELLODOC_MCP_TOKEN
```

每次启动 Codex 时，该环境变量都必须可用。不要将实际令牌写入命令历史或提交到仓库。重启客户端会话后，可以尝试：“查找相关知识库，把上面的讨论总结并保存到合适的目录。”客户端需支持远程 MCP；仅支持 stdio 的客户端仍可使用旧版本地桥接程序，见仓库中的 `hellodoc-mcp-bridge`。

## 检索知识库

可以直接询问 AI，例如：“查找所有知识库中有关部署流程的文档，并引用来源。”AI 可调用 `search_documents` 搜索，再用 `read_document` 读取相关正文。搜索包括账号有权查看的草稿，结果附带文档链接；创建文档时可选择有编辑权限的其他知识库。

搜索采用关键词匹配，不是语义搜索。可以让 AI 提取或更换关键词重试；索引异步更新，刚修改的内容可能短暂无法搜索。搜索结果的 `type` 区分文件和文件夹。读取文件夹会返回有权访问的直接子项及链接，每页最多 50 项；读取文档时正文每次最多返回 12000 个 Unicode 字符。两种情况下均可使用返回的 `nextOffset` 继续读取；文件夹偏移按可访问子项计，文档偏移按 Unicode 字符计。文档可能在分页过程中被修改，每次读取都会重新检查权限。加密文档无法通过这些工具搜索或读取；搜索不到或内容不足时，AI 应说明依据不足。

## 浏览目录和创建文档

`list_knowledge_bases` 分页列出可访问的知识库。若 `canBrowseRoot` 为 false，表示只有部分文档共享权限，可通过搜索定位共享文档。`list_folder_children` 接受从 `list_knowledge_bases` 获取的 `knowledgeBaseId` 和可选的 `parentId`；省略后者时浏览根目录。返回结果包含可访问的直接子项和 `nextOffset`。

`create_document` 需要目标 `knowledgeBaseId`，并接受可选 `parentId`；不提供 `parentId` 时创建在根目录。目标知识库及文件夹须有编辑权限。旧版 `itemId` 调用仍可使用，但不能和 `parentId` 同时传入。`requestId` 应在结果不确定时原样重试，禁止对其他网络错误无条件重放写操作。

`create_folder` 在根目录或指定 `parentId` 下创建文件夹，参数为 `knowledgeBaseId`、`name`、`requestId` 和可选的 `parentId`。创建前先浏览目标目录；结果不确定时使用相同参数和 `requestId` 重试，或通过 `get_operation` 的 `folder` 类型查询结果。同一请求 ID 用于不同参数会被拒绝。

保存前应先查找已有知识库和相关目录；用户指定目录或找到明确匹配时使用对应的 `parentId`。多个目录可能匹配时应询问用户。只有用户指定根目录或没有合适目录时，才使用根目录。

## 移动文档

`move_document` 可在同一知识库内移动普通文档：先调用 `read_document` 取得 `currentParentId`，再指定目标 `parentId`，以读取值作为 `expectedParentId` 提交。两个字段的 `0` 都表示知识库根目录。工具会检查源文档和目标文件夹的权限，不支持跨知识库或移动文件夹。

`move_folder` 移动文件夹及其全部子项，参数为 `folderId`、`expectedParentId` 和目标 `parentId`。先用 `read_document` 读取文件夹的 `currentParentId`，根目录使用 `0`。工具要求源文件夹及目标目录有编辑权限，禁止跨知识库或移入自身和后代。`LOCATION_CONFLICT` 表示位置已变化，应重新读取；`INVALID_DESTINATION` 表示目标会产生目录循环。重复提交已完成的相同移动是安全的。

如果文档已移动到目标目录，相同请求可安全重试；如果它被移动到其他位置，则返回 `LOCATION_CONFLICT`，需要重新读取并确认目标，不应直接覆盖其他人的移动。

## 排序同级文档和文件夹

`reorder_folder_children` 对指定知识库同一目录内的直接子文档和子文件夹混合排序，不改变其父目录。根目录的 `parentId` 使用 `0`；嵌套目录使用文件夹 ID。排序最多支持 10000 个直接子项。

先用 `list_folder_children` 读取第一页，保存返回的 `snapshot`；如果有 `nextOffset`，以该值和 `expectedSnapshot`（第一页的 snapshot）继续读取，直到没有下一页。续页返回 `ORDER_CONFLICT` 时，从第一页重新读取。然后按用户期望排列全部子项 ID，例如：

```json
{"knowledgeBaseId": 91, "parentId": 0, "expectedSnapshot": "12", "orderedIds": [42, 37, 51]}
```

所有当前未删除的直接子项必须出现且只出现一次，包括文件夹。目录或子项存在不可读、不可编辑或加密内容时返回 `DIRECTORY_UNAVAILABLE`；这不会暴露隐藏子项信息。提交缺项、重复或非同级 ID 返回 `INVALID_ORDER`。知识库目录或权限变化后返回 `ORDER_CONFLICT`，应重新读取所有页面并结合用户意图重新计算。成功后可用返回的 `snapshot` 继续操作；相同目标已生效时重复请求返回 `changed: false`。

## 修改已有文档

`read_document` 返回 `version` 和 `updatedAt`。长文档第一页返回的 `version` 要作为后续分页请求的 `expectedVersion`；若返回 `VERSION_CONFLICT`，说明文档在读取时发生变化，应从第一页重新读取，不能拼接不同版本的正文。

`update_document` 可以修改当前账号在任何知识库中有编辑权限的普通文档：提供 `documentId`、从读取结果取得的 `expectedVersion`、`mode`（`replace` 或 `append`）、`markdown` 和 `requestId`。`replace` 可传空字符串清空正文；`append` 会原样拼接，不自动添加换行。单次输入和更新后的正文均不超过 2 MiB（按 UTF-8 字节计算）。文件夹、加密文档和无权限文档不能更新。

例如先读取文档取得 `version: 7`，再以 `expectedVersion: 7`、`mode: "append"` 和需要追加的 Markdown 发起更新。成功后使用返回的新版本；若收到 `VERSION_CONFLICT`，重新读取文档后再处理。

版本发生冲突时服务端不写入，AI 应重新读取并结合用户意图处理，不直接覆盖。超时重试必须使用相同 `requestId` 和相同输入；同一 ID 对应不同输入会返回 `REQUEST_ID_CONFLICT`。成功结果包含本次操作生成的版本与链接；重复请求返回原操作结果，该版本未必仍是当前版本，后续修改前应重新读取。旧版网页或第三方客户端保存正文时，如果没有提交 `expectedVersion`，服务端会拒绝并提示升级；元数据修改不受这一要求影响。

用户要求 Markdown 时，Agent 应使用标题、列表、表格和代码块来表达内容，代码块只用于实际代码，不应把整篇文档放在代码块中。PDF、图片等由 Agent 自行解析；扫描页、图片或表格未能可靠提取时应明确标注。必要时读取保存结果核对内容，无法自动保证转换完整。

## 失败与重试

创建工具需要 `requestId`。发生超时后，AI 可以用 `get_operation` 查询，或用**同一个** `requestId` 重试；相同 ID 对应不同输入会失败。防重信息保存在创建结果的现有扩展元数据中；删除文档或清除该元数据后，不能保证仍可查询原操作，永久删除后也不能保证防重。

普通 HTTP 不加密局域网链路；如果部署环境需要链路加密，可为服务配置 HTTPS。不要将密码、令牌或真实知识库内容写入示例和日志。

服务端远程 MCP 协议路径已通过 HTTP 测试；尚未逐一实测各客户端产品界面，具体配置和重连行为可能不同。
