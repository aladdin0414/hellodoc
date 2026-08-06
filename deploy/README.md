# HelloDoc 部署指南

本文档介绍 HelloDoc 项目的构建打包、容器化部署及 NAS 私有化部署流程。

---

## 📁 目录结构

`deploy/` 目录集成了 Docker 容器化与远程 NAS 部署的核心资源：

```
deploy/
├── Dockerfile              # Docker 镜像构建文件 (基于 JRE 17)
├── docker-compose.yml      # Docker 服务编排文件
├── deploy.sh               # NAS 远程一键部署脚本
├── backup-db.sh            # 数据库 Schema 导出/备份工具
└── .env.example            # 部署环境变量配置示例
```

---

## 🚀 快速开始

所有的部署与打包操作已统一收口至根目录的 `package.json` 命令中，无需手动记忆 Shell 路径。

### 1. 一键打包并部署到 NAS
适用于日常开发完毕后，一键构建并同步发布到 NAS Docker 环境：

```bash
npm run build:deploy
```
> **流程**：编译前端 Vue -> 编译后端 Spring Boot JAR -> SCP 上传至 NAS -> SSH 触发 Docker Compose 重新构建与启动。

### 2. 仅上传与部署（跳过打包）
如果本地已经打好了最新的 JAR 包，仅需重新上传并重启容器：

```bash
npm run deploy
```

### 3. 本地发版打包 (不部署)
仅进行前端 + 后端打统一发版包：

```bash
npm run build
```
> 构建产物输出在 `dist/` 目录下。

---

## ⚙️ 部署配置项 (`.env`)

若要修改 NAS 的 IP、端口、数据库连接或部署目录，无需修改 Shell 脚本。

只需在 `deploy/` 目录下（或项目根目录下）创建 `.env` 文件：

```bash
cp deploy/.env.example deploy/.env
```

`.env` 可配置参数说明：

| 环境变量 | 说明 | 默认值 |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | PostgreSQL 连接地址 | 必填 |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL 用户名 | 必填 |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL 密码 | 必填 |
| `JWT_SECRET` | JWT 签名密钥（至少 64 字节） | 必填 |
| `ADMIN_PASSWORD` | 初始管理员密码 | 必填 |
| `NAS_HOST` | NAS 设备的 IP 地址 | 必填 |
| `NAS_PORT` | NAS SSH 服务的端口号 | `22` |
| `NAS_USER` | NAS 登录用户名 | 必填 |
| `REMOTE_DIR` | NAS 远程部署与 Docker 执行目录 | `/volume1/docker/deploy-hellodoc` |

---

## 🐳 Docker / Docker Compose 服务配置

`deploy/docker-compose.yml` 包含了容器运行的基础配置：

- **对外端口**：映射宿主机 `8899` 端口到容器内 `8080` 端口。
- **数据持久化**：
  - `./data` -> `/app/data`（存储上传文件）
  - `./logs` -> `/app/logs`（存储运行日志）
- **环境变量**：可通过修改 `docker-compose.yml` 配置 Spring Boot 数据库连接字符串（如 PostgreSQL）。

---

## 🛠️ 运维与工具

### 1. 数据库 Schema 备份 (`backup-db.sh`)
用于将 NAS PostgreSQL 数据库中的最新 Schema 导出并清洗覆盖回本地开发环境：

```bash
# 语法: PGPASSWORD='<密码>' ./deploy/backup-db.sh [DB_NAME] [DB_HOST] [DB_PORT] [DB_USER]
PGPASSWORD='your_password' ./deploy/backup-db.sh hellodoc localhost 5432 postgres
```

### 2. NAS 容器运维常用命令
登录 NAS 命令行后进入 `REMOTE_DIR` 目录：

```bash
cd /volume1/docker/deploy-hellodoc

# 查看服务日志
docker compose logs -f

# 手动重启服务
docker compose restart

# 停止并清理容器
docker compose down
```
