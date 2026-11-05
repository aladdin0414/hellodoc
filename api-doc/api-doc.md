# HelloDocE2E 接口请求记录

- 来源：`HelloDocE2ETest.e2e_full_workflow_shouldSucceed`
- 生成时间：2026-11-03T19:58:30.013484+08:00

## 1. POST /api/auth/login

- 描述：【Authentication Management】User login

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Content-Length: 39
```

- Params

```json
{ }
```

- Body

```json
{
  "username" : "liyc",
  "password" : "<redacted>"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "accessToken" : "<redacted>",
    "refreshToken" : "<redacted>",
    "tokenType" : "Bearer",
    "nickname" : "liyc",
    "avatar" : null,
    "isInitialPwd" : false
  }
}
```

## 2. POST /api/auth/login

- 描述：【Authentication Management】User login

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Content-Length: 38
```

- Params

```json
{ }
```

- Body

```json
{
  "username" : "bob",
  "password" : "<redacted>"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "accessToken" : "<redacted>",
    "refreshToken" : "<redacted>",
    "tokenType" : "Bearer",
    "nickname" : "bob",
    "avatar" : null,
    "isInitialPwd" : false
  }
}
```

## 3. POST /api/auth/register

- 描述：【Authentication Management】User registration

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Content-Length: 88
```

- Params

```json
{ }
```

- Body

```json
{
  "username" : "e2e_user_1793707110307",
  "password" : "<redacted>",
  "nickname" : "E2E-1793707110307"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 3,
    "nickname" : "E2E-1793707110307",
    "realName" : null,
    "avatar" : null,
    "phone" : null,
    "email" : null,
    "languageMode" : "AUTO",
    "status" : 0,
    "createTime" : "2026-11-03T11:58:30.308375Z",
    "updateTime" : "2026-11-03T11:58:30.308375Z",
    "username" : null,
    "roles" : [ ]
  }
}
```

## 4. POST /api/auth/register

- 描述：【Authentication Management】User registration

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Content-Length: 88
```

- Params

```json
{ }
```

- Body

```json
{
  "username" : "e2e_user_1793707110307",
  "password" : "<redacted>",
  "nickname" : "E2E-1793707110307"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 1003,
  "message" : "用户名已存在",
  "data" : null
}
```

## 5. POST /api/auth/login

- 描述：【Authentication Management】User login

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Content-Length: 57
```

- Params

```json
{ }
```

- Body

```json
{
  "username" : "e2e_user_1793707110307",
  "password" : "<redacted>"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "accessToken" : "<redacted>",
    "refreshToken" : "<redacted>",
    "tokenType" : "Bearer",
    "nickname" : "E2E-1793707110307",
    "avatar" : null,
    "isInitialPwd" : false
  }
}
```

## 6. GET /api/users/me

- 描述：【User Management】Get current user

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 3,
    "username" : "e2e_user_1793707110307",
    "nickname" : "E2E-1793707110307",
    "realName" : null,
    "email" : null,
    "phone" : null,
    "avatar" : null,
    "role" : "user",
    "languageMode" : "AUTO"
  }
}
```

## 7. GET /api/users/me

- 描述：【User Management】Get current user

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "username" : "liyc",
    "nickname" : "liyc",
    "realName" : null,
    "email" : null,
    "phone" : null,
    "avatar" : null,
    "role" : "admin",
    "languageMode" : "AUTO"
  }
}
```

## 8. GET /api/kb/listKnowledgeBases

- 描述：【Knowledge Base Management】List knowledge bases

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ ]
}
```

## 9. POST /api/kb/createKnowledgeBase

- 描述：【Knowledge Base Management】Create knowledge base

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 127
```

- Params

```json
{ }
```

- Body

```json
{
  "title" : "E2E 知识库",
  "description" : "用于 E2E",
  "color" : "blue",
  "icon" : "book",
  "allowAnonymous" : false,
  "visibility" : "PRIVATE"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "title" : "E2E 知识库",
    "description" : "用于 E2E",
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : null,
    "isPinned" : false,
    "isShared" : false,
    "lastModified" : "2026-11-03T19:58:30.437523+08:00",
    "allowAnonymous" : false,
    "visibility" : "private",
    "pinnedAt" : null,
    "sortOrder" : null,
    "createdAt" : "2026-11-03T19:58:30.437518+08:00",
    "role" : "OWNER"
  }
}
```

## 10. GET /api/kb/listKnowledgeBases

- 描述：【Knowledge Base Management】List knowledge bases

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "title" : "E2E 知识库",
    "description" : "用于 E2E",
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : null,
    "isPinned" : false,
    "isShared" : false,
    "lastModified" : "2026-11-03T19:58:30.437523+08:00",
    "allowAnonymous" : false,
    "visibility" : "private",
    "pinnedAt" : null,
    "sortOrder" : null,
    "createdAt" : "2026-11-03T19:58:30.437518+08:00",
    "role" : "OWNER"
  } ]
}
```

## 11. POST /api/kb/{kbId}/pin

- 描述：【Knowledge Base Management】Pin knowledge base

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 15
```

- Params

```json
{ }
```

- Body

```json
{
  "pinned" : true
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : true
}
```

## 12. POST /api/kb/reorder

- 描述：【Knowledge Base Management】Reorder knowledge bases

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 13
```

- Params

```json
{ }
```

- Body

```json
{
  "kbIds" : [ 1 ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 13. PUT /api/kb/{kbId}

- 描述：【Knowledge Base Management】Update knowledge base

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 57
```

- Params

```json
{ }
```

- Body

```json
{
  "title" : "E2E 知识库-已更新",
  "visibility" : "PUBLIC"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "title" : "E2E 知识库-已更新",
    "description" : null,
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : null,
    "isPinned" : false,
    "isShared" : false,
    "lastModified" : "2026-11-03T19:58:30.455375+08:00",
    "allowAnonymous" : false,
    "visibility" : "public",
    "pinnedAt" : null,
    "sortOrder" : null,
    "createdAt" : "2026-11-03T19:58:30.437518+08:00",
    "role" : "OWNER"
  }
}
```

