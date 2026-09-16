#!/usr/bin/env bash
# 仓库结构治理检查（CI 第一个 job，本地提交前也建议执行）。
#
# 检查项：
#   1. 禁止 package-info.java
#   2. 禁止 spring.factories（Spring Boot 3 使用 AutoConfiguration.imports）
#   3. *-starter 模块只能聚合依赖，不允许包含 Java 代码
#   4. 源码/配置中禁止 TODO、FIXME、伪代码、System.out、printStackTrace；Java 中禁止 RedisUtil 大杂烩
#
# 文本扫描优先使用 ripgrep，未安装时回退到 grep。
# 注意：搜索工具“没有匹配”返回 1 属于正常，只有返回码 >1（工具不存在、参数错误等）才视为失败，
# 不能再用 `|| true` 把命令缺失也一起吞掉，否则检查会静默通过。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

# 参与占位/调试代码扫描的文件类型：只扫描代码与配置，Markdown 文档中的规则说明不算违规。
SCAN_GLOBS=("*.java" "*.kt" "*.py" "*.xml" "*.yml" "*.yaml" "*.properties" "*.sql" "*.sh" "*.imports")
EXCLUDED_DIRS=("target" ".git" "build" "dist" "node_modules" "__pycache__" ".venv")

# search <pattern> [glob...]
# 在 SCAN_GLOBS（或传入的 glob）范围内搜索，输出 "文件:行号:内容"。
search() {
  local pattern="$1"
  shift
  local globs=("$@")
  if [[ ${#globs[@]} -eq 0 ]]; then
    globs=("${SCAN_GLOBS[@]}")
  fi
  local status=0
  local output
  if command -v rg >/dev/null 2>&1; then
    local args=(-n --no-heading --hidden --no-ignore)
    for glob in "${globs[@]}"; do
      args+=(-g "${glob}")
    done
    for dir in "${EXCLUDED_DIRS[@]}"; do
      args+=(-g "!**/${dir}/**")
    done
    args+=(-g '!scripts/verify-structure.sh')
    output="$(rg "${args[@]}" -e "${pattern}" .)" || status=$?
  elif command -v grep >/dev/null 2>&1; then
    local args=(-rnE)
    for glob in "${globs[@]}"; do
      args+=("--include=${glob}")
    done
    for dir in "${EXCLUDED_DIRS[@]}"; do
      args+=("--exclude-dir=${dir}")
    done
    output="$(grep "${args[@]}" -e "${pattern}" . | grep -v '^\./scripts/verify-structure\.sh:')" || status=$?
  else
    echo "未找到 rg 或 grep，无法执行文本扫描。" >&2
    exit 2
  fi
  if [[ ${status} -gt 1 ]]; then
    echo "文本扫描命令执行失败（退出码 ${status}）。" >&2
    exit "${status}"
  fi
  printf '%s' "${output:-}"
}

echo "检查 package-info.java"
if find . -name package-info.java -not -path '*/target/*' -print | grep -q .; then
  echo "发现 package-info.java，当前工程禁止重新引入。"
  exit 1
fi

echo "检查 spring.factories"
if find . -name spring.factories -not -path '*/target/*' -print | grep -q .; then
  echo "发现 spring.factories，Spring Boot 3 自动装配必须使用 AutoConfiguration.imports。"
  exit 1
fi

echo "检查 starter Java 代码"
starter_java_files="$(
  find chaos-starters -mindepth 1 -maxdepth 1 -type d -name '*-starter' -print | while read -r starter_dir; do
    if [[ -d "${starter_dir}/src" ]]; then
      find "${starter_dir}/src" -type f -name '*.java' -print
    fi
  done
)"
if [[ -n "${starter_java_files}" ]]; then
  echo "starter 模块只能聚合依赖，不允许包含 Java 代码："
  echo "${starter_java_files}"
  exit 1
fi

echo "检查明显占位和调试代码"
placeholder_violations="$(search 'TODO|FIXME|伪代码|System\.out|printStackTrace')"
if [[ -n "${placeholder_violations}" ]]; then
  echo "发现未清理的占位或调试代码："
  echo "${placeholder_violations}"
  exit 1
fi

echo "检查 RedisUtil 大杂烩工具类"
redis_util_violations="$(search 'RedisUtil' '*.java')"
if [[ -n "${redis_util_violations}" ]]; then
  echo "禁止新增 RedisUtil，请按锁、布隆过滤器、延迟队列、Lua、幂等分别建模："
  echo "${redis_util_violations}"
  exit 1
fi

echo "结构检查通过"
