#!/usr/bin/env bash
set -euo pipefail

RAGFLOW_VERSION="${RAGFLOW_VERSION:-v0.26.4}"
INSTALL_DIR="${INSTALL_DIR:-/opt/ragflow}"
INSTALL_DOCKER="${INSTALL_DOCKER:-1}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
HTTP_URL="${HTTP_URL:-http://127.0.0.1}"

log() {
  printf '\n==> %s\n' "$*"
}

fail() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

have() {
  command -v "$1" >/dev/null 2>&1
}

as_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  elif have sudo; then
    sudo "$@"
  else
    fail "Need root permission and sudo is not installed."
  fi
}

detect_pkg_manager() {
  if have dnf; then
    echo dnf
  elif have yum; then
    echo yum
  elif have apt-get; then
    echo apt
  else
    echo unknown
  fi
}

install_base_packages() {
  local pm
  pm="$(detect_pkg_manager)"
  log "Installing base packages with ${pm}"

  case "$pm" in
    dnf)
      as_root dnf install -y git curl ca-certificates
      ;;
    yum)
      as_root yum install -y git curl ca-certificates
      ;;
    apt)
      as_root apt-get update
      as_root apt-get install -y git curl ca-certificates gnupg lsb-release
      ;;
    *)
      fail "Unsupported package manager. Please install git, curl, Docker, and Docker Compose manually."
      ;;
  esac
}

install_docker_if_needed() {
  if have docker && docker compose version >/dev/null 2>&1; then
    return
  fi

  if [[ "$INSTALL_DOCKER" != "1" ]]; then
    fail "Docker or Docker Compose is missing. Set INSTALL_DOCKER=1 or install them manually."
  fi

  local pm
  pm="$(detect_pkg_manager)"
  log "Installing Docker with ${pm}"

  case "$pm" in
    dnf)
      as_root dnf install -y dnf-plugins-core
      as_root dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
      as_root dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      ;;
    yum)
      as_root yum install -y yum-utils
      as_root yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
      as_root yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      ;;
    apt)
      as_root install -m 0755 -d /etc/apt/keyrings
      curl -fsSL https://download.docker.com/linux/ubuntu/gpg | as_root gpg --dearmor -o /etc/apt/keyrings/docker.gpg
      as_root chmod a+r /etc/apt/keyrings/docker.gpg
      . /etc/os-release
      echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" | as_root tee /etc/apt/sources.list.d/docker.list >/dev/null
      as_root apt-get update
      as_root apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      ;;
    *)
      fail "Unsupported package manager. Please install Docker and Docker Compose manually."
      ;;
  esac

  as_root systemctl enable --now docker
}

check_system() {
  log "Checking system"

  [[ "$(uname -s)" == "Linux" ]] || fail "RAGFlow Docker deployment should run on Linux."

  local arch
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64)
      ;;
    *)
      fail "Official RAGFlow Docker images mainly support x86_64. Current arch: ${arch}"
      ;;
  esac

  local mem_kb disk_kb cpu_count
  mem_kb="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
  disk_kb="$(df -Pk / | awk 'NR==2 {print $4}')"
  cpu_count="$(getconf _NPROCESSORS_ONLN)"

  printf 'CPU cores: %s\n' "$cpu_count"
  printf 'RAM: %.1f GB\n' "$(awk -v kb="$mem_kb" 'BEGIN {print kb/1024/1024}')"
  printf 'Root free disk: %.1f GB\n' "$(awk -v kb="$disk_kb" 'BEGIN {print kb/1024/1024}')"

  (( cpu_count >= 4 )) || fail "Need at least 4 CPU cores."
  (( mem_kb >= 15000000 )) || fail "Need about 16 GB RAM."
  (( disk_kb >= 50000000 )) || fail "Need about 50 GB free disk on /."
}

configure_kernel() {
  log "Configuring vm.max_map_count"
  as_root sysctl -w vm.max_map_count=262144 >/dev/null
  if ! grep -q '^vm.max_map_count=262144$' /etc/sysctl.conf 2>/dev/null; then
    echo 'vm.max_map_count=262144' | as_root tee -a /etc/sysctl.conf >/dev/null
  fi
}

prepare_source() {
  log "Preparing RAGFlow source at ${INSTALL_DIR}"
  as_root mkdir -p "$INSTALL_DIR"
  as_root chown -R "$(id -u):$(id -g)" "$INSTALL_DIR"

  if [[ ! -d "${INSTALL_DIR}/.git" ]]; then
    git clone https://github.com/infiniflow/ragflow.git "$INSTALL_DIR"
  fi

  cd "$INSTALL_DIR"
  git fetch --tags
  git checkout -f "$RAGFLOW_VERSION"
}

start_ragflow() {
  cd "${INSTALL_DIR}/docker"
  [[ -f "$COMPOSE_FILE" ]] || fail "Compose file not found: ${INSTALL_DIR}/docker/${COMPOSE_FILE}"

  log "Pulling images"
  docker compose -f "$COMPOSE_FILE" pull

  log "Starting RAGFlow"
  docker compose -f "$COMPOSE_FILE" up -d

  log "Current containers"
  docker compose -f "$COMPOSE_FILE" ps
}

wait_http() {
  log "Waiting for HTTP endpoint: ${HTTP_URL}"
  for i in $(seq 1 60); do
    if curl -fsS --max-time 3 "$HTTP_URL" >/dev/null 2>&1; then
      printf 'RAGFlow is reachable: %s\n' "$HTTP_URL"
      return
    fi
    sleep 5
  done

  printf 'RAGFlow HTTP endpoint is not ready yet. Check logs with:\n'
  printf '  cd %s/docker && docker compose -f %s ps\n' "$INSTALL_DIR" "$COMPOSE_FILE"
  printf '  cd %s/docker && docker compose -f %s logs -f --tail=200\n' "$INSTALL_DIR" "$COMPOSE_FILE"
}

main() {
  check_system
  install_base_packages
  install_docker_if_needed

  log "Docker versions"
  docker --version
  docker compose version

  configure_kernel
  prepare_source
  start_ragflow
  wait_http

  cat <<EOF

Done.

Open:
  http://<SERVER_IP>

Operate:
  cd ${INSTALL_DIR}/docker
  docker compose -f ${COMPOSE_FILE} ps
  docker compose -f ${COMPOSE_FILE} logs -f --tail=200
  docker compose -f ${COMPOSE_FILE} down
  docker compose -f ${COMPOSE_FILE} up -d

API:
  Create an API key in the RAGFlow web UI, then use:
  export RAGFLOW_BASE_URL="http://<SERVER_IP>"
  export RAGFLOW_API_KEY="<YOUR_API_KEY>"
EOF
}

main "$@"
