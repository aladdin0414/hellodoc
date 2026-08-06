# HelloDoc Client

`hellodoc-client` 是 HelloDoc 的 Web 前端，基于 Vue 3、TypeScript 与 Vite 构建，负责知识库、文档编辑、检索、移动端视图等交互能力。

## 开发命令

```bash
npm install
npm run dev
```

## 构建命令

```bash
npm run build
```

## 环境变量

复制环境模板并按需调整：

```bash
cp .env.example .env.development
```

关键变量：

| 变量名 | 说明 | 默认值 |
| :--- | :--- | :--- |
| `VITE_API_TARGET` | 本地开发时后端 API 地址 | `http://localhost:8080` |
| `VITE_WS_TARGET` | 本地开发时 WebSocket 地址 | `ws://localhost:8080` |

## 说明

- 本模块为 HelloDoc 的 Web 前端客户端。
- 生产构建产物会由根目录构建脚本同步到后端静态资源目录。
