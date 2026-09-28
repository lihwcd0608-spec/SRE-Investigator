#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/scripts/local.env"
EXAMPLE="$ROOT/scripts/local.env.example"
PORT="${SRE_JAVA_PORT:-8790}"

COMPOSE_FILE="$ROOT/deploy/docker-compose.yml"
DOTENV="$ROOT/.env"
DOTENV_EXAMPLE="$ROOT/.env.example"

usage() {
  cat <<'EOF'
用法: ./scripts/dev.sh <命令> [模式]

命令
  docker        一键：MySQL + Redis + 控制面容器（只要 Docker，http://localhost:8790/）
  docker-down   停掉 compose 里的 app / mysql / redis（数据卷保留）
  status        看端口、MySQL、capabilities
  init          生成 scripts/local.env（仅本机 Maven 启动）
  start         本机 Maven 启动 Java（需要本机 JDK；日常请用 docker）
  stop          停掉占用 8790 的本机进程（不管 Docker 容器）
  env           只打印将要生效的配置（脱敏）

模式（start 可选，覆盖默认 mysql,tools；一键 docker 不用这些）
  mysql       mysql,no-dashscope
  dashscope   mysql,dashscope,tools
  tools       mysql,tools              （默认）
  full        mysql,tools,mcp

示例
  ./scripts/dev.sh docker
  ./scripts/dev.sh docker-down
  ./scripts/dev.sh status
EOF
}

die() { echo "错误: $*" >&2; exit 1; }

ensure_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    [[ -f "$EXAMPLE" ]] || die "缺少 $EXAMPLE"
    cp "$EXAMPLE" "$ENV_FILE"
    echo "已创建 $ENV_FILE"
  fi
}

upsert_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$ENV_FILE"; then
    local escaped
    escaped="$(printf '%s' "$value" | sed -e 's/[&|]/\\&/g')"
    sed -i.bak "s|^${key}=.*|${key}=${escaped}|" "$ENV_FILE"
    rm -f "${ENV_FILE}.bak"
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

load_env() {
  ensure_env_file
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

fill_generated() {
  if [[ -z "${SRE_MCP_SERVER_PATH:-}" ]]; then
    local path="$ROOT/packages/mcp-templates/dist/server.js"
    upsert_env SRE_MCP_SERVER_PATH "$path"
    export SRE_MCP_SERVER_PATH="$path"
  fi
}

apply_mode() {
  local mode="${1:-}"
  case "$mode" in
    "" ) ;;
    local | test | pre | prod ) export SPRING_PROFILES_ACTIVE="$mode" ;;
    mysql ) export SPRING_PROFILES_ACTIVE='mysql,no-dashscope' ;;
    dashscope ) export SPRING_PROFILES_ACTIVE='local,dashscope' ;;
    tools ) export SPRING_PROFILES_ACTIVE='local' ;;
    full ) export SPRING_PROFILES_ACTIVE='local,mcp' ;;
    * ) die "未知模式: $mode（可用 local|test|pre|prod|mysql|dashscope|tools|full）" ;;
  esac
}

cmd_init() {
  ensure_env_file
  load_env
  fill_generated
  echo
  echo "下一步："
  echo "  1. 在 sre-investigator-boot/src/main/resources/application-model.yml 填各厂商 Key，或启动后到页面「模型」配置"
  echo "  2. 环境：默认 local（application-local.yml）"
  echo "  3. 远程改 application-test|pre|prod.yml；日常请用 ./scripts/dev.sh docker"
}

upsert_dotenv() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$DOTENV"; then
    local escaped
    escaped="$(printf '%s' "$value" | sed -e 's/[&|]/\\&/g')"
    sed -i.bak "s|^${key}=.*|${key}=${escaped}|" "$DOTENV"
    rm -f "${DOTENV}.bak"
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$DOTENV"
  fi
}

dotenv_value() {
  local key="$1"
  local line
  line="$(grep -E "^${key}=" "$DOTENV" 2>/dev/null | tail -n1 || true)"
  printf '%s' "${line#*=}"
}

ensure_dotenv() {
  if [[ ! -f "$DOTENV" ]]; then
    [[ -f "$DOTENV_EXAMPLE" ]] || die "缺少 $DOTENV_EXAMPLE"
    cp "$DOTENV_EXAMPLE" "$DOTENV"
    echo "已创建 $DOTENV"
  fi
  local key
  key="$(dotenv_value INVESTIGATOR_MASTER_KEY)"
  if [[ -z "$key" ]]; then
    if command -v openssl >/dev/null 2>&1; then
      key="$(openssl rand -base64 32 | tr -d '\n')"
    else
      key="$(python3 -c 'import os,base64; print(base64.b64encode(os.urandom(32)).decode())')"
    fi
    upsert_dotenv INVESTIGATOR_MASTER_KEY "$key"
    echo "已写入 INVESTIGATOR_MASTER_KEY（页面加密 Binding / 模型 Key 用）"
  fi
}

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

