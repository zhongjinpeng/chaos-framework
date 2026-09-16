#!/usr/bin/env bash
# archetype 端到端验证：用本地构建的 archetype 生成项目，再对生成项目执行 mvn verify。
#
# 为什么需要安装到本地仓库：生成的项目以 com.michael:chaos-boot-parent:<revision> 为 parent，
# 依赖 chaos-*-starter，这些坐标只有 install 到 ~/.m2 后才能被一个“仓库外”的独立项目解析。
# 注意：本脚本默认执行 ./mvnw install，会把当前版本的全部 chaos 构件写入本机 ~/.m2/repository。
#
# 可调参数（环境变量）：
#   ARCHETYPE_SKIP_INSTALL=true   已经 install 过当前代码时跳过安装
#   ARCHETYPE_IT_DIR              生成项目的目录，默认 target/archetype-it
#   ARCHETYPE_OFFLINE=true        对生成项目使用 mvn -o（依赖已全部在本地仓库时可用）
#   ARCHETYPES                    要验证的 archetype 列表（空格分隔），默认全部
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

REVISION="$(sed -n 's/^-Drevision=\(.*\)$/\1/p' .mvn/maven.config)"
if [[ -z "${REVISION}" ]]; then
  echo "无法从 .mvn/maven.config 读取 revision" >&2
  exit 2
fi

IT_DIR="${ARCHETYPE_IT_DIR:-${ROOT_DIR}/target/archetype-it}"
ARCHETYPES="${ARCHETYPES:-web-service gateway auth-server}"
OFFLINE_FLAG=()
if [[ "${ARCHETYPE_OFFLINE:-false}" == "true" ]]; then
  OFFLINE_FLAG=(-o)
fi

if [[ "${ARCHETYPE_SKIP_INSTALL:-false}" != "true" ]]; then
  echo "安装 chaos ${REVISION} 到本地仓库（~/.m2/repository），跳过测试"
  ./mvnw -B -q install -DskipTests
fi

rm -rf "${IT_DIR}"
mkdir -p "${IT_DIR}"

for archetype in ${ARCHETYPES}; do
  artifact_id="demo-${archetype}"
  echo "==> 生成 chaos-archetype-${archetype} → ${IT_DIR}/${artifact_id}"
  (
    cd "${IT_DIR}"
    "${ROOT_DIR}/mvnw" -B -q ${OFFLINE_FLAG[@]+"${OFFLINE_FLAG[@]}"} \
      org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate \
      -DinteractiveMode=false \
      -DarchetypeCatalog=local \
      -DarchetypeGroupId=com.michael \
      -DarchetypeArtifactId="chaos-archetype-${archetype}" \
      -DarchetypeVersion="${REVISION}" \
      -DgroupId=com.acme.demo \
      -DartifactId="${artifact_id}" \
      -Dversion=0.1.0-SNAPSHOT \
      -Dpackage="com.acme.demo.$(echo "${archetype}" | tr -d '-')"
  )

  generated_pom="${IT_DIR}/${artifact_id}/pom.xml"
  if ! grep -q "<version>${REVISION}</version>" "${generated_pom}"; then
    echo "生成项目的 chaos-boot-parent 版本不是 ${REVISION}：${generated_pom}" >&2
    exit 1
  fi

  echo "==> 验证 ${artifact_id}（mvn verify）"
  # Maven 会向上查找 .mvn 目录作为工程根；生成项目位于本仓库 target 下，放一个空 .mvn 截断查找，
  # 避免继承本仓库 .mvn/maven.config 中的框架内部参数，模拟使用方独立仓库的真实情况。
  (
    cd "${IT_DIR}/${artifact_id}"
    mkdir -p .mvn
    "${ROOT_DIR}/mvnw" -B ${OFFLINE_FLAG[@]+"${OFFLINE_FLAG[@]}"} verify
  )
done

echo "archetype 验证通过：${ARCHETYPES}"
