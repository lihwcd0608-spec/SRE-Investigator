# 工具绑定监控功能（MySQL 指标采集）实施计划

## 需求理解

在「工具配置」页，对**高权限账号**开放「监控」开关。以 MySQL 为例，开启后可采集并展示 CPU（负载代理）、内存使用率、慢查询数、连接数、QPS 等只读指标；后续可扩展到 Redis、MongoDB 等。

核心约束：
- **只读**：所有采集走现有 `mysql_query_readonly` 同款 `ReadOnlySqlGuard`，不执行任何写操作。
- **权限隔离**：仅 `SUPER_ADMIN`（或拥有新权限 `action.bindings.monitor` 的账号）可见/可开。当前 V25 已把 `menu.bindings` 收回给超级管理员，运维角色连工具页都看不到，所以"权限高的账号"即超级管理员。
- **不影响排查主路径**：监控是旁路能力，不进 Agent 工具循环、不注入提示词。

## 仓库调研结论

- **数据模型**：`domain/control/ToolBinding.java`（record）+ `infra/persistence/ToolBindingEntity.java`（JPA）。当前字段无监控相关列。Flyway 已到 V25，表名前缀 `investigator_`。
- **权限体系**：`domain/auth/Permissions.java` 定义菜单级（`menu.bindings`）+ 动作级（`action.bindings.write` / `delete`）。`@RequirePermission` 注解 + `PermissionInterceptor`。前端 `Me.permissions` 含权限码，`hasPermission(code)` 判断。V25 迁移把 `menu.bindings` 只授给 `SUPER_ADMIN`。
- **MySQL 工具现状**：`ReadonlyToolCatalog` 中 MySQL 仅 `mysql_query_readonly` 一项，由 `ReadonlyMysqlTool.query()` + `ReadOnlySqlGuard`（只允许 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH，拒多语句、拒 `FOR UPDATE`）守卫。模型理论上已能查 `performance_schema` / `sys` / `SHOW GLOBAL STATUS`，但无结构化监控视图。
- **可复用模式**：`boot/automation/K8sInspectionScanner.java` 是只读巡检的范例——按 Binding 扫描、返回 findings。可参考其结构写 `MysqlMetricsCollector`。
- **调度机制**：`boot/automation/AutomationScheduler.java` 用 `@Scheduled(fixedDelay=60s)` 扫到期任务。监控可复用同款 `@Scheduled`，不必走完整 AutomationJob 框架。
- **前端**：`BindingsPage.tsx` 的 composer 面板编辑 MCP 连接；`ToolMetaFields.tsx` 按工具种类渲染表单。`mcp/connection.ts` 的 `McpForm`/`buildMcpConfiguration` 构造提交体。

**MySQL 可采集的只读指标**（全部走 SELECT/SHOW）：
| 指标 | 查询 |
|---|---|
| 连接数 | `SHOW STATUS LIKE 'Threads_connected'` vs `max_connections` |
| 活跃线程（CPU 代理） | `SHOW STATUS LIKE 'Threads_running'` |
| 内存使用率 | `Innodb_buffer_pool_bytes_data / innodb_buffer_pool_size`；`performance_schema.memory_summary_global_by_event_name` 求和 |
| 慢查询累计 | `SHOW GLOBAL STATUS LIKE 'Slow_queries'` |
| Top 慢 SQL | `performance_schema.events_statements_summary_by_digest ORDER BY SUM_TIMER_WAIT DESC LIMIT 10` |
| 长事务/长查询 | `information_schema.processlist WHERE COMMAND <> 'Sleep' AND TIME > 10` |
| QPS | `SHOW STATUS LIKE 'Questions'`（与上次快照差值 / 秒） |
| InnoDB 磁盘读 | `Innodb_buffer_pool_reads`（越大越差） |

## 文件与模块变更

