#!/usr/bin/env bash
# 修改本机 Docker MySQL 账号密码（root 或普通用户）。
# 默认找映射了 3306 的容器（当前常见：mysql9）；也可用 -c 指定。
#
# 用法:
#   ./scripts/mysql-change-password.sh -n '新密码'
#   ./scripts/mysql-change-password.sh -c mysql9 -u root -o '旧密码' -n '新密码'
#   ./scripts/mysql-change-password.sh -c mysql9 -u root -n '新密码' --forgot
#   ./scripts/mysql-change-password.sh -c mysql9 -u sre_investigator -o '旧' -n '新'
#
# --forgot：忘记旧密码时，临时跳过权限改密（会短暂重启容器）。

set -euo pipefail

CONTAINER=""
USER_NAME="root"
OLD_PASSWORD=""
NEW_PASSWORD=""
FORGOT=0
HOST="%"

usage() {
  cat <<'EOF'
用法: ./scripts/mysql-change-password.sh -n <新密码> [选项]

选项
  -c, --container   容器名（默认自动选映射了 3306 的 mysql 容器）
  -u, --user        要改的账号，默认 root
  -o, --old         当前密码（知道旧密码时必填，除非 --forgot）
  -n, --new         新密码（必填）
  -h, --host        MySQL 用户 host，默认 %（也改 localhost）
      --forgot      忘记旧密码：skip-grant-tables 临时改密后恢复
      --help        显示帮助

示例
  ./scripts/mysql-change-password.sh -n 'MyNewPass'
  ./scripts/mysql-change-password.sh -c mysql9 -u root -o '123456' -n 'MyNewPass'
  ./scripts/mysql-change-password.sh -c mysql9 -u root -n 'MyNewPass' --forgot
EOF
}

die() { echo "错误: $*" >&2; exit 1; }
info() { echo "$*"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    -c|--container) CONTAINER="${2:-}"; shift 2 ;;
    -u|--user) USER_NAME="${2:-}"; shift 2 ;;
    -o|--old) OLD_PASSWORD="${2:-}"; shift 2 ;;
    -n|--new) NEW_PASSWORD="${2:-}"; shift 2 ;;
    -h|--host) HOST="${2:-}"; shift 2 ;;
    --forgot) FORGOT=1; shift ;;
    --help) usage; exit 0 ;;
    *) die "未知参数: $1（--help 看用法）" ;;
  esac
done

[[ -n "$NEW_PASSWORD" ]] || { usage; die "必须用 -n 指定新密码"; }
[[ "$FORGOT" -eq 1 || -n "$OLD_PASSWORD" ]] || die "请提供 -o 旧密码，或加 --forgot"

need_bin() {
  command -v "$1" >/dev/null 2>&1 || die "找不到命令: $1"
}

need_bin docker

detect_container() {
  local name
  # 优先：宿主机 3306 -> 容器 3306
  name="$(docker ps --format '{{.Names}}\t{{.Ports}}' \
    | awk -F'\t' '$2 ~ /(^|,)0\.0\.0\.0:3306->3306\/tcp/ {print $1; exit}')"
  if [[ -n "$name" ]]; then
    echo "$name"
    return
  fi
  # 其次：任意运行中的 mysql 镜像
  name="$(docker ps --format '{{.Names}}\t{{.Image}}' \
    | awk -F'\t' 'tolower($2) ~ /mysql/ {print $1; exit}')"
  if [[ -n "$name" ]]; then
    echo "$name"
    return
  fi
  return 1
}

if [[ -z "$CONTAINER" ]]; then
  CONTAINER="$(detect_container)" || die "找不到运行中的 MySQL 容器，请用 -c 指定（例如 mysql9）"
fi

