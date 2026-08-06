# HelloDoc

<p align="center">
  <strong>现代化全栈知识管理与 AI 协作平台</strong>
</p>

---

## 📖 项目简介

**HelloDoc** 是一套面向知识库、文档协作与 AI 交互场景的全栈平台。项目采用 Monorepo 结构，包含 Web 前端、Spring Boot 后端以及 Electron 桌面客户端，并提供容器化部署方案。

### 🛠️ 技术栈

* **Web 前端** (`hellodoc-client`): Vue 3, TypeScript, Vite, Tailwind CSS, Vue Router
* **后端服务** (`hellodoc-server`): Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, JWT
* **桌面客户端** (`hellodoc-desktop`): Electron, Node.js
* **部署目录** (`deploy`): Docker Compose 与远程部署脚本

---

## 🚀 快速开始

### 预备环境
- Node.js >= 18.0.0
- JDK >= 17
- PostgreSQL >= 13

### 1. 本地开发

#### (1) 克隆项目与环境配置
```bash
git clone <your-repo-url>
cd <your-repo-dir>

# 根目录环境变量用于本地开发 / 构建脚本
cp .env.example .env
```

#### (2) 一键启动前后端服务 (推荐)
```bash
# 读取根目录 .env 文件并一键同时启动后端 (hellodoc-server) 与前端 (hellodoc-client)
./dev.sh
# 或者
npm run dev
```

#### (3) 分别单独启动后端服务 (`hellodoc-server`)
```bash
cd hellodoc-server

# 通过环境变量配置数据库连接信息
export DB_URL=jdbc:postgresql://localhost:5432/hellodoc
export DB_USERNAME=postgres
export DB_PASSWORD=<your-db-password>
export JWT_SECRET=<your-64-byte-secret>
export ADMIN_PASSWORD=<your-admin-password>

# 编译并运行
./gradlew bootRun
```

#### (3) 启动前端服务 (`hellodoc-client`)
```bash
cd hellodoc-client

npm install
npm run dev
```

#### (4) 启动桌面端 (`hellodoc-desktop`)
```bash
cd hellodoc-desktop

npm install
npm run dev
```

---

## 📦 项目打包与部署

### 1. 全局构建打包
在根目录下直接执行构建脚本，脚本将自动编译前端并打包生成可执行 JAR 包至 `dist/` 目录：
```bash
npm run build
```

### 2. Docker Compose 部署
请先在 `deploy/` 目录下准备专用环境变量文件：
```bash
cd deploy

# 复制部署模板并填写数据库 / JWT / 管理员密码
cp .env.example .env

# 运行容器
docker compose up -d --build
```

---

## ⚙️ 环境变量说明

本地开发时建议使用根目录 `.env`；容器部署时请使用 `deploy/.env`。以下为核心变量说明：

| 环境变量 | 说明 | 默认值 |
| :--- | :--- | :--- |
| `DB_URL` | 本地开发数据库连接地址 | `jdbc:postgresql://localhost:5432/hellodoc` |
| `DB_USERNAME` | 本地开发数据库用户名 | `postgres` |
| `DB_PASSWORD` | 数据库密码 | 必填 |
| `JWT_SECRET` | JWT 鉴权签名密钥，至少 64 字节 | 必填 |
| `ADMIN_PASSWORD` | 初始管理员密码 | 必填 |
| `OPENAI_API_KEY` | OpenAI API Key (如开启 AI 功能) | - |
| `OPENAI_BASE_URL` | OpenAI API Proxy/Base URL | `https://api.openai.com/v1` |

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 协议开源。
