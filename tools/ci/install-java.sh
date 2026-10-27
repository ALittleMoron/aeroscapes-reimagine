#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.."
java_url="$(python3 -c 'import json; print(json.load(open("toolchain.json"))["java"]["linux_x64_jdk"]["url"])')"
java_sha256="$(python3 -c 'import json; print(json.load(open("toolchain.json"))["java"]["linux_x64_jdk"]["sha256"])')"
archive="$RUNNER_TEMP/corretto25.tar.gz"
java_home="$RUNNER_TEMP/corretto25"
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' --retry 3 "$java_url" --output "$archive"
printf '%s  %s\n' "$java_sha256" "$archive" | sha256sum --check
mkdir -p "$java_home"
tar -xzf "$archive" --strip-components=1 -C "$java_home"
printf 'JAVA_HOME=%s\nJAVA_HOME_25_X64=%s\n' "$java_home" "$java_home" >> "$GITHUB_ENV"
printf '%s/bin\n' "$java_home" >> "$GITHUB_PATH"
