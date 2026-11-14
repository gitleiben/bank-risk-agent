#!/usr/bin/env bash
# 启动开发环境。
# 本机没有独立安装 JDK17+ / Maven，直接复用 IntelliJ IDEA 自带的 JBR(JDK25) 与 Maven，
# 省掉 200MB+ 的下载。装了正式 JDK21 后把 JAVA_HOME 改掉即可。
set -euo pipefail

cd "$(dirname "$0")/.."

JDK_DEFAULT="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
MVN_DEFAULT="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"

export JAVA_HOME="${JAVA_HOME:-$JDK_DEFAULT}"
export PATH="$JAVA_HOME/bin:$PATH"
MVN="${MVN:-$MVN_DEFAULT}"

if [ ! -x "$MVN" ]; then
  MVN="mvn"
fi

echo "JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
"$MVN" -v 2>&1 | head -1

exec "$MVN" -B spring-boot:run
