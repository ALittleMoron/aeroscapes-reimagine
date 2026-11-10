#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.."
npm_version="$(node -p 'require("./tools/docs/package.json").engines.npm')"
npm_directory="$RUNNER_TEMP/aeroscapes-npm"
npm install --prefix "$npm_directory" --ignore-scripts --no-audit --no-fund "npm@$npm_version"
test "$("$npm_directory/node_modules/.bin/npm" --version)" = "$npm_version"
printf '%s\n' "$npm_directory/node_modules/.bin" >> "$GITHUB_PATH"