## 14. POST /api/kb/{kbId}/members

- 描述：【Knowledge Base Member Management】Add member

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 34
```

- Params

```json
{ }
```

- Body

```json
{
  "username" : "bob",
  "role" : "editor"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "userId" : 2,
    "nickname" : "bob",
    "email" : null,
    "avatar" : null,
    "role" : "editor"
  }
}
```

## 15. GET /api/kb/listKnowledgeBases

- 描述：【Knowledge Base Management】List knowledge bases

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "title" : "E2E 知识库-已更新",
    "description" : null,
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : null,
    "isPinned" : false,
    "isShared" : true,
    "lastModified" : "2026-11-03T19:58:30.455375+08:00",
    "allowAnonymous" : false,
    "visibility" : "public",
    "pinnedAt" : null,
    "sortOrder" : null,
    "createdAt" : "2026-11-03T19:58:30.437518+08:00",
    "role" : "EDITOR"
  } ]
}
```

## 16. POST /api/kb/{kbId}/documents

- 描述：【Document Management】Create document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 70
```

- Params

```json
{ }
```

- Body

```json
{
  "name" : "文档A",
  "type" : "FILE",
  "content" : "Hello HelloDocSearch @bob"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "name" : "文档A",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 10000,
    "status" : "draft",
    "content" : "Hello HelloDocSearch @bob",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.470637+08:00",
    "updatedAt" : "2026-11-03T19:58:30.473212+08:00",
    "authorId" : 1
  }
}
```

## 17. POST /api/kb/{kbId}/documents

- 描述：【Document Management】Create document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 54
```

- Params

```json
{ }
```

- Body

```json
{
  "name" : "文档B",
  "type" : "FILE",
  "content" : "B content"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "name" : "文档B",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 0,
    "status" : "draft",
    "content" : "B content",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.477228+08:00",
    "updatedAt" : "2026-11-03T19:58:30.479049+08:00",
    "authorId" : 1
  }
}
```

## 18. POST /api/kb/{kbId}/documents

- 描述：【Document Management】Create document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 39
```

- Params

```json
{ }
```

- Body

```json
{
  "name" : "测试目录",
  "type" : "folder"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 3,
    "name" : "测试目录",
    "type" : "folder",
    "parentId" : null,
    "orderNum" : -10000,
    "status" : "draft",
    "content" : null,
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 0,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.482761+08:00",
    "updatedAt" : "2026-11-03T19:58:30.482764+08:00",
    "authorId" : 1
  }
}
```

## 19. POST /api/kb/{kbId}/documents

- 描述：【Document Management】Create document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 83
```

- Params

```json
{ }
```

- Body

```json
{
  "name" : "测试文档2",
  "type" : "file",
  "parentId" : 3,
  "content" : "测试文档2内容"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 4,
    "name" : "测试文档2",
    "type" : "file",
    "parentId" : 3,
    "orderNum" : 10000,
    "status" : "draft",
    "content" : "测试文档2内容",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.492307+08:00",
    "updatedAt" : "2026-11-03T19:58:30.493651+08:00",
    "authorId" : 1
  }
}
```

## 20. GET /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Get document details

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "name" : "文档A",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 10000,
    "status" : "draft",
    "content" : "Hello HelloDocSearch @bob",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 2,
    "createdAt" : "2026-11-03T19:58:30.470637+08:00",
    "updatedAt" : "2026-11-03T19:58:30.498397+08:00",
    "authorId" : 1
  }
}
```

## 21. PUT /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Update document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 84
```

- Params

```json
{ }
```

- Body

```json
{
  "content" : "参考[[文档B]]以及![[文档B]] HelloDocSearch",
  "expectedVersion" : 1
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "name" : "文档A",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 10000,
    "status" : "draft",
    "content" : "参考[[文档B]]以及![[文档B]] HelloDocSearch",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 2,
    "viewCount" : 2,
    "createdAt" : "2026-11-03T19:58:30.470637+08:00",
    "updatedAt" : "2026-11-03T19:58:30.513699+08:00",
    "authorId" : 1
  }
}
```

## 22. POST /api/kb/{kbId}/documents/{docId}/revisions

- 描述：【Document Management】Create revision

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 61
```

- Params

```json
{ }
```

- Body

```json
{
  "content" : "rev1 content",
  "message" : "m1",
  "expectedVersion" : 2
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 5,
    "version" : 3,
    "content" : "rev1 content",
    "authorName" : "liyc",
    "createdAt" : "2026-11-03T19:58:30.518638+08:00",
    "message" : "m1"
  }
}
```

## 23. POST /api/kb/{kbId}/documents/{docId}/revisions

- 描述：【Document Management】Create revision

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 61
```

- Params

```json
{ }
```

- Body

```json
{
  "content" : "rev2 content",
  "message" : "m2",
  "expectedVersion" : 3
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 6,
    "version" : 4,
    "content" : "rev2 content",
    "authorName" : "liyc",
    "createdAt" : "2026-11-03T19:58:30.525563+08:00",
    "message" : "m2"
  }
}
```

## 24. POST /api/docs/{docId}/revisions/{version}/restore?expectedVersion={expectedVersion}

