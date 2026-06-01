#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./run-tests.sh "mvn -q -DskipTests=false test"

CMD="${1:-mvn -q -DskipTests=false test}"
echo "[test-fix] running: ${CMD}"
eval "${CMD}"