| 文件 | 变更 |
|---|---|
| `sre-investigator-infra/src/main/resources/db/migration/V26__binding_monitoring.sql` | 新增：binding 表加 `monitoring_enabled` / `monitoring_metrics` 列；新建 `investigator_binding_metric` 时序表；插入 `action.bindings.monitor` 权限并授给 SUPER_ADMIN |
| `sre-investigator-domain/src/main/java/com/maiya/investigator/domain/control/ToolBinding.java` | record 增加 `monitoringEnabled`、`monitoringMetrics` 字段及 `withMonitoring(...)` |
| `sre-investigator-domain/src/main/java/com/maiya/investigator/domain/auth/Permissions.java` | 新增 `BINDINGS_MONITOR = "action.bindings.monitor"` |
| `sre-investigator-infra/.../persistence/ToolBindingEntity.java` | 加两列映射 + 构造器更新 |
| `sre-investigator-infra/.../persistence/MysqlControlPlaneStore.java`（或对应 store） | 持久化时写入/读出监控字段 |
| `sre-investigator-infra/.../persistence/BindingMetricEntity.java` | 新增：时序快照实体（bindingId, metricKey, metricValue, collectedAt） |
| `sre-investigator-infra/.../persistence/BindingMetricRepository.java` | 新增：JPA Repository（存、按 binding+metric 查最近 N 条、清理过期） |
| `sre-investigator-mcp-servers/.../mcp/MysqlMetricsCollector.java` | 新增：只读采集服务，返回 `MetricSnapshot`（key→{value,unit,label}） |
| `sre-investigator-boot/.../config/ControlPlaneController.java` | 新增 `GET /bindings/{id}/metrics`、`GET /bindings/{id}/metrics/history`；扩展 `PATCH /bindings/{id}` 接收监控配置；全部 `@RequirePermission(BINDINGS_MONITOR)` |
| `sre-investigator-boot/.../monitor/BindingMetricsScheduler.java` | 新增：`@Scheduled(fixedDelay=60s)` 扫启用监控的 binding，采集并落库，保留最近 24h |
| `apps/console/src/types.ts` | `Binding` 加 `monitoringEnabled?`、`monitoringMetrics?` |
| `apps/console/src/pages/BindingsPage.tsx` | composer 内 MYSQL 种类 + 有权限时渲染「监控」区（开关 + 指标多选）；卡片上监控启用时显示状态徽标 + 「查看监控」入口 |
| `apps/console/src/pages/BindingMetricsPanel.tsx` | 新增：弹出面板，拉取当前快照 + 可选趋势，展示指标卡片 |
| `docs/sql/sre_investigator_init.sql` | 对齐 V26（若维护终态导入） |

## 实施步骤（依赖顺序）

1. **Flyway V26 迁移**：`ALTER TABLE investigator_tool_binding ADD COLUMN monitoring_enabled TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN monitoring_metrics LONGTEXT NULL;`；建 `investigator_binding_metric(id BINARY(16), binding_id BINARY(16), metric_key VARCHAR(64), metric_value VARCHAR(128), collected_at DATETIME, INDEX idx_binding_metric_time(binding_id, metric_key, collected_at))`；插入 `action.bindings.monitor` 权限并 `INSERT ... SELECT` 给 `SUPER_ADMIN`。

2. **领域层**：`ToolBinding` record 加字段、`withMonitoring()`；`Permissions.BINDINGS_MONITOR` 常量。

3. **持久层**：`ToolBindingEntity` 加列；`MysqlControlPlaneStore.saveBinding` / 映射逻辑同步读写监控字段；新增 `BindingMetricEntity` + Repository（含 `findTopByBindingIdAndMetricKeyOrderByCollectedAtDesc`、`deleteByCollectedAtBefore`）。

4. **采集器**：`MysqlMetricsCollector.collect(projectId, environment, profile)` — 复用 `ReadonlyMysqlTool.query` 跑一组预定义只读 SQL，组装 `MetricSnapshot`（含连接数、活跃线程、内存率、慢查询数、Top 慢 SQL 列表、QPS、InnoDB 磁盘读）。每个查询独立 try-catch，单项失败不影响整体。QPS 计算：取当前 `Questions` 值与 `BindingMetric` 表中上一条同 binding 的快照做差 / 时间差；无历史则为 null。

5. **API**：
   - `GET /api/projects/{pid}/bindings/{bid}/metrics` → 调 collector 返回当前 `MetricSnapshot`（HTTP 200）。
   - `GET /api/projects/{pid}/bindings/{bid}/metrics/history?metricKey=...&limit=60` → 返回该指标最近 N 条 `{value, collectedAt}`。
   - `PATCH /api/projects/{pid}/bindings/{bid}` 现有请求体扩展 `monitoringEnabled`、`monitoringMetrics`（JSON 数组字符串）；仅当 `BINDINGS_MONITOR` 权限且请求含监控字段时更新。
   三个端点均加 `@RequirePermission(Permissions.BINDINGS_MONITOR)`。