- 描述：【Document Revisions】Restore revision

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "expectedVersion" : [ "4" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 25. GET /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Get document details

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "name" : "文档A",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 10000,
    "status" : "draft",
    "content" : "rev1 content",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 5,
    "viewCount" : 4,
    "createdAt" : "2026-11-03T19:58:30.470637+08:00",
    "updatedAt" : "2026-11-03T19:58:30.539202+08:00",
    "authorId" : 1
  }
}
```

## 26. GET /api/kb/{kbId}/documents/{docId}/revisions

- 描述：【Document Management】Get revision history

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "version" : 1,
    "content" : "Hello HelloDocSearch @bob",
    "authorName" : "liyc",
    "createdAt" : "2026-11-03T19:58:30.472075+08:00",
    "message" : "Auto save"
  }, {
    "id" : 4,
    "version" : 2,
    "content" : "参考[[文档B]]以及![[文档B]] HelloDocSearch",
    "authorName" : "liyc",
    "createdAt" : "2026-11-03T19:58:30.512897+08:00",
    "message" : "Auto save"
  }, {
    "id" : 5,
    "version" : 3,
    "content" : "rev1 content",
    "authorName" : "liyc",
    "createdAt" : "2026-11-03T19:58:30.518638+08:00",
    "message" : "m1"
  }, {
    "id" : 6,
    "version" : 4,
    "content" : "rev2 content",
    "authorName" : "liyc",
    "createdAt" : "2026-11-03T19:58:30.525563+08:00",
    "message" : "m2"
  }, {
    "id" : 7,
    "version" : 5,
    "content" : "rev1 content",
    "authorName" : "liyc",
    "createdAt" : "2026-11-03T19:58:30.533904+08:00",
    "message" : "Restored from version 3"
  } ]
}
```

## 27. GET /api/docs/{docId}/revisions?page={page}&size={size}

- 描述：【Document Revisions】Get version history

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "page" : [ "0" ],
  "size" : [ "10" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "content" : [ {
      "id" : 7,
      "version" : 5,
      "authorName" : "liyc",
      "authorId" : 1,
      "message" : "Restored from version 3",
      "type" : "restore",
      "createdAt" : "2026-11-03T19:58:30.533904+08:00",
      "wordCount" : 12
    }, {
      "id" : 6,
      "version" : 4,
      "authorName" : "liyc",
      "authorId" : 1,
      "message" : "m2",
      "type" : "manual",
      "createdAt" : "2026-11-03T19:58:30.525563+08:00",
      "wordCount" : 12
    }, {
      "id" : 5,
      "version" : 3,
      "authorName" : "liyc",
      "authorId" : 1,
      "message" : "m1",
      "type" : "manual",
      "createdAt" : "2026-11-03T19:58:30.518638+08:00",
      "wordCount" : 12
    }, {
      "id" : 4,
      "version" : 2,
      "authorName" : "liyc",
      "authorId" : 1,
      "message" : "Auto save",
      "type" : "auto",
      "createdAt" : "2026-11-03T19:58:30.512897+08:00",
      "wordCount" : 34
    }, {
      "id" : 1,
      "version" : 1,
      "authorName" : "liyc",
      "authorId" : 1,
      "message" : "Auto save",
      "type" : "auto",
      "createdAt" : "2026-11-03T19:58:30.472075+08:00",
      "wordCount" : 25
    } ],
    "page" : {
      "size" : 10,
      "number" : 0,
      "totalElements" : 5,
      "totalPages" : 1
    }
  }
}
```

## 28. GET /api/kb/{kbId}/documents

- 描述：【Document Management】List documents

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "name" : "文档A",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 10000,
    "status" : "draft",
    "content" : null,
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 5,
    "viewCount" : 4,
    "createdAt" : "2026-11-03T19:58:30.470637+08:00",
    "updatedAt" : "2026-11-03T19:58:30.539202+08:00",
    "authorId" : 1
  }, {
    "id" : 2,
    "name" : "文档B",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 0,
    "status" : "draft",
    "content" : null,
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.477228+08:00",
    "updatedAt" : "2026-11-03T19:58:30.479049+08:00",
    "authorId" : 1
  }, {
    "id" : 3,
    "name" : "测试目录",
    "type" : "folder",
    "parentId" : null,
    "orderNum" : -10000,
    "status" : "draft",
    "content" : null,
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 0,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.482761+08:00",
    "updatedAt" : "2026-11-03T19:58:30.482764+08:00",
    "authorId" : 1
  }, {
    "id" : 4,
    "name" : "测试文档2",
    "type" : "file",
    "parentId" : 3,
    "orderNum" : 10000,
    "status" : "draft",
    "content" : null,
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.492307+08:00",
    "updatedAt" : "2026-11-03T19:58:30.493651+08:00",
    "authorId" : 1
  } ]
}
```

## 29. PUT /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Update document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 27
```

- Params

```json
{ }
```

- Body

```json
{
  "parentId" : 3,
  "orderNum" : 7
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "name" : "文档B",
    "type" : "file",
    "parentId" : 3,
    "orderNum" : 7,
    "status" : "draft",
    "content" : null,
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.477228+08:00",
    "updatedAt" : "2026-11-03T19:58:30.565696+08:00",
    "authorId" : 1
  }
}
```

## 30. GET /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Get document details

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "name" : "文档B",
    "type" : "file",
    "parentId" : 3,
    "orderNum" : 7,
    "status" : "draft",
    "content" : "B content",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 2,
    "createdAt" : "2026-11-03T19:58:30.477228+08:00",
    "updatedAt" : "2026-11-03T19:58:30.569432+08:00",
    "authorId" : 1
  }
}
```

## 31. PUT /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Update document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 22
```

- Params

```json
{ }
```

- Body

```json
{
  "status" : "published"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "name" : "文档A",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 10000,
    "status" : "published",
    "content" : null,
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 5,
    "viewCount" : 4,
    "createdAt" : "2026-11-03T19:58:30.470637+08:00",
    "updatedAt" : "2026-11-03T19:58:30.574221+08:00",
    "authorId" : 1
  }
}
```

## 32. POST /api/kb/{kbId}/docs/{docId}/assets

- 描述：【Asset Management】Upload document asset

**请求参数格式**

- Headers

```text
Content-Type: multipart/form-data;charset=UTF-8
Authorization: Bearer <redacted>
```

- Params

```json
{
  "fileName" : [ "hello-doc.txt" ]
}
```

- Multipart

```json
{
  "files" : {
    "file" : {
      "originalFilename" : "hello-doc.txt",
      "contentType" : "text/plain",
      "size" : 15
    }
  },
  "params" : {
    "fileName" : [ "hello-doc.txt" ]
  }
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "kbId" : 1,
    "docId" : 1,
    "storageFileId" : 1,
    "uploaderId" : 1,
    "fileName" : "hello-doc.txt",
    "description" : null,
    "rawUrl" : "/api/assets/1/raw",
    "downloadUrl" : "/api/assets/1/download",
    "createdAt" : "2026-11-03T19:58:30.582982+08:00"
  }
}
```

## 33. GET /api/docs/{docId}/assets

- 描述：【Asset Management】Document asset list

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "kbId" : 1,
    "docId" : 1,
    "storageFileId" : 1,
    "uploaderId" : 1,
    "fileName" : "hello-doc.txt",
    "description" : null,
    "rawUrl" : "/api/assets/1/raw",
    "downloadUrl" : "/api/assets/1/download",
    "createdAt" : "2026-11-03T19:58:30.582982+08:00"
  } ]
}
```

