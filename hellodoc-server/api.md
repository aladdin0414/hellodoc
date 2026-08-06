# API 参考文档

> 生成时间: 2026-01-13T21:50:26.242022200-08:00

**Title**: HelloDoc API

**Version**: 1.0

**Description**: 基于 Spring Boot 3 + JPA + Spring Security 实现

## 更新用户

**URL**: `/api/users/{id}`

**Method**: `PUT`

**Description**: 更新用户信息

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Request Body

**Content-Type**: `application/json`

Schema: `SysUser`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| userPosts | array |  |
| userDepts | array |  |
| tenantId | string |  |
| deptGuid | string |  |
| userName | string |  |
| nickName | string |  |
| userType | string |  |
| email | string |  |
| phonenumber | string |  |
| sex | string |  |
| avatar | string |  |
| password | string |  |
| loginFailureCount | integer |  |
| lockTime | string |  |
| pwdUpdateTime | string |  |
| isInitialPwd | string |  |
| status | string |  |
| delFlag | string |  |
| loginIp | string |  |
| loginDate | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| orderNum | integer |  |
| remark | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysUser`
    - code: integer
    - message: string
    - data: `SysUser`

---

## 删除用户

**URL**: `/api/users/{id}`

**Method**: `DELETE`

**Description**: 删除指定用户

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 

---

## 重置密码

**URL**: `/api/users/{id}/reset-pwd`

**Method**: `PUT`

**Description**: 重置用户密码

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Request Body

**Content-Type**: `application/json`

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 

---

## /api/roles/{id}

**URL**: `/api/roles/{id}`

**Method**: `GET`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysRole`
    - code: integer
    - message: string
    - data: `SysRole`

---

## /api/roles/{id}

**URL**: `/api/roles/{id}`

**Method**: `PUT`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Request Body

**Content-Type**: `application/json`

Schema: `SysRole`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| tenantId | string |  |
| roleName | string |  |
| roleKey | string |  |
| roleSort | integer |  |
| dataScope | string |  |
| menuCheckStrictly | boolean |  |
| deptCheckStrictly | boolean |  |
| status | string |  |
| delFlag | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| remark | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysRole`
    - code: integer
    - message: string
    - data: `SysRole`

---

## /api/roles/{id}

**URL**: `/api/roles/{id}`

**Method**: `DELETE`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 

---

## /api/posts/{id}

**URL**: `/api/posts/{id}`

**Method**: `GET`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysPost`
    - code: integer
    - message: string
    - data: `SysPost`

---

## /api/posts/{id}

**URL**: `/api/posts/{id}`

**Method**: `PUT`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Request Body

**Content-Type**: `application/json`

Schema: `SysPost`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| tenantId | string |  |
| postCode | string |  |
| postName | string |  |
| postSort | integer |  |
| status | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| remark | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysPost`
    - code: integer
    - message: string
    - data: `SysPost`

---

## /api/posts/{id}

**URL**: `/api/posts/{id}`

**Method**: `DELETE`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 

---

## /api/menus/{id}

**URL**: `/api/menus/{id}`

**Method**: `GET`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysMenu`
    - code: integer
    - message: string
    - data: `SysMenu`

---

## /api/menus/{id}

**URL**: `/api/menus/{id}`

**Method**: `PUT`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Request Body

**Content-Type**: `application/json`

Schema: `SysMenu`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| menuName | string |  |
| parentId | integer |  |
| orderNum | integer |  |
| path | string |  |
| component | string |  |
| query | string |  |
| isFrame | integer |  |
| isCache | integer |  |
| menuType | string |  |
| visible | string |  |
| status | string |  |
| perms | string |  |
| icon | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| remark | string |  |
| children | array |  |
| roleIds | array |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysMenu`
    - code: integer
    - message: string
    - data: `SysMenu`

---

## /api/menus/{id}

**URL**: `/api/menus/{id}`

**Method**: `DELETE`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 

---

## /api/depts/{id}

**URL**: `/api/depts/{id}`

**Method**: `GET`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysDept`
    - code: integer
    - message: string
    - data: `SysDept`

