# SRE Investigator：架构现状、目标架构与能力路线图

> 状态：持续演进  
> 更新时间：2026-08-25  
> 适用范围：`sre-investigator` 目录下的 Java 控制面、Node.js MCP 工具服务，以及 Cursor 使用入口。

## 1. 文档目的

本文统一说明三件事：

1. 当前已经实际实现并验证的能力，避免把设计草案误认为可用功能。
2. 最终要建设成什么样的排查 Agent 平台，以及各模块在其中的职责。
3. 从当前版本到目标版本仍缺少什么、为什么缺、优先级如何安排。

系统的核心定位是：为多个微服务项目提供一个**只读、可审计、按项目和环境隔离**的智能排查控制面。它借鉴 OpenClaw/Harness 的“Harness + Agent + Skill + MCP”分层思路，但第一期不做自动修复或高危运维执行。

## 2. 目标与边界

### 2.1 最终目标

用户可以在 Web Console 或 Cursor 中描述一个故障，例如：

> “test 环境 10:20–10:30 for-you 接口 5xx 升高，traceId=xxx”

系统应当：

1. 识别项目、环境、问题类型和缺失的关键标识。
2. 装载相应的 Skill、代码仓上下文和已授权的 Tool Binding。
3. 由模型在明确的预算和只读边界内调用代码、日志、MySQL、Redis、ES 等工具。
4. 输出可追溯的排查报告：事实证据、已排除项、根因假设、下一步建议。
5. 保存会话、工具调用、审计记录和配置变更，以便复盘。

### 2.2 第一阶段明确不做

- 不执行写库、删缓存、重启服务、改 Nacos、发布或回滚。
- 不允许模型自行提交任意连接 URI、账号或 shell 命令。
- 不把生产凭据写入 Git、`mcp.json`、Skill 或前端代码。
- 不以“模型推测”替代实际工具证据。

## 3. 当前架构总览

```mermaid
flowchart TB
    User[使用者]

    subgraph Entrances[使用入口]
        Console[内置 Console<br/>localhost:8790]
        Cursor[Cursor IDE]
    end

    subgraph Java[Java 控制面 / Spring Boot]
        API[REST + SSE API]
        CP[项目 / 仓库 / Binding / Run / Audit]
        Graph[Spring AI Alibaba Graph<br/>route → load_context → investigate → synthesize]
        Agent[InvestigationAgent<br/>DashScope ChatModel + ToolCallback]
        JTools[Java 只读 MySQL / Redis 工具]
    end

    subgraph Storage[控制面存储]
        MySQL[(MySQL + Flyway)]
    end

    subgraph Node[Node.js 只读工具服务]
        McpServer[sre-investigator-readonly<br/>stdio MCP Server]
        Code[代码检索 / 片段读取]
        TMysql[MySQL / Redis / ES / 日志模板]
    end

    User --> Console --> API
    User --> Cursor --> McpServer
    API --> CP --> MySQL
    API --> Graph --> Agent
    Graph --> JTools
    Agent -. 可选 Spring AI MCP Client .-> McpServer
    McpServer --> Code
    McpServer --> TMysql
```

### 3.1 关键设计原则

| 原则 | 当前落实方式 |
|---|---|
| 只读优先 | SQL Guard 拒绝写操作、多语句和锁定读；Redis 不暴露写、删、`KEYS`。 |
| 项目/环境隔离 | Tool Binding 绑定 `projectId + environment + toolKind + profile`。 |
| 凭据隔离 | 工具连接使用部署环境变量；可选补充配置以 AES-256-GCM 加密保存。 |
| 证据优先 | 未配置模型或工具时返回明确降级报告，不伪造事实。 |
| 可审计 | 项目配置、排查启动/完成和工具调用产生审计记录；敏感字段脱敏。 |
| 逐步演进 | 无 MySQL、无模型时可用 `memory` 模式启动；生产能力以 Profile 显式开启。 |

## 4. 当前已实现能力

### 4.1 Java 控制面

Java 17 + Spring Boot 3.4.5 的 Maven 多模块工程已建立，主要模块如下：