## 34. GET /api/docs/recent?limit={limit}

- 描述：【Document Management】Recent documents

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "limit" : [ "10" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 2,
    "kbId" : 1,
    "kbTitle" : "E2E 知识库-已更新",
    "name" : "文档B",
    "type" : "FILE",
    "visitedAt" : "2026-11-03T19:58:30.56925+08:00"
  }, {
    "id" : 1,
    "kbId" : 1,
    "kbTitle" : "E2E 知识库-已更新",
    "name" : "文档A",
    "type" : "FILE",
    "visitedAt" : "2026-11-03T19:58:30.539023+08:00"
  } ]
}
```

## 35. GET /api/kb/{kbId}/search?limit={limit}&q={q}

- 描述：【Search】Search in KB

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "q" : [ "HelloDocSearch" ],
  "limit" : [ "10" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ ]
}
```

## 36. GET /api/search?limit={limit}&q={q}

- 描述：【Search】Global search

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "q" : [ "HelloDocSearch" ],
  "limit" : [ "10" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ ]
}
```

## 37. GET /api/docs/{docId}/links

- 描述：【Document Relations】Referenced documents

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 2,
    "kbId" : 1,
    "kbTitle" : "E2E 知识库-已更新",
    "name" : "文档B",
    "slug" : null,
    "type" : "file",
    "relationType" : "embed"
  } ]
}
```

## 38. GET /api/docs/{docId}/graph?depth={depth}

- 描述：【Document Relations】Relationship graph

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "depth" : [ "2" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "center" : {
      "id" : 1,
      "kbId" : 1,
      "kbTitle" : "E2E 知识库-已更新",
      "name" : "文档A",
      "slug" : null,
      "depth" : 0
    },
    "nodes" : [ {
      "id" : 1,
      "kbId" : 1,
      "kbTitle" : "E2E 知识库-已更新",
      "name" : "文档A",
      "slug" : null,
      "depth" : 0
    }, {
      "id" : 2,
      "kbId" : 1,
      "kbTitle" : "E2E 知识库-已更新",
      "name" : "文档B",
      "slug" : null,
      "depth" : 1
    } ],
    "edges" : [ {
      "sourceId" : 1,
      "targetId" : 2,
      "type" : "embed"
    } ]
  }
}
```

## 39. GET /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Get document details

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "name" : "文档A",
    "type" : "file",
    "parentId" : null,
    "orderNum" : 10000,
    "status" : "published",
    "content" : "rev1 content",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 5,
    "viewCount" : 6,
    "createdAt" : "2026-11-03T19:58:30.470637+08:00",
    "updatedAt" : "2026-11-03T19:58:30.637226+08:00",
    "authorId" : 1
  }
}
```

## 40. GET /api/docs/favorites

- 描述：【Favorite Management】List favorites

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ ]
}
```

## 41. POST /api/docs/{docId}/favorite

- 描述：【Favorite Management】Toggle favorite status

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 42. GET /api/docs/{docId}/favorite

- 描述：【Favorite Management】Check favorite status

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : true
}
```

## 43. DELETE /api/docs/{docId}/favorite

- 描述：【Favorite Management】Remove favorite

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 44. POST /api/docs/{docId}/share/link

- 描述：【Document Share Management】Create share link

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 17
```

- Params

```json
{ }
```

- Body

```json
{
  "role" : "viewer"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "targetType" : "link",
    "targetId" : null,
    "targetName" : "share.public_link",
    "targetAvatar" : "",
    "linkToken" : "6c259acee04c4075af7caffdfd3bd0a3",
    "role" : "viewer",
    "expiresAt" : null
  }
}
```

## 45. POST /api/docs/{docId}/share

- 描述：【Document Share Management】Add permission

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 54
```

- Params

```json
{ }
```

- Body

```json
{
  "targetType" : "user",
  "username" : "bob",
  "role" : "viewer"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "targetType" : "user",
    "targetId" : 2,
    "targetName" : "bob",
    "targetAvatar" : null,
    "linkToken" : null,
    "role" : "viewer",
    "expiresAt" : null
  }
}
```

## 46. POST /api/kb/{kbId}/documents

- 描述：【Document Management】Create document

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 56
```

- Params

```json
{ }
```

- Body

```json
{
  "name" : "Bob Doc",
  "type" : "FILE",
  "content" : "Bob content"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 5,
    "name" : "Bob Doc",
    "type" : "file",
    "parentId" : null,
    "orderNum" : -20000,
    "status" : "draft",
    "content" : "Bob content",
    "paperBgColor" : "",
    "paperBgImage" : "",
    "extraMeta" : { },
    "isOpen" : true,
    "isCover" : false,
    "isEncrypted" : false,
    "version" : 1,
    "viewCount" : 0,
    "createdAt" : "2026-11-03T19:58:30.659669+08:00",
    "updatedAt" : "2026-11-03T19:58:30.661972+08:00",
    "authorId" : 2
  }
}
```

## 47. POST /api/docs/{docId}/comments

- 描述：【Comment Management】Add comment

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 87
```

