#!/bin/bash

# =============================================================================
# HelloDoc Docker Hub 镜像构建与发布脚本
# 功能：运行打包脚本 + 自动检测/配置 Buildx 多架构构建 + 推送到 Docker Hub
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/hellodoc-server"
DIST_DIR="$SCRIPT_DIR/dist"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 1. 获取 Docker Hub 用户名
DOCKER_USER="$1"
if [ -z "$DOCKER_USER" ]; then
    # 尝试从 docker info 中获取登录用户名
    DOCKER_USER=$(docker info 2>/dev/null | grep -i "Username" | awk '{print $2}')
fi

if [ -z "$DOCKER_USER" ]; then
    log_error "未检测到 Docker Hub 用户名！"
    echo "使用方式: ./publish-docker.sh <your-dockerhub-username>"
    echo "或者先在终端登录: docker login"
    exit 1
fi

# 2. 获取当前项目版本号
if [ -f "$SERVER_DIR/build.gradle" ]; then
    VERSION=$(grep "^version =" "$SERVER_DIR/build.gradle" | sed "s/version = '\(.*\)'/\1/" | tr -d '\r')
fi
if [ -z "$VERSION" ]; then
    VERSION="2.0.8"
fi

log_info "目标 Docker Hub 账号: ${DOCKER_USER}"
log_info "发布版本号: ${VERSION}"

# 3. 检查 Docker 及登录状态
if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装或未启动"
    exit 1
fi

# 4. 执行本地编译打包
log_info "Step 1: 正在执行编译打包脚本 (build.sh)..."
bash "$SCRIPT_DIR/build.sh"

JAR_PATH="$DIST_DIR/hellodoc-${VERSION}/hellodoc-${VERSION}.jar"
if [ ! -f "$JAR_PATH" ]; then
    log_error "找不到打包好的 JAR 文件: $JAR_PATH"
    exit 1
fi

# 5. 配置 Docker Buildx 跨平台构建器
log_info "Step 2: 检查并准备 Docker Buildx 多架构构建环境..."
if ! docker buildx inspect hellodoc-builder &>/dev/null; then
    log_info "创建 Buildx 构建器: hellodoc-builder"
    docker buildx create --name hellodoc-builder --use --bootstrap
else
    docker buildx use hellodoc-builder
fi

# 6. 多架构构建并推送到 Docker Hub (linux/amd64, linux/arm64)
log_info "Step 3: 开始构建 linux/amd64 与 linux/arm64 镜像并推送到 Docker Hub..."
log_info "正在推送标签: ${DOCKER_USER}/hellodoc:${VERSION} 与 ${DOCKER_USER}/hellodoc:latest"

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f "$SCRIPT_DIR/deploy/Dockerfile" \
  --build-arg JAR_FILE="dist/hellodoc-${VERSION}/hellodoc-${VERSION}.jar" \
  -t "${DOCKER_USER}/hellodoc:${VERSION}" \
  -t "${DOCKER_USER}/hellodoc:latest" \
  "$SCRIPT_DIR" \
  --push

log_success "=================================================="
log_success "🎉 成功推送 Docker 镜像至 Docker Hub！"
log_success "镜像标签: ${DOCKER_USER}/hellodoc:${VERSION}"
log_success "镜像标签: ${DOCKER_USER}/hellodoc:latest"
log_success "=================================================="
