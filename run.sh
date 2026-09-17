#!/usr/bin/env bash
# Runs the CLI RAG assistant. Pass extra flags through, e.g.:
#   ./run.sh --dir data --topk 5
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -d out ]; then
  ./build.sh
fi

java -cp out com.ragcli.Main "$@"
