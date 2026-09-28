# 配置文件怎么用

技术栈、架构和**推荐启动方式**见仓库根 [README.md](../README.md)。本机逐步操作见 [local-setup.md](./local-setup.md)。

每个环境文件里合写 MySQL，以及**一套** Redis：

```yaml
spring.data.redis:
  host / port / username / password   # 连接与账号密码共用
  session.database                    # Session 库号，默认 2
  memory.database                     # 记忆库号，默认 1
```

无密码可留空 `username` / `password`；Redis 6 ACL 可同时填账号密码，旧版只填 `password` 即可。

## 默认怎么跑

IDEA 不填 Active profiles → **`local`**（自动带 `mysql` + `tools`）。空库可导入 `docs/sql/sre_investigator_init.sql`。

> 控制面读写依赖 Profile **`mysql`**；`local/test/pre/prod` 分组已包含。只开 `tools` 会走内存仓，页面项目为空。

| 环境 | 文件 |
|---|---|
| local | `application-local.yml` |
| test | `application-test.yml` |
| pre | `application-pre.yml` |
| prod | `application-prod.yml` |

```bash
export SPRING_PROFILES_ACTIVE=local          # 默认可省略
export SPRING_PROFILES_ACTIVE=test            # 改 application-test.yml 里的主机口令
export SPRING_PROFILES_ACTIVE=local,mcp
# Docker 一键：local,docker
```

## 文件地图

| 文件 | 作用 |
|---|---|
| `application.yml` | 公共配置 + 环境分组 |
| `application-local.yml` | 本机 MySQL + Redis |
| `application-test\|pre\|prod.yml` | 远程 MySQL + Redis |
| `application-docker.yml` | 容器内主机名 |
| `application-mcp.yml` / `memory.yml` | 可选能力 |
| `application-model.yml` | 模型 Key（本地自建，gitignore） |
| `application-secrets.yml` | 飞书 / master-key（本地自建，gitignore） |
| `application-tools.yml` | 排查目标兜底（本地自建，gitignore） |
| `ALIBABA_CLOUD_ACCESS_KEY_*` | ARMS / ALB / CLB / CDN 全局 RAM 只读 AK，见 [aliyun-readonly-ram.md](./aliyun-readonly-ram.md) |
| `application-mysql.yml` | 旧 `mysql` profile 兼容 |

## 两类库

1. **控制面**：`application-local|test|pre|prod.yml`
2. **排查目标**：对话 Binding；`application-tools.yml` 仅兜底