| 模块 | 当前职责 |
|---|---|
| `sre-investigator-api` | 请求/响应 DTO、环境枚举。 |
| `sre-investigator-domain` | 项目、Run、Repository、Binding 等领域模型和端口接口。 |
| `sre-investigator-infra` | MySQL JPA 适配、Flyway、AES-GCM、审计实现。 |
| `sre-investigator-graph` | Spring AI Alibaba Graph、Skill 装载、Agent 工具编排。 |
| `sre-investigator-mcp-servers` | 进程内 Java MySQL/Redis 只读工具。名称保留历史原因，当前不是 MCP 协议 Server。 |
| `sre-investigator-boot` | REST/SSE API、静态 Console、Profile 组合。 |
| `sre-investigator-console` | 历史占位模块；实际 Console 当前由 Boot 静态资源提供。 |

### 4.2 MySQL 控制面

控制面数据库使用 MySQL，而非 PostgreSQL。已实现并在本机 Docker MySQL 8.4 上验证：

| 表 | 用途 |
|---|---|
| `investigator_project` | 项目基本信息与拓扑类型。 |
| `investigator_repository` | 项目代码仓及角色信息。 |
| `investigator_tool_binding` | 项目/环境对应的工具类型和 profile 授权。 |
| `investigator_run` | 排查问题、状态、报告与时间。 |
| `investigator_audit_event` | 配置和排查行为的审计记录。 |

迁移位于 `sre-investigator-infra/src/main/resources/db/migration/`：

- `V1__control_plane_schema.sql`：初始表结构。
- `V2__align_hibernate_long_text_columns.sql`：对齐 MySQL `LONGTEXT` 与 Hibernate 映射。

验证结果：

1. Flyway V1/V2 都已成功执行。
2. 项目、仓库、Binding、Run、Audit 均成功写入 MySQL。
3. 重启 Java 服务后，以上数据仍可通过 API 查询。

本地 Compose 默认仅用于开发：

```text
host: localhost
port: 33069
database: sre_investigator
application user: sre_investigator
```

这些默认账号密码不得用于任何非本地环境。

### 4.3 排查工作流与 Skill

当前 Graph 是一个确定性骨架叠加可选 LLM 的工作流：

```mermaid
flowchart LR
    Start([开始]) --> Route[route<br/>关键词路由]
    Route --> Context[load_context<br/>读取 Binding 与 Skill]
    Context --> Investigate[investigate<br/>模型与只读工具]
    Investigate --> Synthesize[synthesize<br/>报告或降级报告]
    Synthesize --> End([结束])
```

节点职责：

| 节点 | 已实现行为 |
|---|---|
| `route` | 识别 `http-5xx`、`order-payment` 等基础路由。 |
| `load_context` | 读取当前项目、环境的启用 Binding；加载匹配的 `SKILL.md` 摘要。 |
| `investigate` | 有模型时构造只读系统提示词与工具；无模型时安全降级。 |
| `synthesize` | 输出模型结论或固定格式的“未配置能力”报告。 |

当前 Skill 来源：

- `packages/skills/global/http-5xx/SKILL.md`
- `packages/skills/global/order-payment/SKILL.md`
- `packages/skills/templates/admin-app-dual/SKILL.md`
- `packages/skills/projects/demo-app/SKILL.md`

Skill 文件以运行时文件系统加载，当前会将相关摘要限制后传给 Agent，避免把整个工作区无界注入模型上下文。

### 4.4 模型与工具调用

#### DashScope

启用 `dashscope` Profile 并配置 `DASHSCOPE_API_KEY` 后，Spring AI Alibaba 创建 DashScope `ChatModel`。Agent 可通过 Spring AI `ToolCallback` 调用只读工具。

没有 API Key 时，应使用 `no-dashscope` Profile。它是本项目自定义的 Profile，用于排除 DashScope 自动装配，避免 Starter 在启动阶段因缺 Key 失败。

#### Java 本地只读工具

| 工具 | 已实现保护 |
|---|---|
| MySQL | 只允许 `SELECT`、`SHOW`、`DESCRIBE`、`EXPLAIN`、`WITH`；限制行数；拒绝锁定读和多语句。 |
| Redis | 只读 `get`、`inspect`、游标式前缀扫描；限制返回值大小和扫描数量。 |