compose_app() {
  docker compose --env-file "$DOTENV" -f "$COMPOSE_FILE" --profile app "$@"
}

cmd_docker() {
  command -v docker >/dev/null 2>&1 || die "需要 Docker"
  docker compose version >/dev/null 2>&1 || die "需要 docker compose"
  ensure_dotenv
  local pids
  pids="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    die "${PORT} 已被占用（PID $pids）。Docker 一键会映射 8790，请先停掉占用进程后再跑"
  fi
  echo "构建并启动 mysql + redis + 控制面容器..."
  compose_app up -d --build mysql redis app
  echo "等待 http://localhost:${PORT}/api/health ..."
  local i
  for i in $(seq 1 90); do
    if curl -fsS "http://localhost:${PORT}/api/health" >/dev/null 2>&1; then
      echo
      echo "已就绪：http://localhost:${PORT}/"
      echo "登录用户: $(dotenv_value INVESTIGATOR_BOOTSTRAP_USER)"
      echo "登录密码: $(dotenv_value INVESTIGATOR_BOOTSTRAP_PASSWORD)"
      echo "模型 Key 可写在 .env 的 INVESTIGATOR_MODEL_DASHSCOPE_API_KEY，或登录后到「模型」页填写。"
      echo "停容器：./scripts/dev.sh docker-down"
      return
    fi
    sleep 2
  done
  echo "仍未通过健康检查。看日志：docker compose -f deploy/docker-compose.yml --profile app logs --tail=80 app" >&2
  exit 1
}

cmd_docker_down() {
  echo "停止 compose 中的 app / mysql / redis（数据卷保留）..."
  compose --profile app stop app mysql redis 2>/dev/null || compose stop mysql redis
}

cmd_stop() {
  local pids
  pids="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)"
  if [[ -z "$pids" ]]; then
    echo "8790 没有进程"
    return
  fi
  echo "停止占用 ${PORT} 的进程: $pids"
  kill $pids 2>/dev/null || true
  sleep 1
  pids="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    kill -9 $pids 2>/dev/null || true
  fi
}

cmd_status() {
  echo "端口 ${PORT}:"
  lsof -nP -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null || echo "  未监听"
  echo
  echo "控制面 MySQL:"
  docker compose -f "$COMPOSE_FILE" ps mysql 2>/dev/null || echo "  compose 未运行"
  echo
  if curl -fsS "http://localhost:${PORT}/api/health" >/dev/null 2>&1; then
    echo "health:"
    curl -sS "http://localhost:${PORT}/api/health"
    echo
    echo "capabilities:"
    curl -sS "http://localhost:${PORT}/api/agent/capabilities"
    echo
  else
    echo "Java 服务未就绪: http://localhost:${PORT}/api/health"
  fi
}

cmd_env() {
  load_env
  fill_generated
  python3 - <<'PY'
import os
keys = [
    "SPRING_PROFILES_ACTIVE",
    "SRE_MCP_SERVER_PATH",
]
for key in keys:
    value = os.environ.get(key, "")
    shown = value or "(空)"
    print(f"{key}={shown}")
PY
}

cmd_start() {
  load_env
  fill_generated
  apply_mode "${1:-}"
  cmd_stop
  echo "启动控制面 MySQL..."
  docker compose -f "$COMPOSE_FILE" up -d mysql
  if lsof -nP -iTCP:6379 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "本机 6379 已有 Redis，登录 Session 用 redis://127.0.0.1:6379/2"
  else
    echo "启动控制面 Redis（无密码；Session 用 2 库）..."
    docker compose -f "$COMPOSE_FILE" up -d redis
  fi
  if [[ ",${SPRING_PROFILES_ACTIVE}," == *",mcp,"* ]]; then
    echo "构建 Node MCP..."
    pnpm --filter @sre-investigator/mcp-templates build
  fi
  echo "编译并启动 Java  Profile=${SPRING_PROFILES_ACTIVE:-local}"
  echo "页面: http://localhost:${PORT}/"
  (cd "$ROOT" && mvn -pl sre-investigator-boot -am install -DskipTests)
  (cd "$ROOT" && mvn -f sre-investigator-boot/pom.xml spring-boot:run)
}

main() {
  cd "$ROOT"
  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    init ) cmd_init ;;
    start ) cmd_start "${1:-}" ;;
    docker ) cmd_docker ;;
    docker-down ) cmd_docker_down ;;
    stop ) cmd_stop ;;
    status ) cmd_status ;;
    env ) cmd_env ;;
    -h|--help|help|"" ) usage ;;
    * ) die "未知命令: $cmd" ;;
  esac
}

main "$@"
