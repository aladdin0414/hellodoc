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

> ⚠️ **重要**：启动后端服务前，请确保 PostgreSQL 服务已启动，并已手动创建名为 `hellodoc` 的数据库：
> ```sql
> CREATE DATABASE hellodoc;
> ```

### 1. 本地开发

#### (1) 克隆项目与环境配置
```bash
git clone <your-repo-url>
cd <your-repo-dir>

# 1. 复制根目录环境变量模板并修改关键配置 (数据库密码、JWT私钥等)
cp .env.example .env

# 2. 在 PostgreSQL 中创建数据库
psql -U postgres -c "CREATE DATABASE hellodoc;"

# 3. 安装根目录 Node 依赖
npm install
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

# 通过环境变量配置数据库连接信息（如未修改根目录 .env，bootRun 会读取默认环境变量）
export DB_URL=jdbc:postgresql://localhost:5432/hellodoc
export DB_USERNAME=postgres
export DB_PASSWORD=<your-db-password>
export JWT_SECRET=<your-64-byte-secret>
export ADMIN_PASSWORD=<your-admin-password>

# 编译并运行
./gradlew bootRun
```

#### (4) 启动前端服务 (`hellodoc-client`)
```bash
cd hellodoc-client

npm install
npm run dev
```

#### (5) 启动桌面端 (`hellodoc-desktop`)
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
确保项目根目录下已配置好 `.env` 文件（可参考 `.env.example`）：
```bash
# 复制部署模板并填写实际参数
cp .env.example .env

# 运行容器 (deploy/deploy.sh 会自动读取并拷贝根目录 .env)
npm run deploy
```

---

## ⚙️ 环境变量说明

本地开发时建议使用根目录 `.env`；容器部署时请使用 `deploy/.env`。以下为核心变量说明：

| 环境变量 | 是否必填 | 说明 | 默认值 / 推荐值 |
| :--- | :--- | :--- | :--- |
| `DB_URL` | **是** | 本地开发数据库连接地址（确保数据库已事先创建） | `jdbc:postgresql://localhost:5432/hellodoc` |
| `DB_USERNAME` | **是** | 本地开发数据库用户名 | `postgres` |
| `DB_PASSWORD` | **是** | 数据库密码 | 请修改为实际密码 |
| `JWT_SECRET` | **是** | JWT 鉴权签名密钥，要求至少 64 字节（512 位） | 必须自定义随机长字符串 |
| `ADMIN_PASSWORD` | **是** | 系统初始化创建管理员账号时的默认密码 | 必须自定义密码 |
| `OPENAI_API_KEY` | 否 | OpenAI API Key (开启 AI 文档/问答助手功能) | - |
| `OPENAI_BASE_URL` | 否 | OpenAI API Proxy/Base URL | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | 否 | 使用的大模型名称 | `gpt-4o` |

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 协议开源。
