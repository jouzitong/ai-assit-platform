#!/usr/bin/env bash
set -euo pipefail

if pgrep -f "nacos.nacos" >/dev/null 2>&1; then
  exit 0
fi

exit 1
