# SRE Investigator 开发指南：上手学习与继续开发

> 定位：把 [README](../README.md)、各专项文档和代码串起来的**主学习路径**。第一次接触本仓库，从这份文档开始；细节再跳转对应专项文档。
> 更新：2026-09-18，对齐 Flyway V25 与当前代码。如与 README 冲突，以 README 为准。

---

## 1. 这个项目是什么

给多个业务项目用的**只读、可审计、按项目/环境隔离**的 SRE 排查 Agent 控制面。用户在 Web 控制台、飞书群 `@机器人` 或 Cursor 里描述故障（例："test 环境 10:20–10:30 for-you 接口 5xx 升高，traceId=xxx"），系统按项目和环境装载 Skill / Rule / 工具 Binding，模型在预算内调用只读工具，输出**带证据**的结构化报告（现象 / 已确认证据 / 已排除 / 假设 / 下一步）。

**明确不做**：写库、清缓存、改 Nacos、重启、发布、回滚。工具面没有写命令。

### 设计原则（改动前先记住）

| 原则 | 含义 |
|---|---|
| 只读优先 | 所有数据工具注册前过 Guard：SQL 拒绝写/多语句/锁定读；Redis 无写、删、`KEYS`；代码工作区 allowlist |
| 项目/环境隔离 | Tool Binding 绑定 `projectId + environment + toolKind + profile`，Agent 只见当前环境已启用的工具 |
| 证据优先 | 结论必须能对应工具结果或预读代码；模型未配置/工具未绑定时输出**降级报告**，不编造事实 |
| 可审计 | 会话、工具调用、配置变更、用量落 MySQL；敏感字段脱敏，不写 AK、不写完整 trace |
| 降级可用 | Redis / Qdrant / 模型任一缺失都不阻断主链路 |

---

## 2. 五分钟心智模型

```
入口层   Web 控制台(:8790) / 飞书群机器人 / Cursor MCP(可选旁路)
           │
控制面   登录(RBAC) → 项目 / 代码仓 / Binding / Skill 资产 / 定时任务 / 审计 / 用量
           │
编排层   问句前置过滤 → 会话改写(记忆) → 策略分流 → 循环 Graph
         route → load_context → investigate ⇄ decide → synthesize
           │
工具层   进程内只读适配器：代码 / MySQL / Redis / SLS / MSE / ARMS /
         ALB·CLB / CDN / K8s / StarRocks / Kafka / RocketMQ / Mongo
           │
存储层   MySQL(控制面, Flyway) + Redis(Session db2 / 记忆 db1) + Qdrant(可选)
```

**一次 INVESTIGATE 排查的生命周期**：

1. 入口（控制台对话页 `POST /api/investigations/stream`，或飞书事件回调）。
2. [InvestigationRunService](../sre-investigator-boot/src/main/java/com/maiya/investigator/boot/InvestigationRunService.java) 打开 `AgentThoughts`（SSE 进度）和 `UsageContext`（用量）两个 ThreadLocal 上下文。
3. **问句前置过滤**（`graph.intent.QuestionIntentGate`）：寒暄、夸奖、无故障信号直接礼貌回复，不跑工具。
4. [ConversationSessionService](../sre-investigator-boot/src/main/java/com/maiya/investigator/boot/ConversationSessionService.java) 改写问题：拼接会话记忆（最近 2 轮原文 + 滚动摘要）+ 项目记忆（LTM 事实）。
5. 按 `ConversationMode` 分流到策略（`ConversationStrategyRegistry`）。
6. INVESTIGATE 走 [InvestigationGraph](../sre-investigator-graph/src/main/java/com/maiya/investigator/graph/InvestigationGraph.java)：
   - `route`：判方向。用户已选 → 直接用；否则模型分类 → 关键词匹配 → fallback。子 Agent / 点名 MCP / 纯代码理解问题有专门捷径。
   - `load_context`：读当前环境启用的 Binding、按方向装载 Skill / Rule、生成工具文字目录。
   - `investigate ⇄ decide`：**循环**。每轮 `InvestigationAgent` 用 `ChatClient` 跑一次模型内工具循环（预算 `max-tool-calls`，默认 100）；`decide` 条件边判定是否再取一轮（已给四段结论 / 预算耗尽 / 达到 `max-iterations` 默认 3 → 去 synthesize）。跨轮状态由 `graph.state.InvestigationState` 累积（证据 / 假设 / 工具账本 / 待验证问题）并回喂。
   - `synthesize`：解析 / 补写**结构化四段报告**（`graph.report.StructuredReport`）。
