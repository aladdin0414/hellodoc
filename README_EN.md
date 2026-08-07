# HelloDoc

<p align="center">
  <strong>Modern Full-Stack Knowledge Management & AI Collaboration Platform</strong>
</p>

<p align="center">
  <a href="README.md">简体中文</a> | <strong>English</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange.svg" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg" alt="Spring Boot 3" />
  <img src="https://img.shields.io/badge/Vue-3.x-4fc08d.svg" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Electron-31.x-47848F.svg" alt="Electron" />
  <a href="https://hub.docker.com/r/aladdin0414/hellodoc" target="_blank">
    <img src="https://img.shields.io/docker/pulls/aladdin0414/hellodoc.svg?logo=docker" alt="Docker Pulls" />
  </a>
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License MIT" />
</p>

---

## 📖 Overview

**HelloDoc** is a modern full-stack platform built for document collaboration, knowledge base management, and AI-assisted writing workflows.

Designed with a **Monorepo** architecture, HelloDoc seamlessly integrates a Vue 3 Web frontend, a high-performance Spring Boot 3 backend, cross-platform Electron desktop clients (macOS / Windows), and standard Docker Compose orchestrations for effortless self-hosting.

---

## ✨ Features

* 📚 **Knowledge Base & Document Management**: Multi-tier catalog organization, Rich Text and Markdown editing with instant auto-save.
* 🤖 **Native AI Co-Pilot**: Deep integration with OpenAI / LLM proxies for smart Q&A, auto-completion, text polishing, and summary generation.
* 🖥️ **Cross-Platform Experience**: Uniform experience across responsive Web browsers and native desktop clients.
* 🔒 **Data Ownership & Security**: Self-hosted database (PostgreSQL), JWT authentication, and zero third-party vendor lock-in.
* 🐳 **One-Command Docker Deployment**: Ready-to-use Docker images on Docker Hub for NAS & private VPS.

---

## 🛠️ Tech Stack

| Module | Core Tech | Description |
| :--- | :--- | :--- |
| **Web Frontend** (`hellodoc-client`) | Vue 3, TypeScript, Vite, Tailwind CSS, Vue Router, Vue-i18n | Fast and responsive Web application |
| **Backend API** (`hellodoc-server`) | Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, JWT | High-concurrency RESTful API service |
| **Desktop Client** (`hellodoc-desktop`) | Electron, Node.js, Electron Builder | Cross-platform desktop experience |
| **Containers & DevOps** (`deploy`) | Docker, Docker Compose, Bash | One-command orchestration scripts |

---

## 🚀 Quick Start (Docker Hub - Recommended)

Deploy HelloDoc in less than 2 minutes on your server or NAS without cloning the source code:

1. **Download the Compose Configuration**:
   ```bash
   curl -O https://raw.githubusercontent.com/aladdin0414/hellodoc/main/deploy/docker-compose.hub.yml
   mv docker-compose.hub.yml docker-compose.yml
   ```

2. **Configure Environment Variables (`.env` in the same directory)**:
   ```env
   POSTGRES_PASSWORD=your_secure_db_password
   JWT_SECRET=your_custom_random_long_secret_key_at_least_64_chars
   ADMIN_PASSWORD=your_admin_password
   OPENAI_API_KEY=your_openai_key_optional
   ```

3. **Start the Application**:
   ```bash
   docker compose up -d
   ```
   Open `http://<SERVER_IP>:8080` in your browser.

---

## 💻 Local Development

### Prerequisites
- **Node.js** >= 18.0.0
- **JDK** >= 17
- **PostgreSQL** >= 13

### Run Development Servers
```bash
git clone https://github.com/aladdin0414/hellodoc.git
cd hellodoc

# Copy env template
cp .env.example .env

# Install Node dependencies
npm install

# Start both frontend and backend concurrently
npm run dev
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
