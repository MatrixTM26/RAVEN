#!/usr/bin/env bash

set -euo pipefail

REPO="git@github.com:MatrixTM26/RAVEN.git"
BRANCHES=("main" "dev")
WORK_DIR="$(mktemp -d)"
REPO_OWNER="MatrixTM26"
REPO_NAME="RAVEN"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${CYAN}[INFO]${NC}  $1"; }
log_ok() { echo -e "${GREEN}[OK]${NC}    $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

require_tool() {
	if ! command -v "$1" &>/dev/null; then
		log_error "Required tool not found: $1"
		exit 1
	fi
}

require_tool git
require_tool gh

echo ""
echo -e "${CYAN}   RAVEN Repo Cleaner — github.com/MatrixTM26   ${NC}"
echo ""

if ! gh auth status &>/dev/null; then
	log_error "GitHub CLI is not authenticated. Run: gh auth login"
	exit 1
fi
log_ok "GitHub CLI authenticated"

log_info "Deleting all releases from $REPO_OWNER/$REPO_NAME ..."
RELEASE_IDS=$(gh api "repos/$REPO_OWNER/$REPO_NAME/releases" --paginate --jq '.[].id' 2>/dev/null || true)

if [[ -z "$RELEASE_IDS" ]]; then
	log_warn "No releases found"
else
	while IFS= read -r id; do
		gh api -X DELETE "repos/$REPO_OWNER/$REPO_NAME/releases/$id" &>/dev/null
		log_ok "Deleted release ID: $id"
	done <<<"$RELEASE_IDS"
fi

log_info "Deleting all tags remotely from $REPO_OWNER/$REPO_NAME ..."
REMOTE_TAGS=$(gh api "repos/$REPO_OWNER/$REPO_NAME/tags" --paginate --jq '.[].name' 2>/dev/null || true)

if [[ -z "$REMOTE_TAGS" ]]; then
	log_warn "No remote tags found"
else
	while IFS= read -r tag; do
		gh api -X DELETE "repos/$REPO_OWNER/$REPO_NAME/git/refs/tags/$tag" &>/dev/null
		log_ok "Deleted remote tag: $tag"
	done <<<"$REMOTE_TAGS"
fi

for branch in "${BRANCHES[@]}"; do
	BRANCH_DIR="$WORK_DIR/$branch"

	log_info "Cloning branch '$branch' ..."
	if ! git clone --single-branch --branch "$branch" "$REPO" "$BRANCH_DIR" &>/dev/null; then
		log_warn "Branch '$branch' not found on remote — skipping"
		continue
	fi
	log_ok "Cloned branch: $branch"

	cd "$BRANCH_DIR"

	log_info "[$branch] Removing all local tags ..."
	git tag -l | xargs -r git tag -d &>/dev/null || true

	log_info "[$branch] Running git reflog expire ..."
	git reflog expire --expire=now --all

	log_info "[$branch] Running git gc aggressive ..."
	git gc --aggressive --prune=now &>/dev/null

	log_info "[$branch] Running git repack ..."
	git repack -a -d --depth=250 --window=250 &>/dev/null

	log_info "[$branch] Force pushing to origin ..."
	git push --force origin "$branch"
	log_ok "Branch '$branch' pushed successfully"

	cd "$WORK_DIR"
done

log_info "Cleaning up temp directory ..."
rm -rf "$WORK_DIR"
log_ok "Temp directory removed"

echo ""
log_ok "Repository cleanup complete."
echo -e "${CYAN}  Repo             : ${NC}$REPO"
echo -e "${CYAN}  Branches cleaned : ${NC}${BRANCHES[*]}"
echo -e "${CYAN}  Releases deleted : ${NC}$(echo "$RELEASE_IDS" | grep -c . 2>/dev/null || echo 0)"
echo -e "${CYAN}  Tags deleted     : ${NC}$(echo "$REMOTE_TAGS" | grep -c . 2>/dev/null || echo 0)"
echo ""
