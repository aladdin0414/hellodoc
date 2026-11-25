# Go 迁移接口候选清单

来源：`hellodoc-server/api-docs.json`；这是候选清单，尚未与源码、客户端或运行时逐项核对，不能作为完整覆盖证明。所有条目均未迁移、未验证。

| 方法 | 路径 | OpenAPI 摘要 | 状态 |
| --- | --- | --- | --- |
| DELETE | `/api/assets/{id}` | 删除附件 | 未核对 |
| GET | `/api/assets/{id}` | 附件详情 | 未核对 |
| GET | `/api/assets/{id}/url` | 获取附件访问URL | 未核对 |
| GET | `/api/audit-logs` | 全部日志 | 未核对 |
| GET | `/api/audit-logs/user/{userId}` | 用户日志 | 未核对 |
| POST | `/api/auth/login` | 用户登录 | 未核对 |
| POST | `/api/auth/logout` | 用户登出 | 未核对 |
| POST | `/api/auth/refresh-token` | 刷新 Token | 未核对 |
| POST | `/api/auth/register` | 用户注册 | 未核对 |
| DELETE | `/api/comments/{id}` | 删除评论 | 未核对 |
| PUT | `/api/comments/{id}` | 更新评论 | 未核对 |
| POST | `/api/comments/{id}/reply` | 回复评论 | 未核对 |
| POST | `/api/comments/{id}/resolve` | 解决评论 | 未核对 |
| POST | `/api/comments/{id}/unresolve` | 取消解决评论 | 未核对 |
| GET | `/api/docs/favorites` | 获取收藏列表 | 未核对 |
| DELETE | `/api/docs/recent` | 清空访问历史 | 未核对 |
| GET | `/api/docs/recent` | 最近访问列表 | 未核对 |
| GET | `/api/docs/{docId}/assets` | 文档附件列表 | 未核对 |
| GET | `/api/docs/{docId}/audit-logs` | 文档日志 | 未核对 |
| GET | `/api/docs/{docId}/backlinks` | 反向链接 | 未核对 |
| GET | `/api/docs/{docId}/comments` | 获取文档评论列表 | 未核对 |
| POST | `/api/docs/{docId}/comments` | 发表评论 | 未核对 |
| GET | `/api/docs/{docId}/comments/unresolved-count` | 获取未解决评论数 | 未核对 |
| DELETE | `/api/docs/{docId}/favorite` | 移除收藏 | 未核对 |
| GET | `/api/docs/{docId}/favorite` | 检查是否已收藏 | 未核对 |
| POST | `/api/docs/{docId}/favorite` | 切换收藏状态 | 未核对 |
| GET | `/api/docs/{docId}/graph` | 关系图谱 | 未核对 |
| GET | `/api/docs/{docId}/links` | 引用的文档 | 未核对 |
| POST | `/api/docs/{docId}/links` | 手动添加链接 | 未核对 |
| DELETE | `/api/docs/{docId}/links/{targetDocId}` | 删除链接 | 未核对 |
| GET | `/api/docs/{docId}/revisions` | Get revision history | 未核对 |
| POST | `/api/docs/{docId}/revisions` | Create manual revision | 未核对 |
| GET | `/api/docs/{docId}/revisions/compare` | Compare revisions | 未核对 |
| POST | `/api/docs/{docId}/revisions/milestone` | Create milestone | 未核对 |
| GET | `/api/docs/{docId}/revisions/{version}` | Get specific revision | 未核对 |
| GET | `/api/docs/{docId}/revisions/{version}/content` | Get revision content | 未核对 |
| POST | `/api/docs/{docId}/revisions/{version}/restore` | Restore revision | 未核对 |
| GET | `/api/docs/{docId}/share` | 分享列表 | 未核对 |
| POST | `/api/docs/{docId}/share` | 添加权限 | 未核对 |
| POST | `/api/docs/{docId}/share/link` | 创建分享链接 | 未核对 |
| DELETE | `/api/docs/{docId}/share/{permissionId}` | 取消分享 | 未核对 |
| POST | `/api/files/avatar` | 上传头像 | 未核对 |
| GET | `/api/files/avatars/{filename}` | 获取头像 | 未核对 |
| POST | `/api/kb/createKnowledgeBase` | 创建知识库 | 未核对 |
| GET | `/api/kb/listKnowledgeBases` | 获取知识库列表 | 未核对 |
| POST | `/api/kb/reorder` | 重排知识库 | 未核对 |
| GET | `/api/kb/trash` | 回收站列表 | 未核对 |
| DELETE | `/api/kb/{kbId}` | 删除知识库 | 未核对 |
| PUT | `/api/kb/{kbId}` | 更新知识库 | 未核对 |
| GET | `/api/kb/{kbId}/assets` | 知识库附件列表 | 未核对 |
| POST | `/api/kb/{kbId}/assets` | 上传知识库附件 | 未核对 |
| GET | `/api/kb/{kbId}/audit-logs` | 知识库日志 | 未核对 |
| POST | `/api/kb/{kbId}/docs/{docId}/assets` | 上传文档附件 | 未核对 |
| GET | `/api/kb/{kbId}/documents` | 获取文档列表 | 未核对 |
| POST | `/api/kb/{kbId}/documents` | 创建文档 | 未核对 |
| DELETE | `/api/kb/{kbId}/documents/{docId}` | 删除文档 | 未核对 |
| GET | `/api/kb/{kbId}/documents/{docId}` | 获取文档详情 | 未核对 |
| PUT | `/api/kb/{kbId}/documents/{docId}` | 更新文档 | 未核对 |
| GET | `/api/kb/{kbId}/documents/{docId}/revisions` | 获取修订历史 | 未核对 |
| POST | `/api/kb/{kbId}/documents/{docId}/revisions` | 创建修订 | 未核对 |
| GET | `/api/kb/{kbId}/members` | 成员列表 | 未核对 |
| POST | `/api/kb/{kbId}/members` | 添加成员 | 未核对 |
| POST | `/api/kb/{kbId}/members/leave` | 主动退出 | 未核对 |
| DELETE | `/api/kb/{kbId}/members/{userId}` | 移除成员 | 未核对 |
| PUT | `/api/kb/{kbId}/members/{userId}` | 修改角色 | 未核对 |
| POST | `/api/kb/{kbId}/pin` | 置顶知识库 | 未核对 |
| POST | `/api/kb/{kbId}/restore` | 恢复知识库 | 未核对 |
| GET | `/api/kb/{kbId}/search` | 知识库内搜索 | 未核对 |
| GET | `/api/notifications` | 获取通知列表 | 未核对 |
| PUT | `/api/notifications/read-all` | 全部标记已读 | 未核对 |
| GET | `/api/notifications/unread-count` | 获取未读通知数 | 未核对 |
| DELETE | `/api/notifications/{id}` | 删除通知 | 未核对 |
| PUT | `/api/notifications/{id}/read` | 标记已读 | 未核对 |
| GET | `/api/public/configs/frontend` | 获取前端配置 | 未核对 |
| GET | `/api/public/dict/{dictCode}` | 获取字典项 | 未核对 |
| GET | `/api/search` | 全局搜索 | 未核对 |
| POST | `/api/search/rebuild` | 重建索引 | 未核对 |
| GET | `/api/storage/public` | 获取公开资源 | 未核对 |
| GET | `/api/system/configs` | 配置列表 | 未核对 |
| POST | `/api/system/configs` | 创建配置项 | 未核对 |
| PUT | `/api/system/configs` | 更新配置 | 未核对 |
| POST | `/api/system/configs/refresh` | 刷新配置缓存 | 未核对 |
| POST | `/api/system/dict/data` | 创建字典数据 | 未核对 |
| GET | `/api/system/dict/data/type/{typeId}` | 根据类型获取字典数据 | 未核对 |
| DELETE | `/api/system/dict/data/{id}` | 删除字典数据 | 未核对 |
| PUT | `/api/system/dict/data/{id}` | 更新字典数据 | 未核对 |
| GET | `/api/system/dict/types` | 字典类型列表 | 未核对 |
| POST | `/api/system/dict/types` | 创建字典类型 | 未核对 |
| DELETE | `/api/system/dict/types/{id}` | 删除字典类型 | 未核对 |
| GET | `/api/system/dict/types/{id}` | 字典类型详情 | 未核对 |
| PUT | `/api/system/dict/types/{id}` | 更新字典类型 | 未核对 |
| GET | `/api/system/permissions` | 权限列表 | 未核对 |
| POST | `/api/system/permissions` | 创建权限 | 未核对 |
| GET | `/api/system/permissions/roles/{roleId}` | 角色权限列表 | 未核对 |
| POST | `/api/system/permissions/roles/{roleId}` | 授予权限 | 未核对 |
| DELETE | `/api/system/permissions/roles/{roleId}/permissions/{permId}` | 撤销权限 | 未核对 |
| GET | `/api/system/permissions/users/{userId}` | 用户权限列表 | 未核对 |
| DELETE | `/api/system/permissions/{id}` | 删除权限 | 未核对 |
| GET | `/api/system/permissions/{id}` | 权限详情 | 未核对 |
| GET | `/api/system/roles` | 角色列表 | 未核对 |
| POST | `/api/system/roles` | 创建角色 | 未核对 |
| GET | `/api/system/roles/users/{userId}` | 用户角色列表 | 未核对 |
| POST | `/api/system/roles/users/{userId}` | 分配角色 | 未核对 |
| DELETE | `/api/system/roles/users/{userId}/roles/{roleId}` | 撤销角色 | 未核对 |
| DELETE | `/api/system/roles/{id}` | 删除角色 | 未核对 |
| GET | `/api/system/roles/{id}` | 角色详情 | 未核对 |
| PUT | `/api/system/roles/{id}` | 更新角色 | 未核对 |
| GET | `/api/system/storage/configs` | 存储配置列表 | 未核对 |
| POST | `/api/system/storage/configs` | 创建存储配置 | 未核对 |
| DELETE | `/api/system/storage/configs/{id}` | 删除存储配置 | 未核对 |
| PUT | `/api/system/storage/configs/{id}` | 更新存储配置 | 未核对 |
| POST | `/api/system/storage/configs/{id}/default` | 设为默认存储配置 | 未核对 |
| POST | `/api/system/storage/configs/{id}/test` | 测试存储配置连接 | 未核对 |
| GET | `/api/users` | 获取用户列表 | 未核对 |
| POST | `/api/users` | 创建用户 | 未核对 |
| PUT | `/api/users/avatar` | 更新头像 | 未核对 |
| PUT | `/api/users/change-pwd` | 修改密码 | 未核对 |
| GET | `/api/users/getUserInfo/{username}` | 获取用户详情 | 未核对 |
| GET | `/api/users/me` | 获取当前用户 | 未核对 |
| PUT | `/api/users/profile` | 更新个人资料 | 未核对 |
| DELETE | `/api/users/{id}` | 删除用户 | 未核对 |
| PUT | `/api/users/{id}` | 更新用户 | 未核对 |
| PUT | `/api/users/{id}/init-pwd` | 初始化密码 | 未核对 |
| PUT | `/api/users/{id}/reset-pwd` | 重置密码 | 未核对 |