---

## /api/depts/{id}

**URL**: `/api/depts/{id}`

**Method**: `PUT`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | string |  |

### Request Body

**Content-Type**: `application/json`

Schema: `SysDept`


| Property | Type | Description |
| --- | --- | --- |
| deptGuid | string |  |
| tenantId | string |  |
| parentGuid | string |  |
| ancestors | string |  |
| deptName | string |  |
| orderNum | integer |  |
| leader | string |  |
| phone | string |  |
| email | string |  |
| status | string |  |
| delFlag | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| children | array |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysDept`
    - code: integer
    - message: string
    - data: `SysDept`

---

## /api/depts/{id}

**URL**: `/api/depts/{id}`

**Method**: `DELETE`

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| id | path | true | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 

---

## 获取用户列表

**URL**: `/api/users`

**Method**: `GET`

**Description**: 获取所有用户列表

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| user | query | true |  |  |
| pageNum | query | false | integer |  |
| pageSize | query | false | integer |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponsePageSysUser`
    - code: integer
    - message: string
    - data: `PageSysUser`

---

## 创建用户

**URL**: `/api/users`

**Method**: `POST`

**Description**: 创建新用户

### Request Body

**Content-Type**: `application/json`

Schema: `SysUser`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| userPosts | array |  |
| userDepts | array |  |
| tenantId | string |  |
| deptGuid | string |  |
| userName | string |  |
| nickName | string |  |
| userType | string |  |
| email | string |  |
| phonenumber | string |  |
| sex | string |  |
| avatar | string |  |
| password | string |  |
| loginFailureCount | integer |  |
| lockTime | string |  |
| pwdUpdateTime | string |  |
| isInitialPwd | string |  |
| status | string |  |
| delFlag | string |  |
| loginIp | string |  |
| loginDate | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| orderNum | integer |  |
| remark | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysUser`
    - code: integer
    - message: string
    - data: `SysUser`

---

## 同步用户数据

**URL**: `/api/tool/sync-users`

**Method**: `POST`

**Description**: 从SQL Server备份文件同步用户数据到sys_user表

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseString`
    - code: integer
    - message: string
    - data: string

---

## 同步部门数据

**URL**: `/api/tool/sync-depts`

**Method**: `POST`

**Description**: 从SQL Server备份文件同步部门数据到sys_dept表

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseString`
    - code: integer
    - message: string
    - data: string

---

## /api/roles

**URL**: `/api/roles`

**Method**: `GET`

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseListSysRole`
    - code: integer
    - message: string
    - data: array of `SysRole`

---

## /api/roles

**URL**: `/api/roles`

**Method**: `POST`

### Request Body

**Content-Type**: `application/json`

Schema: `SysRole`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| tenantId | string |  |
| roleName | string |  |
| roleKey | string |  |
| roleSort | integer |  |
| dataScope | string |  |
| menuCheckStrictly | boolean |  |
| deptCheckStrictly | boolean |  |
| status | string |  |
| delFlag | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| remark | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysRole`
    - code: integer
    - message: string
    - data: `SysRole`

---

## /api/posts

**URL**: `/api/posts`

**Method**: `GET`

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseListSysPost`
    - code: integer
    - message: string
    - data: array of `SysPost`

---

## /api/posts

**URL**: `/api/posts`

**Method**: `POST`

### Request Body

**Content-Type**: `application/json`

Schema: `SysPost`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| tenantId | string |  |
| postCode | string |  |
| postName | string |  |
| postSort | integer |  |
| status | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| remark | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysPost`
    - code: integer
    - message: string
    - data: `SysPost`

---

## /api/menus

**URL**: `/api/menus`

**Method**: `GET`

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseListSysMenu`
    - code: integer
    - message: string
    - data: array of `SysMenu`

---

## /api/menus

**URL**: `/api/menus`

**Method**: `POST`

### Request Body

**Content-Type**: `application/json`

Schema: `SysMenu`