工具只会在当前项目/环境具有对应 Binding，且 profile 已在部署环境配置时才向 Agent 暴露。

#### Spring AI MCP Client

Java 可以在 `mcp` Profile 下通过 stdio 启动并连接 Node MCP Server：

```text
Spring Boot Agent
  -> Spring AI MCP Client
  -> packages/mcp-templates/dist/server.js
  -> code_search / code_read / mysql_query_readonly / redis_* / ...
```

该链路已验证能建立 MCP 协议连接并获取工具能力。MCP 工具名可能被 Spring AI 加上客户端命名空间；Java 侧按工具名类别继续应用 Binding 过滤。

### 4.5 Web API、SSE 和配置页

Boot 提供：

| 能力 | API |
|---|---|
| 健康检查 | `GET /api/health` |
| 项目 | `GET/POST /api/projects` |
| 仓库配置 | `GET/POST /api/projects/{projectId}/repositories` |
| Tool Binding | `GET/POST /api/projects/{projectId}/bindings` |
| 同步排查 | `POST /api/investigations` |
| SSE 排查 | `POST /api/investigations/stream` |
| 查询 Run | `GET /api/investigations/{runId}` |
| Agent 能力 | `GET /api/agent/capabilities` |
| 审计记录 | `GET /api/audit-events` |

访问 `http://localhost:8790/` 可使用最小配置页：创建项目、配置仓库/Binding、发起 SSE 排查、查看阶段和结果。

当前 SSE 会推送 `phase`、`result`、`error` 事件。它是**阶段级流式**，尚不是模型 token 逐字流式。

### 4.6 Cursor 与 Node MCP

Cursor 工作区通过 `.cursor/mcp.json` 注册 `sre-investigator-readonly` stdio MCP Server。Node 工具服务已支持：

- profile 化 MySQL/Redis 连接；
- 代码工作区 allowlist；
- `code_search` 与 `code_read`；
- SQL/Redis 只读防护；
- JSONL 脱敏审计。

因此，Cursor 可直接调用 Node MCP 进行代码和只读数据排查；Java 控制面则负责配置、会话、审计与未来的统一 Agent 编排。

## 5. 当前运行 Profile

| 场景 | Profile | 含义 |
|---|---|---|
| 零依赖本地演示 | 默认 `memory` | 内存存储，无数据源和 DashScope，输出安全降级报告。 |
| 仅验证 MySQL 控制面 | `mysql,no-dashscope` | 启用 MySQL/Flyway/JPA，禁用 DashScope 自动装配。 |
| 端到端智能排查 | `mysql,dashscope,tools,mcp` | MySQL 控制面 + 模型 + 进程内工具 + Node MCP Client。 |
| Cursor 内使用 | Cursor MCP 配置 | 无需启动 Java 控制面，但不具备 Java 会话/配置能力。 |

推荐完整启动方式：

```bash
docker compose -f deploy/docker-compose.yml up -d mysql
pnpm --filter @sre-investigator/mcp-templates build

export DASHSCOPE_API_KEY='...'
export SRE_SECRETS_MASTER_KEY="$(openssl rand -base64 32)"
export SRE_TEST_MYSQL_URI='jdbc:mysql://readonly_user:password@mysql-test:3306/app'
export SRE_TEST_REDIS_URI='redis://readonly_user:password@redis-test:6379/0'
export SRE_MCP_SERVER_PATH="$PWD/packages/mcp-templates/dist/server.js"
export SRE_CODE_WORKSPACES_JSON='{"admin":"/absolute/path/demo-admin-broad","app":"/absolute/path/demo-app-broad"}'
export SPRING_PROFILES_ACTIVE='mysql,dashscope,tools,mcp'

mvn -f sre-investigator-boot/pom.xml spring-boot:run
```

## 6. 最终目标架构

当前版本已经具备控制面的基础骨架，但最终形态应进一步收敛为下面的结构：