6. **调度器**：`BindingMetricsScheduler`（`@Service` + `@Scheduled(fixedDelay = 60_000)`）。从 `ControlPlaneStore` 拉所有 `monitoring_enabled=true` 的 binding，逐个调 collector，把每个指标值写入 `investigator_binding_metric`；最后清理超过 24h 的历史。Java 进程没起就不跑（同 K8s 巡检约定）。

7. **前端**：
   - `types.ts` 扩展 `Binding`。
   - `BindingsPage.tsx`：composer 中 `if (form.toolKind === "MYSQL" && hasPermission("action.bindings.monitor"))` 渲染监控区——switch 开关 + 指标多选（默认全选：连接数/内存/慢查询/活跃线程/QPS）。保存时随 `configuration` 之外的独立字段提交（`monitoringEnabled`、`monitoringMetrics`）。
   - 卡片：`binding.monitoringEnabled` 为真时在状态条加「监控中」徽标 + 「查看」按钮。
   - 新 `BindingMetricsPanel.tsx`：弹层，拉 `/metrics` 渲染指标卡片（数值 + 单位 + 健康色），慢查询 Top 列表用表格；可选迷你趋势（拉 `/metrics/history` 画简单折线，用 SVG 内联）。
   - 遵循 MASTER.md 暗色规范。

8. **回归与文档**：`docs/sql/sre_investigator_init.sql` 同步 V26 列与表；本地空库可导 init 或让 Flyway 自跑。

## 依赖与注意事项

- **只读边界**：所有采集 SQL 必须经 `ReadOnlySqlGuard.normalize`，不拼接用户输入的表名（指标查询全是固定 SQL 字符串）。慢查询 Top N 的 `LIMIT` 固定。
- **性能**：`@Scheduled` 串行采集，每 binding 约 5–8 条轻量 SQL；启用监控的 binding 数量预期很少。若超时，单 binding 失败不阻塞其余。
- **历史保留**：默认保留 24h，避免表膨胀；可调配置 `investigator.monitoring.retention-hours`。
- **权限**：新权限 `action.bindings.monitor` 只授 SUPER_ADMIN；普通运维连 `menu.bindings` 都没有，天然不可见。
- **兼容**：`monitoring_enabled` 默认 0，旧数据零影响；前端无权限时不渲染监控 UI。
- **QPS 精度**：基于 `Questions` 差值，首次采集无历史返回 null；调度器跑两轮后才有值。
- **CPU 说明**：MySQL 协议无法直接读 OS CPU，用 `Threads_running`（活跃并发数）作负载代理，前端 label 明确标注「活跃线程（负载代理）」。若后续需真实 CPU，需接宿主机/Prometheus，超出本计划。

## 验证

- `mvn -pl sre-investigator-infra -am test`：新增 V26 在空库能成功 apply（Flyway + Hibernate validate）。
- `mvn -pl sre-investigator-boot test`：`ControlPlaneController` 权限——无 `BINDINGS_MONITOR` 调 `/metrics` 返回 403；有则 200。
- `mvn -pl sre-investigator-mcp-servers test`：`MysqlMetricsCollector` 在 mock `ReadonlyMysqlTool` 下能正确组装 snapshot，单条 SQL 失败不中断。
- 手工：登录 super admin → 工具页建 MySQL binding → 开启监控 → 「查看监控」看到指标卡 → 等 1–2 分钟后刷新看到 QPS 有值 → 用普通账号登录确认工具页不可见。
- 前端 `pnpm --filter @sre-investigator/console check` 类型通过。

## 风险

| 风险 | 应对 |
|---|---|
| `performance_schema` / `sys` 在某些 MySQL 实例未开或账号无权限 | collector 每项查询独立 try-catch，无权限的指标返回 `unavailable` 而非报错；前端灰显该指标并提示「账号无 performance_schema 权限」 |
| 慢查询 Top N 查询本身慢 | 固定 `LIMIT 10`，且只在显式开启监控时跑；可加 `investigator.monitoring.slow-query-limit` 配置 |
| 调度器与排查抢连接 | 复用 `ReadonlyMysqlTool` 的临时 `DriverManagerDataSource`，每次用完即弃；采集间隔 60s，压力极小 |
| 时序表增长过快 | 每 binding 每轮约 6 条记录 × 24h ≈ 8640 条/binding，加索引 + 24h 清理；必要时调小间隔或只存关键指标 |
| 权限遗漏导致非管理员能开监控 | `@RequirePermission(BINDINGS_MONITOR)` 在三个端点都加；前端 `hasPermission` 双重守卫 |