7. 落库：Run（COMPLETED / NEEDS_INPUT / FAILED）、会话消息、滚动摘要、审计、模型用量。

**会话模式**：

| 模式 | 用途 | 工具 |
|---|---|---|
| INVESTIGATE | 故障排查（主路径） | 当前环境 Binding ∩ 方向白名单（代码/日志/库/缓存/MQ/K8s…） |
| REVIEW | 代码审查，对比 `base..head` | 代码 + Git diff |
| ASK | 代码问答 | 仅 `code_read` / `code_search` |
| CODE | 已注册、`enabled=false` | — |

---

## 3. 仓库地图

```
sre-investigator/
├── apps/
│   ├── console/                  ★ 前端源码（React 18 + Vite 6 + TS）
│   └── harness-api/              早期 TS 原型 Harness（历史遗留，主线不依赖，别加新功能）
├── packages/
│   ├── mcp-templates/            Cursor 用的 Node stdio 只读 MCP（可选旁路）
│   ├── skills/                   SKILL.md 知识库：global/ templates/ projects/
│   ├── domain / runtime-*        TS 原型配套（同 harness-api，历史遗留）
├── sre-investigator-api/         请求/响应 DTO、环境枚举
├── sre-investigator-domain/      领域模型 + 端口接口（不依赖基础设施）
├── sre-investigator-infra/       JPA 适配、Flyway(V1–V25)、AES-GCM、审计、记忆索引
├── sre-investigator-graph/       ★ 编排核心：过滤/路由/Graph/Agent/状态/报告/用量
├── sre-investigator-mcp-servers/ ★ 进程内只读工具适配器（名字是历史原因，不是 MCP Server）
├── sre-investigator-boot/        ★ 进程入口：REST/SSE、RBAC、飞书、定时、托管前端
├── sre-investigator-console/     历史占位模块，控制台源码在 apps/console
├── deploy/docker-compose.yml     开发用依赖（mysql 33069 / redis / 可选 app 容器）
├── scripts/dev.sh                一键启动 / 停止 / 状态脚本
├── docs/                         本文档所在，见 §13 文档索引
└── design-system/sre-investigator/MASTER.md   前端设计规范（OLED 暗色终端风）
```

Java 依赖方向（严禁反向）：`boot → graph → domain ← infra`，`graph/boot → mcp-servers → domain`。domain 是最底层端口层，infra 实现它，boot 组装一切。

---

## 4. 技术栈速查

| 层 | 选型 | 备注 |
|---|---|---|
| 运行时 | Java 17（禁 Preview） | Maven 多模块，入口 `InvestigatorApplication` |
| 后端 | Spring Boot 3.4.5 | Web / Validation / Security |
| 编排 | Spring AI 1.0.0 + Spring AI Alibaba Graph 1.0.0.2 | `StateGraph` + 条件边 |
| 会话 | Spring Session + Redis | Cookie `INVESTIGATOR_SESSION`，Redis db 2 |
| 控制面库 | MySQL 8 + Flyway + Hibernate `validate` | 表只由 Flyway 管，Hibernate 不建表 |
| 模型 | DashScope / OpenAI 兼容 / Anthropic / Ollama | 控制台「模型」页可覆盖，或 gitignore 的 `application-model.yml` |
| 前端 | React 18 + Vite 6 + TypeScript | pnpm workspace，build 产物进 Boot `static/` |
| 可选记忆 | Redis db 1（窗口缓存）、Qdrant（向量） | 默认关；挂了回退 MySQL |
| 可选 Cursor | Node stdio MCP | `packages/mcp-templates`，主路径不依赖 |

包管理：根 `package.json` 用 pnpm workspace（`pnpm build` / `pnpm check` / `pnpm dev`）。

---

## 5. 第一次跑起来

