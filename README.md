# HelloDoc

<p align="center">
  <strong>现代化全栈知识管理与 AI 协作平台</strong>
</p>

<p align="center">
  <strong>简体中文</strong> | <a href="README_EN.md">English</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange.svg" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg" alt="Spring Boot 3" />
  <img src="https://img.shields.io/badge/Vue-3.x-4fc08d.svg" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Electron-31.x-47848F.svg" alt="Electron" />
  <a href="https://hub.docker.com/r/aladdin0414/hellodoc" target="_blank">
    <img src="https://img.shields.io/docker/pulls/aladdin0414/hellodoc.svg?logo=docker" alt="Docker Image" />
  </a>
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License MIT" />
</p>

---

## 📖 项目简介

**HelloDoc** 是一套面向知识库管理、团队文档协作与 AI 交互场景的现代化全栈平台。
项目采用 **Monorepo** 架构，集成了 Web 前端、Spring Boot 高性能后端、Electron 桌面客户端以及基于 Docker Compose 的自动化容器部署方案，旨在为用户提供极致顺畅的知识管理与智能写作体验。

---

## ✨ 核心功能

* 📚 **文档与知识库管理**：支持多层级目录组织、富文本/Markdown 编辑与即时保存。
* 🤖 **AI 智能助手**：内置大模型深度集成（OpenAI API / 自定义 Proxy），提供智能问答、文档续写、润色与摘要生成。
* 🖥️ **多端无缝体验**：同时提供响应式 Web 前端与桌面客户端 (macOS / Windows)，随时随地高效办公。
* 🔐 **安全认证与权限**：基于 JWT 的无状态身份验证与高强度加密密码存储，保障团队数据安全。
* 🐳 **容器化一键部署**：提供标准的 Docker Compose 与环境一键同步脚本，轻松完成生产环境部署。

---

## 🛠️ 技术栈

| 模块 | 核心技术 | 描述 |
| :--- | :--- | :--- |
| **Web 前端** (`hellodoc-client`) | Vue 3, TypeScript, Vite, Tailwind CSS, Vue Router | 现代化轻量高效的前端界面 |
| **后端服务** (`hellodoc-server`) | Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, JWT | 高并发、稳定的 RESTful API 服务 |
| **桌面客户端** (`hellodoc-desktop`) | Electron, Node.js, Electron Builder | 跨平台桌面端极速体验 |
| **容器与部署** (`deploy`) | Docker, Docker Compose, Bash | 标准化服务编排与环境自动构建 |

---

## 📂 项目结构

```text
hellodoc/
├── hellodoc-client/    # Vue 3 + TypeScript Web 前端项目
├── hellodoc-server/    # Spring Boot 3 Java 后端 API 服务
├── hellodoc-desktop/   # Electron 桌面客户端项目
├── deploy/             # Docker Compose 部署配置与容器启动脚本
├── dev.sh              # 前后端本地一键开发启动脚本
├── build.sh            # 自动化全量构建与产物打包脚本
└── update-deploy.sh    # 部署包同步与自动更新脚本
```

---

## 🚀 快速开始

### 预备环境
- **Node.js** >= 18.0.0
- **JDK** >= 17
- **PostgreSQL** >= 13

> ⚠️ **重要**：启动后端服务前，请确保 PostgreSQL 服务已启动，并已创建名为 `hellodoc` 的数据库：
> ```sql
> CREATE DATABASE hellodoc;
> ```

### 1. 克隆项目与环境配置

```bash
git clone https://github.com/aladdin0414/hellodoc.git
cd hellodoc

# 1. 复制根目录环境变量模板并修改关键配置 (数据库密码、JWT私钥等)
cp .env.example .env

# 2. 创建 PostgreSQL 数据库 (如已手动创建可跳过)
psql -U postgres -c "CREATE DATABASE hellodoc;"

# 3. 安装根目录及子项目 Node 依赖
npm install
```

### 2. 本地开发运行

#### 方式 A：一键启动前后端服务 (推荐)
```bash
# 自动读取根目录 .env 并同时启动后端 (8080) 与 Web 前端 (3000)
npm run dev
# 或执行脚本
./dev.sh
```

#### 方式 B：单独分模块启动

