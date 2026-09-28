package com.maiya.investigator.domain.control;

import java.util.List;
import java.util.Optional;

public final class OpenSourceCatalog {
    private static final List<CatalogTemplate> ITEMS = List.of(
            new CatalogTemplate(
                    "mcp-github",
                    CatalogKind.MCP,
                    CatalogSource.OPEN_SOURCE,
                    "GitHub Official MCP",
                    "官方开源 GitHub MCP，只读查仓库、Issue、PR。",
                    "npm",
                    "@modelcontextprotocol/server-github",
                    "https://github.com/modelcontextprotocol/servers",
                    "main",
                    "npx",
                    "-y @modelcontextprotocol/server-github",
                    null,
                    "MCP_SERVER",
                    null
            ),
            new CatalogTemplate(
                    "mcp-filesystem",
                    CatalogKind.MCP,
                    CatalogSource.OPEN_SOURCE,
                    "Filesystem Official MCP",
                    "官方开源文件系统 MCP，按授权目录只读。",
                    "npm",
                    "@modelcontextprotocol/server-filesystem",
                    "https://github.com/modelcontextprotocol/servers",
                    "main",
                    "npx",
                    "-y @modelcontextprotocol/server-filesystem",
                    null,
                    "MCP_SERVER",
                    null
            ),
            new CatalogTemplate(
                    "mcp-fetch",
                    CatalogKind.MCP,
                    CatalogSource.OPEN_SOURCE,
                    "Fetch Official MCP",
                    "官方开源 HTTP Fetch MCP，抓取公开页面。",
                    "npm",
                    "@modelcontextprotocol/server-fetch",
                    "https://github.com/modelcontextprotocol/servers",
                    "main",
                    "npx",
                    "-y @modelcontextprotocol/server-fetch",
                    null,
                    "MCP_SERVER",
                    null
            ),
            new CatalogTemplate(
                    "skill-http-5xx",
                    CatalogKind.SKILL,
                    CatalogSource.BUILTIN,
                    "HTTP 5xx 排查",
                    "内置：网关错误、超时和下游 5xx。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "http-5xx",
                    null,
                    """
                    ---
                    name: http-5xx
                    description: 排查 HTTP 5xx、网关错误和服务超时。仅使用当前项目绑定的只读工具。
                    ---
                    # HTTP 5xx 排查
                    1. 先确认环境、时间范围、请求路径、traceId 和调用方。
                    2. 有 SLS 时用 sls_list_logstores 确认 logstore，再 sls_query_readonly 按 traceId 拉网关/应用日志。
                    3. 有 MSE 时用 mse_list_routes / mse_get_service 核对路由、后端和实例健康。
                    4. 查服务日志与调用链，再检查 Dubbo 或 HTTP 下游。
                    5. 只在已有证据指向数据层时，使用只读数据库和 Redis 工具。
                    6. 输出事实、已排除项、最多三个按概率排序的假设；没有证据时明确说明。
                    不得修改配置、重启服务、写库、删除缓存或建议直接执行高危命令。
                    """
            ),
            new CatalogTemplate(
                    "skill-order-payment",
                    CatalogKind.SKILL,
                    CatalogSource.BUILTIN,
                    "订单与支付排查",
                    "内置：订单创建、支付回调、幂等和状态不一致。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "order-payment",
                    null,
                    """
                    ---
                    name: order-payment
                    description: 排查订单创建、支付回调、幂等和状态不一致问题。
                    ---
                    # 订单与支付排查
                    1. 收集 orderId、paymentId、用户、渠道、金额、币种、时间和回调 requestId。
                    2. 按时间轴核验订单、支付单、回调日志、消息消费结果和幂等记录。
                    3. 避免假设订单表名；先查 schema 或从当前项目代码确认。
                    4. 只读核验后给出状态差异和补偿建议，但不执行补单、退款、重放消息。
                    """
            ),
            new CatalogTemplate(
                    "skill-admin-app-dual",
                    CatalogKind.SKILL,
                    CatalogSource.BUILTIN,
                    "Admin + App 双端",
                    "内置：后台与 C 端分仓的微服务排查。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    """
                    ---
                    name: admin-app-dual
                    description: 适用于后台管理端和 C 端接口分仓的微服务项目。
                    ---
                    # Admin + App 双端排查模板
                    先判定请求来自 B 端运营后台还是 C 端 APP/H5，再选择对应仓库、网关和认证服务。
                    两侧 Remote 接口名称可能相同，但不能把契约 JAR 或服务实现混为同一接口。跨端问题须分别列出每侧证据。
                    """
            ),
            new CatalogTemplate(
                    "skill-demo-app",
                    CatalogKind.SKILL,
                    CatalogSource.BUILTIN,
                    "Demo App 覆盖",
                    "内置：demo 双仓、Nacos、Dubbo 上下文。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    """
                    ---
                    name: demo-app
                    description: demo-app 项目覆盖：管理后台和 APP 双仓、Nacos、Dubbo 的排查上下文。
                    ---
                    # Demo App 覆盖规则
                    - demo-admin-broad：运营后台，入口 demo-gateway。
                    - demo-app-broad：C 端，入口 demo-app-gateway。
                    服务配置均从 Nacos 读取。排查前确认环境 namespace，避免把 test/gray/prod 的证据混用。
                    """
            ),
            new CatalogTemplate(
                    "skill-github-incident",
                    CatalogKind.SKILL,
                    CatalogSource.OPEN_SOURCE,
                    "GitHub Incident 模板",
                    "开源事故排查提纲，可改成你们的 runbook。",
                    "github",
                    null,
                    "https://github.com/google/sre-book",
                    "master",
                    null,
                    null,
                    null,
                    null,
                    """
                    # Incident 排查提纲
                    1. 先写清影响面：谁、什么接口、何时开始、是否仍在发生。
                    2. 固定时间窗和环境，不要混用 prod/test 证据。
                    3. 按调用链收集日志、指标、配置和代码入口，再下钻数据层。
                    4. 每条结论必须对应工具结果或源码，没有证据就写「未知」。
                    5. 只给只读下一步，不建议重启、回滚、改配置或写库。
                    """
            ),
            new CatalogTemplate(
                    "skill-code-review",
                    CatalogKind.SKILL,
                    CatalogSource.BUILTIN,
                    "代码审查",
                    "内置：对比两个分支，按 Skill / Rule 只读审查。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "review",
                    null,
                    """
                    ---
                    name: code-review
                    description: 对比基准分支与对比分支的 diff，按已配置 Rule 做只读代码审查。
                    ---
                    # 代码审查
                    1. 只看已注入的变更文件列表和 diff，不要读未改动的文件，不要凭空猜改了什么。
                    2. 对列表里的文件用 code_read(startLine) 分段读差异附近，或用 code_search 定位符号。禁止一次读完整大文件。
                    3. 整库 SQL、lock、生成物可跳过，除非审查焦点点名。
                    4. 正文用 Markdown 表格。问题表：严重级别 | 文件 | 行号 | 风险 | 证据；建议表：范围 | 建议 | 依据。
                    5. 没有问题时问题表写一行「无」；信息不足写「未知」。
                    6. 正文后输出 review-marks JSON 数组，把有问题的行标到对比分支上。
                    不得 commit、push、改代码、写库或建议直接合并。
                    """
            ),
            new CatalogTemplate(
                    "rule-readonly",
                    CatalogKind.RULE,
                    CatalogSource.BUILTIN,
                    "只读边界",
                    "内置：禁止写库、删缓存、改配置、重启。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    """
                    # 只读边界
                    - 不得建议或执行写 SQL、删 key、改 Nacos、重启、发布。
                    - 工具结果不足时写「未知」，不要编造数据。
                    - 输出必须能回溯到工具结果或预读代码。
                    """
            ),
            new CatalogTemplate(
                    "rule-code-review",
                    CatalogKind.RULE,
                    CatalogSource.BUILTIN,
                    "代码审查边界",
                    "内置：只读对照 diff，指出风险和回归。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "review",
                    null,
                    """
                    # 代码审查边界
                    - 只依据 diff 和读到的源码下结论，不得编造未出现的改动。
                    - 优先看正确性、安全、兼容性和可回滚性，再看风格。
                    - 不得建议 commit、push、改生产配置或直接合并。
                    """
            ),
            reviewRule(
                    "rule-review-mysql-mapper",
                    "数据访问边界",
                    "内置审查：MySQL 禁止跨 schema，表名来自代码。",
                    """
                    # 数据访问边界
                    - 一条 MySQL SQL 里禁止写 demo_xxx.table 跨 schema。跨库走 Dubbo / API，Mapper 只写本库表名。
                    - OLAP / StarRocks 可以跨库，但不得写进 MySQL Mapper。
                    - 表名、字段必须来自刚读到的代码或 XML，禁止按业务名猜测。
                    - SQL 变更应落在当前仓 docs/sql/，禁止追加进历史 db/init.sql。
                    - 发现跨库 SQL、猜表名、或把数仓查询塞进 MySQL 访问层时，标为高优先级。
                    """
            ),
            reviewRule(
                    "rule-review-java-style",
                    "Java 工程约束",
                    "内置审查：Java 17、可变空集合、锁超时、禁止 hutool-json。",
                    """
                    # Java 工程约束
                    - 编译目标 Java 17，禁止 Preview 与 Java 18+ API。
                    - 接口返回或后续还要改的集合用 ArrayList，禁止 List.of() / Stream.toList()。
                    - 无副作用 map/filter/toMap 用 Stream，toMap 必须写 merge。
                    - 任何锁必须设置获取超时。
                    - 新增 JSON 禁止 cn.hutool.json，用 JsonUtils 或 Jackson。
                    - 业务日志文案用中文。
                    """
            ),
            reviewRule(
                    "rule-review-jetcache",
                    "缓存失效契约",
                    "内置审查：App JetCache 与 Admin 失效必须带 key。",
                    """
                    # 缓存失效契约
                    - App 新增或修改 @Cached / JetCache 时，Admin 失效广播必须同一 cacheName，且带 key / keys。
                    - 禁止只传 cacheName（空 key 删不到 'all'）。
                    - 查到缓存写入与失效不对称、或跨端 cacheName 不一致，标为高优先级。
                    """
            ),
            reviewRule(
                    "rule-review-domain-boundary",
                    "领域与双端契约",
                    "内置审查：服务归属、通用 DTO、Admin 与 App Remote 分离。",
                    """
                    # 领域与双端契约
                    - 先判定改动属于哪个服务：Video ≠ User ≠ Strategy ≠ Order，不要为了少一次 RPC 把编排塞进被调方。
                    - 禁止把单一业务字段写进通用接口、公共 DTO、公共枚举或全局默认逻辑。
                    - demo-admin-broad 与 demo-app-broad 同名 Remote 不是同一契约，不能混用实现或 JAR。
                    - demo-app-portal-content 走门户包根，不要把 App 字段塞进门户响应。
                    - 修改被多服务使用的通用能力前，必须证明至少两个独立业务域在用。
                    """
            ),
            new CatalogTemplate(
                    "rule-evidence",
                    CatalogKind.RULE,
                    CatalogSource.BUILTIN,
                    "证据优先",
                    "内置：先代码后数据，禁止猜表名。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    """
                    # 证据优先
                    - 表名、字段、缓存 key 只能来自刚读到的代码。
                    - 禁止按业务名猜测表名后查库。
                    - 同一符号不要重复 code_search。
                    """
            ),
            new CatalogTemplate(
                    "rule-sls-query",
                    CatalogKind.RULE,
                    CatalogSource.BUILTIN,
                    "SLS 日志查询",
                    "内置：按 logstore、traceId、userId 只读查日志。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    """
                    # SLS 日志查询
                    - 先 sls_list_logstores 确认 project / logstore，不要猜库名；列表中的均可查，Binding 配置只是默认入口。
                    - 有 traceId 时 query 只写裸 id，或标准键值：traceId: <值>。禁止把类名、AND、OR 拼进同一条。
                    - 不要写 traceId\":\"<值>（那是未建索引时的全文碎片；工具会在零命中时自动回退）。
                    - 有 userId 时：userId: <值> 或只搜 id；不要和类名混查。
                    - 时间窗：用户没给具体日期时 from/to 必须留空（默认近 7 天，工具按 24h 分段）。不要编造 2024 等年份。可用 now、-1h、-7d。
                    - 最大 7 天；结果只作证据，不得改日志或索引。
                    - 需要前后文时再缩小 query，不要一次拉全量。
                    """
            ),
            new CatalogTemplate(
                    "rule-k8s-readonly",
                    CatalogKind.RULE,
                    CatalogSource.BUILTIN,
                    "K8s 只读排查",
                    "内置：经 Kuboard 只读查节点、Pod、事件和容器日志。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "investigate",
                    null,
                    """
                    # K8s 只读排查
                    - 先 k8s_list_clusters，再 k8s_list_namespaces / k8s_list_nodes，确认集群和命名空间，不要猜。
                    - Pod 异常看 CrashLoopBackOff、OOMKilled、重启次数；节点看 Ready=False。
                    - 再用 k8s_get_pod 看最近事件，k8s_pod_logs 拉容器尾部日志。
                    - 不得建议或执行重启、调容、删除、exec、进节点。
                    """
            ),
            new CatalogTemplate(
                    "rule-starrocks-readonly",
                    CatalogKind.RULE,
                    CatalogSource.BUILTIN,
                    "StarRocks 只读查询",
                    "内置：按 MySQL 协议只读查数仓，禁止写表。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    """
                    # StarRocks 只读查询
                    - 用 starrocks_query_readonly，仅 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH。
                    - 表名来自代码或 SHOW TABLES，不要猜。
                    - 不得 INSERT/UPDATE/DELETE 或改物化视图。
                    """
            ),
            new CatalogTemplate(
                    "rule-mq-readonly",
                    CatalogKind.RULE,
                    CatalogSource.BUILTIN,
                    "Kafka / RocketMQ 只读",
                    "内置：列 topic、偷看消息、看消费积压，不生产不提交。",
                    "catalog",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "investigate",
                    null,
                    """
                    # 消息队列只读
                    - 先 kafka_list_topics / rocketmq_list_topics 确认 topic，不要猜。
                    - 偷看用 kafka_peek_messages / rocketmq_peek_messages，不提交位移、不 ack、不发送。
                    - 积压用 kafka_consumer_lag / rocketmq_consumer_lag，消费组名来自代码或配置。
                    """
            ),
            new CatalogTemplate(
                    "mcp-aliyun-observability",
                    CatalogKind.MCP,
                    CatalogSource.OPEN_SOURCE,
                    "阿里云可观测 MCP",
                    "官方开源可观测 MCP。本产品默认只用 sls_list_* / sls_execute_sql / sls_get_context_logs，关闭计费 AI 工具。",
                    "uv",
                    "alibabacloud-observability-mcp-server",
                    "https://github.com/aliyun/alibabacloud-observability-mcp-server",
                    "master",
                    "uvx",
                    "--from alibabacloud-observability-mcp-server observability-mcp start --stdio",
                    null,
                    "MCP_SERVER",
                    null
            ),
            new CatalogTemplate(
                    "rule-sre-error-budget",
                    CatalogKind.RULE,
                    CatalogSource.OPEN_SOURCE,
                    "SRE Error Budget",
                    "开源 SRE 思路：用错误预算约束结论强度。",
                    "github",
                    null,
                    "https://github.com/google/sre-book",
                    "master",
                    null,
                    null,
                    null,
                    null,
                    """
                    # Error Budget
                    - 先判断这次故障是否在错误预算内，再决定结论强度。
                    - 区分症状（用户可见）和原因（系统内部）。
                    - 没有 SLO 数据时不要用「大规模故障」这类词。
                    """
            ),
            builtinAgent(
                    "agent-sre-investigate",
                    "sre-investigate",
                    "SRE 排障",
                    "默认排障 Agent：日志、中间件、代码仓一起看，结论必须带证据。",
                    "official,investigate,full-stack",
                    "MYSQL,REDIS,MONGODB,ELASTICSEARCH,LOGS,MSE,K8S,STARROCKS,KAFKA,ROCKETMQ,CODE,MCP_SERVER",
                    """
                    你是 SRE Investigator 的默认排障 Agent。
                    - 只读查询，禁止写库、清缓存、重启。
                    - 先复述症状和时间窗，再选最少的工具取证。
                    - 结论必须带来源：日志、SQL、配置或代码位置。
                    """,
                    "最近 15 分钟网关 5xx 升高，先看哪？",
                    "订单超时但用户已扣款，怎么查？"
            ),
            builtinFlowAgent(
                    "agent-sre-incident-flow",
                    "sre-incident-flow",
                    "标准排查",
                    "按业务步骤走的标准排障：入口代码 → 网关 → 集群 → 日志 → 数据。可在广场里改节点和 MCP。",
                    "official,investigate,playbook",
                    "CODE,MSE,K8S,LOGS,MYSQL,REDIS",
                    """
                    你是标准排查任务 Agent，按卡上业务流程图分步取证。
                    - 只读查询，禁止写库、清缓存、重启、改配置。
                    - 每步只完成本步目标，把证据交给下一步，不要提前做后续步骤。
                    - 缺环境、时间窗或 identifier（traceId / orderId / userId）时先说明缺什么。
                    - 表名、缓存 key 只能来自已读到的代码；没有证据就写「未知」，不要编造。
                    """,
                    incidentFlowJson(),
                    "test 环境最近 15 分钟网关 5xx，按标准步骤查",
                    "订单超时但已扣款，从入口代码跟到数据"
            ),
            builtinAgent(
                    "agent-k8s-inspect",
                    "k8s-inspect",
                    "K8s 巡检",
                    "看 Pod / 事件 / 重启，不改集群。",
                    "official,k8s,readonly",
                    "K8S",
                    """
                    你是 Kubernetes 只读巡检 Agent。
                    - 先确认 namespace 与工作负载，再看事件和重启次数。
                    - 禁止 apply、delete、scale、exec 进容器改状态。
                    - 把异常事件原文带回结论。
                    """,
                    "这个 namespace 有没有 Pod 在重启",
                    "看一下最近的 Warning 事件"
            ),
            builtinAgent(
                    "agent-user-profile",
                    "user-profile",
                    "用户画像",
                    "预留的用户域只读 Agent。当前以提示词定义契约，查询工具后续接入。",
                    "official,user,preview",
                    "",
                    """
                    你是用户画像只读 Agent。
                    - 只解释用户状态、账号与资料字段，不写库。
                    - 现在还没有用户域查询工具：说明缺口，不要假装查到了数据。
                    - 需要 UID / 手机号时向用户要，不要猜测。
                    """,
                    "UID 10001 现在是什么账号状态",
                    "这个用户有没有被封禁"
            )
    );

    private OpenSourceCatalog() {
    }

    private static CatalogTemplate builtinAgent(
            String id,
            String slug,
            String name,
            String description,
            String tags,
            String toolKinds,
            String prompt,
            String... starters
    ) {
        return agentTemplate(id, slug, name, description, tags, toolKinds, prompt, "", starters);
    }

    private static CatalogTemplate builtinFlowAgent(
            String id,
            String slug,
            String name,
            String description,
            String tags,
            String toolKinds,
            String prompt,
            String flowJson,
            String... starters
    ) {
        return agentTemplate(id, slug, name, description, tags, toolKinds, prompt, flowJson, starters);
    }

    private static CatalogTemplate agentTemplate(
            String id,
            String slug,
            String name,
            String description,
            String tags,
            String toolKinds,
            String prompt,
            String flowJson,
            String... starters
    ) {
        StringBuilder content = new StringBuilder();
        content.append("{\"systemPrompt\":").append(jsonString(prompt.trim()));
        content.append(",\"allowedToolKinds\":").append(jsonCsvArray(toolKinds));
        content.append(",\"skillSlugs\":[]");
        content.append(",\"mcpSlugs\":[]");
        content.append(",\"starters\":").append(startersJson(starters));
        if (flowJson != null && !flowJson.isBlank()) {
            content.append(",\"flow\":").append(flowJson);
        }
        content.append('}');
        return new CatalogTemplate(
                id,
                CatalogKind.AGENT,
                CatalogSource.BUILTIN,
                name,
                description,
                "official",
                tags,
                null,
                null,
                null,
                null,
                slug,
                null,
                content.toString()
        );
    }

    private static String incidentFlowJson() {
        StringBuilder nodes = new StringBuilder("[");
        nodes.append(flowNode(
                "n1",
                "定位入口",
                "先确认环境、时间窗、路径或接口，以及 traceId / orderId / userId。在已绑定代码仓定位入口（Controller / Dubbo），跟到下游调用。表名、缓存 key、topic 只能来自刚读到的代码。找不到就写未知，不要猜。",
                "CODE",
                "http-5xx,admin-app-dual",
                40,
                40
        ));
        nodes.append(',');
        nodes.append(flowNode(
                "n2",
                "核网关",
                "只读查 MSE：路由 path、后端服务、实例健康，核对是否打到预期服务。没有 MSE 时明确写本步跳过。",
                "MSE",
                "http-5xx",
                240,
                40
        ));
        nodes.append(',');
        nodes.append(flowNode(
                "n3",
                "核集群",
                "只读查 K8s：namespace、Pod 相位、重启次数、Warning 事件、容器日志尾。禁止 exec、重启或改工作负载。没有 K8s 时明确写本步跳过。",
                "K8S",
                "",
                440,
                40
        ));
        nodes.append(',');
        nodes.append(flowNode(
                "n4",
                "核日志",
                "用 SLS 按 traceId、userId 或路径拉网关和应用日志；先确认 logstore。没有命中就写未取到，不要编造。",
                "LOGS",
                "http-5xx",
                40,
                160
        ));
        nodes.append(',');
        nodes.append(flowNode(
                "n5",
                "核数据",
                "仅当入口代码已给出真实表名或缓存 key 时，才用 MySQL / Redis 只读查询。禁止猜表名、禁止无 WHERE 大扫。订单支付问题按时间轴核状态，不执行补单或退款。",
                "MYSQL,REDIS",
                "order-payment",
                240,
                160
        ));
        nodes.append(']');
        StringBuilder edges = new StringBuilder("[");
        edges.append(flowEdge("n1", "n2")).append(',');
        edges.append(flowEdge("n2", "n3")).append(',');
        edges.append(flowEdge("n3", "n4")).append(',');
        edges.append(flowEdge("n4", "n5")).append(']');
        return "{\"nodes\":" + nodes + ",\"edges\":" + edges + "}";
    }

    private static String flowNode(
            String id,
            String title,
            String instruction,
            String toolKinds,
            String skillSlugs,
            int x,
            int y
    ) {
        return "{\"id\":" + jsonString(id)
                + ",\"title\":" + jsonString(title)
                + ",\"instruction\":" + jsonString(instruction)
                + ",\"toolKinds\":" + jsonCsvArray(toolKinds)
                + ",\"skillSlugs\":" + jsonCsvArray(skillSlugs)
                + ",\"mcps\":[]"
                + ",\"x\":" + x
                + ",\"y\":" + y
                + "}";
    }

    private static String flowEdge(String from, String to) {
        return "{\"from\":" + jsonString(from) + ",\"to\":" + jsonString(to) + "}";
    }

    private static String jsonCsvArray(String csv) {
        if (csv == null || csv.isBlank()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        String[] parts = csv.split(",");
        boolean first = true;
        for (String part : parts) {
            String value = part.trim();
            if (value.isBlank()) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(jsonString(value));
        }
        return json.append(']').toString();
    }

    private static String startersJson(String... starters) {
        if (starters == null || starters.length == 0) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < starters.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(jsonString(starters[i]));
        }
        return json.append("]").toString();
    }

    private static String jsonString(String raw) {
        String escaped = raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "\"" + escaped + "\"";
    }

    private static CatalogTemplate reviewRule(String id, String name, String summary, String content) {
        return new CatalogTemplate(
                id,
                CatalogKind.RULE,
                CatalogSource.BUILTIN,
                name,
                summary,
                "catalog",
                null,
                null,
                null,
                null,
                null,
                "review",
                null,
                content
        );
    }

    public static List<CatalogTemplate> all() {
        return ITEMS;
    }

    public static List<CatalogTemplate> byKind(CatalogKind kind) {
        return ITEMS.stream().filter(item -> item.kind() == kind).toList();
    }

    public static Optional<CatalogTemplate> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return ITEMS.stream().filter(item -> item.id().equals(id)).findFirst();
    }
}
