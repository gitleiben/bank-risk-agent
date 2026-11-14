#!/usr/bin/env bash
# 一键把本项目推到 GitHub（在你自己的终端里跑，不要在 WorkBuddy 沙箱里跑）
#
# 用法：
#   ./scripts/push-to-github.sh                  # 默认仓库名 bank-risk-agent，公开
#   ./scripts/push-to-github.sh my-repo private  # 自定义仓库名、私有
#
# 前提：gh 已安装且已登录（gh auth login）
set -euo pipefail

REPO_NAME="${1:-bank-risk-agent}"
VISIBILITY="${2:-public}"

cd "$(dirname "$0")/.."

echo "==> 检查 GitHub CLI"
if ! command -v gh >/dev/null 2>&1; then
  echo "✗ 找不到 gh。装一个：brew install gh  或参考 https://github.com/cli/cli#installation"
  exit 1
fi
echo "    $(gh --version | head -1)"

echo "==> 检查登录状态"
if ! gh auth status >/dev/null 2>&1; then
  echo "✗ 还没登录。先跑：gh auth login"
  echo "  选 GitHub.com → HTTPS → Login with a web browser，把终端里的一次性验证码填到浏览器里。"
  exit 1
fi
gh auth status 2>&1 | sed 's/^/    /'

echo "==> 检查提交身份"
NAME="$(git config user.name || true)"
EMAIL="$(git config user.email || true)"
if [ -z "$NAME" ] || [ -z "$EMAIL" ]; then
  echo "    未配置，使用 GitHub 账号信息回填"
  GH_LOGIN="$(gh api user -q .login)"
  GH_ID="$(gh api user -q .id)"
  git config user.name "$GH_LOGIN"
  git config user.email "${GH_ID}+${GH_LOGIN}@users.noreply.github.com"
  echo "    user.name=$GH_LOGIN"
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "! 还有未提交的改动，先提交再推会更干净："
  git status --short | sed 's/^/    /'
  read -r -p "    是否继续（未提交内容不会被推上去）？[y/N] " ok
  [ "$ok" = "y" ] || exit 1
fi

echo "==> 创建远端仓库 $REPO_NAME（$VISIBILITY）并推送"
if git remote get-url origin >/dev/null 2>&1; then
  echo "    已存在 origin，改为直接推送"
  git push -u origin HEAD
else
  gh repo create "$REPO_NAME" "--$VISIBILITY" --source=. --remote=origin --push \
    --description "银行运营监督与风险预警 Agent：本地大模型 + RAG + Tool Calling + MCP 的个人预研项目"
fi

echo
echo "✓ 完成：$(gh repo view --json url -q .url 2>/dev/null || echo "（打开 GitHub 查看）")"
