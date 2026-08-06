#!/usr/bin/env bash

set -euo pipefail

# Usage:
#   ./deploy/backup-db.sh [DB_NAME] [DB_HOST] [DB_PORT] [DB_USER] [OUTPUT_FILE]
# Example:
#   PGPASSWORD='[PASSWORD]' ./deploy/backup-db.sh hellodoc localhost 5432 postgres

DB_NAME="${1:-hellodoc}"
DB_HOST="${2:-localhost}"
DB_PORT="${3:-5432}"
DB_USER="${4:-postgres}"
OUTPUT_FILE="${5:-hellodoc-server/src/main/resources/schema.sql}"

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "Error: pg_dump not found. Please install PostgreSQL client tools first."
  exit 1
fi

if [[ -z "${PGPASSWORD:-}" ]]; then
  read -r -s -p "PostgreSQL password for user '$DB_USER': " PGPASSWORD
  echo
  if [[ -z "${PGPASSWORD}" ]]; then
    echo "Error: empty password."
    exit 1
  fi
  export PGPASSWORD
fi

OUTPUT_DIR="$(dirname "$OUTPUT_FILE")"
mkdir -p "$OUTPUT_DIR"

echo "Backing up database..."
echo "  host: $DB_HOST"
echo "  port: $DB_PORT"
echo "  db  : $DB_NAME"
echo "  user: $DB_USER"
echo "  out : $OUTPUT_FILE"

pg_dump \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  --schema-only \
  --no-owner \
  --no-privileges \
  -f "$OUTPUT_FILE"

# Sanitize dump for JDBC execution:
# 1) Remove psql meta-commands (lines starting with '\', e.g. \restrict / \unrestrict)
# 2) Remove version-specific GUC unsupported by older servers (SET transaction_timeout = ...)
tmp_file="$(mktemp "${OUTPUT_FILE}.XXXXXX")"
awk '
  substr($0, 1, 1) == "\\" { next }
  tolower($0) ~ /^[[:space:]]*set[[:space:]]+transaction_timeout[[:space:]]*=/ { next }
  { print }
' "$OUTPUT_FILE" > "$tmp_file"
mv "$tmp_file" "$OUTPUT_FILE"
echo "Sanitized dump for JDBC compatibility."

echo "Backup completed: $OUTPUT_FILE"