- Params

```json
{ }
```

- Body

```json
{
  "anchorType" : "POINT",
  "anchorData" : "{\"x\":1}",
  "anchorText" : "",
  "content" : "@bob 你好"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "docId" : 1,
    "userId" : 1,
    "user" : null,
    "parentId" : null,
    "anchorType" : "POINT",
    "anchorData" : "{\"x\":1}",
    "anchorText" : "",
    "content" : "@bob 你好",
    "isResolved" : false,
    "resolvedBy" : null,
    "resolvedAt" : null,
    "createdAt" : "2026-11-03T19:58:30.665451",
    "updatedAt" : "2026-11-03T19:58:30.665453"
  }
}
```

## 48. GET /api/docs/{docId}/comments

- 描述：【Comment Management】List document comments

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "docId" : 1,
    "userId" : 1,
    "user" : {
      "id" : 1,
      "nickname" : "liyc",
      "avatar" : null
    },
    "parentId" : null,
    "anchorType" : "POINT",
    "anchorData" : "{\"x\":1}",
    "anchorText" : "",
    "content" : "@bob 你好",
    "isResolved" : false,
    "resolvedBy" : null,
    "resolvedAt" : null,
    "createdAt" : "2026-11-03T19:58:30.665451",
    "updatedAt" : "2026-11-03T19:58:30.665453"
  } ]
}
```

## 49. GET /api/docs/{docId}/comments/unresolved-count

- 描述：【Comment Management】Get unresolved count

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : 1
}
```

## 50. GET /api/notifications/unread-count

- 描述：【Notification Management】Get unread count

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : 1
}
```

## 51. GET /api/notifications?page={page}&size={size}&unreadOnly={unreadOnly}

- 描述：【Notification Management】Get notification list

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "unreadOnly" : [ "true" ],
  "page" : [ "0" ],
  "size" : [ "10" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "content" : [ {
      "id" : 1,
      "docId" : 1,
      "userId" : 2,
      "senderId" : 1,
      "notifyType" : "MENTION",
      "refId" : 1,
      "content" : "liyc notify.mentioned_you_in《文档A》",
      "titleKey" : "notification.mention.title",
      "contentKey" : "notification.mention.content",
      "params" : {
        "senderName" : "liyc",
        "docName" : "文档A",
        "commentSnippet" : "@bob 你好"
      },
      "channel" : "IN_APP",
      "isRead" : false,
      "readAt" : null,
      "createdAt" : "2026-11-03T19:58:30.667054"
    } ],
    "page" : {
      "size" : 10,
      "number" : 0,
      "totalElements" : 1,
      "totalPages" : 1
    }
  }
}
```

## 52. POST /api/comments/{id}/reply

- 描述：【Comment Management】Reply to comment

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 26
```

- Params

```json
{ }
```

- Body

```json
{
  "content" : "收到 @liyc"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "docId" : 1,
    "userId" : 2,
    "user" : null,
    "parentId" : 1,
    "anchorType" : null,
    "anchorData" : null,
    "anchorText" : null,
    "content" : "收到 @liyc",
    "isResolved" : false,
    "resolvedBy" : null,
    "resolvedAt" : null,
    "createdAt" : "2026-11-03T19:58:30.67821",
    "updatedAt" : "2026-11-03T19:58:30.67821"
  }
}
```

## 53. GET /api/docs/{docId}/comments

- 描述：【Comment Management】List document comments

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "docId" : 1,
    "userId" : 1,
    "user" : {
      "id" : 1,
      "nickname" : "liyc",
      "avatar" : null
    },
    "parentId" : null,
    "anchorType" : "POINT",
    "anchorData" : "{\"x\":1}",
    "anchorText" : "",
    "content" : "@bob 你好",
    "isResolved" : false,
    "resolvedBy" : null,
    "resolvedAt" : null,
    "createdAt" : "2026-11-03T19:58:30.665451",
    "updatedAt" : "2026-11-03T19:58:30.665453"
  }, {
    "id" : 2,
    "docId" : 1,
    "userId" : 2,
    "user" : {
      "id" : 2,
      "nickname" : "bob",
      "avatar" : null
    },
    "parentId" : 1,
    "anchorType" : null,
    "anchorData" : null,
    "anchorText" : null,
    "content" : "收到 @liyc",
    "isResolved" : false,
    "resolvedBy" : null,
    "resolvedAt" : null,
    "createdAt" : "2026-11-03T19:58:30.67821",
    "updatedAt" : "2026-11-03T19:58:30.67821"
  } ]
}
```

## 54. POST /api/comments/{id}/resolve

- 描述：【Comment Management】Resolve comment

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "docId" : 1,
    "userId" : 1,
    "user" : {
      "id" : 1,
      "nickname" : "liyc",
      "avatar" : null
    },
    "parentId" : null,
    "anchorType" : "POINT",
    "anchorData" : "{\"x\":1}",
    "anchorText" : "",
    "content" : "@bob 你好",
    "isResolved" : true,
    "resolvedBy" : 1,
    "resolvedAt" : "2026-11-03T19:58:30.682516",
    "createdAt" : "2026-11-03T19:58:30.665451",
    "updatedAt" : "2026-11-03T19:58:30.682567"
  }
}
```

## 55. GET /api/docs/{docId}/comments/unresolved-count

- 描述：【Comment Management】Get unresolved count

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : 0
}
```

## 56. PUT /api/notifications/read-all

- 描述：【Notification Management】Mark all as read

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 57. GET /api/notifications/unread-count