### 5.1 前置依赖

- JDK 17（IDEA Project SDK 也设 17）
- 本机 MySQL `127.0.0.1:3306`、Redis `127.0.0.1:6379`（没有就用 Docker 起依赖，见下）
- Node 20+ / pnpm 9（只在改前端或 Node MCP 时需要）

### 5.2 三条启动路径（不要同时占 8790）

**A. IDEA Debug（日常开发，推荐）**

1. IDEA Open 仓库根 `pom.xml`，SDK 17。
2. 空库二选一，**不要混**：
   - `mysql -h127.0.0.1 -P3306 -uroot -p < docs/sql/sre_investigator_init.sql`（终态导入，对齐 V25，启动不重放）；
   - 或什么都不导，直接启动让 Flyway 从 V1 跑到 V25。
3. Debug `sre-investigator-boot` → `com.maiya.investigator.boot.InvestigatorApplication`。**Active profiles 留空** = `local`（自动含 `mysql` + `tools`）。
4. 打开 http://localhost:8790/ ，默认超管 `admin` / `admin123456`（仅本地）。

**B. Docker 一键（看页面 / 演示）**

```bash
cp .env.example .env    # 可改超管口令、模型 Key；MASTER_KEY 留空会自动生成
./scripts/dev.sh docker
./scripts/dev.sh docker-down   # 停（数据卷保留）
```

**C. 改控制台 UI**

```bash
# Java 已在 8790 跑着时：
pnpm --filter @sre-investigator/console dev    # Vite :5173，/api 代理到 8790
# 或构建进静态资源后重启 Java：
pnpm --filter @sre-investigator/console build
```

### 5.3 配置文件地图（详见 [config-profiles.md](./config-profiles.md)）

| 文件 | 作用 | 提交？ |
|---|---|---|
| `application.yml` | 公共配置 + 环境分组 + `investigator.*` 业务配置 | 是 |
| `application-local.yml` | 本机 MySQL / Redis（默认 profile） | 是（口令走本机） |
| `application-test / pre / prod.yml` | 远程环境 | 是 |
| `application-docker.yml` | 容器内主机名 | 是 |
| `application-model.yml` | 模型 Key | **否**，自建 |
| `application-secrets.yml` | AES 主密钥 / 飞书 / 可选阿里云 AK | **否**，自建 |
| `application-tools.yml` | 排查目标兜底 | **否**，自建 |

注意：`deploy/docker-compose.yml` 的 MySQL 映射宿主机 **33069**，和 `application-local.yml` 的本机 **3306** 是两个库，别混用。

### 5.4 首次验证清单

```bash
curl http://localhost:8790/api/health             # 服务健康
curl http://localhost:8790/api/agent/capabilities # 工具能力（登录态）
```

页面走一遍：登录 → 建项目 → 绑代码仓 → 建一条 MySQL 只读 Binding 并「测试连通」 → 对话页发一条排查问题 → 看到阶段事件流和四段报告 → 「用量」「审计」页有记录。

### 5.5 常见故障（详见 [local-setup.md](./local-setup.md) 末尾表格）

| 现象 | 处理 |
|---|---|
| 8790 占用 | 停本机 Java 或 `./scripts/dev.sh docker-down`，不要双跑 |
| 页面项目为空 | profile 没带 `mysql`，走了内存仓 |
| `未设置 investigator.secrets.master-key` | 自建 `application-secrets.yml` 填 AES 主密钥 |
| 排查回复「模型未就绪」 | 配 `application-model.yml` 或控制台「模型」页 |
| 改了代码页面没变 | Java 改动要重启 Debug；前端要 dev 或重新 build |
| 以前加密的 Binding 解不开 | master-key 换过；用旧 key 解密重存，或重录 |

---

## 6. 架构深入

### 6.1 编排层（`sre-investigator-graph`）