```mermaid
flowchart TB
    subgraph Experience[体验层]
        Web[Web Console]
        Cursor2[Cursor Skill + MCP]
        OpenAPI[REST / SSE / OpenAPI]
    end

    subgraph Control[Harness 控制层]
        Session[会话与 Run 管理]
        Policy[权限、预算、审批与策略]
        Catalog[Project / Repo / Skill / Binding Catalog]
        Secrets[Secret Provider]
        Audit[不可抵赖审计]
    end

    subgraph Intelligence[智能编排层]
        Router[故障路由]
        Planner[排查计划]
        Graph2[LangGraph / Spring AI Alibaba Graph]
        Evaluator[证据评估与报告生成]
    end

    subgraph Tooling[工具访问层]
        McpGateway[MCP Client / Gateway]
        JavaAdapters[Java Readonly Adapters]
        CodeTools[代码检索]
        DataTools[MySQL / Redis / ES / Logs / Nacos]
    end

    subgraph External[受控外部系统]
        Repo[代码仓]
        Data[(业务数据源)]
        Logs[日志与链路]
        Config[Nacos / 配置中心]
    end

    Experience --> Control --> Intelligence --> Tooling --> External
    Control --> Audit
    Control --> Secrets
    Intelligence --> Policy
```

### 6.1 目标分层职责

| 层 | 最终职责 |
|---|---|
| 体验层 | Web、Cursor、API 三种一致入口，展示过程与证据。 |
| 控制层 | 多项目配置、身份权限、凭据管理、Run 生命周期、审计与预算。 |
| 智能编排层 | 路由、计划、工具循环、证据聚合、人工确认点。 |
| 工具层 | 统一 MCP/Adapter 协议、只读校验、超时/分页/脱敏、结果标准化。 |
| 外部系统 | 代码、MySQL、Redis、ES、日志、配置中心等，始终以最小权限账户访问。 |

### 6.2 目标的端到端排查流程

1. 用户选择项目/环境并提交问题。
2. 控制层创建 Run，快照当时的项目、Binding 和策略版本。
3. Router 选择 Skill 与排查路径。
4. Planner 生成最多 N 步的只读工具计划，并请求缺失的关键标识。
5. Tool Gateway 根据 Binding、RBAC 和预算执行工具。
6. Evaluator 为每个结论关联证据，禁止无依据的根因断言。
7. SSE/Console 输出过程；Run、工具输入摘要、输出摘要和决策写入审计。
8. 对需要写操作的后续建议，只生成工单或人工审批请求，不直接执行。

## 7. 当前缺失与优先级

### 7.1 P0：上线前必须补齐

| 缺失 | 影响 | 建议 |
|---|---|---|
| 身份认证与 RBAC | 当前 API 默认无身份边界，无法安全用于多人/生产。 | 接入企业 SSO，按项目、环境、工具类型授权。 |
| Secret Provider | 加密字段依赖环境变量主密钥，尚未接入 KMS/Vault。 | 接入外部 KMS/Vault，支持轮换、版本和审计。 |
| Binding 凭据动态解析 | UI 可加密保存补充配置，但运行时数据工具仍主要读取部署环境 profile。 | 将 Binding 的加密配置接入受控 Secret Resolver，禁止把明文下发给模型。 |
| 审计查询结构化 | MySQL 审计持久化已具备，但 metadata 当前以字符串存储，Run 与审计事件关联仍不完整。 | 使用 JSON 列或结构化 schema，补齐 `run_id`、操作者、工具调用 ID。 |
| Web 输入安全 | 最小 Console 无认证、CSRF、细粒度权限和配置审批。 | 在暴露给他人前先加认证、授权、输入限制。 |
| MySQL/Flyway 版本治理 | 当前 MySQL 8.4 可运行，但 Flyway 会提示版本支持未充分测试。 | 升级 Flyway 或将本地镜像锁定为已验证版本。 |

### 7.2 P1：让 Agent 真正具备稳定排查能力

