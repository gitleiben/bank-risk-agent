#!/usr/bin/env bash
# 跑评测：先测检索（秒级，不依赖模型），可选连模型一起测。
# 用法：./scripts/eval.sh        只测检索
#      ./scripts/eval.sh llm    连模型一起测
set -euo pipefail

BASE="http://localhost:8080"
WITH_LLM="false"
[ "${1:-}" = "llm" ] && WITH_LLM="true"

echo "== 知识库状态 =="
curl -s "$BASE/api/stats"; echo

echo "== 重建索引（含向量化，模型未启动时会自动回退） =="
curl -s -X POST "$BASE/api/kb/reindex"; echo

echo "== 评测 withLlm=$WITH_LLM =="
curl -s "$BASE/api/eval/run?withLlm=$WITH_LLM" | python3 -m json.tool --no-ensure-ascii 2>/dev/null \
  || curl -s "$BASE/api/eval/run?withLlm=$WITH_LLM"
