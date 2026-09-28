# 编排现状与缺口

配套图：[`orchestration-architecture.html`](./orchestration-architecture.html)（浏览器打开，右上角可导出 PNG / PDF）。

一句话：**会话层先改写问题，策略再分流；INVESTIGATE 走一个证据驱动的循环 Graph（investigate ⇄ decide），每轮把已累积的证据/账本回喂模型，直到结论充分或预算耗尽。**

## 主路径

```
对话页 → POST /api/investigations/stream
      → RunService（UsageContext + AgentThoughts）
      → ConversationSessionService 改写 question
      → ASK 直连 Agent
        或 Graph: route → load_context → investigate ⇄ decide(条件边) → synthesize
      → ChatModel.call（模型内工具循环，带重试）
      → synthesize 解析出结构化四段（reportStructured）
      → 落库 COMPLETED / NEEDS_INPUT
```

### 循环 Graph（本次重构）

`investigate` 不再是单次黑盒调用，而是循环 Graph 的一轮 plan+act+observe：

- 每轮结束由条件边 `decide`（`InvestigationGraph.decideNext`）判定：**模型已给四段结论 / 预算耗尽 / 达到 `investigator.agent.max-iterations`（默认 3）/ 点名 MCP / 子 Agent** → 去 `synthesize`；否则回 `investigate` 再取一轮。
- 跨轮状态用 `InvestigationState`（`graph.state` 包）累积：证据、假设、工具账本（去重）、待验证问题、预算；`toContext()` 把这些回喂下一轮的 `memoryPrompt`，让模型在已有进展上继续，而不是从零重来。
- 常见场景（首轮就给出四段结论）行为不变，只有「首轮没结论」时才会进入第二、三轮。
- 模型调用经 `ResilientModelCall` 做重试 + 指数退避（`investigator.agent.model-max-attempts` / `model-retry-backoff-ms`），扛住 dashscope/openai 的瞬时 I/O 报错，不再一抖动就整轮废。

- **ASK**：System 是助手政策；User 是环境 + 仓摘要 + `@` 预读 + 历史问题。工具只有 `code_read` / `code_search`。不装 Skill、Rule、库、缓存。
- **INVESTIGATE**：System 是只读排查政策。User 一层堆叠 Brief、工具目录、Skill、Rule、`@` 预读。工具只注册**本轮方向白名单 ∩ 已启用 Binding**（问句关键词可加种类）。点名 MCP / 子 Agent / 业务流程图不闸。`synthesize` 在预算用尽或缺少「已确认的证据」时再无工具补写四段结论。
- **CODE**：注册了，`enabled=false`。

## 排查方向

方向表在 `application.yml` 的 `investigator.routes`（也可只改配置、不改代码）。默认两项：`http-5xx`（接口/可用性）、`order-payment`（订单/支付）。`always` 只给 Skill 用，不是可选方向。

INVESTIGATE 时 `route` 节点在 `load_context` 之前判定方向：

1. 用户在对话页选了方向，且 id 在配置表里 → 直接用，不调分类模型。
2. 未选（自动判断）→ 用当前模型对照配置表分类（无工具，只输出 id）。
3. 模型不可用或答了未知 id → 按配置表 keywords 匹配。
4. 仍无命中 → `investigator.routes.fallback`（默认 `http-5xx`）。

方向还可配 `tool-kinds`。默认：`http-5xx` 开 CODE/LOGS/MSE/ARMS/LOAD_BALANCER/MYSQL/K8S；`order-payment` 开 CODE/MYSQL/REDIS。问句出现 pod/日志/订单/数仓/CDN 回源等词只会**加**种类，不减。

**软门控（本次重构）**：方向白名单只作用于**第一轮**。若首轮没能给出结论，循环 Graph 的后续轮次会把工具**放开到本环境全部已启用 Binding**（`widenProfilesByKind`），避免开局按方向锁死后中途够不到日志/数仓等工具。点名 MCP / 子 Agent 仍是单轮，不参与放开。

`GET /api/agent/routes` 把配置表交给对话页和 Skill「适用路由」下拉。

加方向：在 yml 里加一项（含 `tool-kinds`），并把对应 Skill 的场景设成同一个 id，然后重启 Java。

## 材料怎么进模型

| 材料 | 进哪 | 怎么选 |
|------|------|--------|
| 系统政策 | System | ASK 一套、INVESTIGATE 一套（yml 可覆盖） |
| 多轮历史 / 续排查 / 附件 | 进策略前拼进 question | 会话摘要 + 最近 2 轮原文（每条最多 800 字）+ 项目记忆；Brief 只收当前问句 |
| Skill | 仅 INVESTIGATE / REVIEW 的 user | 点名 slug 只装一条。有场景命中（scene = 方向 id）只装这些；否则 investigate 且场景为空的最多 2 条。`other` 只在点名时装。入口截 800 字，附属文件用 `skill_read` |
| Rule | 仅 INVESTIGATE 的 user | 只读 DB，按类型 investigate/review 过滤，各截 800 字 |
| Binding | 工具回调 + 文字目录 | 先取当前环境已启用 Binding，再按方向 `tool-kinds` + 问句加种收窄。点名 MCP 只留一个 profile |
| `@类名` | user 预读块 | 入口约 180 行，再跟最多 6 个 Mapper / DAO 邻居 |

## 缺口

| 优先级 | 缺口 | 为什么急 | 建议 |
|--------|------|----------|------|
| P0 | ~~日志没进 Agent~~ | 已做：`LOGS` Binding + `sls_*`；MSE `mse_*`；K8s `k8s_*`（Kuboard 只读：节点/Pod/事件/日志） | ES 仍未接线；官方可观测 MCP 为可选 `obs-mcp`；K8s 不写、不重启；定时只读巡检见 [`automations.md`](./automations.md) |
| P1 | ~~会话记忆从不写~~ | 已做：STM 最近 2 轮 / Episodic 摘要 / LTM 事实+Qdrant | Redis、Qdrant 可选，挂了回退 MySQL |
| P1 | 提示词一层堆叠 | 政策仍可能被 Brief / Rule / 预读冲淡 | System 放政策 + Rule；User 放 Brief + 预读 |
| P2 | ~~Graph 无分支~~ | 已做：`investigate ⇄ decide` 条件边循环 + `InvestigationState` 结构化累积 + 软门控放开 | 缺 ID 先澄清仍未做 |
| P2 | MCP 工具面未独立 | 只读工具仍以进程内 `FunctionToolCallback` 装载；工具名已收敛到单一来源 `ReadonlyToolCatalog`（43 项，含 ARMS / LB / CDN） | 下一步：加 `spring-ai-starter-mcp-server`，把 `Readonly*` 按 `ReadonlyToolCatalog` 暴露为 MCP 工具（入参带 project/env/profile），编排端经 MCP client 统一内外工具。需真机验证启动与传输，未在本轮接线 |
| P2 | 半成品 | CODE 禁用；Mongo / 图片 / `schema` / `scanPrefix` 未暴露 | 按业务再开，不挡主路径 |

## 建议顺序

1. ~~排查方向可选，或模型先分类再装 Skill。~~ 已做。
2. ~~接一种只读日志。~~ 已做 SLS；MSE 网关/Nacos/治理只读已接线；K8s（Kuboard）只读已接线。
3. ~~写 `memorySummary`，历史只留最近两轮原文。~~ 已做。
4. 提示词分层：System = 政策 + Rule；User = Brief + `@` 预读。

ASK 问「查这个用户订单」会查不到库。要么标明 ASK 只能讲代码，要么给只读数据开关。
