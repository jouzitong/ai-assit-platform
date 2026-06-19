#!/usr/bin/env bash
set -euo pipefail

NACOS_HOME="/Users/zhouzhitong/tools/cloud/nacos-2.3.1/nacos"
SHUTDOWN_SCRIPT="${NACOS_HOME}/bin/shutdown.sh"

if [[ ! -f "${SHUTDOWN_SCRIPT}" ]]; then
  echo "Nacos shutdown script not found: ${SHUTDOWN_SCRIPT}"
  exit 1
fi

cd "${NACOS_HOME}"

exec bash "${SHUTDOWN_SCRIPT}"
