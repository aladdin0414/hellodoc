#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
wrapper="$root_dir/hellodoc-server/gradle/wrapper/gradle-wrapper.properties"
expected="$(sed -n 's/^distributionSha256Sum=//p' "$wrapper")"
archive="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/hellodoc-gradle-8.14.3-bin.zip"
urls=(
  'https://services.gradle.org/distributions/gradle-8.14.3-bin.zip'
  'https://repo.huaweicloud.com/gradle/gradle-8.14.3-bin.zip'
  'https://mirrors.cloud.tencent.com/gradle/gradle-8.14.3-bin.zip'
)
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo 'Missing Gradle SHA-256 checksum' >&2; exit 1; }

verified=false
for url in "${urls[@]}"; do
  echo "Downloading Gradle from $url"
  if curl --fail --location --retry 1 --connect-timeout 15 --max-time 300 --output "$archive" "$url" &&
     [[ "$(sha256sum "$archive" | cut -d ' ' -f 1)" == "$expected" ]]; then
    verified=true
    break
  fi
  echo 'Download or checksum verification failed; trying the next source.' >&2
  rm -f "$archive"
done
[[ "$verified" == true ]] || { echo 'Unable to obtain a verified Gradle distribution' >&2; exit 1; }

# CI jobs use the verified local archive without changing the committed wrapper URL.
sed "s|^distributionUrl=.*|distributionUrl=file:$archive|" "$wrapper" > "$wrapper.tmp"
mv "$wrapper.tmp" "$wrapper"
echo 'Gradle distribution checksum verified.'
