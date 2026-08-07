# HelloDoc

<p align="center">
  <strong>Modern Full-Stack Knowledge Management & AI Collaboration Platform</strong>
</p>

<p align="center">
  <strong>English</strong> | <a href="README_ZH.md">简体中文</a>
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

## 📖 Overview

**HelloDoc** is a modern full-stack platform built for document collaboration, knowledge base management, and AI-assisted writing workflows.
Designed with a **Monorepo** architecture, HelloDoc seamlessly integrates a Vue 3 Web frontend, a high-performance Spring Boot 3 backend, cross-platform Electron desktop clients (macOS / Windows), and standard Docker Compose orchestrations for effortless self-hosting.

---

## ✨ Key Features

* 📚 **Knowledge Base & Document Management**: Multi-tier catalog organization, Rich Text and Markdown editing with instant auto-save.
* 🤖 **Native AI Co-Pilot**: Deep integration with LLMs (OpenAI API / custom Proxy) for smart Q&A, auto-completion, text polishing, and summary generation.
* 🖥️ **Cross-Platform Experience**: Uniform experience across responsive Web browsers and native desktop clients (macOS / Windows).
* 🔐 **Security & Access Control**: JWT-based stateless authentication and high-strength password encryption ensuring team data security.
* 🐳 **One-Command Docker Deployment**: Ready-to-use Docker images on Docker Hub for NAS & private VPS.

---

## 🛠️ Tech Stack

| Module | Core Technologies | Description |
| :--- | :--- | :--- |
| **Web Frontend** (`hellodoc-client`) | Vue 3, TypeScript, Vite, Tailwind CSS, Vue Router | Modern, fast, and responsive Web application |
| **Backend API** (`hellodoc-server`) | Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, JWT | High-concurrency, robust RESTful API service |
| **Desktop Client** (`hellodoc-desktop`) | Electron, Node.js, Electron Builder | Cross-platform desktop experience |
| **Containers & DevOps** (`deploy`) | Docker, Docker Compose, Bash | One-command container orchestration scripts |

---

## 📂 Project Structure

```text
hellodoc/
├── hellodoc-client/    # Vue 3 + TypeScript Web Frontend
├── hellodoc-server/    # Spring Boot 3 Java Backend API Service
├── hellodoc-desktop/   # Electron Desktop Client
├── deploy/             # Docker Compose configurations and deployment scripts
├── dev.sh              # One-command local development startup script
├── build.sh            # Automated full build & packaging script
└── update-deploy.sh    # Deployment sync and auto-update script
```

---

## 🚀 Quick Start

### Prerequisites
- **Node.js** >= 18.0.0
- **JDK** >= 17
- **PostgreSQL** >= 13

> ⚠️ **Important**: Ensure PostgreSQL service is running and create the `hellodoc` database before starting the backend:
> ```sql
> CREATE DATABASE hellodoc;
> ```

### 1. Clone & Environment Setup

```bash
git clone https://github.com/aladdin0414/hellodoc.git
cd hellodoc

# 1. Copy environment variable template and set configuration
cp .env.example .env

# 2. Create PostgreSQL database (skip if already created)
psql -U postgres -c "CREATE DATABASE hellodoc;"

# 3. Install Node.js dependencies
npm install
```

### 2. Local Development

#### Option A: One-Command Startup (Recommended)
```bash
# Reads root .env and launches both backend (8080) and frontend (3000)
npm run dev
# Or execute script
./dev.sh
```

#### Option B: Launch Modules Separately

* **Backend Service (`hellodoc-server`)**:
  ```bash
  npm run dev:server
  # Or run directly in hellodoc-server
  # cd hellodoc-server && ./gradlew bootRun
  ```

* **Web Frontend (`hellodoc-client`)**:
  ```bash
  npm run dev:client
  # Or run in directory
  # cd hellodoc-client && npm run dev
  ```

* **Desktop Client (`hellodoc-desktop`)**:
  ```bash
  cd hellodoc-desktop
  npm install
  npm run dev
  ```

---

## 📦 Build & Deployment

### 1. Full Build (Web + Backend)
Compile Web static assets and bundle them into the Java backend JAR file (outputs to `dist/`):
```bash
npm run build
```

### 2. Desktop Client Build
Build executable installers (Windows installer / Portable or macOS):
```bash
cd hellodoc-desktop
npm install

# Build desktop apps for configured platforms
npm run build

# Or build Windows 64-bit portable version only
npm run build:win:x64
```

### 3. Docker Deployment

Official Docker images are available on Docker Hub: [aladdin0414/hellodoc](https://hub.docker.com/r/aladdin0414/hellodoc)

#### 🚀 Option A: Docker Hub Instant Deployment (Recommended for NAS / VPS)

Deploy without cloning the repository source code:

1. **Download Compose Configuration**:
   ```bash
   curl -O https://raw.githubusercontent.com/aladdin0414/hellodoc/main/deploy/docker-compose.hub.yml
   mv docker-compose.hub.yml docker-compose.yml
   ```

2. **Create `.env` file in the same directory**:
   ```env
   POSTGRES_PASSWORD=your_secure_db_password
   JWT_SECRET=your_custom_random_long_secret_key_at_least_64_chars
   ADMIN_PASSWORD=your_admin_password
   OPENAI_API_KEY=your_openai_key_optional
   ```

3. **Start Containers**:
   ```bash
   docker compose up -d
   ```
   *Access `http://<SERVER_IP>:8080` in your browser.*

#### 🔨 Option B: Build & Deploy from Source with Docker Compose

If you cloned the source code:
```bash
npm run deploy
# Or run deployment script directly
./deploy/deploy.sh
```

---

## ⚙️ Environment Variables

| Variable | Required | Description | Default / Recommended |
| :--- | :--- | :--- | :--- |
| `DB_URL` | **Yes** | Database JDBC connection URL | `jdbc:postgresql://localhost:5432/hellodoc` |
| `DB_USERNAME` | **Yes** | Database username | `postgres` |
| `DB_PASSWORD` | **Yes** | Database password | Secure custom password |
| `JWT_SECRET` | **Yes** | Secret key for JWT signing (at least 64 bytes) | Custom random long string |
| `ADMIN_PASSWORD` | **Yes** | Default password for initial admin user | Custom password |
| `OPENAI_API_KEY` | No | OpenAI API Key (Enables AI Assistant) | - |
| `OPENAI_BASE_URL` | No | OpenAI API Proxy/Base URL | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | No | LLM Model Name | `gpt-4o` |
| `VITE_API_TARGET` | No | Dev proxy target API URL for Web client | `http://localhost:8080` |
| `VITE_WS_TARGET` | No | Dev proxy target WebSocket URL for Web client | `ws://localhost:8080` |

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
