#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

cd "${REPO_ROOT}"

unset HTTP_PROXY HTTPS_PROXY ALL_PROXY
unset http_proxy https_proxy all_proxy
unset NO_PROXY no_proxy
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS

system_proxy_value() {
  local key="$1"
  if [[ "$(uname -s)" != "Darwin" ]] || ! command -v scutil >/dev/null 2>&1; then
    return 0
  fi
  scutil --proxy 2>/dev/null | awk -v key="${key}" '$1 == key { print $3; exit }'
}

append_proxy_jvm_args() {
  local protocol="$1"
  local host="$2"
  local port="$3"
  if [[ ! "${host}" =~ ^[A-Za-z0-9._:-]+$ ]] || [[ ! "${port}" =~ ^[0-9]{1,5}$ ]]; then
    return 1
  fi
  JVM_ARGS="${JVM_ARGS} -D${protocol}.proxyHost=${host} -D${protocol}.proxyPort=${port}"
}

# Chat talks to external providers (for example the RAGFlow knowledge base).
# Keep the shell/build environment isolated, but allow Java to use the host's
# configured proxy for those outbound calls. Java HTTP clients used by Chat do
# not consistently honor java.net.useSystemProxies, so on macOS resolve the
# system proxy into explicit JVM properties. Set AI_CHAT_USE_SYSTEM_PROXY=false
# when running in an environment where the providers are directly reachable.
if [[ "${AI_CHAT_USE_SYSTEM_PROXY:-true}" == "true" ]]; then
  http_proxy_host="${AI_CHAT_HTTP_PROXY_HOST:-}"
  http_proxy_port="${AI_CHAT_HTTP_PROXY_PORT:-}"
  https_proxy_host="${AI_CHAT_HTTPS_PROXY_HOST:-}"
  https_proxy_port="${AI_CHAT_HTTPS_PROXY_PORT:-}"

  if [[ -z "${http_proxy_host}" ]] && [[ "$(system_proxy_value HTTPEnable)" == "1" ]]; then
    http_proxy_host="$(system_proxy_value HTTPProxy)"
    http_proxy_port="$(system_proxy_value HTTPPort)"
  fi
  if [[ -z "${https_proxy_host}" ]] && [[ "$(system_proxy_value HTTPSEnable)" == "1" ]]; then
    https_proxy_host="$(system_proxy_value HTTPSProxy)"
    https_proxy_port="$(system_proxy_value HTTPSPort)"
  fi

  if [[ -n "${http_proxy_host}" || -n "${https_proxy_host}" ]]; then
    JVM_ARGS="-Djava.net.useSystemProxies=false"
    if [[ -n "${http_proxy_host}" ]] && ! append_proxy_jvm_args http "${http_proxy_host}" "${http_proxy_port}"; then
      echo "Invalid HTTP proxy configuration; set AI_CHAT_HTTP_PROXY_HOST/PORT or disable system proxy." >&2
      exit 1
    fi
    if [[ -n "${https_proxy_host}" ]] && ! append_proxy_jvm_args https "${https_proxy_host}" "${https_proxy_port}"; then
      echo "Invalid HTTPS proxy configuration; set AI_CHAT_HTTPS_PROXY_HOST/PORT or disable system proxy." >&2
      exit 1
    fi
  else
    JVM_ARGS="-Djava.net.useSystemProxies=true"
  fi
else
  JVM_ARGS="-Djava.net.useSystemProxies=false -Dhttp.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyHost= -Dhttps.proxyPort= -DsocksProxyHost= -DsocksProxyPort="
fi

if [[ -n "${EXTRA_JVM_ARGS:-}" ]]; then
  JVM_ARGS="${JVM_ARGS} ${EXTRA_JVM_ARGS}"
fi

echo "Starting ai-chat service (system proxy=${AI_CHAT_USE_SYSTEM_PROXY:-true})..."

echo "Preparing ai-chat module dependencies..."
mvn -f app/app-platform-chat/pom.xml -pl boot -am \
  -DskipTests \
  install

CHAT_HOME="${REPO_ROOT}/app/app-platform-chat/boot/target/app-platform-chat"
CHAT_LAUNCHER="${CHAT_HOME}/bin/chat"

if [[ ! -x "${CHAT_LAUNCHER}" ]]; then
  echo "Generated chat launcher not found: ${CHAT_LAUNCHER}" >&2
  exit 1
fi

export EXTRA_JVM_ARGS="${JVM_ARGS}"
exec "${CHAT_LAUNCHER}" run "${1:-dev}"