- 描述：【Notification Management】Get unread count

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : 0
}
```

## 58. GET /api/kb/{kbId}/audit-logs?pageNum={pageNum}&pageSize={pageSize}

- 描述：【Audit Logs】Knowledge Base logs

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "pageNum" : [ "1" ],
  "pageSize" : [ "20" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "content" : [ {
      "id" : 2,
      "userId" : 1,
      "targetType" : "kb",
      "targetId" : 1,
      "action" : "update",
      "oldValue" : {
        "allowAnonymous" : false,
        "deletedAt" : null,
        "color" : "blue",
        "visibility" : "private",
        "icon" : "book",
        "description" : "用于 E2E",
        "id" : 1,
        "title" : "E2E 知识库",
        "ownerId" : 1,
        "publicRole" : null,
        "updatedAt" : 1.793707110437523E9
      },
      "newValue" : {
        "allowAnonymous" : false,
        "deletedAt" : null,
        "color" : "blue",
        "visibility" : "public",
        "icon" : "book",
        "description" : null,
        "id" : 1,
        "title" : "E2E 知识库-已更新",
        "ownerId" : 1,
        "publicRole" : null,
        "updatedAt" : 1.793707110455375E9
      },
      "ipAddress" : "127.0.0.1",
      "userAgent" : null,
      "createdAt" : "2026-11-03T19:58:30.455421+08:00"
    }, {
      "id" : 1,
      "userId" : 1,
      "targetType" : "kb",
      "targetId" : 1,
      "action" : "create",
      "oldValue" : null,
      "newValue" : {
        "allowAnonymous" : false,
        "deletedAt" : null,
        "color" : "blue",
        "visibility" : "private",
        "icon" : "book",
        "description" : "用于 E2E",
        "id" : 1,
        "title" : "E2E 知识库",
        "ownerId" : 1,
        "publicRole" : null,
        "updatedAt" : 1.793707110437523E9
      },
      "ipAddress" : "127.0.0.1",
      "userAgent" : null,
      "createdAt" : "2026-11-03T19:58:30.438146+08:00"
    } ],
    "page" : {
      "size" : 20,
      "number" : 0,
      "totalElements" : 2,
      "totalPages" : 1
    }
  }
}
```

## 59. GET /api/docs/{docId}/audit-logs?pageNum={pageNum}&pageSize={pageSize}

- 描述：【Audit Logs】Document logs

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{
  "pageNum" : [ "1" ],
  "pageSize" : [ "20" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "content" : [ {
      "id" : 11,
      "userId" : 1,
      "targetType" : "document",
      "targetId" : 1,
      "action" : "update",
      "oldValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 5,
        "status" : "draft",
        "updatedAt" : 1.793707110539202E9
      },
      "newValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 5,
        "status" : "published",
        "updatedAt" : 1.793707110573846E9
      },
      "ipAddress" : "127.0.0.1",
      "userAgent" : null,
      "createdAt" : "2026-11-03T19:58:30.573899+08:00"
    }, {
      "id" : 9,
      "userId" : 1,
      "targetType" : "document",
      "targetId" : 1,
      "action" : "update",
      "oldValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 3,
        "status" : "draft",
        "updatedAt" : 1.793707110519677E9
      },
      "newValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 4,
        "status" : "draft",
        "updatedAt" : 1.793707110526302E9
      },
      "ipAddress" : "127.0.0.1",
      "userAgent" : null,
      "createdAt" : "2026-11-03T19:58:30.526458+08:00"
    }, {
      "id" : 8,
      "userId" : 1,
      "targetType" : "document",
      "targetId" : 1,
      "action" : "update",
      "oldValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 2,
        "status" : "draft",
        "updatedAt" : 1.793707110513699E9
      },
      "newValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 3,
        "status" : "draft",
        "updatedAt" : 1.793707110519299E9
      },
      "ipAddress" : "127.0.0.1",
      "userAgent" : null,
      "createdAt" : "2026-11-03T19:58:30.519378+08:00"
    }, {
      "id" : 7,
      "userId" : 1,
      "targetType" : "document",
      "targetId" : 1,
      "action" : "update",
      "oldValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 1,
        "status" : "draft",
        "updatedAt" : 1.793707110498397E9
      },
      "newValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 2,
        "status" : "draft",
        "updatedAt" : 1.793707110512351E9
      },
      "ipAddress" : "127.0.0.1",
      "userAgent" : null,
      "createdAt" : "2026-11-03T19:58:30.513345+08:00"
    }, {
      "id" : 3,
      "userId" : 1,
      "targetType" : "document",
      "targetId" : 1,
      "action" : "create",
      "oldValue" : null,
      "newValue" : {
        "kbId" : 1,
        "deletedAt" : null,
        "isOpen" : true,
        "name" : "文档A",
        "id" : 1,
        "type" : "file",
        "isCover" : false,
        "parentId" : null,
        "slug" : null,
        "currentVersion" : 1,
        "status" : "draft",
        "updatedAt" : 1.793707110470641E9
      },
      "ipAddress" : "127.0.0.1",
      "userAgent" : null,
      "createdAt" : "2026-11-03T19:58:30.472883+08:00"
    } ],
    "page" : {
      "size" : 20,
      "number" : 0,
      "totalElements" : 5,
      "totalPages" : 1
    }
  }
}
```

## 60. POST /api/kb/{kbId}/assets

- 描述：【Asset Management】Upload knowledge base asset

**请求参数格式**

- Headers

```text
Content-Type: multipart/form-data;charset=UTF-8
Authorization: Bearer <redacted>
```

- Params

```json
{
  "fileName" : [ "hello.txt" ]
}
```

- Multipart

```json
{
  "files" : {
    "file" : {
      "originalFilename" : "hello.txt",
      "contentType" : "text/plain",
      "size" : 13
    }
  },
  "params" : {
    "fileName" : [ "hello.txt" ]
  }
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "kbId" : 1,
    "docId" : null,
    "storageFileId" : 2,
    "uploaderId" : 1,
    "fileName" : "hello.txt",
    "description" : null,
    "rawUrl" : "/api/assets/2/raw",
    "downloadUrl" : "/api/assets/2/download",
    "createdAt" : "2026-11-03T19:58:30.697212+08:00"
  }
}
```

## 61. GET /api/assets/{id}/url

- 描述：【Asset Management】Get asset access URL

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "url" : "/api/storage/public?key=%2F2026%2F11%2F03%2F7be8fba8-a4f7-47cb-b6ea-34f83b75ec0e.txt&exp=1793710710&sig=4d70a9e1aa1b0abf0be34216fa567ecf003d726ec51960db2e6fffb33ec3d4c3&filename=hello.txt"
  }
}
```