| 包 | 职责 | 关键类 |
|---|---|---|
| `intent` | 问句前置过滤：寒暄/试探不跑工具 | `QuestionIntentGate`、`QuestionIntentHeuristics` |
| `route` | 方向路由 + 工具种类门控 | `InvestigationRouteResolver`、`ToolKindGate` |
| `skill` | Skill/Rule 装载（DB 资产优先，回退 `packages/skills`） | `SkillLoader` |
| `conversation` | 策略分流 + 会话记忆压缩 | `ConversationStrategyRegistry`、`ConversationMemoryCompactor`、`TurnHistoryPrompt` |
| `state` | 跨轮累积结构化状态 | `InvestigationState`、`EvidenceItem`、`Hypothesis`、`ToolCallRecord` |
| `report` | 结构化四段报告 ↔ Markdown | `StructuredReport`、`ReportMarkdown` |
| `agent` | 子 Agent（领域专家）定义与业务流程执行 | `AgentCatalog`、`AgentDefinition`、`BusinessFlowExecutor` |
| `usage` | 模型用量统计（包装 ChatModel） | `UsageRecordingChatModel`、`UsageContext` |
| 根包 | Graph 装配、Agent 工具循环、模型工厂、韧性重试 | `InvestigationGraph`、`InvestigationAgent`、`ChatModelFactory`、`ResilientModelCall` |

**方向路由优先级**（`route` 节点）：子 Agent 捷径 → 点名 MCP → 纯代码理解捷径（`ToolKindGate.codeUnderstanding`）→ 用户手选 → 模型分类 → 关键词匹配 → `investigator.routes.fallback`。方向表在 `application.yml` 的 `investigator.routes`，加方向只改配置。

**工具门控（软门控）**：方向白名单只在第一轮生效，后续轮次放开到本环境全部已启用 Binding（`widenProfilesByKind`），避免"开局锁死、中途够不到日志"。

**模型韧性**：`ResilientModelCall` 对模型调用做同线程重试 + 指数退避（`investigator.agent.model-max-attempts` 默认 2），保住 ThreadLocal 的 SSE 进度 sink。

### 6.2 会话与三层记忆

| 层 | 内容 | 压缩策略 | 预算 |
|---|---|---|---|
| STM | 最近 N 轮原文 | 固定窗口 + 单条截断 | `recent-turns: 2`，每条 800 字 |
| Episodic | 滚动摘要 | `ConversationMemoryCompactor`：正则抽取"标识 / 已查 / 结论 / 未决"四行，增量合并去重，超长保尾裁剪 | `summary-max-chars: 600` |
| LTM | 跨会话项目事实 | `MemoryFactExtractor`：正则抽表名/组件类型/坑位（≤3 条/轮，拒收一次性 ID），异步落库 + 可选 Qdrant 向量 | `ltm-max-chars: 800` |

全部为**确定性压缩（无 LLM 调用）**，可单测。检索侧（`ProjectMemoryService`）：Qdrant 向量 top5 → 未命中回退词面相关度 → 无相关不注入。任何读失败只跳过记忆，不阻断排查。

### 6.3 工具层（`sre-investigator-mcp-servers`）

- **单一事实来源**：[ReadonlyToolCatalog](../sre-investigator-mcp-servers/src/main/java/com/maiya/investigator/mcp/ReadonlyToolCatalog.java)（约 35 项，按 `ToolKind` 归档）。`InvestigationAgent.capabilities()`、Binding 过滤、文字工具目录都从它取，别再硬编码清单。
- **工具清单**：代码（`code_search`/`code_read`/`@` 预读）、MySQL、Redis、SLS 日志、MSE、ARMS、ALB/CLB、CDN、K8s（经 Kuboard）、StarRocks、Kafka、RocketMQ、Mongo。
- **阿里云族**（SLS/MSE/ARMS/LB/CDN）走 `AliyunSigner` HMAC 签名 HTTP，不引官方 SDK。
- **防护**：`ReadOnlySqlGuard`（仅 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH，拒多语句/锁定读/跨 schema）、`MongoReadGuard`、Redis 不注册写删 `KEYS`、代码工作区 allowlist + 拒绝路径越界/符号链接/`.env`/证书。
- **凭据解析**：`BindingConnectionLookup` 从控制面 Binding 读加密连接配置；ARMS/ALB/CDN 例外，共用进程级 RAM 只读 AK（环境变量或 secrets 文件），Binding 只存地域和资源标识。

