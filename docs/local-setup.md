# 本地配置与启动

先看仓库根 [README.md](../README.md) 里的「启动方式（怎么选）」。这里是 **IDEA Debug** 的逐步说明。

日常开发推荐本机 Java，不要用 Docker 再起一份 `app`。只想打开页面、不改代码，用仓库根 `./scripts/dev.sh docker`。

## 依赖

- JDK **17**（IDEA Project SDK 也要 17）
- 本机 MySQL `127.0.0.1:3306`，账号默认 `root` / `123456`
- 本机 Redis `127.0.0.1:6379`（无密码即可；Session 用 db 2）
- 库名 `sre_investigator`：JDBC 已带 `createDatabaseIfNotExist=true`，没有库会自动建库，**不会**靠 Hibernate 建表

没有本机 MySQL / Redis 时，可以只起依赖、Java 仍用 IDEA：

```bash
docker compose -f deploy/docker-compose.yml up -d redis
# MySQL 容器映射的是 33069，要让 IDEA 用它必须改 application-local.yml 的端口，一般不这么干
```

## 空库

二选一，**不要混着跑**（不要既导 init 又指望 Flyway 从 V1 重放）：

```bash
# 推荐：一次导入终态（对齐 V25），启动不再跑 V1～V25
mysql -h127.0.0.1 -P3306 -uroot -p123456 < docs/sql/sre_investigator_init.sql
```

或者什么都不导，直接启动，Flyway 按 `V1`～`V25` 自己建。已有数据的库只走 Flyway，不要再导 init。

## IDEA Debug

1. Open 仓库根 `pom.xml`，SDK Java 17。
2. 运行配置：`sre-investigator-boot` → `com.maiya.investigator.boot.InvestigatorApplication`。
3. **Active profiles 留空**（默认 `local` = `mysql` + `tools`）。不要填 `docker`。
4. http://localhost:8790/ ，账号 `admin` / `admin123456`。
5. 改了 Java、`application-*.yml`、secrets **必须重启 Debug**。

换测试 RDS：运行配置里加 `SPRING_PROFILES_ACTIVE=test`，并保证 `application-test.yml` 里的主机口令正确。Profile 含义见 [config-profiles.md](./config-profiles.md)。

## 本地密钥（不要提交）

在 `sre-investigator-boot/src/main/resources/` 自建，均已 gitignore：

**`application-secrets.yml`**

```yaml
investigator:
  secrets:
    master-key: "Base64 的 32 字节 AES 密钥"   # 换了之后以前加密保存的 Binding / 模型 Key 解不开
  aliyun:
    access-key-id: LTAI...                      # 也可改用环境变量，见下
    access-key-secret: "..."
  auth:
    feishu:
      app-id: ""
      app-secret: ""
```

**`application-model.yml`**：DashScope / OpenAI 兼容网关等。不配也能打开页面，排查会降级成「模型未就绪」。配好后也可在控制台「模型」页改。

**ARMS / 负载均衡 / CDN** 不在工具卡上填 AK，三选一即可：

- secrets 里的 `investigator.aliyun`（如上）
- 环境变量 `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET`（IDEA 运行配置 Environment）
- 最小 RAM 策略：[aliyun-readonly-ram.md](./aliyun-readonly-ram.md)

SLS、MSE、MySQL、Redis、K8s 等仍在**工具页每条 Binding**里填只读账号，保存后加密进控制面库。

## 改控制台 UI

源码在 `apps/console`。两种方式：

1. Java 已在 8790 时：仓库根 `pnpm --filter @sre-investigator/console dev`，打开 Vite http://localhost:5173/（`/api` 代理到 8790）。
2. 构建打进静态资源后再重启 Java：`pnpm --filter @sre-investigator/console build`（输出到 `sre-investigator-boot/src/main/resources/static`）。

## 失败时

| 现象 | 处理 |
|---|---|
| 8790 占用 | 停掉本机 Java 或 `./scripts/dev.sh docker-down`，不要两套一起跑 |
| MySQL 连不上 | 确认本机 3306 和 root 口令；不要拿 Docker 的 33069 当 3306 |
| Redis 连不上 | 起本机 6379，或 `docker compose -f deploy/docker-compose.yml up -d redis` |
| `未设置 investigator.secrets.master-key` | 建 `application-secrets.yml` 填一把 AES 主密钥 |
| `未配置全局阿里云 RAM 只读 AK` | 配 `investigator.aliyun` 或 `ALIBABA_CLOUD_ACCESS_KEY_*` 后重启 |
| 页面项目是空的 | 确认 profile 带了 `mysql`（`local` 默认有）；不要只开 `tools` |
| 改了代码页面没变 | Java 改动要重启 Debug；前端要 dev 或重新 build |

飞书逐步说明：[feishu-group.md](./feishu-group.md)。接入业务项目：[onboarding-project.md](./onboarding-project.md)。
