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

前端已接入根目录全栈统一配置，开发时请在**项目根目录**的 `.env` 文件中按需调整（模板可参考根目录 `.env.example`）：

```bash
# 项目根目录下
cp .env.example .env
```

关键前端配置项：

| 变量名 | 说明 | 默认值 |
| :--- | :--- | :--- |
| `VITE_API_TARGET` | 本地开发时后端 API 地址 | `http://localhost:8080` |
| `VITE_WS_TARGET` | 本地开发时 WebSocket 地址 | `ws://localhost:8080` |

## 说明

- 本模块为 HelloDoc 的 Web 前端客户端。
- Vite 已配置 `envDir` 读取项目根目录 `.env`。
- 生产构建产物会由根目录构建脚本同步到后端静态资源目录。

