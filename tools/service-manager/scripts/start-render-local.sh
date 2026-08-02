#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

cd "${REPO_ROOT}"

unset HTTP_PROXY HTTPS_PROXY ALL_PROXY
unset http_proxy https_proxy all_proxy
unset NO_PROXY no_proxy
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS

JVM_ARGS="-Djava.net.useSystemProxies=false -Dhttp.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyHost= -Dhttps.proxyPort= -DsocksProxyHost= -DsocksProxyPort="

if [[ -n "${EXTRA_JVM_ARGS:-}" ]]; then
  JVM_ARGS="${JVM_ARGS} ${EXTRA_JVM_ARGS}"
fi

echo "Starting render service without proxy inheritance..."

echo "Preparing render module dependencies..."
mvn -f app/app-platform-render/pom.xml -pl boot -am \
  -DskipTests \
  install

exec mvn -f app/app-platform-render/boot/pom.xml spring-boot:run \
  -Dspring-boot.run.jvmArguments="${JVM_ARGS}" \
  "$@"