## 62. GET /api/storage/public?exp={exp}&key={key}&sig={sig}

- 描述：【Public Storage APIs】Get public resource

**请求参数格式**

- Headers

```text
```

- Params

```json
{
  "key" : [ "/2026/11/03/7be8fba8-a4f7-47cb-b6ea-34f83b75ec0e.txt" ],
  "exp" : [ "1793710710" ],
  "sig" : [ "4d70a9e1aa1b0abf0be34216fa567ecf003d726ec51960db2e6fffb33ec3d4c3" ]
}
```

**响应参数格式**

- HTTP 200
- Content-Type text/plain

```text
hello-storage
```

## 63. DELETE /api/assets/{id}

- 描述：【Asset Management】Delete asset

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 64. POST /api/system/dict/types

- 描述：【System Dictionary Types】Create dictionary type

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 60
```

- Params

```json
{ }
```

- Body

```json
{
  "dictCode" : "e2e_status",
  "dictName" : "E2E 状态",
  "status" : 0
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "dictCode" : "e2e_status",
    "dictName" : "E2E 状态",
    "description" : null,
    "isSystem" : null,
    "status" : null,
    "createTime" : "2026-11-03T11:58:30.709037Z",
    "updateTime" : "2026-11-03T11:58:30.709038Z"
  }
}
```

## 65. POST /api/system/dict/data

- 描述：【System Dictionary Data】Create dictionary data

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 108
```

- Params

```json
{ }
```

- Body

```json
{
  "dictTypeId" : 1,
  "label" : "正常",
  "value" : "0",
  "valueType" : "string",
  "sortOrder" : 1,
  "isDefault" : true,
  "status" : 0
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "dictCode" : "e2e_status",
    "label" : "正常",
    "labelI18n" : null,
    "value" : "0",
    "valueType" : "string",
    "cssClass" : null,
    "styleAttr" : null,
    "sortOrder" : 1,
    "isDefault" : true,
    "status" : 0,
    "remark" : null,
    "createTime" : "2026-11-03T11:58:30.712196Z",
    "updateTime" : "2026-11-03T11:58:30.712196Z"
  }
}
```

## 66. GET /api/public/dict/{dictCode}

- 描述：【Public APIs】Get dictionary items

**请求参数格式**

- Headers

```text
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "dictTypeId" : 1,
    "dictCode" : "e2e_status",
    "label" : "正常",
    "localizedLabel" : "正常",
    "labelI18n" : { },
    "value" : "0",
    "valueType" : "string",
    "cssClass" : null,
    "styleAttr" : null,
    "sortOrder" : 1,
    "isDefault" : true,
    "status" : 0,
    "remark" : null
  } ]
}
```

## 67. POST /api/system/configs

- 描述：【System Configuration】Create configuration

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 165
```

- Params

```json
{ }
```

- Body

```json
{
  "configKey" : "e2e.site.name",
  "configName" : "站点名",
  "configValue" : "HelloDoc",
  "valueType" : "string",
  "configGroup" : "e2e",
  "isFrontend" : true,
  "isSystem" : true,
  "status" : 0
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "configKey" : "e2e.site.name",
    "configName" : "站点名",
    "configValue" : "HelloDoc",
    "valueType" : "string",
    "configGroup" : "e2e",
    "isSystem" : true,
    "isFrontend" : true,
    "description" : null,
    "configNameI18n" : null,
    "descriptionI18n" : null
  }
}
```

## 68. GET /api/public/configs/frontend

- 描述：【Public APIs】Get frontend configurations

**请求参数格式**

- Headers

```text
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "e2e.site.name" : "HelloDoc",
    "collab.enabled" : "false"
  }
}
```

## 69. POST /api/system/roles

- 描述：【Role Management】Create role

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 72
```

- Params

```json
{ }
```

- Body

```json
{
  "roleCode" : "E2E_ROLE_1793707110718",
  "roleName" : "E2E 角色",
  "status" : 0
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 3,
    "roleCode" : "E2E_ROLE_1793707110718",
    "roleName" : "E2E 角色",
    "status" : 0,
    "createTime" : "2026-11-03T11:58:30.720286Z"
  }
}
```

## 70. POST /api/system/permissions

- 描述：【Permission Management】Create permission

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 61
```

- Params

```json
{ }
```

- Body

```json
{
  "permCode" : "e2e:perm:1793707110721",
  "permName" : "E2E 权限"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 1,
    "permCode" : "e2e:perm:1793707110721",
    "permName" : "E2E 权限",
    "createTime" : "2026-11-03T11:58:30.722730Z"
  }
}
```

## 71. POST /api/system/permissions/roles/{roleId}

- 描述：【Permission Management】Grant permission

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 12
```

- Params

```json
{ }
```

- Body

```json
{
  "permId" : 1
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 72. POST /api/system/roles/users/{userId}

- 描述：【Role Management】Assign role

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 12
```

- Params

```json
{ }
```

- Body

```json
{
  "roleId" : 3
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 73. GET /api/system/roles/users/{userId}

- 描述：【Role Management】User role list

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 2,
    "roleCode" : "user",
    "roleName" : "普通用户",
    "status" : 0,
    "createTime" : "2026-11-03T11:58:30.013761Z"
  }, {
    "id" : 3,
    "roleCode" : "E2E_ROLE_1793707110718",
    "roleName" : "E2E 角色",
    "status" : 0,
    "createTime" : "2026-11-03T11:58:30.720286Z"
  } ]
}
```

## 74. GET /api/system/permissions/users/{userId}