| Property | Type | Description |
| --- | --- | --- |
| id | integer |  |
| menuName | string |  |
| parentId | integer |  |
| orderNum | integer |  |
| path | string |  |
| component | string |  |
| query | string |  |
| isFrame | integer |  |
| isCache | integer |  |
| menuType | string |  |
| visible | string |  |
| status | string |  |
| perms | string |  |
| icon | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| remark | string |  |
| children | array |  |
| roleIds | array |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysMenu`
    - code: integer
    - message: string
    - data: `SysMenu`

---

## /api/depts

**URL**: `/api/depts`

**Method**: `GET`

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseListSysDept`
    - code: integer
    - message: string
    - data: array of `SysDept`

---

## /api/depts

**URL**: `/api/depts`

**Method**: `POST`

### Request Body

**Content-Type**: `application/json`

Schema: `SysDept`


| Property | Type | Description |
| --- | --- | --- |
| deptGuid | string |  |
| tenantId | string |  |
| parentGuid | string |  |
| ancestors | string |  |
| deptName | string |  |
| orderNum | integer |  |
| leader | string |  |
| phone | string |  |
| email | string |  |
| status | string |  |
| delFlag | string |  |
| createBy | string |  |
| createTime | string |  |
| updateBy | string |  |
| updateTime | string |  |
| children | array |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysDept`
    - code: integer
    - message: string
    - data: `SysDept`

---

## 用户注册

**URL**: `/api/auth/register`

**Method**: `POST`

**Description**: 注册新用户

### Request Body

**Content-Type**: `application/json`

Schema: `RegisterRequest`


| Property | Type | Description |
| --- | --- | --- |
| username | string |  |
| password | string |  |
| nickname | string |  |
| email | string |  |
| phonenumber | string |  |
| sex | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseSysUser`
    - code: integer
    - message: string
    - data: `SysUser`

---

## 刷新 Token

**URL**: `/api/auth/refresh-token`

**Method**: `POST`

**Description**: 通过 Refresh Token 获取新的 Access Token 和 Refresh Token

### Request Body

**Content-Type**: `application/json`

Schema: `RefreshTokenRequest`


| Property | Type | Description |
| --- | --- | --- |
| refreshToken | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseAuthResponse`
    - code: integer
    - message: string
    - data: `AuthResponse`

---

## 用户登录

**URL**: `/api/auth/login`

**Method**: `POST`

**Description**: 通过用户名和密码登录，返回 AccessToken 和 RefreshToken

### Request Body

**Content-Type**: `application/json`

Schema: `LoginRequest`


| Property | Type | Description |
| --- | --- | --- |
| username | string |  |
| password | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseAuthResponse`
    - code: integer
    - message: string
    - data: `AuthResponse`

---

## 获取用户详情

**URL**: `/api/users/getUserInfo/{username}`

**Method**: `GET`

**Description**: 根据 ID 获取用户详情

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| username | path | true | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseUserInfo`
    - code: integer
    - message: string
    - data: `UserInfo`

---

## 获取部门用户列表

**URL**: `/api/users/dept/{deptGuid}`

**Method**: `GET`

**Description**: 根据部门ID获取用户列表（不分页）

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| deptGuid | path | true | string |  |

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseListSysUser`
    - code: integer
    - message: string
    - data: array of `SysUser`

---

## 匿名访问测试

**URL**: `/api/test/hello`

**Method**: `GET`

**Description**: 不需要登录即可访问

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseString`
    - code: integer
    - message: string
    - data: string

---

## /api/menus/routers

**URL**: `/api/menus/routers`

**Method**: `GET`

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseListSysMenu`
    - code: integer
    - message: string
    - data: array of `SysMenu`

---

## /api/depts/tree

**URL**: `/api/depts/tree`

**Method**: `GET`

### Responses

- **404**: Not Found
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **403**: Forbidden
  - Schema: `ApiResponseVoid`
    - code: integer
    - message: string
    - data: 
- **200**: OK
  - Schema: `ApiResponseListSysDept`
    - code: integer
    - message: string
    - data: array of `SysDept`

---

