#!/bin/bash

# 获取绝对路径基准
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source JAR path
if [ -f "$ROOT_DIR/hellodoc-server/build.gradle" ]; then
    VERSION=$(grep "^version =" "$ROOT_DIR/hellodoc-server/build.gradle" | sed "s/version = '\(.*\)'/\1/" | tr -d '\r')
fi

if [ -z "$VERSION" ]; then
    VERSION="2.0.0"
fi

# 加载部署配置文件（根目录 .env）
if [ -f "$ROOT_DIR/.env" ]; then
    set -a
    source "$ROOT_DIR/.env"
    set +a
elif [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
fi

# Remote Server / Host Configuration
DEPLOY_USER="${DEPLOY_USER:-}"
DEPLOY_HOST="${DEPLOY_HOST:-}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/opt/hellodoc}"

if [ -z "$DEPLOY_HOST" ] || [ -z "$DEPLOY_USER" ]; then
    echo "Error: DEPLOY_HOST and DEPLOY_USER environment variables are required."
    echo "Please configure them in your .env file or set them in your environment."
    exit 1
fi
REMOTE_JAR="hellodoc.jar"

SOURCE_DIR="$ROOT_DIR/dist/hellodoc-${VERSION}"
SOURCE_JAR="$SOURCE_DIR/hellodoc-${VERSION}.jar"

# 1. Check Source JAR
echo "Checking source JAR ($SOURCE_JAR)..."
if [ ! -f "$SOURCE_JAR" ]; then
     # Fallback: Find the latest jar under $ROOT_DIR/dist
     SOURCE_JAR=$(find "$ROOT_DIR/dist" -name "*.jar" -not -name "*-plain.jar" 2>/dev/null | xargs ls -t 2>/dev/null | head -n 1)
     if [ -z "$SOURCE_JAR" ]; then
        echo "Error: Source JAR not found in $ROOT_DIR/dist."
        echo "💡 Suggestion: Please run 'npm run build' or 'npm run build:deploy' first."
        exit 1
     fi
     echo "Found latest fallback JAR: $SOURCE_JAR"
fi

# 2. 确保远程服务器部署目录存在
echo "Ensuring remote directory exists ($REMOTE_DIR)..."
ssh -p "$DEPLOY_PORT" "$DEPLOY_USER@$DEPLOY_HOST" "mkdir -p '$REMOTE_DIR'" || { echo "Error: Failed to connect via SSH or create remote directory '$REMOTE_DIR'. Please check DEPLOY_HOST, DEPLOY_USER and SSH credentials/permissions."; exit 1; }

# 3. Copy JAR file and Deployment configs via SCP
echo "Copying JAR file and deployment configurations to $DEPLOY_USER@$DEPLOY_HOST:$REMOTE_DIR..."
scp -P "$DEPLOY_PORT" "$SOURCE_JAR" "$DEPLOY_USER@$DEPLOY_HOST:$REMOTE_DIR/$REMOTE_JAR" || { echo "Error: Failed to copy JAR file via SCP."; exit 1; }

if [ -f "$SCRIPT_DIR/docker-compose.yml" ]; then
    scp -P "$DEPLOY_PORT" "$SCRIPT_DIR/docker-compose.yml" "$DEPLOY_USER@$DEPLOY_HOST:$REMOTE_DIR/docker-compose.yml" || { echo "Error: Failed to copy docker-compose.yml via SCP."; exit 1; }
fi
if [ -f "$SCRIPT_DIR/Dockerfile" ]; then
    scp -P "$DEPLOY_PORT" "$SCRIPT_DIR/Dockerfile" "$DEPLOY_USER@$DEPLOY_HOST:$REMOTE_DIR/Dockerfile" || { echo "Error: Failed to copy Dockerfile via SCP."; exit 1; }
fi
if [ -f "$ROOT_DIR/.env" ]; then
    scp -P "$DEPLOY_PORT" "$ROOT_DIR/.env" "$DEPLOY_USER@$DEPLOY_HOST:$REMOTE_DIR/.env" || { echo "Error: Failed to copy .env via SCP."; exit 1; }
elif [ -f "$SCRIPT_DIR/.env" ]; then
    scp -P "$DEPLOY_PORT" "$SCRIPT_DIR/.env" "$DEPLOY_USER@$DEPLOY_HOST:$REMOTE_DIR/.env" || { echo "Error: Failed to copy .env via SCP."; exit 1; }
else
    echo "Notice: No local .env file found to sync. The remote deployment will rely on existing remote .env."
fi

echo "Files copied successfully."

# 3. Execute remote commands via SSH
echo "Executing remote deployment commands..."
# Use -tt to force TTY allocation for sudo password prompt
ssh -tt -p "$DEPLOY_PORT" "$DEPLOY_USER@$DEPLOY_HOST" "
    # Add common binary paths
    export PATH=\$PATH:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/var/packages/Docker/target/usr/bin

    cd $REMOTE_DIR || exit 1
    
    # Auto-detect if sudo is needed (try 'docker info' without sudo first)
    if docker info &> /dev/null; then
        SUDO_PREFIX=''
    else
        SUDO_PREFIX='sudo'
    fi

    # Check for docker-compose or docker compose
    if command -v docker-compose &> /dev/null; then
        DOCKER_COMPOSE_CMD=\"\$SUDO_PREFIX docker-compose\"
    elif docker compose version &> /dev/null; then
        DOCKER_COMPOSE_CMD=\"\$SUDO_PREFIX docker compose\"
    else
        # Try direct path fallback
        if [ -f /usr/local/bin/docker-compose ]; then
            DOCKER_COMPOSE_CMD=\"\$SUDO_PREFIX /usr/local/bin/docker-compose\"
        else
            echo 'Error: docker-compose not found.'
            exit 1
        fi
    fi

    echo \"Using command: \$DOCKER_COMPOSE_CMD\"
    \$DOCKER_COMPOSE_CMD up -d --build --remove-orphans
"
DEPLOY_EXIT_CODE=$?

if [ $DEPLOY_EXIT_CODE -eq 0 ]; then
    echo "----------------------------------------"
    echo "✅ Deployment successful!"
    echo "----------------------------------------"
else
    echo "----------------------------------------"
    echo "❌ Error: Remote deployment failed (Exit Code: $DEPLOY_EXIT_CODE)."
    echo "If sudo timed out, please ensure you are ready to input the remote user password."
    echo "----------------------------------------"
    exit $DEPLOY_EXIT_CODE
fi
