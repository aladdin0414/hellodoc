#!/usr/bin/env bash

# =============================================================================
# HelloDoc 一键本地开发启动脚本 (dev.sh)
# 自动加载根目录 .env 环境变量，同时启动后端 (hellodoc-server) 和前端 (hellodoc-client)
# 按 Ctrl+C 可一键停止前后端所有进程
# =============================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$ROOT_DIR/hellodoc-server"
CLIENT_DIR="$ROOT_DIR/hellodoc-client"
ENV_FILE="$ROOT_DIR/.env"

# 颜色控制
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}=====================================================${NC}"
echo -e "${CYAN}        🚀 HelloDoc 一键本地开发环境启动             ${NC}"
echo -e "${CYAN}=====================================================${NC}"

# 1. 检查并加载根目录 .env 环境变量
if [ -f "$ENV_FILE" ]; then
    echo -e "${GREEN}✓ 已加载本地环境变量: .env${NC}"
    set -a
    # 忽略注释行与空行，安全导出环境变量
    source <(grep -v '^\s*#' "$ENV_FILE" | grep -v '^\s*$')
    set +a
else
    echo -e "${YELLOW}⚠ 未在根目录找到 .env 文件，将尝试以环境默认值启动${NC}"
fi

# 2. 捕捉 Ctrl+C / SIGINT 信号，安全结束前后端子进程
cleanup() {
    echo -e "\n${YELLOW}⏹ 正在停止前后端开发服务...${NC}"
    kill $SERVER_PID $CLIENT_PID 2>/dev/null
    wait $SERVER_PID $CLIENT_PID 2>/dev/null
    echo -e "${GREEN}✓ 所有服务已安全退出${NC}"
    exit 0
}
trap cleanup SIGINT SIGTERM

# 3. 启动后端服务 (hellodoc-server)
echo -e "${GREEN}▶ 正在启动后端服务 (hellodoc-server)...${NC}"
(cd "$SERVER_DIR" && ./gradlew bootRun) &
SERVER_PID=$!

# 4. 启动前端服务 (hellodoc-client)
echo -e "${GREEN}▶ 正在启动前端服务 (hellodoc-client)...${NC}"
(cd "$CLIENT_DIR" && npm run dev) &
CLIENT_PID=$!

echo -e "${CYAN}-----------------------------------------------------${NC}"
echo -e "${CYAN}前端与后端服务均已启动。按 Ctrl+C 可停止所有服务。${NC}"
echo -e "${CYAN}-----------------------------------------------------${NC}"

# 保持脚本运行，等待前后端子进程
wait $SERVER_PID $CLIENT_PID
