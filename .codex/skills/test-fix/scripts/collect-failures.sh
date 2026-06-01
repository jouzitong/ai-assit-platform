#!/usr/bin/env bash
set -euo pipefail

# Collect basic Maven surefire failures.
find . -type f \( -name "*.txt" -o -name "*.xml" \) \
  | rg "surefire-reports|failsafe-reports" \
  | sort -u
