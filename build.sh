#!/bin/bash

# =============================================================================
# ${PRODUCT_DISPLAY_NAME} 统一打包脚本
# 功能：构建前端 + 后端，生成可独立运行的 Executable JAR 包
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENT_DIR="$SCRIPT_DIR/hellodoc-client"
SERVER_DIR="$SCRIPT_DIR/hellodoc-server"
DIST_DIR="$SCRIPT_DIR/dist"

# 版本号处理
if [ -n "$1" ]; then
    VERSION="$1"
else
    if [ -f "$SERVER_DIR/build.gradle" ]; then
        VERSION=$(grep "^version =" "$SERVER_DIR/build.gradle" | sed "s/version = '\(.*\)'/\1/" | tr -d '\r')
    fi
    if [ -z "$VERSION" ]; then
        VERSION="2.0.0"
    fi
fi
PRODUCT_NAME="hellodoc"
PRODUCT_DISPLAY_NAME="HelloDoc"
BUILD_TIME=$(date '+%Y-%m-%d %H:%M:%S')
BUILD_TIMESTAMP=$(date '+%Y%m%d%H%M%S')

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    if ! command -v node &> /dev/null; then
        log_error "Node.js 未安装"
        exit 1
    fi
    if ! command -v npm &> /dev/null; then
        log_error "npm 未安装"
        exit 1
    fi
    log_success "依赖检查通过: Node $(node -v), npm $(npm -v)"
}

# 清理
clean() {
    log_info "清理旧构建文件..."
    rm -rf "$DIST_DIR"
    rm -rf "$SERVER_DIR/src/main/resources/static/"*
    log_success "清理完成"
}

# 构建前端
build_frontend() {
    log_info "构建前端项目..."
    cd "$CLIENT_DIR"
    if [ ! -d "node_modules" ]; then
        log_info "安装前端依赖..."
        npm install
    fi
    npm run build
    log_success "前端构建完成，输出目录: $SERVER_DIR/src/main/resources/static"
}

# 构建后端
build_backend() {
    log_info "构建后端项目..."
    cd "$SERVER_DIR"
    ./gradlew bootJar --no-daemon -Pversion="$VERSION"
    log_success "后端构建完成"
}

# 生成构建元数据信息
generate_build_info() {
    local output_dir="$1"
    local info_file="$output_dir/build-info.txt"
    
    log_info "生成构建元数据..."
    
    BUILD_END_TIME=$(date '+%Y-%m-%d %H:%M:%S')
    BUILD_END_SECONDS=$(date +%s)
    BUILD_DURATION=$((BUILD_END_SECONDS - BUILD_START_SECONDS))
    
    GIT_COMMIT="N/A"
    GIT_BRANCH="N/A"
    GIT_LAST_COMMIT_MSG="N/A"
    if command -v git &> /dev/null && git rev-parse --git-dir &> /dev/null; then
        GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "N/A")
        GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "N/A")
        GIT_LAST_COMMIT_MSG=$(git log -1 --pretty=format:"%s" 2>/dev/null || echo "N/A")
    fi
    
    JAR_PATH="$output_dir/${PRODUCT_NAME}-${VERSION}.jar"
    JAR_SHA256="N/A"
    JAR_SIZE="N/A"
    if [ -f "$JAR_PATH" ]; then
        JAR_SHA256=$(shasum -a 256 "$JAR_PATH" 2>/dev/null | awk '{print $1}' || echo "N/A")
        JAR_SIZE=$(ls -lh "$JAR_PATH" | awk '{print $5}')
    fi
    
    cat > "$info_file" << EOF
================================================================================
                           ${PRODUCT_DISPLAY_NAME} 构建信息
================================================================================
产品名称:       ${PRODUCT_NAME}
版本号:         ${VERSION}
构建时间:       ${BUILD_TIME} -> ${BUILD_END_TIME} (${BUILD_DURATION}s)
Git 分支/提交:  ${GIT_BRANCH} / ${GIT_COMMIT}
最近提交:       ${GIT_LAST_COMMIT_MSG}

JAR 文件:       ${PRODUCT_NAME}-${VERSION}.jar (${JAR_SIZE})
SHA256:         ${JAR_SHA256}
构建主机/用户:  $(hostname) / $(whoami)
================================================================================
EOF

    log_success "构建元数据已生成: $info_file"
}

# 收集构建产物
collect_artifacts() {
    log_info "收集构建产物..."
    
    OUTPUT_DIR="$DIST_DIR/${PRODUCT_NAME}-${VERSION}"
    rm -rf "$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"
    
    local SEARCH_RESULT=$(find "$SERVER_DIR/build/libs" -name "*.jar" -not -name "*-plain.jar" -type f | head -1)
    
    if [ -n "$SEARCH_RESULT" ]; then
        JAR_NAME="${PRODUCT_NAME}-${VERSION}.jar"
        cp "$SEARCH_RESULT" "$OUTPUT_DIR/$JAR_NAME"
        local JAR_SIZE_INFO=$(ls -lh "$SEARCH_RESULT" | awk '{print $5}')
        log_success "JAR 包已复制到: $OUTPUT_DIR/$JAR_NAME (大小: ${JAR_SIZE_INFO})"
    else
        log_error "未找到 JAR 文件，请检查构建日志"
        exit 1
    fi
    
    generate_build_info "$OUTPUT_DIR"
}

# 显示部署说明
show_deploy_instructions() {
    echo ""
    echo "=============================================="
    echo -e "${GREEN}打包完成！${NC}"
    echo "=============================================="
    echo ""
    echo "产品版本: ${VERSION}"
    echo "输出目录: $DIST_DIR/${PRODUCT_NAME}-${VERSION}/"
    echo ""
    echo "运行 JAR 包："
    echo "   java -jar $DIST_DIR/${PRODUCT_NAME}-${VERSION}/${PRODUCT_NAME}-${VERSION}.jar"
    echo ""
}

main() {
    BUILD_START_SECONDS=$(date +%s)
    
    echo ""
    echo "=============================================="
    echo "       ${PRODUCT_DISPLAY_NAME} 统一打包脚本"
    echo "=============================================="
    echo ""
    
    check_dependencies
    clean
    build_frontend
    build_backend
    collect_artifacts
    show_deploy_instructions
}

main "$@"
