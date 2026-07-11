#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CHAT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PYTHON_PROJECT_DIR="${CHAT_DIR}/providers/ai-provider-ai-agent/src/main/python"
PYPROJECT_FILE="${PYTHON_PROJECT_DIR}/pyproject.toml"
VENV_DIR="${PYTHON_PROJECT_DIR}/.venv"
DEPENDENCY_STAMP="${VENV_DIR}/.dependency-stamp"
PYTHON_COMMAND="${AI_AGENT_PYTHON_BOOTSTRAP_COMMAND:-python3.11}"
FORCE_INSTALL=false

usage() {
  cat <<'EOF'
Usage: init-ai-agent-python.sh [--force] [--python <command>]

Options:
  --force             Reinstall the Python project and dependencies.
  --python <command>  Python 3.11+ bootstrap command or absolute path.
  -h, --help          Show this help message.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force)
      FORCE_INSTALL=true
      shift
      ;;
    --python)
      if [[ $# -lt 2 ]]; then
        echo "missing value for --python" >&2
        exit 1
      fi
      PYTHON_COMMAND="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

dependency_fingerprint() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "${PYPROJECT_FILE}" | awk '{print $1}'
  elif command -v shasum > /dev/null 2>&1; then
    shasum -a 256 "${PYPROJECT_FILE}" | awk '{print $1}'
  else
    echo "sha256sum or shasum is required" >&2
    exit 1
  fi
}

if [[ ! -f "${PYPROJECT_FILE}" ]]; then
  echo "Python project not found: ${PYPROJECT_FILE}" >&2
  exit 1
fi

if ! command -v "${PYTHON_COMMAND}" > /dev/null 2>&1; then
  echo "Python command not found: ${PYTHON_COMMAND}" >&2
  exit 1
fi

if ! "${PYTHON_COMMAND}" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)'; then
  echo "Python 3.11 or newer is required: ${PYTHON_COMMAND}" >&2
  exit 1
fi

if [[ ! -x "${VENV_DIR}/bin/python" ]]; then
  echo "Creating virtual environment: ${VENV_DIR}"
  "${PYTHON_COMMAND}" -m venv "${VENV_DIR}"
  FORCE_INSTALL=true
fi

expected_fingerprint="$(dependency_fingerprint)"
current_fingerprint=""
if [[ -f "${DEPENDENCY_STAMP}" ]]; then
  current_fingerprint="$(cat "${DEPENDENCY_STAMP}")"
fi

if [[ "${FORCE_INSTALL}" == true || "${expected_fingerprint}" != "${current_fingerprint}" ]]; then
  echo "Installing AI Agent Python project and dependencies"
  "${VENV_DIR}/bin/python" -m pip install \
    --disable-pip-version-check \
    --upgrade \
    --editable "${PYTHON_PROJECT_DIR}"
  echo "${expected_fingerprint}" > "${DEPENDENCY_STAMP}"
else
  echo "Dependencies are unchanged; skipping installation"
fi

"${VENV_DIR}/bin/python" -c 'import agents, quickjs'

echo "AI Agent Python runtime is ready"
echo "Python command: ${VENV_DIR}/bin/python"
echo "Agent script: ${PYTHON_PROJECT_DIR}/agent_provider/main.py"
