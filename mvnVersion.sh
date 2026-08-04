#!/usr/bin/env bash

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_POM="$ROOT_DIR/pom.xml"

TARGET_VERSION=""
RELEASE_MESSAGE=""

usage() {
  cat <<EOF
Usage:
  ./$SCRIPT_NAME -v <version> -m <message>

Examples:
  ./$SCRIPT_NAME -v 0.0.1 -m '这个是第一个版本'
  ./$SCRIPT_NAME --version 1.0.0-SNAPSHOT --message '开发版本'

Behavior:
  1. Update the single root <revision> version source.
  2. Run Maven package with tests skipped.
  3. Commit the root POM change.
  4. Create an annotated v<version> tag only for x.y.z versions.

Versions with any suffix, such as -SNAPSHOT, -RC1, or .RELEASE, never create a tag.

The script does not push commits or tags automatically.
EOF
}

log() {
  printf '[%s] %s\n' "$SCRIPT_NAME" "$*"
}

die() {
  printf '[%s] ERROR: %s\n' "$SCRIPT_NAME" "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -v|--version)
        [[ $# -ge 2 ]] || die "--version requires a value / --version 需要传入版本号"
        TARGET_VERSION="$2"
        shift 2
        ;;
      -m|--message)
        [[ $# -ge 2 ]] || die "--message requires a value / --message 需要传入版本说明"
        RELEASE_MESSAGE="$2"
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        die "Unknown option: $1 / 未知参数: $1"
        ;;
    esac
  done

  [[ -n "$TARGET_VERSION" ]] || die "Missing required option: -v <version> / 缺少必填参数: -v <version>"
  [[ -n "$RELEASE_MESSAGE" ]] || die "Missing required option: -m <message> / 缺少必填参数: -m <message>"

  [[ "$TARGET_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] \
    || die "Invalid version: $TARGET_VERSION / 版本号格式不正确"
}

ensure_git_clean() {
  if [[ -n "$(git status --porcelain)" ]]; then
    die "Git working tree is dirty. Commit or stash current changes first. / Git 工作区不干净，请先提交或暂存当前改动。"
  fi
}

current_version() {
  sed -n 's/^[[:space:]]*<revision>[[:space:]]*\([^<[:space:]]*\)[[:space:]]*<\/revision>[[:space:]]*$/\1/p' "$ROOT_POM" \
    | head -n 1
}

is_stable_version() {
  [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

ensure_tag_available() {
  local tag_name="v$TARGET_VERSION"

  if is_stable_version "$TARGET_VERSION" \
    && git rev-parse -q --verify "refs/tags/$tag_name" >/dev/null 2>&1; then
    die "Git tag already exists: $tag_name / Git 标签已存在: $tag_name"
  fi
}

update_versions() {
  TARGET_VERSION="$TARGET_VERSION" perl -0pi -e '
    s{(<revision>\s*)[^<\s]+(\s*</revision>)}
     {$1 . $ENV{TARGET_VERSION} . $2}ge;
  ' "$ROOT_POM"
}

commit_changes() {
  git add -- "$ROOT_POM"

  if git diff --cached --quiet; then
    die "No POM changes detected / 未检测到 POM 版本变更"
  fi

  git commit \
    -m "chore: upgrade version to $TARGET_VERSION" \
    -m "$RELEASE_MESSAGE"
}

create_tag() {
  local tag_name="v$TARGET_VERSION"

  if ! is_stable_version "$TARGET_VERSION"; then
    log "Pre-release version detected; skip tag: $tag_name"
    return
  fi

  git tag -a "$tag_name" -m "$RELEASE_MESSAGE"
  log "Created tag: $tag_name"
}

main() {
  parse_args "$@"

  require_cmd git
  require_cmd mvn
  require_cmd perl

  cd "$ROOT_DIR"

  [[ -f "$ROOT_POM" ]] || die "Root pom not found: $ROOT_POM / 未找到根 pom 文件"
  git rev-parse --show-toplevel >/dev/null 2>&1 \
    || die "Not inside a Git repository / 当前目录不在 Git 仓库内"

  ensure_git_clean
  ensure_tag_available

  local before_version
  before_version="$(current_version)"
  [[ -n "$before_version" ]] || die "Cannot read root POM version / 无法读取根 POM 版本"
  [[ "$before_version" != "$TARGET_VERSION" ]] \
    || die "Version is already $TARGET_VERSION / 当前版本已经是 $TARGET_VERSION"

  log "Current version: $before_version"
  log "Target version: $TARGET_VERSION"
  log "Updating root revision"
  update_versions

  [[ "$(current_version)" == "$TARGET_VERSION" ]] \
    || die "Root revision was not updated / 根 POM 的 revision 更新失败"

  log "Run Maven package"
  mvn -q -DskipTests package

  commit_changes
  create_tag

  printf '\n'
  log "Done."
  log "Commit: $(git rev-parse --short HEAD)"

  local branch_name
  branch_name="$(git rev-parse --abbrev-ref HEAD)"
  printf '\nPush manually when ready:\n'
  printf 'git push origin %s\n' "$branch_name"
  if is_stable_version "$TARGET_VERSION"; then
    printf 'git push origin v%s\n' "$TARGET_VERSION"
  fi
}

main "$@"
