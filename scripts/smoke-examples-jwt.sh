#!/usr/bin/env bash
# 示例 JWT 链路 smoke：auth-server 签发 token -> gateway 鉴权 -> order-service 查询/创建订单
# -> ABAC 策略拒绝大额订单 -> Prometheus 指标。
#
# 流程：先用 ./mvnw 一次性打包三个示例（避免三个 spring-boot:run 并发重复编译），再用 java -jar 启动。
# 前置条件：本机 localhost:6379 有可用 Redis（order-service 的 Redisson 启动即连接），无需 Nacos。
# 可调参数（环境变量）：
#   AUTH_PORT / ORDER_PORT / GATEWAY_PORT   服务端口，默认 9000 / 8081 / 8080
#                                           （示例之间的调用地址写在 yml 中，通常只建议覆盖 GATEWAY_PORT）
#   SMOKE_WAIT_ATTEMPTS                     每个服务健康检查最大尝试次数，默认 150
#   SMOKE_WAIT_INTERVAL                     健康检查间隔秒数，默认 2
#   SMOKE_SKIP_BUILD=true                   已经打包过时跳过构建
#   SMOKE_HEALTH_PATH                       就绪检查路径，默认 /actuator/health/readiness
#                                           （readiness 不聚合 Redis 健康指标，避免依赖抖动误判服务未启动）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/target/smoke-logs"
MAVEN_SETTINGS="${ROOT_DIR}/.mvn/settings-central.xml"
AUTH_PORT="${AUTH_PORT:-9000}"
ORDER_PORT="${ORDER_PORT:-8081}"
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
SMOKE_WAIT_ATTEMPTS="${SMOKE_WAIT_ATTEMPTS:-150}"
SMOKE_WAIT_INTERVAL="${SMOKE_WAIT_INTERVAL:-2}"
SMOKE_SKIP_BUILD="${SMOKE_SKIP_BUILD:-false}"
SMOKE_HEALTH_PATH="${SMOKE_HEALTH_PATH:-/actuator/health/readiness}"
EXAMPLE_MODULES="chaos-examples/example-auth-server,chaos-examples/example-order-service,chaos-examples/example-gateway"

mkdir -p "${LOG_DIR}"

pids=()

cleanup() {
  for pid in "${pids[@]:-}"; do
    if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
      kill "${pid}" >/dev/null 2>&1 || true
    fi
  done
}
trap cleanup EXIT

build_examples() {
  if [[ "${SMOKE_SKIP_BUILD}" == "true" ]]; then
    echo "跳过示例构建（SMOKE_SKIP_BUILD=true）"
    return
  fi
  echo "打包示例工程"
  (
    cd "${ROOT_DIR}"
    ./mvnw -B -q -gs "${MAVEN_SETTINGS}" -s "${MAVEN_SETTINGS}" -pl "${EXAMPLE_MODULES}" -am -DskipTests package
  )
}

# 定位 spring-boot-maven-plugin repackage 后的可执行 jar（排除 *.original）。
example_jar() {
  local module="$1"
  local jar
  # target 下可能残留旧版本 jar（未 clean），按修改时间取最新构建的那个，避免启动过期代码。
  jar="$(find "${ROOT_DIR}/${module}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -exec ls -t {} + 2>/dev/null | head -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "未找到 ${module} 的可执行 jar，请先执行构建" >&2
    exit 1
  fi
  printf '%s' "${jar}"
}

start_service() {
  local module="$1"
  local log_file="$2"
  shift 2
  local jar
  jar="$(example_jar "${module}")"
  java -jar "${jar}" "$@" >"${log_file}" 2>&1 &
  pids+=("$!")
}

wait_for_http() {
  local url="$1"
  local name="$2"
  local max_attempts="${3:-${SMOKE_WAIT_ATTEMPTS}}"
  for _ in $(seq 1 "${max_attempts}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      echo "${name} 已就绪"
      return 0
    fi
    sleep "${SMOKE_WAIT_INTERVAL}"
  done
  echo "${name} 未在预期时间内就绪：${url}，日志目录：${LOG_DIR}"
  return 1
}

extract_access_token() {
  sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

build_examples

echo "启动 example-auth-server"
start_service "chaos-examples/example-auth-server" "${LOG_DIR}/auth-server.log" --server.port="${AUTH_PORT}"
wait_for_http "http://localhost:${AUTH_PORT}${SMOKE_HEALTH_PATH}" "auth-server"

echo "启动 example-order-service"
start_service "chaos-examples/example-order-service" "${LOG_DIR}/order-service.log" --spring.profiles.active=jwt-token --server.port="${ORDER_PORT}"
wait_for_http "http://localhost:${ORDER_PORT}${SMOKE_HEALTH_PATH}" "order-service"

echo "启动 example-gateway"
start_service "chaos-examples/example-gateway" "${LOG_DIR}/gateway.log" --server.port="${GATEWAY_PORT}"
wait_for_http "http://localhost:${GATEWAY_PORT}${SMOKE_HEALTH_PATH}" "gateway"

echo "获取 JWT token"
token_response="$(
  curl -fsS -u chaos-client:chaos-secret \
    -H 'X-Device-Id: smoke-web-001' \
    -d 'grant_type=password&username=admin&password=123456&scope=read write' \
    "http://localhost:${AUTH_PORT}/oauth2/token"
)"
access_token="$(printf '%s' "${token_response}" | extract_access_token)"
if [[ -z "${access_token}" ]]; then
  echo "未能从授权响应中解析 access_token"
  echo "${token_response}"
  exit 1
fi

echo "验证 Gateway 订单查询"
curl -fsS \
  -H "Authorization: Bearer ${access_token}" \
  -H 'X-Tenant-Id: tenant-a' \
  "http://localhost:${GATEWAY_PORT}/api/orders" >/dev/null

echo "验证 Gateway 订单创建"
curl -fsS -X POST "http://localhost:${GATEWAY_PORT}/api/orders" \
  -H "Authorization: Bearer ${access_token}" \
  -H 'X-Tenant-Id: tenant-a' \
  -H "Idempotency-Key: smoke-$(date +%s)" \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"SMOKE-20260525-001","buyerId":"10001","amount":199.90}' >/dev/null

echo "验证 ABAC 策略：大额订单被拒绝"
large_order_status="$(
  curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:${GATEWAY_PORT}/api/orders" \
    -H "Authorization: Bearer ${access_token}" \
    -H 'X-Tenant-Id: tenant-a' \
    -H "Idempotency-Key: smoke-large-$(date +%s)" \
    -H 'Content-Type: application/json' \
    -d '{"orderNo":"SMOKE-LARGE-20260525-001","buyerId":"10001","amount":200000.00}'
)"
if [[ "${large_order_status}" != "403" ]]; then
  echo "大额订单应被 chaos.security.access.policies 中的 deny-large-order 策略拒绝，实际返回 ${large_order_status}"
  exit 1
fi

echo "验证 Prometheus 指标"
curl -fsS "http://localhost:${AUTH_PORT}/actuator/prometheus" >/dev/null
curl -fsS "http://localhost:${ORDER_PORT}/actuator/prometheus" >/dev/null
curl -fsS "http://localhost:${GATEWAY_PORT}/actuator/prometheus" >/dev/null

echo "示例 JWT 链路 smoke 通过，日志目录：${LOG_DIR}"
