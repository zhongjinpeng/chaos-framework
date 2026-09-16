#!/usr/bin/env bash
#
# 校验 API 兼容检查确实执行过，而不是被静默跳过。
#
# japicmp 被跳过时不输出任何内容：skip=true、基线制品不在仓库里（ignoreMissingOldVersion=true）
# 都会让它安静地什么都不做，发布日志和真跑过一遍长得一模一样。发布到 Maven Central 不可撤销，
# 所以发布前要能确认"检查真的跑了"，而不是"构建没报错"。
#
# 用法：先跑一次发布验证，再跑本脚本
#   ./mvnw -B -Pchaos-release -Dchaos.api.check.skip=false \
#          -Dchaos.release.compareVersion=<上一个已发布版本> -Dgpg.skip=true verify
#   scripts/verify-api-compatibility.sh <上一个已发布版本>
set -euo pipefail

cd "$(dirname "$0")/.."

readonly REPORT_NAME="api-compatibility-check.xml"
readonly BASELINE="${1:-}"

if [[ -z "${BASELINE}" ]]; then
    echo "用法: $0 <基线版本>   例如: $0 1.0.0" >&2
    exit 2
fi

revision="$(sed -n 's/^-Drevision=//p' .mvn/maven.config | tr -d '[:space:]')"
if [[ -z "${revision}" ]]; then
    echo "无法从 .mvn/maven.config 读取 revision" >&2
    exit 2
fi
if [[ "${revision}" == "${BASELINE}" ]]; then
    echo "基线版本与当前 revision 都是 ${revision}，japicmp 会拿自己和自己比，结果没有意义" >&2
    exit 1
fi

checked=0
missing=()

# 只看真正参与发布的 jar 模块：产出了主 jar，且模块没有把 chaos.api.check.skip 设为 true
# （chaos-examples / chaos-docs / chaos-architecture-tests 不发布，自行跳过）。
while IFS= read -r jar; do
    module_target="$(dirname "${jar}")"
    module_dir="$(dirname "${module_target}")"
    if grep -q '<chaos.api.check.skip>true</chaos.api.check.skip>' "${module_dir}/pom.xml" 2>/dev/null; then
        continue
    fi
    # 父目录声明跳过的（如 chaos-examples 下的子模块）一并排除。
    parent_pom="$(dirname "${module_dir}")/pom.xml"
    if grep -q '<chaos.api.check.skip>true</chaos.api.check.skip>' "${parent_pom}" 2>/dev/null; then
        continue
    fi
    if [[ -f "${module_target}/japicmp/${REPORT_NAME}" ]]; then
        checked=$((checked + 1))
    else
        missing+=("$(basename "${module_dir}")")
    fi
done < <(find . -type f -name "*-${revision}.jar" -path "*/target/*" -not -path "*/target/classes/*" | sort)

echo "API 兼容检查：${checked} 个模块对比了基线 ${BASELINE}"

if [[ ${checked} -eq 0 ]]; then
    cat >&2 <<EOF
没有任何模块产出兼容性报告，API 兼容检查整体空转了。

常见原因：
  - chaos.api.check.skip 仍为 true；
  - 没有加 -Pchaos-release（japicmp 只绑定在这个 profile 的 verify 阶段）；
  - 基线 ${BASELINE} 的制品在仓库中完全不存在，被 ignoreMissingOldVersion 全部跳过；
  - 只跑到 package 就结束了，没有执行到 verify。
EOF
    exit 1
fi

if [[ ${#missing[@]} -gt 0 ]]; then
    echo ""
    echo "以下 ${#missing[@]} 个模块没有产出报告，基线 ${BASELINE} 中大概率不存在该模块："
    printf '  %s\n' "${missing[@]}"
    echo ""
    echo "如果其中有本就该存在于 ${BASELINE} 的模块，说明它的基线制品没解析到，检查被静默跳过了 —— 需要人工确认。"
fi