### 6.4 控制面（`sre-investigator-boot` + `domain`/`infra`）

- **端口与适配**：domain 定义 `ControlPlaneStore` / `ConversationStore` / `AuthStore` / `SettingStore` / `ModelUsageStore` 等接口；infra 提供每个端口的 `Mysql*`（JPA）与 `InMemory*` 双实现，按 `investigator.persistence.mode` 切换。
- **RBAC**：飞书 OAuth + 本地账号，用户/角色/权限（菜单 `menu.*`、动作 `action.*`），`@RequirePermission` 注解 + `PermissionInterceptor` 拦截；项目成员控制数据可见范围。
- **审计**：`AuditTrail` 记录会话启动/完成/失败、配置变更；脱敏。
- **用量**：`UsageRecordingChatModel` 包装每次模型调用记 token/费用，`UsageQueryService` 聚合出报表。
- **预算**：`AgentBudgetService` + `ToolBudget`，每 Run 工具次数上限；耗尽返回 NEEDS_INPUT 并附提示。
- **飞书机器人**：`boot/feishu` 包。事件回调进 `FeishuEventController` → `FeishuBotService` 复用同一套 RunService；群聊绑定（`investigator_feishu_chat_binding`）、允许环境白名单、群消息线程上下文。
- **定时巡检**：`boot/automation` 包，进程内 `@Scheduled` 每 60s 扫到期任务（EVERY/CRON/AT），载荷 `K8S_INSPECT`（纯 GET 巡检，不调模型）与 `AGENT_TURN`（走 INVESTIGATE）；会话 `origin=AUTOMATION`。详见 [explain/automations.md](./explain/automations.md)。
- **代码审查**：`boot/review` 包 + `GitCompareService`，拉 base..head diff 装审查 Skill/Rule。

### 6.5 SSE 事件协议

| event | 时机 | 载荷 |
|---|---|---|
| `phase` | 控制器预发 / 图阶段 | `{name?, message}` |
| `started` | 会话与 run 落库 | `{conversationId, runId}` |
| `thought` | 工具/模型/步骤进度 | `{kind, message}`，kind ∈ tool/model/phase/evidence/think |
| `heartbeat` | 每 8s 保活 | `{ok:true}` |
| `evidence` | synthesize 后、result 前 | 结构化四段 |
| `result` | 整轮结束 | `RunOutcome`（含 `result.state.reportStructured`） |
| `error` | 失败 | `{message}` |

前端消费在 [liveSession.ts](../apps/console/src/conversation/liveSession.ts)。**当前是阶段级流式，不是 token 级**（已知缺口）。

### 6.6 数据模型（Flyway V1–V25，按主题）

| 主题 | 迁移 | 核心表 |
|---|---|---|
| 控制面基础 | V1–V2 | `investigator_project` / `investigator_repository` / `investigator_tool_binding` / `investigator_run` / `investigator_audit_event` |
| 模型与配置 | V3 | `investigator_setting`（模型覆盖） |
| 会话 | V4 / V6 / V13 / V23 | 会话模式、线程、origin、owner |
| 仓库 | V5 | Git 地址 / 分支 / SSH |
| 目录资产 | V7 / V8 / V17 | `investigator_catalog_asset`（Skill/Rule/子 Agent 定义）、工具注解、资产文件 |
| RBAC | V9 / V25 | 用户 / 角色 / 权限 / 用户身份、动作与审计扩展 |
| 用量与记忆 | V10 / V11 | `investigator_model_usage`、`investigator_memory_fact` |
| 定时任务 | V12 | `investigator_automation_job` |
| 环境 | V15 | test / gray→pre / prod |
| 飞书 | V18 / V19 / V21 / V24 | 群绑定（二进制 UUID）、环境白名单、线程 |
| 菜单 | V14 / V16 / V20 / V22 | 审查、领域专家菜单 |

---

## 7. 新人代码导览（建议阅读顺序）

**Day 1 —— 跑起来 + 主链路（boot）**