| 缺失 | 当前状态 | 目标状态 |
|---|---|---|
| DashScope 实战验收 | 代码已接入；尚未以真实 API Key 验证模型工具循环。 | 用测试环境和脱敏样本验证模型路由、工具调用与报告质量。 |
| 工具调用预算 | Java 本地工具已有上限；外部 MCP 的统一预算和取消尚不完整。 | 统一每 Run 的次数、耗时、字节数和费用预算。 |
| token 级 SSE | 当前是阶段级事件。 | 支持模型文本增量、工具调用状态、取消和断线恢复。 |
| 代码仓配置闭环 | Repository 已持久化，但 Node MCP 代码 workspace 仍依赖环境变量。 | 从控制面下发受限 workspace 快照，做路径存在性与权限校验。 |
| ES/日志/Nacos 工具 | Node 模板覆盖部分能力，Java 图中尚未形成完整统一适配。 | 建立标准化证据模型和只读 Adapter。 |
| 技能生命周期 | Skill 从本地文件加载，无版本、灰度、审批和测试。 | Skill Catalog、版本快照、测试样例和发布审批。 |
| 结构化报告 | 当前以 Markdown 字符串为主。 | `evidence / hypotheses / exclusions / nextActions` 等结构化字段。 |

### 7.3 P2：平台化与高级能力

| 缺失 | 目标 |
|---|---|
| 多 Agent 协作 | Router、代码 Agent、数据 Agent、报告 Agent 分工并共享受控证据。 |
| 事件驱动排查 | 接入告警后自动创建草稿 Run，等待人工确认后继续。 |
| 评测体系 | 回放脱敏历史事故，衡量工具选择、结论准确率、耗时和成本。 |
| Runbook 自动化 | 将稳定排查流程沉淀为可测、可版本化 Skill。 |
| 人工审批执行面 | 如未来需要修复，只可在独立审批/执行通道中进行。 |
| 生产部署治理 | 多副本、任务队列、限流、Tracing、指标、告警、数据保留策略。 |

## 8. 已知技术债与决策

### 8.1 Java 与 Node 的职责收敛

当前存在 Java 控制面和 Node MCP 双栈，这不是最终重复建设：

- **Java**：长期承担控制面、Graph、配置、Run、审计、权限和 Web API。
- **Node MCP**：短期/中期承担 Cursor 可直接使用的 stdio 工具服务，且可被 Java MCP Client 复用。

需要避免两边各自实现一套 Project/Run/权限 API。后续新能力优先放到 Java 控制面；Node 侧保持工具协议和 Cursor 适配。

### 8.2 `sre-investigator-mcp-servers` 的命名

该 Java 模块目前实现的是进程内只读工具，并未暴露 MCP 协议 Server。后续可：

1. 重命名为 `sre-investigator-tool-adapters`；或
2. 真正加入 Java MCP Server transport。

在完成前，文档和代码评审中应避免把它称为“Java MCP Server”。

### 8.3 本地 Compose 的定位

`deploy/docker-compose.yml` 仅用于开发验收。它包含易记的本地默认密码、持久卷和端口映射，不能直接作为生产部署清单。

## 9. 验收基线

下列能力已经完成并验证：

- [x] Maven 全模块测试通过。
- [x] Node MCP 模板测试通过。
- [x] MySQL Docker 启动、Flyway V1/V2 执行成功。
- [x] Project / Repository / Binding / Run / Audit 写入 MySQL。
- [x] Java 服务重启后 MySQL 数据仍可读取。
- [x] MySQL Profile 下同步排查与 SSE 排查可用。
- [x] `memory` 模式下无模型安全降级。
- [x] Spring AI MCP Client 能建立到 Node MCP Server 的 stdio 连接。

下列能力尚不应宣称“生产可用”：

- [ ] 真实 DashScope 模型工具循环质量验收。
- [ ] 多用户认证、RBAC 和项目级授权。
- [ ] 生产 Secret/KMS 接入与轮换。
- [ ] 完整 ES/日志/Nacos 工具链。
- [ ] token 级 SSE、取消、重连和 Run 回放。
- [ ] 生产部署、容量、监控和告警方案。

## 10. 近期建议执行顺序

1. 接入测试环境 DashScope Key，建立 10～20 个脱敏故障样例的回归集。
2. 把 Binding 加密连接配置接入统一 Secret Resolver，并补齐动态 profile 解析。
3. 接入企业 SSO/RBAC，先限制生产环境和高敏工具访问。
4. 为每个工具统一结果 schema、超时、预算、脱敏与证据引用。
5. 将 Web Console 从最小静态页演进为带鉴权、Run 历史和审计检索的正式前端。
6. 在此基础上再考虑自动告警触发、多 Agent 协作和审批式修复。
