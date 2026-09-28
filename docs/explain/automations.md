# 定时任务与 K8s 巡检

调度跑在 **本进程内**（`@Scheduled` 每 60 秒扫一次到期任务），不是系统 crontab。Java 没起来就不会跑。改完代码或 Console 后需重启 **8790**。

对齐 [OpenClaw Automations](https://github.com/openclaw/openclaw/blob/main/docs/automation/cron-jobs.md) 的模型：任务定义 + 运行状态 + 执行历史落控制面 MySQL；间隔 / cron / 一次性。执行仍复用对话落库（消息 / run），但会话 `origin` 标成 `AUTOMATION`，不会出现在对话页历史里。详情走定时二级页。

## 调度

| 类型 | 表达式 | 首次 `next_run_at` |
|------|--------|-------------------|
| `EVERY` | `15m` / `1h` / `1d` | 保存时刻 + 间隔（不立刻跑） |
| `CRON` | 5 或 6 段，如 `0 9 * * *` | 下一命中，时区默认 `Asia/Shanghai` |
| `AT` | ISO-8601 | 指定时刻；成功后停用 |

同一任务若仍在跑（或未超过 `timeoutSeconds`）则跳过重叠。手动「立即执行」随时可跑。启动后到期任务会在第一次扫描时补跑。

一次性成功后停用并保留记录（对应 OpenClaw 的 `deleteAfterRun`）。

## 载荷

- **`K8S_INSPECT`**（默认）：只 GET。扫 NotReady 节点、CrashLoop / OOM / ImagePull、重启 ≥ 5、Warning 事件。报告写入定时会话，不调模型。
- **`AGENT_TURN`**：按提示词走现有 INVESTIGATE，会话同样标 `AUTOMATION`。

巡检需要当前环境已启用的 K8s Binding。发现不会自动升级成排查（`escalateOnFinding` 未开）。

## 权限

菜单 `menu.automations`（侧栏「定时」），默认给超级管理员和运维。

## API

`GET /api/automations?environment=`：跨可见项目列出当前环境的任务。详情 `GET /api/automations/{jobId}/executions/{executionId}`。

写操作仍走 `/api/projects/{projectId}/automations`：新建 / 更新 / 启停 / 删除 / `POST {id}/run` / 执行历史。

控制台路径：`/automations`，详情 `/automations/:jobId/executions/:executionId`。顶栏不选项目，切环境会重刷任务列表。新建时在表单里选所属项目。
