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
    # 逐行解析，正确处理含有 :// 等特殊字符的值
    while IFS= read -r line; do
        # 跳过空行和注释行
        [[ -z "$line" || "$line" =~ ^\s*# ]] && continue
        # 去掉行首行尾空格
        line="${line#"${line%%[![:space:]]*}"}"
        line="${line%"${line##*[![:space:]]}"}"
        # 仅处理 KEY=VALUE 格式的行
        if [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then
            export "$line"
        fi
    done < "$ENV_FILE"
else
    echo -e "${YELLOW}⚠ 未在根目录找到 .env 文件，将尝试以环境默认值启动${NC}"
fi

# 2. 启动前清理旧进程（释放默认端口）
BACKEND_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT=3000

kill_port() {
    local port=$1
    local pids
    pids=$(lsof -ti tcp:"$port" 2>/dev/null)
    if [ -n "$pids" ]; then
        echo -e "${YELLOW}⚠ 端口 $port 被占用，正在终止旧进程 (PID: $pids)...${NC}"
        echo "$pids" | xargs kill -9 2>/dev/null
        sleep 0.5
        echo -e "${GREEN}✓ 端口 $port 已释放${NC}"
    fi
}

kill_port "$BACKEND_PORT"
kill_port "$FRONTEND_PORT"

# 3. 捕捉 Ctrl+C / SIGINT 信号，安全结束前后端子进程
cleanup() {
    echo -e "\n${YELLOW}⏹ 正在停止前后端开发服务...${NC}"
    kill $SERVER_PID $CLIENT_PID 2>/dev/null
    wait $SERVER_PID $CLIENT_PID 2>/dev/null
    echo -e "${GREEN}✓ 所有服务已安全退出${NC}"
    exit 0
}
trap cleanup SIGINT SIGTERM

# 4. 启动后端服务 (hellodoc-server)
echo -e "${GREEN}▶ 正在启动后端服务 (hellodoc-server)...${NC}"
(cd "$SERVER_DIR" && ./gradlew bootRun) &
SERVER_PID=$!

# 5. 启动前端服务 (hellodoc-client)
echo -e "${GREEN}▶ 正在启动前端服务 (hellodoc-client)...${NC}"
(cd "$CLIENT_DIR" && npm run dev) &
CLIENT_PID=$!

echo -e "${CYAN}-----------------------------------------------------${NC}"
echo -e "${CYAN}前端与后端服务均已启动。按 Ctrl+C 可停止所有服务。${NC}"
echo -e "${CYAN}-----------------------------------------------------${NC}"

# 保持脚本运行，等待前后端子进程
wait $SERVER_PID $CLIENT_PID
