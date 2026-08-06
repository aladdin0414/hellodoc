#!/bin/bash

# =============================================================================
# HelloDoc 一键拉取代码、构建打包并部署至 NAS
# 流程：git pull -> npm run build -> npm run deploy
# =============================================================================

set -e

# 颜色输出
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}==============================================${NC}"
echo -e "${BLUE}   HelloDoc 一键更新 + 打包 + NAS 部署工具     ${NC}"
echo -e "${BLUE}==============================================${NC}\n"

# 1. 拉取最新 Git 代码
echo -e "${BLUE}[步骤 1/3] 正在从远程仓库拉取最新代码 (git pull)...${NC}"
git pull
echo -e "${GREEN}✅ Git 代码更新完成！${NC}\n"

# 2. 本地项目构建打包
echo -e "${BLUE}[步骤 2/3] 正在编译前端并打包后端 JAR (npm run build)...${NC}"
npm run build
echo -e "${GREEN}✅ 本地打包构建完成！${NC}\n"

# 3. 上传并部署至 NAS
echo -e "${BLUE}[步骤 3/3] 正在传输 JAR 产物并部署至 NAS (npm run deploy)...${NC}"
npm run deploy

echo -e "\n${GREEN}🚀 =====================================${NC}"
echo -e "${GREEN}🚀 所有任务完成：更新、打包与 NAS 部署成功！${NC}"
echo -e "${GREEN}🚀 =====================================${NC}"
