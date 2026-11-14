#!/usr/bin/env bash
# 拉取本地模型（数据不出域的关键：模型在本机跑，语料不出网）。
#
# 体积参考（第一次拉取需要联网，之后离线可用）：
#   qwen2.5:3b   ≈ 1.9GB   7B 量化版，Mac 上 8G 内存可跑，先跑通用它最省
#   qwen2.5:7b   ≈ 4.7GB   效果好一档，16G 内存推荐
#   bge-m3       ≈ 1.2GB   中文检索效果好的 embedding 模型
set -euo pipefail

if ! command -v ollama >/dev/null 2>&1; then
  echo "未安装 Ollama。macOS 安装：brew install ollama  或官网下载 https://ollama.com/download"
  exit 1
fi

MODEL_CHAT="${1:-qwen2.5:3b}"
MODEL_EMBED="${2:-bge-m3}"

echo "拉取对话模型 $MODEL_CHAT ..."
ollama pull "$MODEL_CHAT"
echo "拉取向量模型 $MODEL_EMBED ..."
ollama pull "$MODEL_EMBED"

echo "完成。当前本地模型："
ollama list