1. [InvestigatorApplication](../sre-investigator-boot/src/main/java/com/maiya/investigator/boot/InvestigatorApplication.java) → [application.yml](../sre-investigator-boot/src/main/resources/application.yml)
2. [InvestigationRunService](../sre-investigator-boot/src/main/java/com/maiya/investigator/boot/InvestigationRunService.java)（一次排查的编排入口，读 `runInvestigation`）
3. [ConversationSessionService](../sre-investigator-boot/src/main/java/com/maiya/investigator/boot/ConversationSessionService.java)（问题改写、记忆拼装、落库）
4. [sse/InvestigationStreamController](../sre-investigator-boot/src/main/java/com/maiya/investigator/boot/sse/InvestigationStreamController.java)（SSE 怎么推）

**Day 2 —— Graph 与 Agent（graph）**

5. [InvestigationGraph](../sre-investigator-graph/src/main/java/com/maiya/investigator/graph/InvestigationGraph.java)（四节点 + `decide` 条件边）
6. [InvestigationAgent](../sre-investigator-graph/src/main/java/com/maiya/investigator/graph/InvestigationAgent.java)（工具循环、提示词、预算）
7. `state/InvestigationState` + `report/StructuredReport`（跨轮状态与结构化报告）
8. `intent/QuestionIntentGate`、`route/InvestigationRouteResolver`、`route/ToolKindGate`

**Day 3 —— 工具面（mcp-servers）**

9. [ReadonlyToolCatalog](../sre-investigator-mcp-servers/src/main/java/com/maiya/investigator/mcp/ReadonlyToolCatalog.java)（工具清单单一来源）
10. 挑一个精读：[ReadonlyMysqlTool](../sre-investigator-mcp-servers/src/main/java/com/maiya/investigator/mcp/ReadonlyMysqlTool.java) + [ReadOnlySqlGuard](../sre-investigator-mcp-servers/src/main/java/com/maiya/investigator/mcp/ReadOnlySqlGuard.java)
11. [BindingConnectionLookup](../sre-investigator-mcp-servers/src/main/java/com/maiya/investigator/mcp/BindingConnectionLookup.java)（凭据怎么从控制面流到工具）

**Day 4 —— 前端（apps/console）**

12. [App.tsx](../apps/console/src/App.tsx)（路由 + 菜单权限）→ [api.ts](../apps/console/src/api.ts)（接口封装）
13. [InvestigatePage](../apps/console/src/pages/InvestigatePage.tsx) + [liveSession.ts](../apps/console/src/conversation/liveSession.ts)（SSE 消费）
14. [MASTER.md](../design-system/sre-investigator/MASTER.md)（设计规范：OLED 暗色、青色信号、高密度、IBM Plex + JetBrains Mono）

**Day 5 —— 按方向选读**

- 记忆：`graph/conversation/*Compactor*` + `infra/memory/*`
- RBAC/飞书：`boot/auth/*`、`boot/feishu/*`、[feishu-group.md](./feishu-group.md)
- 定时：`boot/automation/*` + [explain/automations.md](./explain/automations.md)
- 编排设计取舍：[explain/orchestration.md](./explain/orchestration.md)、[plans/对话排查编排重构.md](./plans/对话排查编排重构.md)

---

## 8. 构建与测试

```bash
# Java —— 全模块构建 + 测试（根目录）
mvn clean install

# 只编译启动链路（跳测试，改 Java 后日常用）
mvn -pl sre-investigator-boot -am install -DskipTests

# 单模块测试
mvn -pl sre-investigator-graph test
mvn -pl sre-investigator-boot test

# 前端 —— 类型检查 / 构建 / 开发
pnpm --filter @sre-investigator/console check
pnpm --filter @sre-investigator/console build
pnpm --filter @sre-investigator/console dev

# Node MCP（Cursor 侧）
pnpm --filter @sre-investigator/mcp-templates test

# 一键 Docker / 状态
./scripts/dev.sh docker
./scripts/dev.sh status
```

测试分布：`graph`（压缩器、路由、状态、报告、图循环冒烟）、`boot`（RBAC、会话、目录、模型、用量）、`mcp-servers`（各 Guard、URL 解析、连接测试）、`infra`（加密、聚合、异步写入）。**改到哪层，跑哪层测试；提交前 `mvn clean install` 全绿。**

---

## 9. 常见开发任务 Playbook

### 9.1 调排查提示词

