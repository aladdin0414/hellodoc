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

# 加载部署配置文件（优先 deploy/.env，其次项目根目录 .env）
if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
elif [ -f "$ROOT_DIR/.env" ]; then
    set -a
    source "$ROOT_DIR/.env"
    set +a
fi

# Remote NAS Configuration
NAS_USER="${NAS_USER:-}"
NAS_HOST="${NAS_HOST:-}"
NAS_PORT="${NAS_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/volume1/docker/deploy-hellodoc}"

if [ -z "$NAS_HOST" ] || [ -z "$NAS_USER" ]; then
    echo "Error: NAS_HOST and NAS_USER environment variables are required."
    echo "Please configure them in deploy/.env or set them in your environment."
    exit 1
fi
REMOTE_JAR="hellodoc.jar"

SOURCE_DIR="$ROOT_DIR/dist/hellodoc-${VERSION}"
SOURCE_JAR="$SOURCE_DIR/hellodoc-${VERSION}.jar"

# 1. Check Source JAR
echo "Checking source JAR ($SOURCE_JAR)..."
if [ ! -f "$SOURCE_JAR" ]; then
     # Fallback: Find the latest jar under $ROOT_DIR/dist
     SOURCE_JAR=$(find "$ROOT_DIR/dist" -name "*.jar" -not -name "*-plain.jar" | xargs ls -t 2>/dev/null | head -n 1)
     if [ -z "$SOURCE_JAR" ]; then
        echo "Error: Source JAR not found in $ROOT_DIR/dist"
        exit 1
     fi
     echo "Found latest fallback JAR: $SOURCE_JAR"
fi

# 2. Copy JAR file via SCP
echo "Copying JAR file to $NAS_USER@$NAS_HOST:$REMOTE_DIR/$REMOTE_JAR..."
scp -O -P "$NAS_PORT" "$SOURCE_JAR" "$NAS_USER@$NAS_HOST:$REMOTE_DIR/$REMOTE_JAR"

if [ $? -eq 0 ]; then
    echo "Copy successful."
else
    echo "Error: Failed to copy file via SCP."
    exit 1
fi

# 3. Execute remote commands via SSH
echo "Executing remote deployment commands..."
# Use -tt to force TTY allocation for sudo password prompt
ssh -tt -p "$NAS_PORT" "$NAS_USER@$NAS_HOST" "
    # Add common paths for Synology NAS
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
        # Try direct path for Synology
        if [ -f /usr/local/bin/docker-compose ]; then
            DOCKER_COMPOSE_CMD=\"\$SUDO_PREFIX /usr/local/bin/docker-compose\"
        else
            echo 'Error: docker-compose not found.'
            exit 1
        fi
    fi

    echo \"Using command: \$DOCKER_COMPOSE_CMD\"
    \$DOCKER_COMPOSE_CMD down
    \$DOCKER_COMPOSE_CMD up -d --build
"
DEPLOY_EXIT_CODE=$?

if [ $DEPLOY_EXIT_CODE -eq 0 ]; then
    echo "----------------------------------------"
    echo "✅ Deployment successful!"
    echo "----------------------------------------"
else
    echo "----------------------------------------"
    echo "❌ Error: Remote deployment failed (Exit Code: $DEPLOY_EXIT_CODE)."
    echo "If sudo timed out, please ensure you are ready to input the NAS password."
    echo "----------------------------------------"
    exit $DEPLOY_EXIT_CODE
fi
