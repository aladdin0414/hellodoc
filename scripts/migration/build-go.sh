#!/bin/sh
set -eu
if [ "$#" -ne 1 ] || [ -z "$1" ]; then
    echo 'Usage: build-go.sh OUTPUT' >&2
    exit 2
fi
case "$1" in
    /*) output=$1 ;;
    *) output=$PWD/$1 ;;
esac
root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
properties=$(cd "$root/hellodoc-server" && ./gradlew -q properties --property version)
build_version=$(printf '%s\n' "$properties" | awk '/^version:[[:space:]]*/ {n++; sub(/^version:[[:space:]]*/, ""); value=$0} END {if (n != 1 || value !~ /^[^[:space:]]+$/) exit 1; print value}')
cd "$root/hellodoc-go"
go build -ldflags "-X main.version=$build_version" -o "$output" ./cmd/hellodoc
