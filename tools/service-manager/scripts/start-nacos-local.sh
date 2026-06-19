#!/usr/bin/env bash
set -euo pipefail

NACOS_ROOT="/Users/zhouzhitong/tools/cloud/nacos-2.3.1"
START_SCRIPT="${NACOS_ROOT}/start-standalone.sh"

if [[ ! -x "${START_SCRIPT}" ]]; then
  echo "Nacos start script not found or not executable: ${START_SCRIPT}"
  exit 1
fi

cd "${NACOS_ROOT}"

exec "${START_SCRIPT}" "$@"