`application.yml` → `investigator.agent.investigate-prompt`（默认值在 `InvestigationAgent.DEFAULT_INVESTIGATE_PROMPT`，含"先读 @ 入口→表名只能来自代码→再查库→固定四段"的顺序约束）。改完重启。

### 9.2 加一个排查方向（不改代码）

1. `application.yml` 的 `investigator.routes.items` 加一项（id / label / keywords）。
2. 把对应 Skill 的 `route` 设为同一个 id（DB 资产或 `packages/skills/*/SKILL.md`）。
3. 重启 Java。控制台「适用路由」下拉和 `GET /api/agent/routes` 自动出现。

### 9.3 加 / 改 Skill、Rule

优先走控制台（目录资产，存 DB，`SkillLoader` DB 优先于文件）；也可改 `packages/skills/` 下的 `SKILL.md`。子 Agent（领域专家）定义在控制台「领域专家」页（`AgentCatalog`/`AgentDefinition`，可配业务流程图 `AgentFlow`）。

### 9.4 加一个只读工具（改动面最大的任务）

1. `domain/control/ToolKind` 加枚举值。
2. `mcp-servers` 写 `ReadonlyXxxTool`（含只读 Guard、超时、返回大小限制）。
3. `ReadonlyToolCatalog` 注册工具名与描述（单一事实来源）。
4. `ToolCatalog.lines`（domain）让文字目录能展示。
5. `BindingConnectionLookup` + `config/XxxEndpoint` 接 Binding 加密凭据。
6. 前端 `apps/console/src/catalog/toolMeta.ts` / `ToolMetaFields.tsx` 加 Binding 表单元数据。
7. 如需新注解/注解列，加 Flyway 迁移（参考 V8）。
8. 验收：capabilities 出现该工具 → 建 Binding 测连通 → INVESTIGATE 提问能调到。

### 9.5 加 API + 前端页面

1. boot 写 Controller，方法加 `@RequirePermission("menu.xxx")`（参考 `AutomationJobController`）。
2. 需要新菜单/权限时加 Flyway 迁移插权限数据（参考 `V14__review_menu.sql`、`V20__agent_plaza_menu.sql`），并给默认角色授权。
3. 前端：`api.ts` 加接口 → `pages/XxxPage.tsx` → `App.tsx` 加路由（包 `RequireMenu`）→ `AppLayout` 加菜单项（图标用 `icons.tsx` 内联 SVG）。
4. 遵循 [MASTER.md](../design-system/sre-investigator/MASTER.md)：暗色、表单可见 label、空态带下一步、状态不只靠颜色。
5. `pnpm --filter @sre-investigator/console build` 后重启 Java（或 dev 模式联调）。

### 9.6 加表 / 字段

只加 Flyway 迁移 `V{n}__xxx.sql`（下一个版本号），同步改 JPA Entity。`ddl-auto: validate`，Hibernate 不建表、不改表。**不要改已发布的迁移**；本地空库可直接导入 `docs/sql/sre_investigator_init.sql` 重来。

### 9.7 换 / 加模型供应商

控制台「模型」页切换（DB 覆盖，即时生效），或 `application-model.yml`。新供应商要改 `ChatModelFactory` + `domain/model/ModelProvider`。模型未配置时链路自动降级，不报错。

### 9.8 调预算 / 循环 / 记忆

| 配置 | 默认 | 含义 |
|---|---|---|
| `investigator.agent.max-tool-calls` | 100 | 每 Run 工具调用上限 |
| `investigator.agent.max-iterations` | 3 | 循环 Graph 最大轮数 |
| `investigator.agent.model-max-attempts` | 2 | 模型调用重试次数 |
| `investigator.agent.model-retry-backoff-ms` | 500 | 重试退避基数 |
| `investigator.memory.recent-turns / summary-max-chars / ltm-max-chars` | 2 / 600 / 800 | 三层记忆预算 |
| `investigator.memory.redis/qdrant.enabled` | false | 可选记忆后端 |

---

## 10. 约定与红线

**安全红线**

