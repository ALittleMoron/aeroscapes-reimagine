#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s <java-25-executable> <accepted-eula-file>\n' "$0" >&2
  exit 2
fi

java_binary=$1
eula_file=$2
if [[ -z "$java_binary" || -z "$eula_file" ]]; then
  printf 'Provide JAVA_BINARY and EULA_FILE; see tools/pack/README.md.\n' >&2
  exit 2
fi
uv run --locked --project tools/pack python -c \
  'from pathlib import Path; import sys; from aeroscapes_pack.smoke import eula_accepted; sys.exit(0 if eula_accepted(Path(sys.argv[1])) else "The supplied EULA file must contain eula=true")' \
  "$eula_file"

mkdir -p .runtime
run_directory=$(mktemp -d "$PWD/.runtime/ship-smoke.XXXXXX")
server_directory="$run_directory/server"
printf 'Disposable server and evidence: %s\n' "$server_directory"

if ! uv run --locked --project tools/pack aeroscapes-pack test-server --target "$server_directory" --java "$java_binary" > "$run_directory/install.log" 2>&1; then
  printf 'Server installation failed; see %s\n' "$run_directory/install.log" >&2
  tail -n 30 "$run_directory/install.log" >&2
  exit 1
fi
cp -- "$eula_file" "$server_directory/eula.txt"
cp -- mods/aeroscapes-reimagine/build/libs/aeroscapes-worldgen-regression-0.1.0.jar "$server_directory/mods/"
printf '%s\n' '-Xms1G' '-Xmx6G' '-Daeroscapes.regression.core=verify' '-Daeroscapes.regression.shipProgression=true' > "$server_directory/user_jvm_args.txt"
printf '%s\n' 'level-seed=1' >> "$server_directory/server.properties"

exec uv run --locked --project tools/pack aeroscapes-smoke --directory "$server_directory" --java "$java_binary" --ready-marker 'Ship progression recipe and loot regression passed' --timeout 600
