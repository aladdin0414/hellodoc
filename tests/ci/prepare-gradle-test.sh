#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/.github/scripts" "$test_dir/hellodoc-server/gradle/wrapper" "$test_dir/bin" "$test_dir/temp"
cp "$root_dir/.github/scripts/prepare-gradle.sh" "$test_dir/.github/scripts/"
printf 'verified test archive' > "$test_dir/good.zip"
printf 'unexpected content' > "$test_dir/bad.zip"
hash="$(sha256sum "$test_dir/good.zip" | cut -d ' ' -f 1)"
printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip\ndistributionSha256Sum=%s\n' "$hash" > "$test_dir/hellodoc-server/gradle/wrapper/gradle-wrapper.properties"
cat > "$test_dir/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
output=''
url=''
while (($#)); do
  case "$1" in
    --output) output="$2"; shift 2 ;;
    https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
if [[ "$url" == *services.gradle.org* ]]; then exit 22; fi
cp "$TEST_ARCHIVE" "$output"
MOCK
chmod +x "$test_dir/bin/curl"
export PATH="$test_dir/bin:$PATH" RUNNER_TEMP="$test_dir/temp" TEST_ARCHIVE="$test_dir/good.zip"
"$test_dir/.github/scripts/prepare-gradle.sh"
cmp "$test_dir/good.zip" "$test_dir/temp/hellodoc-gradle-8.14.3-bin.zip"
grep -q '^distributionUrl=file:' "$test_dir/hellodoc-server/gradle/wrapper/gradle-wrapper.properties"
printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip\ndistributionSha256Sum=%s\n' "$hash" > "$test_dir/hellodoc-server/gradle/wrapper/gradle-wrapper.properties"
export TEST_ARCHIVE="$test_dir/bad.zip"
if "$test_dir/.github/scripts/prepare-gradle.sh"; then echo 'Expected checksum failure' >&2; exit 1; fi
grep -q '^distributionUrl=https' "$test_dir/hellodoc-server/gradle/wrapper/gradle-wrapper.properties"
echo 'Verified fallback download and checksum rejection.'