docker inspect "$CONTAINER" >/dev/null 2>&1 || die "容器不存在: $CONTAINER"
[[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" == "true" ]] \
  || die "容器未运行: $CONTAINER（先 docker start $CONTAINER）"

info "目标容器: $CONTAINER"
info "账号: $USER_NAME"
info "新密码长度: ${#NEW_PASSWORD}"

sql_escape() {
  # 单引号加倍，供 SQL 字面量使用
  printf "%s" "${1//\'/\'\'}"
}

USER_ESC="$(sql_escape "$USER_NAME")"
NEW_ESC="$(sql_escape "$NEW_PASSWORD")"
HOST_ESC="$(sql_escape "$HOST")"

alter_sql() {
  cat <<SQL
ALTER USER '${USER_ESC}'@'${HOST_ESC}' IDENTIFIED BY '${NEW_ESC}';
ALTER USER '${USER_ESC}'@'localhost' IDENTIFIED BY '${NEW_ESC}';
FLUSH PRIVILEGES;
SELECT user, host FROM mysql.user WHERE user='${USER_ESC}';
SQL
}

run_mysql() {
  # stdin = SQL；其余参数传给 mysql 客户端
  docker exec -i "$CONTAINER" mysql --protocol=TCP -h127.0.0.1 "$@"
}

change_with_old_password() {
  info "使用旧密码登录并改密..."
  if ! alter_sql | run_mysql -u"$USER_NAME" -p"$OLD_PASSWORD"; then
    die "旧密码登录失败。可改用: $0 -c $CONTAINER -u $USER_NAME -n '新密码' --forgot"
  fi
}

wait_mysql_ready() {
  local i
  for i in $(seq 1 40); do
    if docker exec "$CONTAINER" mysqladmin ping -h127.0.0.1 --silent >/dev/null 2>&1 \
      || docker exec "$CONTAINER" mysqladmin ping -h127.0.0.1 -uroot --silent >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# 读取容器当前启动命令，便于 --forgot 后恢复
ORIGINAL_CMD="$(docker inspect -f '{{json .Config.Cmd}}' "$CONTAINER")"
ORIGINAL_ENTRYPOINT="$(docker inspect -f '{{json .Config.Entrypoint}}' "$CONTAINER")"

change_forgot() {
  [[ "$USER_NAME" == "root" ]] || die "--forgot 目前只支持改 root；普通用户请先用 root 改，或提供 -o"

  info "忘记旧密码模式：将短暂重启容器（skip-grant-tables）..."
  info "当前 Cmd: $ORIGINAL_CMD"

  # 用同名容器临时重建不安全：优先 docker commit + 临时容器？更稳的做法是：
  # docker run --rm --volumes-from 同 volume。但用户容器多为匿名/命名 volume 挂载。
  # 对「已有运行容器」最实用：stop → 同 volume 起临时 skip-grant 容器改密 → 再 start 原容器。

  local mounts project_args=()
  mounts="$(docker inspect -f '{{range .Mounts}}{{.Source}}={{.Destination}} {{end}}' "$CONTAINER")"
  [[ -n "$mounts" ]] || die "容器没有挂载数据卷，无法安全 --forgot"

  local image network
  image="$(docker inspect -f '{{.Config.Image}}' "$CONTAINER")"
  network="$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$CONTAINER" | awk '{print $1}')"

  local tmp="mysql-pwreset-$$"
  info "停止原容器 $CONTAINER ..."
  docker stop "$CONTAINER" >/dev/null

  # 拼 -v 参数（只挂载 Destination 为 /var/lib/mysql 的卷，避免乱挂）
  local src dst vol_args=()
  while read -r src dst; do
    [[ -n "$src" && -n "$dst" ]] || continue
    if [[ "$dst" == "/var/lib/mysql" || "$dst" == /var/lib/mysql/* ]]; then
      vol_args+=(-v "$src:$dst")
    fi
  done < <(docker inspect -f '{{range .Mounts}}{{.Source}} {{.Destination}}{{"\n"}}{{end}}' "$CONTAINER")

  [[ ${#vol_args[@]} -gt 0 ]] || {
    docker start "$CONTAINER" >/dev/null || true
    die "未找到 /var/lib/mysql 挂载，已尝试拉起原容器"
  }

  info "启动临时容器 $tmp（skip-grant-tables）..."
  local net_args=()
  [[ -n "$network" ]] && net_args=(--network "$network")

  docker run -d --name "$tmp" \
    "${vol_args[@]}" \
    "${net_args[@]}" \
    "$image" \
    --skip-grant-tables --skip-networking >/dev/null

  cleanup_tmp() {
    docker rm -f "$tmp" >/dev/null 2>&1 || true
    docker start "$CONTAINER" >/dev/null 2>&1 || true
  }
  trap cleanup_tmp EXIT

  sleep 3
  info "等待临时 MySQL 就绪..."
  local i
  for i in $(seq 1 40); do
    if docker exec "$tmp" mysqladmin ping -h127.0.0.1 --silent >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  info "无密码改密..."
  # skip-grant-tables 下先 flush，再 alter
  docker exec -i "$tmp" mysql -uroot <<SQL
FLUSH PRIVILEGES;
ALTER USER '${USER_ESC}'@'${HOST_ESC}' IDENTIFIED BY '${NEW_ESC}';
ALTER USER '${USER_ESC}'@'localhost' IDENTIFIED BY '${NEW_ESC}';
FLUSH PRIVILEGES;
SELECT user, host FROM mysql.user WHERE user='${USER_ESC}';
SQL

  info "清理临时容器并启动原容器..."
  docker rm -f "$tmp" >/dev/null
  docker start "$CONTAINER" >/dev/null
  trap - EXIT

  wait_mysql_ready || die "原容器已启动，但 MySQL 尚未就绪，请稍后手动验证"
}

verify_login() {
  info "用新密码验证登录..."
  if echo 'SELECT 1 AS ok;' | run_mysql -u"$USER_NAME" -p"$NEW_PASSWORD" >/dev/null; then
    info "验证成功。"
  else
    die "改密可能成功，但新密码验证失败，请检查账号 host（可用 -h localhost 再试）"
  fi
}

if [[ "$FORGOT" -eq 1 ]]; then
  change_forgot
else
  change_with_old_password
fi

verify_login

cat <<EOF

完成。请同步更新本机配置，例如:
  sre-investigator-boot/src/main/resources/application-local.yml
    spring.datasource.password: <新密码>

若用的是 deploy/docker-compose.yml 里的库，还要改:
  MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD（仅新 volume 生效）
  healthcheck 里的 -proot

注意: Docker 环境变量里的 MYSQL_ROOT_PASSWORD 不会在已有数据卷上自动改口令；
以本次 ALTER USER 为准。
EOF