* **启动后端服务 (`hellodoc-server`)**：
  ```bash
  npm run dev:server
  # 或直接在 hellodoc-server 目录下运行
  # cd hellodoc-server && ./gradlew bootRun
  ```

* **启动 Web 前端 (`hellodoc-client`)**：
  ```bash
  npm run dev:client
  # 或进入子目录运行
  # cd hellodoc-client && npm run dev
  ```

* **启动桌面客户端 (`hellodoc-desktop`)**：
  ```bash
  cd hellodoc-desktop
  npm install
  npm run dev
  ```

---

## 📦 项目打包与部署

### 1. 全局构建打包 (Web + 后端)
在根目录下直接执行构建命令，系统将自动编译 Web 前端静态资源，并将其打入 Java 后端 JAR 包中，打包产物生成至 `dist/` 目录：
```bash
npm run build
```

### 2. 桌面客户端打包 (Desktop Build)
若需打包构建各平台的桌面可执行程序（如 Windows 安装包/绿色版或 macOS 安装包）：
```bash
cd hellodoc-desktop
npm install

# 构建全平台桌面程序
npm run build

# 或仅构建 Windows 64位绿色便携版
npm run build:win:x64
```

### 3. Docker 部署说明

HelloDoc 官方镜像已构建完成并自动发布至 Docker Hub：[aladdin0414/hellodoc](https://hub.docker.com/r/aladdin0414/hellodoc)

#### 🚀 方式 A：基于 Docker Hub 镜像极速部署（推荐 NAS / 私有服务器用户，无需克隆源码）

只需下载 Compose 文件与配置环境变量，即可一键拉取镜像并启动：

1. **获取 Compose 配置文件**：
   ```bash
   # 下载项目预设的 Docker Hub 一键部署文件
   curl -O https://raw.githubusercontent.com/aladdin0414/hellodoc/main/deploy/docker-compose.hub.yml
   mv docker-compose.hub.yml docker-compose.yml
   ```

2. **创建环境变量文件 `.env`（同级目录下）**：
   ```env
   POSTGRES_PASSWORD=your_secure_db_password
   JWT_SECRET=your_custom_random_long_secret_key_at_least_64_chars
   ADMIN_PASSWORD=your_admin_password
   OPENAI_API_KEY=your_openai_key_optional
   ```

3. **启动容器集群**：
   ```bash
   docker compose up -d
   ```
   *服务启动后，在浏览器访问 `http://<服务器IP>:8080` 即可开始使用 HelloDoc。*

#### 🔨 方式 B：源码本地 Docker Compose 自动构建与部署

如果您拉取了本 GitHub 仓库源码并希望在本地进行容器构建：
```bash
# 确保根目录下已正确配置 .env 参数
npm run deploy
# 或直接运行部署脚本
./deploy/deploy.sh
```

---

## ⚙️ 环境变量说明

开发与部署均依托环境变量驱动，本地开发请维护根目录 `.env`。核心变量详见下表：

| 环境变量 | 是否必填 | 说明 | 默认值 / 推荐值 |
| :--- | :--- | :--- | :--- |
| `DB_URL` | **是** | 本地开发数据库连接地址 | `jdbc:postgresql://localhost:5432/hellodoc` |
| `DB_USERNAME` | **是** | 数据库用户名 | `postgres` |
| `DB_PASSWORD` | **是** | 数据库密码 | 请修改为实际强密码 |
| `JWT_SECRET` | **是** | JWT 鉴权签名密钥，要求至少 64 字节（512 位） | 必须自定义随机长字符串 |
| `ADMIN_PASSWORD` | **是** | 系统初始化创建管理员账号时的默认密码 | 必须自定义密码 |
| `OPENAI_API_KEY` | 否 | OpenAI API Key (开启 AI 文档/问答助手功能) | - |
| `OPENAI_BASE_URL` | 否 | OpenAI API Proxy/Base URL | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | 否 | 使用的大模型名称 | `gpt-4o` |
| `VITE_API_TARGET` | 否 | 前端开发服务代理的目标 API 地址 | `http://localhost:8080` |
| `VITE_WS_TARGET` | 否 | 前端开发服务代理的目标 WebSocket 地址 | `ws://localhost:8080` |

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 协议开源。