- 描述：【Permission Management】User permission list

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ "e2e:perm:1793707110721" ]
}
```

## 75. GET /api/system/storage/configs

- 描述：【Storage Configuration】List storage configurations

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "name" : "test-local",
    "provider" : "local",
    "bucket" : null,
    "region" : null,
    "endpoint" : null,
    "accessKeyId" : null,
    "cdnDomain" : null,
    "isDefault" : true,
    "isActive" : true,
    "hasSecret" : false,
    "createdAt" : "2026-11-03T19:58:30.012851+08:00",
    "updatedAt" : "2026-11-03T19:58:30.012851+08:00"
  } ]
}
```

## 76. POST /api/users/avatar

- 描述：【User Management】Upload and update avatar

**请求参数格式**

- Headers

```text
Content-Type: multipart/form-data;charset=UTF-8
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

- Multipart

```json
{
  "files" : {
    "file" : {
      "originalFilename" : "a.png",
      "contentType" : "image/png",
      "size" : 9
    }
  },
  "params" : { }
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "filename" : "avatar_5a567596215d452ab11b23c8459fdd46.png",
    "url" : "/api/files/avatars/avatar_5a567596215d452ab11b23c8459fdd46.png"
  }
}
```

## 77. GET /api/files/avatars/{filename}

- 描述：【File Upload】Get avatar

**请求参数格式**

- Headers

```text
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type image/png

```text
<binary 9 bytes>
```

## 78. POST /api/kb/createKnowledgeBase

- 描述：【Knowledge Base Management】Create knowledge base

**请求参数格式**

- Headers

```text
Content-Type: application/json;charset=UTF-8
Authorization: Bearer <redacted>
Content-Length: 132
```

- Params

```json
{ }
```

- Body

```json
{
  "title" : "待删除知识库",
  "description" : "用于 E2E",
  "color" : "blue",
  "icon" : "book",
  "allowAnonymous" : false,
  "visibility" : "PRIVATE"
}
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : {
    "id" : 2,
    "title" : "待删除知识库",
    "description" : "用于 E2E",
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : "/api/files/avatars/avatar_5a567596215d452ab11b23c8459fdd46.png",
    "isPinned" : false,
    "isShared" : false,
    "lastModified" : "2026-11-03T19:58:30.742639+08:00",
    "allowAnonymous" : false,
    "visibility" : "private",
    "pinnedAt" : null,
    "sortOrder" : null,
    "createdAt" : "2026-11-03T19:58:30.742638+08:00",
    "role" : "OWNER"
  }
}
```

## 79. DELETE /api/kb/{kbId}

- 描述：【Knowledge Base Management】Delete knowledge base

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 80. GET /api/kb/listKnowledgeBases

- 描述：【Knowledge Base Management】List knowledge bases

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "title" : "E2E 知识库-已更新",
    "description" : null,
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : "/api/files/avatars/avatar_5a567596215d452ab11b23c8459fdd46.png",
    "isPinned" : true,
    "isShared" : true,
    "lastModified" : "2026-11-03T19:58:30.455375+08:00",
    "allowAnonymous" : false,
    "visibility" : "public",
    "pinnedAt" : "2026-11-03T19:58:30.448284+08:00",
    "sortOrder" : 0,
    "createdAt" : "2026-11-03T19:58:30.437518+08:00",
    "role" : "OWNER"
  } ]
}
```

## 81. GET /api/kb/trash

- 描述：【Knowledge Base Management】Trash list

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 2,
    "title" : "待删除知识库",
    "description" : "用于 E2E",
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "",
    "ownerAvatar" : "",
    "isPinned" : false,
    "isShared" : false,
    "lastModified" : "2026-11-03T19:58:30.742639+08:00",
    "allowAnonymous" : false,
    "visibility" : "private",
    "pinnedAt" : null,
    "sortOrder" : null,
    "createdAt" : "2026-11-03T19:58:30.742638+08:00",
    "role" : "OWNER"
  } ]
}
```

## 82. POST /api/kb/{kbId}/restore

- 描述：【Knowledge Base Management】Restore knowledge base

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 83. GET /api/kb/listKnowledgeBases

- 描述：【Knowledge Base Management】List knowledge bases

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : [ {
    "id" : 1,
    "title" : "E2E 知识库-已更新",
    "description" : null,
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : "/api/files/avatars/avatar_5a567596215d452ab11b23c8459fdd46.png",
    "isPinned" : true,
    "isShared" : true,
    "lastModified" : "2026-11-03T19:58:30.455375+08:00",
    "allowAnonymous" : false,
    "visibility" : "public",
    "pinnedAt" : "2026-11-03T19:58:30.448284+08:00",
    "sortOrder" : 0,
    "createdAt" : "2026-11-03T19:58:30.437518+08:00",
    "role" : "OWNER"
  }, {
    "id" : 2,
    "title" : "待删除知识库",
    "description" : "用于 E2E",
    "icon" : "book",
    "color" : "blue",
    "ownerId" : 1,
    "ownerName" : "liyc",
    "ownerAvatar" : "/api/files/avatars/avatar_5a567596215d452ab11b23c8459fdd46.png",
    "isPinned" : false,
    "isShared" : false,
    "lastModified" : "2026-11-03T19:58:30.742639+08:00",
    "allowAnonymous" : false,
    "visibility" : "private",
    "pinnedAt" : null,
    "sortOrder" : null,
    "createdAt" : "2026-11-03T19:58:30.742638+08:00",
    "role" : "OWNER"
  } ]
}
```

## 84. DELETE /api/kb/{kbId}

- 描述：【Knowledge Base Management】Delete knowledge base

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 85. DELETE /api/kb/{kbId}/documents/{docId}

- 描述：【Document Management】Delete document

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

## 86. DELETE /api/kb/{kbId}

- 描述：【Knowledge Base Management】Delete knowledge base

**请求参数格式**

- Headers

```text
Authorization: Bearer <redacted>
```

- Params

```json
{ }
```

**响应参数格式**

- HTTP 200
- Content-Type application/json

```json
{
  "code" : 0,
  "message" : "成功",
  "data" : null
}
```