- 工具面只读：任何新工具必须过 Guard；禁止注册写、删、`KEYS`、多语句 SQL、锁定读。
- 凭据两层：控制面凭据写 gitignore 的 `application-secrets.yml` / `application-model.yml`；排查目标凭据走 Binding（AES-GCM 加密，主密钥在 secrets）。**密钥不进已提交的 yml、不进前端、不进日志、不进截图。**
- 生产用最小权限只读账号；本地 compose 的默认口令不得用于非本地环境。
- 自动修复、写库、清缓存、重启、发布永远不做（未来若做，走独立审批通道，不进只读工具面）。

**工程约定**

- Java 17，禁 Preview 特性；包名 `com.maiya.investigator.*`。
- 依赖方向：`boot → graph → domain ← infra`；domain 不碰 Spring Web/JPA。
- 表结构只经 Flyway；已发布迁移不改。
- 前端改动必须过 MASTER.md 设计规范。
- `sre-investigator-console` 是占位模块、`apps/harness-api` + `packages/runtime-*` 是早期 TS 原型——**新功能一律进 Java 主线，不在这些地方加。**
- 8790 端口不双跑；本机 3306 与 compose 33069 是两个库。

---

## 11. 已知缺口与规划（动手前先看）

以 README「还没做 / 规划」为准，摘要：

| 优先级 | 方向 |
|---|---|
| 近 | 提示词分层（System 放政策，User 放 Brief / `@` 预读——当前仍是巨型 user prompt 一层堆叠）；Binding 密钥统一解析并接 KMS；脱敏故障评测集 |
| 中 | ES 只读；token 级 SSE / 取消；告警进草稿再确认排查 |
| 远 | 多 Agent 分工；审批式执行面 |

其余已知项（见 [explain/orchestration.md](./explain/orchestration.md) 缺口表）：旧报告 Markdown 兼容解析、CODE 模式禁用、Mongo/图片等半成品工具按需再开。历史讨论稿 [plans/sre-investigator-architecture-and-roadmap.md](./plans/sre-investigator-architecture-and-roadmap.md) 部分段落已落后，以 README 为准。

---

## 12. 学习路径总结

1. **第 1 天**：读完本文 + README，Docker 跑起来，控制台每个菜单点一圈，发起一次 INVESTIGATE。
2. **第 2–4 天**：按 §7 导览读主链路代码，对照一次真实排查的日志和 SSE 事件。
3. **第 5 天起**：挑 §9 的 Playbook 实操——建议从"加排查方向"和"调提示词"开始（改动小、反馈快），再做前端页面，最后挑战"加只读工具"。
4. **持续**：改哪层跑哪层测试；提交前 `mvn clean install` + `pnpm check` 全绿；涉密配置只写 gitignore 文件。

---

## 13. 文档索引

| 文档 | 内容 |
|---|---|
| [README](../README.md) | 权威入口：定位、技术栈、架构、启动方式、规划 |
| [local-setup.md](./local-setup.md) | IDEA Debug 逐步操作、本地密钥、故障表 |
| [config-profiles.md](./config-profiles.md) | Profile、配置文件地图、两类库 |
| [onboarding-project.md](./onboarding-project.md) | 接入一个业务项目的步骤与验收 |
| [feishu-group.md](./feishu-group.md) | 飞书登录与群机器人配置 |
| [aliyun-readonly-ram.md](./aliyun-readonly-ram.md) | ARMS / ALB / CDN 的 RAM 只读 AK |
| [explain/orchestration.md](./explain/orchestration.md) | 编排现状：循环 Graph、材料进模型的方式、缺口 |
| [explain/automations.md](./explain/automations.md) | 定时任务与 K8s 巡检 |
| [plans/对话排查编排重构.md](./plans/对话排查编排重构.md) | 循环 Graph 重构的设计记录与 SSE 协议 |
| [plans/sre-investigator-architecture-and-roadmap.md](./plans/sre-investigator-architecture-and-roadmap.md) | 早期架构讨论稿（部分落后） |
| [design-system/sre-investigator/MASTER.md](../design-system/sre-investigator/MASTER.md) | 前端设计规范 |
| [docs/sql/sre_investigator_init.sql](./sql/sre_investigator_init.sql) | 空库终态导入（对齐 Flyway V25） |
