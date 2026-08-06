# HelloDoc 部署指南

本文档介绍 HelloDoc 项目的构建打包、容器化部署及 NAS 私有化部署流程。

---

## 📁 目录结构

`deploy/` 目录集成了 Docker 容器化与远程 NAS 部署的核心资源：

```text
deploy/
├── Dockerfile              # Docker 镜像构建文件 (基于 JRE 17)
├── docker-compose.yml      # Docker 服务编排文件
├── deploy.sh               # NAS 远程一键部署与容器构建脚本
├── backup-db.sh            # 数据库 Schema 导出/备份工具
└── README.md               # 部署与运维使用说明文档
```

---

## 🚀 快速开始

所有的部署与打包操作已统一收口至根目录的 `package.json` 命令中，无需手动记忆 Shell 脚本路径。

### 1. 一键打包并部署到 NAS (推荐)
适用于日常开发完毕后，一键编译并同步发布到 NAS 远程 Docker 环境：

```bash
npm run build:deploy
```
> **自动执行流程**：编译前端 Vue 静态资源 ➔ 打包后端 Spring Boot JAR 包 ➔ SCP 增量上传至 NAS ➔ SSH 触发 Docker Compose 重新构建与启动容器。

### 2. 仅上传与部署（跳过编译）
如果本地已经打好了最新的 JAR 包，仅需将当前产物与配置增量同步至 NAS 并重启服务：

```bash
npm run deploy
```

### 3. 本地发版打包 (不部署)
仅进行前端 + 后端打统一发版包：

```bash
npm run build
```
> 构建产物输出在项目根目录 `dist/` 目录下。

### 4. 增量同步部署文件
若修改了部署配置或部署脚本，需要快速推送到 NAS 远程目录：

```bash
npm run sync
```

---

## ⚙️ 部署配置项 (`.env`)

修改 NAS 的 IP、端口、数据库连接或部署目录时，**无需修改 Shell 脚本**。

只需在**项目根目录**下创建并维护 `.env` 文件（可参考根目录 `.env.example`）：

```bash
cp .env.example .env
```

`.env` 部署核心环境变量说明：

| 环境变量 | 说明 | 默认值 / 推荐值 |
| :--- | :--- | :--- |
| `DB_URL` / `SPRING_DATASOURCE_URL` | PostgreSQL 数据库连接地址 | `jdbc:postgresql://localhost:5432/hellodoc` |
| `DB_USERNAME` | PostgreSQL 用户名 | `postgres` |
| `DB_PASSWORD` | PostgreSQL 密码 | 必须修改为实际强密码 |
| `JWT_SECRET` | JWT 签名密钥（至少 64 字节） | 必须自定义随机长字符串 |
| `ADMIN_PASSWORD` | 系统初始管理员密码 | 必须自定义密码 |
| `NAS_HOST` | NAS 设备的 IP 地址或域名 | 必填 |
| `NAS_PORT` | NAS SSH 服务的端口号 | `22` |
| `NAS_USER` | NAS SSH 登录用户名 | 必填 |
| `REMOTE_DIR` | NAS 远程部署与 Docker 执行目录 | `/volume1/docker/hellodoc` |

---

## 🐳 Docker / Docker Compose 服务配置

`deploy/docker-compose.yml` 包含了容器运行的基础配置：

- **对外端口**：默认映射宿主机 `8899` 端口到容器内 `8080` 端口（可通过 `docker-compose.yml` 灵活修改）。
- **数据持久化**：
  - `./data` ➔ `/app/data`（存储用户上传的文件与附件）
  - `./logs` ➔ `/app/logs`（存储后端运行日志）
- **自动检测与适配**：部署脚本 `deploy.sh` 会自动适配 Synology / QNAP 等 NAS 环境中的 `docker-compose` 与 `docker compose` 命令及 `sudo` 权限。

---

## 🛠️ 运维与工具

### 1. 数据库 Schema 备份与导出 (`backup-db.sh`)
用于将 NAS 或生产环境 PostgreSQL 数据库中的最新 Schema 导出并备份：

```bash
# 语法: PGPASSWORD='<密码>' ./deploy/backup-db.sh [DB_NAME] [DB_HOST] [DB_PORT] [DB_USER]
PGPASSWORD='your_password' ./deploy/backup-db.sh hellodoc localhost 5432 postgres
```

### 2. NAS 容器运维常用命令
登录 NAS 命令行终端后，进入 `REMOTE_DIR` 目录执行运维操作：

```bash
cd /volume1/docker/hellodoc

# 查看运行日志（实时跟随）
docker compose logs -f

# 查看容器运行状态
docker compose ps

# 手动重启服务
docker compose restart

# 停止并清理容器
docker compose down
```

