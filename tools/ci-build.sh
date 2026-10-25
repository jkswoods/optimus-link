#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/build.py --server "$HYTALE_SERVER_JAR" --jdk "$JAVA_HOME" --test
