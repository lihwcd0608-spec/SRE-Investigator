package com.maiya.investigator.graph;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogAssetFile;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.domain.model.ModelProvider;
import com.maiya.investigator.domain.model.ModelSettingsResolver;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.mcp.BindingConnectionLookup;
import com.maiya.investigator.mcp.MentionParser;
import com.maiya.investigator.mcp.MongoJsonArg;
import com.maiya.investigator.mcp.NamedMcpFocus;
import com.maiya.investigator.mcp.ProfileNames;
import com.maiya.investigator.mcp.ReadonlyCodeTool;
import com.maiya.investigator.mcp.ReadonlyCodeTool.CodeWorkspace;
import com.maiya.investigator.mcp.ReadonlyCdnTool;
import com.maiya.investigator.mcp.ReadonlyKafkaTool;
import com.maiya.investigator.mcp.ReadonlyK8sTool;
import com.maiya.investigator.mcp.ReadonlyLoadBalancerTool;
import com.maiya.investigator.mcp.ReadonlyMseTool;
import com.maiya.investigator.mcp.ReadonlyMongodbTool;
import com.maiya.investigator.mcp.ReadonlyMysqlTool;
import com.maiya.investigator.mcp.ReadonlyRedisTool;
import com.maiya.investigator.mcp.ReadonlyRocketMqTool;
import com.maiya.investigator.mcp.ReadonlySlsTool;
import com.maiya.investigator.mcp.ReadonlyArmsTool;
import com.maiya.investigator.mcp.ReadonlyStarrocksTool;
import com.maiya.investigator.mcp.config.RepositoryWorkspaceProperties;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class InvestigationAgent {
    private static final Logger log = LoggerFactory.getLogger(InvestigationAgent.class);
    static final String DEFAULT_INVESTIGATE_PROMPT = """
            你是只读 SRE 排查 Agent。只使用本轮已列出的工具，不要把预算浪费在扫目录或猜表名上：
            1. 有代码工具时，先顺着 @ 入口和预读源码找到真实表名、字段、缓存 key。同一符号不要再 code_search。
            2. 表名、字段、缓存 key 只能来自刚读到的代码。禁止按业务名猜测后查库。
            3. 确认后再查本轮可用的数据工具。profile 必须用提示里的 MCP 名，不要加前缀。SHOW/DESCRIBE 不要加 LIMIT。
            4. 不要为了「有这类工具」就先 list 一遍。预算用尽也必须先交结论，不要空转。
            5. 不得编造工具结果，不得建议或执行写库、删缓存、重启、改配置。
            6. 输出固定四段：已确认的证据、假设、已排除项、下一步。证据必须能对应到工具结果或预读代码。
            """;

    static final String NAMED_MCP_INVESTIGATE_PROMPT = """
            你是只读数据查询助手。用户已点名只使用某一个 MCP，按以下规则，不要当成长链路排查：
            1. 只调用该 MCP 对应的只读工具。禁止 mysql_* / redis_* / sls_* / k8s_* / starrocks_* 等其它 MCP。
            2. 用户已经给出集合/表名和 id 时，直接查询，不要先列全部库、不要猜别的表。
            3. 查到结果后，把工具返回的 documents 原样放进 JSON 代码块输出。不要写「已确认的证据 / 假设 / 已排除项 / 下一步」。
            4. 若会话上下文或上一步工具结果里已经有完整文档，直接输出 JSON，不要再调任何工具。
            5. 「继续」「输出」「给 json」表示把已查到的数据贴出来，不是换数据源继续排查。
            6. 不得编造工具结果，不得写库、删缓存、重启或改配置。
            """;

    private final ModelSettingsResolver modelSettings;
    private final ChatModelFactory chatModels;
    private final ObjectProvider<ToolCallbackProvider> mcpToolCallbacks;
    private final ReadonlyMysqlTool mysql;
    private final ReadonlyRedisTool redis;
    private final ReadonlySlsTool sls;
    private final ReadonlyMseTool mse;
    private final ReadonlyArmsTool arms;
    private final ReadonlyLoadBalancerTool loadBalancer;
    private final ReadonlyCdnTool cdn;
    private final ReadonlyK8sTool k8s;
    private final ReadonlyStarrocksTool starrocks;
    private final ReadonlyKafkaTool kafka;
    private final ReadonlyRocketMqTool rocketmq;
    private final ReadonlyMongodbTool mongodb;
    private final ReadonlyCodeTool code;
    private final BindingConnectionLookup bindings;
    private final ControlPlaneStore controlPlane;
    private final RepositoryWorkspaceProperties repositoryDefaults;
    private final AgentProperties agent;
    private final boolean externalMcpEnabled;
    private final String investigatePrompt;

    public InvestigationAgent(
            ModelSettingsResolver modelSettings,
            ChatModelFactory chatModels,
            ObjectProvider<ToolCallbackProvider> mcpToolCallbacks,
            ReadonlyMysqlTool mysql,
            ReadonlyRedisTool redis,
            ReadonlySlsTool sls,
            ReadonlyMseTool mse,
            ReadonlyArmsTool arms,
            ReadonlyLoadBalancerTool loadBalancer,
            ReadonlyCdnTool cdn,
            ReadonlyK8sTool k8s,
            ReadonlyStarrocksTool starrocks,
            ReadonlyKafkaTool kafka,
            ReadonlyRocketMqTool rocketmq,
            ReadonlyMongodbTool mongodb,
            ReadonlyCodeTool code,
            BindingConnectionLookup bindings,
            ControlPlaneStore controlPlane,
            RepositoryWorkspaceProperties repositoryDefaults,
            AgentProperties agent
    ) {
        this.modelSettings = modelSettings;
        this.chatModels = chatModels;
        this.mcpToolCallbacks = mcpToolCallbacks;
        this.mysql = mysql;
        this.redis = redis;
        this.sls = sls;
        this.mse = mse;
        this.arms = arms;
        this.loadBalancer = loadBalancer;
        this.cdn = cdn;
        this.k8s = k8s;
        this.starrocks = starrocks;
        this.kafka = kafka;
        this.rocketmq = rocketmq;
        this.mongodb = mongodb;
        this.code = code;
        this.bindings = bindings;
        this.controlPlane = controlPlane;
        this.repositoryDefaults = repositoryDefaults == null ? new RepositoryWorkspaceProperties() : repositoryDefaults;
        this.agent = agent == null ? new AgentProperties() : agent;
        this.externalMcpEnabled = this.agent.isEnableExternalMcp();
        String prompt = this.agent.getInvestigatePrompt();
        this.investigatePrompt = prompt.isBlank() ? DEFAULT_INVESTIGATE_PROMPT : prompt;
    }

    private int maxToolCalls() {
        return agent.getMaxToolCalls();
    }

    public AgentResult ask(String question, String environment, String provider, String modelName) {
        return ask(question, environment, provider, modelName, null);
    }

    public AgentResult ask(String question, String environment, String provider, String modelName, String projectId) {
        ResolvedModelSettings settings = resolveSettings(provider, modelName);
        if (!settings.available()) {
            AgentThoughts.emit("model", settings.missingReason());
            log.warn("ASK 跳过：模型未就绪 {}", settings.missingReason());
            return AgentResult.degraded(settings.missingReason());
        }
        List<CodeWorkspace> workspaces = codeWorkspaces(projectId);
        ToolBudget budget = new ToolBudget(maxToolCalls());
        Consumer<AgentThought> progress = AgentThoughts.sink();
        List<ToolCallback> tools = codeToolCallbacks(budget, workspaces, progress);
        String hint = code.expandMentions(workspaces, question);
        log.info(
                "ASK 开始 provider={} model={} tools={} workspaces={} mentions={} question={}",
                settings.provider().id(),
                settings.model(),
                tools.stream().map(item -> item.getToolDefinition().name()).toList(),
                workspaces.stream().map(CodeWorkspace::name).toList(),
                MentionParser.extract(question),
                LogText.preview(question)
        );
        if (tools.isEmpty()) {
            progress.accept(AgentThought.of("model", "当前没有可搜索的代码仓，仅用模型回答"));
        } else {
            progress.accept(AgentThought.of(
                    "model",
                    "已装载代码工具：" + tools.stream().map(item -> item.getToolDefinition().name()).toList()
            ));
        }
        progress.accept(AgentThought.of("model", "正在调用模型…"));
        long started = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec request = ChatClient.create(chatModels.create(settings))
                    .prompt()
                    .system("""
                            你是 SRE 助手，用中文回答用户问题。可以解释架构、日志、接口和配置，
                            也可以用 code_read / code_search 阅读当前项目已配置的代码仓。
                            用户用 @ 指定的类或路径已预读时，直接基于预读内容回答，不要重复搜索。
                            不得编造未读到的源码或未提供的生产数据，不得建议或执行写库、删缓存、重启或改配置。
                            若信息不足，明确指出缺什么。
                            """)
                    .user("""
                            环境：%s
                            %s
                            %s
                            问题：
                            %s
                            """.formatted(environment, code.summarize(workspaces), hint, question));
            if (!tools.isEmpty()) {
                request = request.toolCallbacks(tools);
            }
            ChatClient.ChatClientRequestSpec askRequest = request;
            String response = ResilientModelCall.content(
                    () -> askRequest.call().content(),
                    agent.getModelMaxAttempts(),
                    agent.getModelRetryBackoffMs(),
                    progress
            );
            progress.accept(AgentThought.of("model", "模型已返回，正在整理回答"));
            log.info(
                    "ASK 完成 elapsedMs={} usedTools={} budgetExhausted={} reply={}",
                    System.currentTimeMillis() - started,
                    budget.used(),
                    budget.exhausted(),
                    LogText.preview(response)
            );
            return AgentResult.completed(response, tools.size(), budget.used(), budget.exhausted(), maxToolCalls());
        } catch (Exception exception) {
            progress.accept(AgentThought.of("model", "模型调用失败"));
            log.error("ASK 失败 elapsedMs={}：{}", System.currentTimeMillis() - started, exception.getMessage(), exception);
            return AgentResult.degraded(explainModelFailure(exception));
        }
    }

    public AgentResult review(
            String question,
            String environment,
            String provider,
            String modelName,
            String projectId,
            List<String> skills,
            List<String> rules,
            String compareBrief
    ) {
        ResolvedModelSettings settings = resolveSettings(provider, modelName);
        if (!settings.available()) {
            AgentThoughts.emit("model", settings.missingReason());
            log.warn("REVIEW 跳过：模型未就绪 {}", settings.missingReason());
            return AgentResult.degraded(settings.missingReason());
        }
        List<CodeWorkspace> workspaces = codeWorkspaces(projectId);
        ToolBudget budget = new ToolBudget(maxToolCalls());
        Consumer<AgentThought> progress = AgentThoughts.sink();
        List<ToolCallback> tools = new ArrayList<>();
        tools.addAll(skillToolCallbacks(budget, projectId, progress));
        tools.addAll(codeToolCallbacks(budget, workspaces, progress));
        log.info(
                "REVIEW 开始 provider={} model={} tools={} workspaces={} question={}",
                settings.provider().id(),
                settings.model(),
                tools.stream().map(item -> item.getToolDefinition().name()).toList(),
                workspaces.stream().map(CodeWorkspace::name).toList(),
                LogText.preview(question)
        );
        if (tools.isEmpty()) {
            progress.accept(AgentThought.of("model", "当前没有可搜索的代码仓，仅用已注入的 diff 审查"));
        } else {
            progress.accept(AgentThought.of(
                    "model",
                    "已装载代码工具：" + tools.stream().map(item -> item.getToolDefinition().name()).toList()
            ));
        }
        progress.accept(AgentThought.of("model", "正在调用模型…"));
        long started = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec request = ChatClient.create(chatModels.create(settings))
                    .prompt()
                    .system("""
                            你是只读代码审查助手。根据下方已配置的 Skill / Rule，对照两个分支的差异文件给出审查意见。
                            只审查变更文件列表里的路径，不要读未改动的文件，不要一次读完整大文件。
                            需要核对源码时，用 code_read 带 startLine 分段读差异附近，或用 code_search 定位符号。
                            Skill 附属文件用 skill_read，按清单里的 slug 和 path 读取。
                            整库 SQL、lock、生成物、二进制可跳过，除非审查焦点点名。
                            不得编造未读到的源码，不得建议或执行 commit、push、写库、删缓存、重启或改配置。
                            输出结构（正文必须用 Markdown，问题与建议用表格，不要用纯列表堆条目）：
                            1. 变更摘要：一段话说明改了什么。
                            2. 问题表，表头固定为：严重级别 | 文件 | 行号 | 风险 | 证据。严重级别只用高/中/低。
                            3. 回归与测试建议表，表头固定为：范围 | 建议 | 依据。
                            4. 已排除项；信息不足时在对应单元格写「未知」，不要省略列。没有问题时问题表保留表头并写一行「无」。
                            正文之后必须再输出一个 review-marks 代码块（语言标记就写 review-marks），内容为 JSON 数组。
                            每条字段：path（变更列表里的相对路径）、line（对比分支行号，从 1 起）、side（一般 right）、severity（high|medium|low）、title、comment。
                            只标注有问题的代码行；没有问题时输出空数组 []。
                            """)
                    .user("""
                            环境：%s
                            %s
                            已配置 Skill：
                            %s
                            已配置 Rule：
                            %s
                            分支对比：
                            %s
                            审查焦点：
                            %s
                            """.formatted(
                            environment,
                            code.summarize(workspaces),
                            joinBlocks(skills),
                            joinBlocks(rules),
                            compareBrief == null || compareBrief.isBlank() ? "（没有 diff）" : compareBrief,
                            question == null ? "" : question
                    ));
            if (!tools.isEmpty()) {
                request = request.toolCallbacks(tools);
            }
            ChatClient.ChatClientRequestSpec reviewRequest = request;
            String response = ResilientModelCall.content(
                    () -> reviewRequest.call().content(),
                    agent.getModelMaxAttempts(),
                    agent.getModelRetryBackoffMs(),
                    progress
            );
            progress.accept(AgentThought.of("model", "模型已返回，正在整理审查意见"));
            log.info(
                    "REVIEW 完成 elapsedMs={} usedTools={} budgetExhausted={} reply={}",
                    System.currentTimeMillis() - started,
                    budget.used(),
                    budget.exhausted(),
                    LogText.preview(response)
            );
            return AgentResult.completed(response, tools.size(), budget.used(), budget.exhausted(), maxToolCalls());
        } catch (Exception exception) {
            progress.accept(AgentThought.of("model", "模型调用失败"));
            log.error("REVIEW 失败 elapsedMs={}：{}", System.currentTimeMillis() - started, exception.getMessage(), exception);
            return AgentResult.degraded(explainModelFailure(exception));
        }
    }

    private static String joinBlocks(List<String> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "（当前项目没有匹配的条目，按内置只读审查规则执行）";
        }
        return String.join("\n", blocks);
    }

    public AgentResult investigate(InvestigateCommand command) {
        String question = command.question();
        String route = command.route();
        String environment = command.environment();
        boolean codeBound = command.codeBound();
        List<String> skills = command.skills();
        List<String> rules = command.rules();
        String provider = command.provider();
        String modelName = command.model();
        String projectId = command.projectId();
        List<String> toolCatalog = command.toolCatalog();
        String memoryPrompt = command.memoryPrompt();
        List<String> allowedMysqlProfiles = command.profiles(ToolKind.MYSQL);
        List<String> allowedRedisProfiles = command.profiles(ToolKind.REDIS);
        List<String> allowedLogsProfiles = command.profiles(ToolKind.LOGS);
        List<String> allowedMseProfiles = command.profiles(ToolKind.MSE);
        List<String> allowedK8sProfiles = command.profiles(ToolKind.K8S);
        List<String> allowedStarrocksProfiles = command.profiles(ToolKind.STARROCKS);
        List<String> allowedKafkaProfiles = command.profiles(ToolKind.KAFKA);
        List<String> allowedRocketmqProfiles = command.profiles(ToolKind.ROCKETMQ);
        List<String> allowedMongodbProfiles = command.profiles(ToolKind.MONGODB);
        List<String> allowedArmsProfiles = command.profiles(ToolKind.ARMS);
        List<String> allowedLoadBalancerProfiles = command.profiles(ToolKind.LOAD_BALANCER);
        List<String> allowedCdnProfiles = command.profiles(ToolKind.CDN);
        String pinnedMcpProfile = command.pinnedMcpProfile();
        UUID repositoryId = command.repositoryId();
        String agentSystemPrompt = command.agentSystemPrompt();
        int toolCallLimit = command.toolCallLimit();
        ResolvedModelSettings settings = resolveSettings(provider, modelName);
        if (!settings.available()) {
            AgentThoughts.emit("model", settings.missingReason());
            log.warn("排查跳过：模型未就绪 {}", settings.missingReason());
            return AgentResult.degraded(settings.missingReason() + " 已完成路由，未执行外部查询。");
        }
        ChatModel model = chatModels.create(settings);
        UUID projectUuid = parseProjectId(projectId);
        Consumer<AgentThought> progress = AgentThoughts.sink();

        Environment resolvedEnvironment = Environment.from(environment);
        List<ToolBinding> enabledBindings = projectUuid == null
                ? List.of()
                : controlPlane.bindings(projectUuid).stream()
                        .filter(ToolBinding::enabled)
                        .filter(binding -> binding.environment() == resolvedEnvironment)
                        .toList();
        NamedMcpFocus.AppliedMcpFocus applied = NamedMcpFocus.apply(
                enabledBindings,
                question,
                pinnedMcpProfile,
                allowedMysqlProfiles,
                allowedRedisProfiles,
                allowedLogsProfiles,
                allowedMseProfiles,
                allowedK8sProfiles,
                allowedStarrocksProfiles,
                allowedKafkaProfiles,
                allowedRocketmqProfiles,
                allowedMongodbProfiles,
                allowedArmsProfiles,
                allowedLoadBalancerProfiles,
                allowedCdnProfiles
        );
        allowedMysqlProfiles = applied.mysqlProfiles();
        allowedRedisProfiles = applied.redisProfiles();
        allowedLogsProfiles = applied.logsProfiles();
        allowedMseProfiles = applied.mseProfiles();
        allowedK8sProfiles = applied.k8sProfiles();
        allowedStarrocksProfiles = applied.starrocksProfiles();
        allowedKafkaProfiles = applied.kafkaProfiles();
        allowedRocketmqProfiles = applied.rocketmqProfiles();
        allowedMongodbProfiles = applied.mongodbProfiles();
        allowedArmsProfiles = applied.armsProfiles();
        allowedLoadBalancerProfiles = applied.loadBalancerProfiles();
        allowedCdnProfiles = applied.cdnProfiles();
        Optional<NamedMcpFocus> namedMcp = applied.focus();
        List<CodeWorkspace> workspaces = resolveWorkspaces(projectUuid, repositoryId, question, namedMcp);
        boolean canReadCode = codeBound && !workspaces.isEmpty();
        if (namedMcp.isPresent()) {
            NamedMcpFocus focus = namedMcp.get();
            progress.accept(AgentThought.of(
                    "phase",
                    "用户点名 MCP " + focus.profile() + "（" + focus.kindLabel() + "），本轮仅启用对应只读工具"
            ));
            if (MentionParser.extract(question).isEmpty()) {
                workspaces = List.of();
                canReadCode = false;
            }
        }

        // 本次重构：数据类工具的 profile 统一按 ToolKind 归档，驱动工具装载与外部 MCP 过滤
        Map<ToolKind, List<String>> profilesByKind = new EnumMap<>(ToolKind.class);
        profilesByKind.put(ToolKind.MYSQL, allowedMysqlProfiles);
        profilesByKind.put(ToolKind.REDIS, allowedRedisProfiles);
        profilesByKind.put(ToolKind.LOGS, allowedLogsProfiles);
        profilesByKind.put(ToolKind.MSE, allowedMseProfiles);
        profilesByKind.put(ToolKind.K8S, allowedK8sProfiles);
        profilesByKind.put(ToolKind.STARROCKS, allowedStarrocksProfiles);
        profilesByKind.put(ToolKind.KAFKA, allowedKafkaProfiles);
        profilesByKind.put(ToolKind.ROCKETMQ, allowedRocketmqProfiles);
        profilesByKind.put(ToolKind.MONGODB, allowedMongodbProfiles);
        profilesByKind.put(ToolKind.ARMS, allowedArmsProfiles);
        profilesByKind.put(ToolKind.LOAD_BALANCER, allowedLoadBalancerProfiles);
        profilesByKind.put(ToolKind.CDN, allowedCdnProfiles);
        ToolBudget budget = new ToolBudget(toolCallLimit);
        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        for (ToolCallback callback : localToolCallbacks(
                budget,
                projectUuid,
                environment,
                profilesByKind,
                workspaces,
                namedMcp.isEmpty(),
                progress
        )) {
            byName.put(callback.getToolDefinition().name(), callback);
        }
        if (externalMcpEnabled) {
            ToolCallbackProvider toolProvider = mcpToolCallbacks.getIfAvailable();
            if (toolProvider != null) {
                for (ToolCallback callback : toolProvider.getToolCallbacks()) {
                    if (isBindingAllowed(callback.getToolDefinition().name(), profilesByKind, canReadCode)) {
                        byName.put(callback.getToolDefinition().name(), callback);
                    }
                }
            }
        }
        List<ToolCallback> tools = new ArrayList<>(byName.values());
        String hint = code.expandMentions(workspaces, question);
        String brief = InvestigationBrief.rewrite(
                question,
                environment,
                allowedMysqlProfiles,
                allowedRedisProfiles,
                endpointHint(projectUuid, environment, allowedMysqlProfiles, "mysql"),
                endpointHint(projectUuid, environment, allowedRedisProfiles, "redis"),
                allowedLogsProfiles,
                allowedMseProfiles,
                endpointHint(projectUuid, environment, allowedLogsProfiles, "logs"),
                endpointHint(projectUuid, environment, allowedMseProfiles, "mse"),
                allowedK8sProfiles,
                endpointHint(projectUuid, environment, allowedK8sProfiles, "k8s"),
                allowedStarrocksProfiles,
                endpointHint(projectUuid, environment, allowedStarrocksProfiles, "starrocks"),
                allowedKafkaProfiles,
                endpointHint(projectUuid, environment, allowedKafkaProfiles, "kafka"),
                allowedRocketmqProfiles,
                endpointHint(projectUuid, environment, allowedRocketmqProfiles, "rocketmq"),
                allowedMongodbProfiles,
                endpointHint(projectUuid, environment, allowedMongodbProfiles, "mongodb"),
                allowedArmsProfiles,
                endpointHint(projectUuid, environment, allowedArmsProfiles, "arms"),
                allowedLoadBalancerProfiles,
                endpointHint(projectUuid, environment, allowedLoadBalancerProfiles, "loadbalancer"),
                allowedCdnProfiles,
                endpointHint(projectUuid, environment, allowedCdnProfiles, "cdn"),
                namedMcp.orElse(null)
        );
        String orchestrationHint = namedMcp.isPresent()
                ? "用户已点名 MCP " + namedMcp.get().profile() + "（" + namedMcp.get().kindLabel()
                + "）。本轮只调用该 profile 对应工具，禁止 mysql/redis/sls 等其他 MCP；不要先读代码猜缓存 key。"
                : agentSystemPrompt != null && !agentSystemPrompt.isBlank()
                ? "本轮只使用上方已装载的工具。不要调用未列出的 MCP。结论用中文写清证据来源。"
                : """
                调用 mysql_query_readonly / redis_* / sls_* / mse_* / arms_* / lb_* / cdn_* / k8s_* / starrocks_* / kafka_* / rocketmq_* / mongodb_* 时必须使用上面的 MCP profile 名，不要自行加前缀。
                请按代码调用链 → 真实表名/缓存 key → 只读数据 / 日志 / 网关 / 调用链 / 负载均衡 / CDN / 集群 / 数仓 / 消息队列 / 文档库 的顺序排查。
                """;
        String prompt = """
                %s
                %s
                已启用工具目录（按简介和标签选择工具，不要凭名字猜测）：
                %s
                已路由 Skill：%s
                Skill 上下文：%s
                已启用 Rules：%s
                %s
                %s
                %s
                若缺少必要标识（traceId、userId、orderId、shortPlayId），先说明缺什么。
                """.formatted(
                brief,
                memoryPrompt == null || memoryPrompt.isBlank() ? "" : memoryPrompt.trim(),
                String.join("\n", toolCatalog == null || toolCatalog.isEmpty() ? List.of("（未配置简介/标签）") : toolCatalog),
                route,
                String.join("\n", skills == null ? List.of() : skills),
                String.join("\n", rules == null || rules.isEmpty() ? List.of("（未配置）") : rules),
                code.summarize(workspaces),
                hint,
                orchestrationHint
        );
        log.info(
                "排查开始 route={} provider={} model={} tools={} mysql={} redis={} sls={} mse={} arms={} lb={} cdn={} k8s={} starrocks={} kafka={} rocketmq={} mongodb={} workspaces={} question={}",
                route,
                settings.provider().id(),
                settings.model(),
                tools.stream().map(item -> item.getToolDefinition().name()).toList(),
                allowedMysqlProfiles,
                allowedRedisProfiles,
                allowedLogsProfiles,
                allowedMseProfiles,
                allowedArmsProfiles,
                allowedLoadBalancerProfiles,
                allowedCdnProfiles,
                allowedK8sProfiles,
                allowedStarrocksProfiles,
                allowedKafkaProfiles,
                allowedRocketmqProfiles,
                allowedMongodbProfiles,
                workspaces.stream().map(CodeWorkspace::name).toList(),
                LogText.preview(question)
        );
        if (tools.isEmpty()) {
            progress.accept(AgentThought.of("model", "当前没有可用的只读工具，仅用模型和已装载上下文排查"));
        } else {
            progress.accept(AgentThought.of(
                    "model",
                    "已装载 " + tools.size() + " 个只读工具："
                            + tools.stream().map(item -> item.getToolDefinition().name()).toList()
            ));
        }
        progress.accept(AgentThought.of("model", "正在调用模型…"));
        long started = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec request = ChatClient.create(model)
                    .prompt()
                    .system(systemPromptFor(namedMcp, agentSystemPrompt))
                    .user(prompt);
            if (!tools.isEmpty()) {
                request = request.toolCallbacks(tools);
            }
            ChatClient.ChatClientRequestSpec finalRequest = request;
            String response = ResilientModelCall.content(
                    () -> finalRequest.call().content(),
                    agent.getModelMaxAttempts(),
                    agent.getModelRetryBackoffMs(),
                    progress
            );
            progress.accept(AgentThought.of("model", "模型已返回，正在整理结论"));
            log.info(
                    "排查完成 route={} elapsedMs={} usedTools={} budgetExhausted={} reply={}",
                    route,
                    System.currentTimeMillis() - started,
                    budget.used(),
                    budget.exhausted(),
                    LogText.preview(response)
            );
            return AgentResult.completed(response, tools.size(), budget.used(), budget.exhausted(), maxToolCalls());
        } catch (Exception exception) {
            progress.accept(AgentThought.of("model", "模型调用失败"));
            log.error("排查失败 route={} elapsedMs={}：{}", route, System.currentTimeMillis() - started, exception.getMessage(), exception);
            return AgentResult.degraded(explainModelFailure(exception));
        }
    }

    private String systemPromptFor(Optional<NamedMcpFocus> namedMcp, String agentSystemPrompt) {
        if (namedMcp != null && namedMcp.isPresent()) {
            return NAMED_MCP_INVESTIGATE_PROMPT;
        }
        if (agentSystemPrompt != null && !agentSystemPrompt.isBlank()) {
            return agentSystemPrompt.trim() + "\n不得编造工具结果，不得建议或执行写库、删缓存、重启或改配置。";
        }
        return investigatePrompt;
    }

    public String synthesize(String question, String route, AgentResult investigation) {
        if (investigation.modelUsed()) {
            String draft = investigation.content() == null ? "" : investigation.content();
            if (!needsConclusion(investigation, draft)) {
                return draft;
            }
            AgentThoughts.emit("phase", "工具循环已结束，正在整理结论");
            String concluded = concludeWithoutTools(question, route, draft);
            return concluded == null || concluded.isBlank() ? draft : concluded;
        }
        if (investigation.content() != null && !investigation.content().isBlank()) {
            return """
                    ## 现象
                    %s

                    ## 已确认的证据
                    路由至 %s，但模型调用失败。

                    ## 失败原因
                    %s

                    ## 建议下一步
                    检查模型供应商、Key / Base URL、模型名和 MCP 绑定后重试。
                    """.formatted(question, route, investigation.content());
        }
        return """
                ## 现象
                %s

                ## 已确认的证据
                路由至 %s；当前未配置可用模型或数据工具。

                ## 建议下一步
                在「模型」页或 application-model.yml 配置供应商后，再为当前环境绑定只读 ToolBinding。
                """.formatted(question, route);
    }

    private static boolean needsConclusion(AgentResult investigation, String draft) {
        if (looksLikeReport(draft)) {
            return false;
        }
        if (looksLikeNamedMcpDump(draft) && !investigation.budgetExhausted()) {
            return false;
        }
        return true;
    }

    private static boolean looksLikeReport(String draft) {
        return draft != null && draft.contains("已确认的证据");
    }

    private static boolean looksLikeNamedMcpDump(String draft) {
        return draft != null && draft.contains("```") && !draft.contains("已确认的证据");
    }

    private String concludeWithoutTools(String question, String route, String draft) {
        ResolvedModelSettings settings = modelSettings.current();
        if (!settings.available()) {
            log.info("结论补写跳过：{}", settings.missingReason());
            return null;
        }
        try {
            return ResilientModelCall.content(
                    () -> ChatClient.create(chatModels.create(settings))
                            .prompt()
                            .system("""
                                    根据排查草稿写成四段中文结论，不得再调工具，不得编造草稿里没有的证据。
                                    固定四段标题：已确认的证据、假设、已排除项、下一步。
                                    """)
                            .user("问题：\n" + (question == null ? "" : question)
                                    + "\n方向：" + (route == null ? "" : route)
                                    + "\n草稿：\n" + (draft == null ? "" : draft))
                            .call()
                            .content(),
                    agent.getModelMaxAttempts(),
                    agent.getModelRetryBackoffMs(),
                    AgentThoughts.sink()
            );
        } catch (Exception exception) {
            log.warn("结论补写失败：{}", exception.getMessage());
            return null;
        }
    }

    public boolean modelAvailable() {
        return modelSettings.current().available();
    }

    public Map<String, Object> capabilities() {
        // 工具名从只读工具面的单一事实来源取，避免与实际装载逻辑漂移
        List<String> local = new ArrayList<>(com.maiya.investigator.mcp.ReadonlyToolCatalog.allLocalToolNames());
        List<String> external = new ArrayList<>();
        if (externalMcpEnabled) {
            ToolCallbackProvider toolProvider = mcpToolCallbacks.getIfAvailable();
            if (toolProvider != null) {
                for (ToolCallback callback : toolProvider.getToolCallbacks()) {
                    external.add(callback.getToolDefinition().name());
                }
            }
        }
        ResolvedModelSettings settings = modelSettings.current();
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("dashscopeModel", settings.available() && settings.provider().id().equals("dashscope"));
        capabilities.put("modelAvailable", settings.available());
        capabilities.put("modelProvider", settings.provider().id());
        capabilities.put("modelName", settings.model());
        capabilities.put("localReadonlyTools", local);
        capabilities.put("mcpEnabled", externalMcpEnabled);
        capabilities.put("mcpTools", external);
        capabilities.put("maxToolCalls", maxToolCalls());
        capabilities.put("maxUserTurns", agent.getMaxUserTurns());
        return capabilities;
    }

    private List<ToolCallback> localToolCallbacks(
            ToolBudget budget,
            UUID projectId,
            String environment,
            Map<ToolKind, List<String>> profilesByKind,
            List<CodeWorkspace> workspaces,
            boolean includeSkillTools,
            Consumer<AgentThought> progress
    ) {
        // 本次重构：按 ToolKind 归档的 profile 统一驱动装载，某类为空则该类工具整体不挂
        List<String> allowedMysqlProfiles = profilesFor(profilesByKind, ToolKind.MYSQL);
        List<String> allowedRedisProfiles = profilesFor(profilesByKind, ToolKind.REDIS);
        List<String> allowedLogsProfiles = profilesFor(profilesByKind, ToolKind.LOGS);
        List<String> allowedMseProfiles = profilesFor(profilesByKind, ToolKind.MSE);
        List<String> allowedK8sProfiles = profilesFor(profilesByKind, ToolKind.K8S);
        List<String> allowedStarrocksProfiles = profilesFor(profilesByKind, ToolKind.STARROCKS);
        List<String> allowedKafkaProfiles = profilesFor(profilesByKind, ToolKind.KAFKA);
        List<String> allowedRocketmqProfiles = profilesFor(profilesByKind, ToolKind.ROCKETMQ);
        List<String> allowedMongodbProfiles = profilesFor(profilesByKind, ToolKind.MONGODB);
        List<String> allowedArmsProfiles = profilesFor(profilesByKind, ToolKind.ARMS);
        List<String> allowedLoadBalancerProfiles = profilesFor(profilesByKind, ToolKind.LOAD_BALANCER);
        List<String> allowedCdnProfiles = profilesFor(profilesByKind, ToolKind.CDN);
        List<ToolCallback> callbacks = new ArrayList<>();
        if (includeSkillTools) {
            callbacks.addAll(skillToolCallbacks(budget, projectId == null ? null : projectId.toString(), progress));
        }
        callbacks.addAll(codeToolCallbacks(budget, workspaces, progress));
        if (!allowedMysqlProfiles.isEmpty()) {
            callbacks.add(FunctionToolCallback
                    .builder("mysql_query_readonly", (MysqlQuery input) -> safeTool(
                            progress,
                            "mysql_query_readonly",
                            input.sql(),
                            () -> {
                                budget.consume();
                                String profile = requireAllowedProfile("MySQL", input.profile(), allowedMysqlProfiles);
                                log.info("调用 mysql_query_readonly requested={} resolved={} sql={}", input.profile(), profile, LogText.preview(input.sql()));
                                return mysql.query(projectId, environment, profile, input.sql());
                            }
                    ))
                    .description("在当前项目 MCP 页已启用的 MySQL 连接上执行只读 SQL。当前可用 profile："
                            + String.join(", ", allowedMysqlProfiles)
                            + "。仅允许 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH。SELECT/WITH 会自动限制 200 行；SHOW/DESCRIBE 不要加 LIMIT。表名必须来自刚读到的代码。")
                    .inputType(MysqlQuery.class)
                    .build());
        }
        if (!allowedRedisProfiles.isEmpty()) {
            callbacks.add(FunctionToolCallback
                    .builder("redis_get", (RedisGet input) -> safeTool(progress, "redis_get", input.key(), () -> {
                        budget.consume();
                        String profile = requireAllowedProfile("Redis", input.profile(), allowedRedisProfiles);
                        log.info("调用 redis_get requested={} resolved={} key={}", input.profile(), profile, input.key());
                        return redis.get(projectId, environment, profile, input.key());
                    }))
                    .description("在当前项目 MCP 页已启用的 Redis 连接上读取指定 key。当前可用 profile："
                            + String.join(", ", allowedRedisProfiles)
                            + "。不支持写入或删除。key 必须来自刚读到的代码。")
                    .inputType(RedisGet.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("redis_inspect", (RedisGet input) -> safeTool(progress, "redis_inspect", input.key(), () -> {
                        budget.consume();
                        String profile = requireAllowedProfile("Redis", input.profile(), allowedRedisProfiles);
                        log.info("调用 redis_inspect requested={} resolved={} key={}", input.profile(), profile, input.key());
                        return redis.inspect(projectId, environment, profile, input.key());
                    }))
                    .description("在当前项目 MCP 页已启用的 Redis 连接上读取 key 的类型和 TTL。当前可用 profile："
                            + String.join(", ", allowedRedisProfiles)
                            + "。不支持写入或删除。")
                    .inputType(RedisGet.class)
                    .build());
        }
        if (allowedLogsProfiles != null && !allowedLogsProfiles.isEmpty()) {
            callbacks.add(FunctionToolCallback
                    .builder("sls_query_readonly", (SlsQuery input) -> safeTool(
                            progress,
                            "sls_query_readonly",
                            input.query(),
                            () -> {
                                budget.consume();
                                String profile = requireAllowedProfile("SLS", input.profile(), allowedLogsProfiles);
                                log.info("调用 sls_query_readonly requested={} resolved={} query={}", input.profile(), profile, LogText.preview(input.query()));
                                return sls.query(projectId, environment, profile, input.query(), input.from(), input.to(), input.limit(), input.logstore());
                            }
                    ))
                    .description("在当前项目 MCP 页已启用的 SLS 连接上只读查日志。当前可用 profile："
                            + String.join(", ", allowedLogsProfiles)
                            + "。query 只写裸 id 或 traceId: <值>，禁止把类名、AND、OR 拼进同一条；"
                            + "工具会在零命中时自动换写法，并按 24 小时从近到远分段扫近 7 天。"
                            + "logstore 可填 list 返回的任意库（不填则用 Binding 默认入口）；"
                            + "from/to 用户没给时间就留空（默认近 7 天），不要编造年份；可用 now、-1h、-7d。"
                            + "limit 默认 50、最大 100。")
                    .inputType(SlsQuery.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("sls_list_logstores", (SlsList input) -> safeTool(progress, "sls_list_logstores", input.profile(), () -> {
                        budget.consume();
                        String profile = requireAllowedProfile("SLS", input.profile(), allowedLogsProfiles);
                        log.info("调用 sls_list_logstores requested={} resolved={}", input.profile(), profile);
                        return sls.listLogstores(projectId, environment, profile);
                    }))
                    .description("列出当前 SLS Binding 的 project 下全部 logstore。当前可用 profile："
                            + String.join(", ", allowedLogsProfiles)
                            + "。返回列表中的 logstore 均可查；Binding 里配置的只是默认入口。查日志前先确认名字，不要猜。")
                    .inputType(SlsList.class)
                    .build());
        }
        if (allowedMseProfiles != null && !allowedMseProfiles.isEmpty()) {
            String mseNames = String.join(", ", allowedMseProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("mse_list_gateways", (MseProfile input) -> mseCall(progress, budget, "mse_list_gateways", input.profile(), allowedMseProfiles, () -> mse.listGateways(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles))))
                    .description("列出当前区域 MSE 云原生网关。可用 profile：" + mseNames + "。只读。")
                    .inputType(MseProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_list_routes", (MseKeyword input) -> mseCall(progress, budget, "mse_list_routes", input.keyword(), allowedMseProfiles, () -> mse.listRoutes(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.keyword())))
                    .description("列出网关路由（path、后端服务、权重）。可用 profile：" + mseNames + "。Binding 需填 gatewayUniqueId。")
                    .inputType(MseKeyword.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_get_route", (MseId input) -> mseCall(progress, budget, "mse_get_route", input.id(), allowedMseProfiles, () -> mse.getRoute(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.id())))
                    .description("查看网关路由详情。参数 id 为 RouteId。可用 profile：" + mseNames + "。")
                    .inputType(MseId.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_list_services", (MseKeyword input) -> mseCall(progress, budget, "mse_list_services", input.keyword(), allowedMseProfiles, () -> mse.listServices(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.keyword())))
                    .description("列出网关订阅的后端服务。可用 profile：" + mseNames + "。")
                    .inputType(MseKeyword.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_get_service", (MseId input) -> mseCall(progress, budget, "mse_get_service", input.id(), allowedMseProfiles, () -> mse.getService(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.id())))
                    .description("查看网关服务详情（实例 IP、健康）。参数 id 为 ServiceId。可用 profile：" + mseNames + "。")
                    .inputType(MseId.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_list_nacos_services", (MseNacosService input) -> mseCall(progress, budget, "mse_list_nacos_services", input.keyword(), allowedMseProfiles, () -> mse.listNacosServices(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.keyword(), input.namespaceId())))
                    .description("列出 MSE Nacos 服务。可用 profile：" + mseNames + "。Binding 需填 nacosInstanceId。只读。")
                    .inputType(MseNacosService.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_list_nacos_instances", (MseNacosInstances input) -> mseCall(progress, budget, "mse_list_nacos_instances", input.serviceName(), allowedMseProfiles, () -> mse.listNacosInstances(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.serviceName(), input.namespaceId(), input.clusterName())))
                    .description("列出 Nacos 服务实例与健康状态。可用 profile：" + mseNames + "。")
                    .inputType(MseNacosInstances.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_get_nacos_config", (MseNacosConfig input) -> mseCall(progress, budget, "mse_get_nacos_config", input.dataId(), allowedMseProfiles, () -> mse.getNacosConfig(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.dataId(), input.group(), input.namespaceId())))
                    .description("只读获取 Nacos 配置，不发布、不修改。可用 profile：" + mseNames + "。")
                    .inputType(MseNacosConfig.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mse_list_governance", (MseGovernance input) -> mseCall(progress, budget, "mse_list_governance", input.appName(), allowedMseProfiles, () -> mse.listGovernance(projectId, environment, requireAllowedProfile("MSE", input.profile(), allowedMseProfiles), input.appName(), input.ruleType(), input.namespace())))
                    .description("按应用名只读查治理规则。ruleType：flow（限流）/ circuit（熔断）/ isolation（隔离降级）/ gray（灰度泳道）。可用 profile：" + mseNames + "。")
                    .inputType(MseGovernance.class)
                    .build());
        }
        if (allowedK8sProfiles != null && !allowedK8sProfiles.isEmpty()) {
            String k8sNames = String.join(", ", allowedK8sProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("k8s_list_clusters", (K8sProfile input) -> k8sCall(progress, budget, "k8s_list_clusters", input.profile(), allowedK8sProfiles, () -> k8s.listClusters(projectId, environment, requireAllowedProfile("K8s", input.profile(), allowedK8sProfiles))))
                    .description("列出该 K8s Binding 已开放的集群名和默认集群。不确定集群时先调这个。可用 profile：" + k8sNames + "。只读。")
                    .inputType(K8sProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("k8s_list_namespaces", (K8sProfile input) -> k8sCall(progress, budget, "k8s_list_namespaces", input.profile(), allowedK8sProfiles, () -> k8s.listNamespaces(projectId, environment, requireAllowedProfile("K8s", input.profile(), allowedK8sProfiles), input.cluster())))
                    .description("列出集群命名空间。cluster 可选，不填则用 Binding 默认集群。可用 profile：" + k8sNames + "。只读，不创建。")
                    .inputType(K8sProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("k8s_list_nodes", (K8sProfile input) -> k8sCall(progress, budget, "k8s_list_nodes", input.profile(), allowedK8sProfiles, () -> k8s.listNodes(projectId, environment, requireAllowedProfile("K8s", input.profile(), allowedK8sProfiles), input.cluster())))
                    .description("列出节点及 Ready / NotReady。cluster 可选，不填则用 Binding 默认集群。可用 profile：" + k8sNames + "。")
                    .inputType(K8sProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("k8s_list_pods", (K8sPods input) -> k8sCall(progress, budget, "k8s_list_pods", input.namespace(), allowedK8sProfiles, () -> k8s.listPods(projectId, environment, requireAllowedProfile("K8s", input.profile(), allowedK8sProfiles), input.namespace(), input.labelSelector(), input.phase(), input.cluster())))
                    .description("列出命名空间内 Pod（phase、重启次数、容器等待原因）。优先用 Binding 默认/业务 namespace；若返回无权限，换有权限的命名空间，不要反复扫 nacos/kube-system。cluster 可选。可用 profile：" + k8sNames + "。可按 labelSelector / phase 过滤。")
                    .inputType(K8sPods.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("k8s_get_pod", (K8sPod input) -> k8sCall(progress, budget, "k8s_get_pod", input.name(), allowedK8sProfiles, () -> k8s.getPod(projectId, environment, requireAllowedProfile("K8s", input.profile(), allowedK8sProfiles), input.namespace(), input.name(), input.cluster())))
                    .description("查看单个 Pod 摘要和最近事件，不回完整 YAML。cluster 可选，不填则用 Binding 默认集群。可用 profile：" + k8sNames + "。")
                    .inputType(K8sPod.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("k8s_list_events", (K8sNamespace input) -> k8sCall(progress, budget, "k8s_list_events", input.namespace(), allowedK8sProfiles, () -> k8s.listEvents(projectId, environment, requireAllowedProfile("K8s", input.profile(), allowedK8sProfiles), input.namespace(), input.cluster())))
                    .description("列出命名空间事件，Warning 优先。cluster 可选，不填则用 Binding 默认集群。可用 profile：" + k8sNames + "。")
                    .inputType(K8sNamespace.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("k8s_pod_logs", (K8sLogs input) -> k8sCall(progress, budget, "k8s_pod_logs", input.name(), allowedK8sProfiles, () -> k8s.podLogs(projectId, environment, requireAllowedProfile("K8s", input.profile(), allowedK8sProfiles), input.namespace(), input.name(), input.container(), input.tail(), input.cluster())))
                    .description("读取单个容器日志尾部。tail 默认 100、最大 200。cluster 可选，不填则用 Binding 默认集群。可用 profile：" + k8sNames + "。只读，不 exec。")
                    .inputType(K8sLogs.class)
                    .build());
        }
        if (allowedStarrocksProfiles != null && !allowedStarrocksProfiles.isEmpty()) {
            callbacks.add(FunctionToolCallback
                    .builder("starrocks_query_readonly", (MysqlQuery input) -> safeTool(
                            progress,
                            "starrocks_query_readonly",
                            input.sql(),
                            () -> {
                                budget.consume();
                                String profile = requireAllowedProfile("StarRocks", input.profile(), allowedStarrocksProfiles);
                                log.info("调用 starrocks_query_readonly requested={} resolved={} sql={}", input.profile(), profile, LogText.preview(input.sql()));
                                return starrocks.query(projectId, environment, profile, input.sql());
                            }
                    ))
                    .description("在当前项目工具页已启用的 StarRocks 连接上执行只读 SQL（MySQL 协议）。当前可用 profile："
                            + String.join(", ", allowedStarrocksProfiles)
                            + "。仅允许 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH。不得写表。")
                    .inputType(MysqlQuery.class)
                    .build());
        }
        if (allowedKafkaProfiles != null && !allowedKafkaProfiles.isEmpty()) {
            String kafkaNames = String.join(", ", allowedKafkaProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("kafka_list_topics", (MqProfile input) -> mqCall(progress, budget, "kafka_list_topics", input.profile(), allowedKafkaProfiles, () -> kafka.listTopics(projectId, environment, requireAllowedProfile("Kafka", input.profile(), allowedKafkaProfiles))))
                    .description("列出 Kafka topic。可用 profile：" + kafkaNames + "。只读。")
                    .inputType(MqProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("kafka_describe_topic", (MqTopic input) -> mqCall(progress, budget, "kafka_describe_topic", input.topic(), allowedKafkaProfiles, () -> kafka.describeTopic(projectId, environment, requireAllowedProfile("Kafka", input.profile(), allowedKafkaProfiles), input.topic())))
                    .description("查看 Kafka topic 分区数。可用 profile：" + kafkaNames + "。")
                    .inputType(MqTopic.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("kafka_peek_messages", (MqPeek input) -> mqCall(progress, budget, "kafka_peek_messages", input.topic(), allowedKafkaProfiles, () -> kafka.peek(projectId, environment, requireAllowedProfile("Kafka", input.profile(), allowedKafkaProfiles), input.topic(), input.limit())))
                    .description("从 topic 尾部偷看最近消息，不提交位移、不生产。limit 默认 10、最大 20。可用 profile：" + kafkaNames + "。")
                    .inputType(MqPeek.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("kafka_consumer_lag", (MqGroup input) -> mqCall(progress, budget, "kafka_consumer_lag", input.groupId(), allowedKafkaProfiles, () -> kafka.consumerLag(projectId, environment, requireAllowedProfile("Kafka", input.profile(), allowedKafkaProfiles), input.groupId())))
                    .description("查看 Kafka 消费组积压。可用 profile：" + kafkaNames + "。")
                    .inputType(MqGroup.class)
                    .build());
        }
        if (allowedRocketmqProfiles != null && !allowedRocketmqProfiles.isEmpty()) {
            String rocketNames = String.join(", ", allowedRocketmqProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("rocketmq_list_topics", (MqProfile input) -> mqCall(progress, budget, "rocketmq_list_topics", input.profile(), allowedRocketmqProfiles, () -> rocketmq.listTopics(projectId, environment, requireAllowedProfile("RocketMQ", input.profile(), allowedRocketmqProfiles))))
                    .description("列出 RocketMQ topic。可用 profile：" + rocketNames + "。只读。")
                    .inputType(MqProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("rocketmq_peek_messages", (MqPeek input) -> mqCall(progress, budget, "rocketmq_peek_messages", input.topic(), allowedRocketmqProfiles, () -> rocketmq.peek(projectId, environment, requireAllowedProfile("RocketMQ", input.profile(), allowedRocketmqProfiles), input.topic(), input.limit())))
                    .description("从 topic 尾部偷看最近消息，不 ack、不发送。limit 默认 10、最大 20。可用 profile：" + rocketNames + "。")
                    .inputType(MqPeek.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("rocketmq_consumer_lag", (MqGroup input) -> mqCall(progress, budget, "rocketmq_consumer_lag", input.groupId(), allowedRocketmqProfiles, () -> rocketmq.consumerLag(projectId, environment, requireAllowedProfile("RocketMQ", input.profile(), allowedRocketmqProfiles), input.groupId())))
                    .description("查看 RocketMQ 消费组积压。可用 profile：" + rocketNames + "。")
                    .inputType(MqGroup.class)
                    .build());
        }
        if (allowedMongodbProfiles != null && !allowedMongodbProfiles.isEmpty()) {
            String mongoNames = String.join(", ", allowedMongodbProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("mongodb_list_databases", (MongodbProfile input) -> safeTool(progress, "mongodb_list_databases", input.profile(), () -> {
                        budget.consume();
                        String profile = requireAllowedProfile("MongoDB", input.profile(), allowedMongodbProfiles);
                        log.info("调用 mongodb_list_databases requested={} resolved={}", input.profile(), profile);
                        return mongodb.listDatabases(projectId, environment, profile);
                    }))
                    .description("列出当前项目 MCP 页已启用的 MongoDB 上的库。当前可用 profile：" + mongoNames + "。只读。")
                    .inputType(MongodbProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mongodb_list_collections", (MongodbCollections input) -> safeTool(progress, "mongodb_list_collections", input.database(), () -> {
                        budget.consume();
                        String profile = requireAllowedProfile("MongoDB", input.profile(), allowedMongodbProfiles);
                        log.info("调用 mongodb_list_collections requested={} resolved={} db={}", input.profile(), profile, input.database());
                        return mongodb.listCollections(projectId, environment, profile, input.database());
                    }))
                    .description("列出指定库的集合。database 可空（用连接串里的默认库）。可用 profile：" + mongoNames + "。")
                    .inputType(MongodbCollections.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("mongodb_query_readonly", (MongodbQuery input) -> safeTool(progress, "mongodb_query_readonly", input.collection(), () -> {
                        budget.consume();
                        String profile = requireAllowedProfile("MongoDB", input.profile(), allowedMongodbProfiles);
                        log.info("调用 mongodb_query_readonly requested={} resolved={} collection={}", input.profile(), profile, input.collection());
                        return mongodb.query(
                                projectId,
                                environment,
                                profile,
                                input.database(),
                                input.collection(),
                                MongoJsonArg.toJsonString(input.filter()),
                                MongoJsonArg.toJsonString(input.projection()),
                                MongoJsonArg.toJsonString(input.sort()),
                                input.limit()
                        );
                    }))
                    .description("在已启用 MongoDB 上只读 find。filter/projection/sort 传 JSON 对象（不要包成字符串）。"
                            + "用户画像集合 ums_user_tag_attr 用 {\"_id\": 用户id数字} 查，不要用 user_id。"
                            + "禁止 $where/$out。limit 默认 20、最大 50。可用 profile："
                            + mongoNames + "。")
                    .inputType(MongodbQuery.class)
                    .build());
        }
        if (allowedArmsProfiles != null && !allowedArmsProfiles.isEmpty()) {
            String armsNames = String.join(", ", allowedArmsProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("arms_list_apps", (CloudProfile input) -> cloudCall(progress, budget, "arms_list_apps", input.profile(), allowedArmsProfiles, () -> {
                        String profile = requireAllowedProfile("ARMS", input.profile(), allowedArmsProfiles);
                        log.info("调用 arms_list_apps requested={} resolved={}", input.profile(), profile);
                        return arms.listApps(projectId, environment, profile);
                    }))
                    .description("列出当前 Binding 开放的 ARMS 应用。没有白名单时才调 ListTraceApps。可用 profile：" + armsNames + "。只读。")
                    .inputType(CloudProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("arms_search_traces", (ArmsSearch input) -> cloudCall(progress, budget, "arms_search_traces", input.application(), allowedArmsProfiles, () -> {
                        String profile = requireAllowedProfile("ARMS", input.profile(), allowedArmsProfiles);
                        log.info("调用 arms_search_traces requested={} resolved={} app={}", input.profile(), profile, input.application());
                        return arms.searchTraces(
                                projectId,
                                environment,
                                profile,
                                input.application(),
                                input.operation(),
                                input.from(),
                                input.to(),
                                input.minDurationMs(),
                                input.limit()
                        );
                    }))
                    .description("按应用搜 ARMS 调用链。必须指定 application。时间窗默认近 7 天，单次最多 50 条。可用 profile：" + armsNames + "。")
                    .inputType(ArmsSearch.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("arms_get_trace", (ArmsTrace input) -> cloudCall(progress, budget, "arms_get_trace", input.traceId(), allowedArmsProfiles, () -> {
                        String profile = requireAllowedProfile("ARMS", input.profile(), allowedArmsProfiles);
                        log.info("调用 arms_get_trace requested={} resolved={} traceSuffix={}", input.profile(), profile, suffix(input.traceId()));
                        return arms.getTrace(projectId, environment, profile, input.application(), input.traceId(), input.from(), input.to());
                    }))
                    .description("按 traceId 拉 ARMS 单条调用链。不要把完整 trace 贴进结论。可用 profile：" + armsNames + "。")
                    .inputType(ArmsTrace.class)
                    .build());
        }
        if (allowedLoadBalancerProfiles != null && !allowedLoadBalancerProfiles.isEmpty()) {
            String lbNames = String.join(", ", allowedLoadBalancerProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("lb_list", (CloudProfile input) -> cloudCall(progress, budget, "lb_list", input.profile(), allowedLoadBalancerProfiles, () -> {
                        String profile = requireAllowedProfile("负载均衡", input.profile(), allowedLoadBalancerProfiles);
                        log.info("调用 lb_list requested={} resolved={}", input.profile(), profile);
                        return loadBalancer.list(projectId, environment, profile);
                    }))
                    .description("列出当前 Binding 开放的 ALB/CLB 实例。可用 profile：" + lbNames + "。只读。")
                    .inputType(CloudProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("lb_health", (LbHealth input) -> cloudCall(progress, budget, "lb_health", input.instanceId(), allowedLoadBalancerProfiles, () -> {
                        String profile = requireAllowedProfile("负载均衡", input.profile(), allowedLoadBalancerProfiles);
                        log.info("调用 lb_health requested={} resolved={} instance={}", input.profile(), profile, input.instanceId());
                        return loadBalancer.health(projectId, environment, profile, input.instanceId(), input.listenerPort());
                    }))
                    .description("查看负载均衡实例健康检查。必须指定 instanceId。可用 profile：" + lbNames + "。")
                    .inputType(LbHealth.class)
                    .build());
        }
        if (allowedCdnProfiles != null && !allowedCdnProfiles.isEmpty()) {
            String cdnNames = String.join(", ", allowedCdnProfiles);
            callbacks.add(FunctionToolCallback
                    .builder("cdn_list_domains", (CloudProfile input) -> cloudCall(progress, budget, "cdn_list_domains", input.profile(), allowedCdnProfiles, () -> {
                        String profile = requireAllowedProfile("CDN", input.profile(), allowedCdnProfiles);
                        log.info("调用 cdn_list_domains requested={} resolved={}", input.profile(), profile);
                        return cdn.listDomains(projectId, environment, profile);
                    }))
                    .description("列出当前 Binding 开放的 CDN 域名。可用 profile：" + cdnNames + "。只读。")
                    .inputType(CloudProfile.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("cdn_domain_detail", (CdnDomain input) -> cloudCall(progress, budget, "cdn_domain_detail", input.domain(), allowedCdnProfiles, () -> {
                        String profile = requireAllowedProfile("CDN", input.profile(), allowedCdnProfiles);
                        log.info("调用 cdn_domain_detail requested={} resolved={} domain={}", input.profile(), profile, input.domain());
                        return cdn.domainDetail(projectId, environment, profile, input.domain());
                    }))
                    .description("查看 CDN 域名源站与 HTTPS 配置。必须指定 domain。可用 profile：" + cdnNames + "。")
                    .inputType(CdnDomain.class)
                    .build());
            callbacks.add(FunctionToolCallback
                    .builder("cdn_usage", (CdnUsage input) -> cloudCall(progress, budget, "cdn_usage", input.domain(), allowedCdnProfiles, () -> {
                        String profile = requireAllowedProfile("CDN", input.profile(), allowedCdnProfiles);
                        log.info("调用 cdn_usage requested={} resolved={} domain={}", input.profile(), profile, input.domain());
                        return cdn.usage(projectId, environment, profile, input.domain(), input.from(), input.to(), input.field());
                    }))
                    .description("查看 CDN 流量/带宽/QPS。必须指定 domain；时间必须是 ISO-8601 UTC，默认近 1 小时、最长 7 天。field 仅 bps/traf/qps。可用 profile："
                            + cdnNames + "。")
                    .inputType(CdnUsage.class)
                    .build());
        }
        return callbacks;
    }

    private Object cloudCall(
            Consumer<AgentThought> progress,
            ToolBudget budget,
            String name,
            String detail,
            List<String> allowedProfiles,
            java.util.function.Supplier<Object> action
    ) {
        return safeTool(progress, name, detail, () -> {
            budget.consume();
            if (allowedProfiles == null || allowedProfiles.isEmpty()) {
                throw new IllegalStateException("当前环境没有已启用的阿里云排障 Binding");
            }
            return action.get();
        });
    }

    private Object mqCall(
            Consumer<AgentThought> progress,
            ToolBudget budget,
            String name,
            String detail,
            List<String> allowedProfiles,
            java.util.function.Supplier<Object> action
    ) {
        return safeTool(progress, name, detail, () -> {
            budget.consume();
            if (allowedProfiles == null || allowedProfiles.isEmpty()) {
                throw new IllegalStateException("当前环境没有已启用的消息队列 Binding");
            }
            return action.get();
        });
    }

    private Object mseCall(
            Consumer<AgentThought> progress,
            ToolBudget budget,
            String name,
            String detail,
            List<String> allowedMseProfiles,
            java.util.function.Supplier<Object> action
    ) {
        return safeTool(progress, name, detail, () -> {
            budget.consume();
            if (allowedMseProfiles == null || allowedMseProfiles.isEmpty()) {
                throw new IllegalStateException("当前环境没有已启用的 MSE Binding");
            }
            return action.get();
        });
    }

    private Object k8sCall(
            Consumer<AgentThought> progress,
            ToolBudget budget,
            String name,
            String detail,
            List<String> allowedK8sProfiles,
            java.util.function.Supplier<Object> action
    ) {
        return safeTool(progress, name, detail, () -> {
            budget.consume();
            if (allowedK8sProfiles == null || allowedK8sProfiles.isEmpty()) {
                throw new IllegalStateException("当前环境没有已启用的 K8s Binding");
            }
            return action.get();
        });
    }

    private List<ToolCallback> codeToolCallbacks(
            ToolBudget budget,
            List<CodeWorkspace> workspaces,
            Consumer<AgentThought> progress
    ) {
        if (workspaces == null || workspaces.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.add(FunctionToolCallback
                .builder("code_read", (CodeRead input) -> safeTool(progress, "code_read", input.path(), () -> {
                    budget.consume();
                    log.info("调用 code_read workspace={} path={} startLine={}", input.workspace(), input.path(), input.startLine());
                    return code.read(workspaces, input.workspace(), input.path(), input.startLine(), input.maxLines());
                }))
                .description("读取已配置代码仓中的一段源码。单次最多 300 行；大文件必须传 startLine 分段读，不要一次读完整文件。workspace 为端口（admin=管理端，app=客户端，other=其他）；path 可以是仓内相对路径或绝对路径。")
                .inputType(CodeRead.class)
                .build());
        callbacks.add(FunctionToolCallback
                .builder("code_search", (CodeSearch input) -> safeTool(progress, "code_search", input.query(), () -> {
                    budget.consume();
                    log.info("调用 code_search workspace={} query={} path={}", input.workspace(), input.query(), input.path());
                    return code.search(workspaces, input.workspace(), input.query(), input.path(), input.glob());
                }))
                .description("在已配置代码仓中按字面量搜索源码。workspace 为端口（admin=管理端，app=客户端，other=其他）；path/glob 可选，用于缩小范围。")
                .inputType(CodeSearch.class)
                .build());
        return callbacks;
    }

    private List<ToolCallback> skillToolCallbacks(
            ToolBudget budget,
            String projectId,
            Consumer<AgentThought> progress
    ) {
        UUID id = parseProjectId(projectId);
        if (id == null) {
            return new ArrayList<>();
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.add(FunctionToolCallback
                .builder("skill_read", (SkillRead input) -> safeTool(progress, "skill_read", input.path(), () -> {
                    budget.consume();
                    log.info("调用 skill_read slug={} path={}", input.slug(), input.path());
                    return readSkillFile(id, input.slug(), input.path());
                }))
                .description("读取当前项目已启用 Skill 包里的附属文件。slug 为 Skill 标识，path 为包内相对路径，例如 references/playbook.md。不要编造未列出的文件。")
                .inputType(SkillRead.class)
                .build());
        return callbacks;
    }

    private String readSkillFile(UUID projectId, String slug, String path) {
        if (slug == null || slug.isBlank() || path == null || path.isBlank()) {
            return "请提供 Skill 的 slug 和文件 path。";
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.startsWith("/") || normalized.contains("..")) {
            return "非法路径";
        }
        CatalogAsset skill = null;
        for (CatalogAsset asset : controlPlane.assets(projectId)) {
            if (asset.enabled() && asset.kind() == CatalogKind.SKILL && slug.equalsIgnoreCase(asset.slug())) {
                skill = asset;
                break;
            }
        }
        if (skill == null) {
            return "未找到已启用 Skill：" + slug;
        }
        if ("SKILL.md".equalsIgnoreCase(normalized)) {
            return clipSkillFile(skill.content());
        }
        for (CatalogAssetFile file : controlPlane.assetFiles(skill.id())) {
            if (normalized.equalsIgnoreCase(file.path())) {
                return clipSkillFile(file.content());
            }
        }
        return "Skill " + slug + " 没有文件：" + normalized;
    }

    private static String clipSkillFile(String content) {
        if (content == null || content.isBlank()) {
            return "（空文件）";
        }
        return content.length() <= 8_000 ? content : content.substring(0, 8_000) + "\n…";
    }

    private List<CodeWorkspace> codeWorkspaces(String projectId) {
        return resolveWorkspaces(parseProjectId(projectId), null, "", Optional.empty());
    }

    private List<CodeWorkspace> resolveWorkspaces(
            UUID projectUuid,
            UUID repositoryId,
            String question,
            Optional<NamedMcpFocus> namedMcp
    ) {
        if (projectUuid == null) {
            return List.of();
        }
        if (namedMcp.isPresent() && (question == null || MentionParser.extract(question).isEmpty())) {
            return List.of();
        }
        if (repositoryId != null) {
            return code.workspacesForRepository(projectUuid, repositoryId);
        }
        if (question != null && !MentionParser.extract(question).isEmpty()) {
            return code.workspaces(projectUuid);
        }
        List<CodeWorkspace> defaultRepo = code.workspacesForName(projectUuid, repositoryDefaults.getDefaultName());
        return defaultRepo.isEmpty() ? List.of() : defaultRepo;
    }

    private String endpointHint(UUID projectId, String environment, List<String> profiles, String kind) {
        if (profiles == null || profiles.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String profile : profiles) {
            try {
                String summary = switch (kind) {
                    case "mysql" -> bindings.mysqlSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "redis" -> bindings.redisSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "logs" -> bindings.slsSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "mse" -> bindings.mseSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "arms" -> bindings.armsSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "loadbalancer" -> bindings.loadBalancerSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "cdn" -> bindings.cdnSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "k8s" -> bindings.k8sSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "starrocks" -> bindings.starrocksSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "kafka" -> bindings.kafkaSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "rocketmq" -> bindings.rocketmqSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    case "mongodb" -> bindings.mongodbSummary(projectId, environment, profile).orElse("未找到 MCP 配置");
                    default -> "未找到 MCP 配置";
                };
                parts.add(profile + " → " + summary);
            } catch (RuntimeException exception) {
                parts.add(profile + " → 配置无效");
            }
        }
        return String.join("；", parts);
    }

    private static UUID parseProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String requireAllowedProfile(String tool, String profile, List<String> allowedProfiles) {
        return ProfileNames.resolve(profile, allowedProfiles, tool);
    }

    private Object safeTool(Consumer<AgentThought> progress, String name, String detail, java.util.function.Supplier<Object> action) {
        progress.accept(AgentThought.of("tool", ToolThought.start(name, detail)));
        try {
            Object result = action.get();
            progress.accept(AgentThought.of("tool", ToolThought.done(name, result)));
            return result;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            if (isSoftToolFailure(message)) {
                log.info("工具 {} 跳过：{}", name, message);
            } else {
                log.warn("工具 {} 失败：{}", name, message);
            }
            progress.accept(AgentThought.of("tool", ToolThought.failed(name, message)));
            return Map.of("ok", false, "tool", name, "error", message);
        }
    }

    static boolean isSoftToolFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return message.contains("K8s 无权限")
                || message.contains("403")
                || lower.contains("forbidden")
                || lower.contains("cannot list")
                || lower.contains("cannot get");
    }

    private static final Set<String> BLOCKED_EXTERNAL_TOOLS = Set.of(
            "sls_text_to_sql",
            "sls_text_to_spl",
            "sls_sop",
            "cms_natural_language_query"
    );

    // 本次重构：数据类工具名前缀与 ToolKind 的唯一映射，供本地装载与外部 MCP 过滤同源复用
    private static final Map<ToolKind, String> TOOL_NAME_PREFIX = Map.ofEntries(
            Map.entry(ToolKind.MYSQL, "mysql_"),
            Map.entry(ToolKind.REDIS, "redis_"),
            Map.entry(ToolKind.LOGS, "sls_"),
            Map.entry(ToolKind.MSE, "mse_"),
            Map.entry(ToolKind.ARMS, "arms_"),
            Map.entry(ToolKind.LOAD_BALANCER, "lb_"),
            Map.entry(ToolKind.CDN, "cdn_"),
            Map.entry(ToolKind.K8S, "k8s_"),
            Map.entry(ToolKind.STARROCKS, "starrocks_"),
            Map.entry(ToolKind.KAFKA, "kafka_"),
            Map.entry(ToolKind.ROCKETMQ, "rocketmq_"),
            Map.entry(ToolKind.MONGODB, "mongodb_")
    );

    private static List<String> profilesFor(Map<ToolKind, List<String>> profilesByKind, ToolKind kind) {
        List<String> value = profilesByKind == null ? null : profilesByKind.get(kind);
        return value == null ? new ArrayList<>() : value;
    }

    private static boolean isBindingAllowed(
            String toolName,
            Map<ToolKind, List<String>> profilesByKind,
            boolean codeBound
    ) {
        String normalized = toolName.toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTERNAL_TOOLS.contains(normalized)) {
            return false;
        }
        for (Map.Entry<ToolKind, String> entry : TOOL_NAME_PREFIX.entrySet()) {
            if (normalized.contains(entry.getValue())) {
                List<String> profiles = profilesByKind == null ? null : profilesByKind.get(entry.getKey());
                return profiles != null && !profiles.isEmpty();
            }
        }
        if (normalized.contains("code_")) {
            return codeBound;
        }
        return normalized.endsWith("investigator_capabilities");
    }

    private ResolvedModelSettings resolveSettings(String provider, String modelName) {
        if ((provider == null || provider.isBlank()) && (modelName == null || modelName.isBlank())) {
            return modelSettings.current();
        }
        ModelProvider type = provider == null || provider.isBlank()
                ? modelSettings.current().provider()
                : ModelProvider.from(provider);
        return modelSettings.resolve(type, modelName, null, null);
    }

    private static String explainModelFailure(Exception exception) {
        String message = safeMessage(exception);
        if (message.contains("Arrearage") || message.contains("overdue-payment")) {
            return "百炼账号欠费或余额不足，模型接口已拒绝。请到百炼控制台充值后再排查。";
        }
        if (message.contains("url error") || message.contains("InvalidParameter")) {
            return "模型名或接口不匹配。百炼请用 qwen-plus / qwen-max / qwen3-max；其他厂商请核对模型名和 Base URL。";
        }
        if (message.contains("incorrect api key") || message.contains("invalid_api_key") || message.contains("401")) {
            return "模型 API Key 无效或已过期。请在「模型」页或 application-model.yml 更新。";
        }
        if (message.contains("Connection refused") || message.contains("Failed to connect")) {
            return "连不上模型服务。若用 Ollama，请确认本机已启动；其他厂商请检查 Base URL 和网络。";
        }
        return "模型或工具调用失败：" + message;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.replaceAll("(?i)(token|password|secret)=[^\\s,]+", "$1=***");
    }

    public record MysqlQuery(String profile, String sql) {
    }

    public record RedisGet(String profile, String key) {
    }

    public record SlsQuery(String profile, String query, String from, String to, Integer limit, String logstore) {
    }

    public record SlsList(String profile) {
    }

    public record MqProfile(String profile) {
    }

    public record MqTopic(String profile, String topic) {
    }

    public record MqPeek(String profile, String topic, Integer limit) {
    }

    public record MqGroup(String profile, String groupId) {
    }

    public record MongodbProfile(String profile) {
    }

    public record MongodbCollections(String profile, String database) {
    }

    public record MongodbQuery(
            String profile,
            String database,
            String collection,
            Object filter,
            Object projection,
            Object sort,
            Integer limit
    ) {
    }

    public record MseProfile(String profile) {
    }

    public record MseKeyword(String profile, String keyword) {
    }

    public record MseId(String profile, String id) {
    }

    public record MseNacosService(String profile, String keyword, String namespaceId) {
    }

    public record MseNacosInstances(String profile, String serviceName, String namespaceId, String clusterName) {
    }

    public record MseNacosConfig(String profile, String dataId, String group, String namespaceId) {
    }

    public record MseGovernance(String profile, String appName, String ruleType, String namespace) {
    }

    public record K8sProfile(String profile, String cluster) {
    }

    public record K8sNamespace(String profile, String namespace, String cluster) {
    }

    public record K8sPods(String profile, String namespace, String labelSelector, String phase, String cluster) {
    }

    public record K8sPod(String profile, String namespace, String name, String cluster) {
    }

    public record K8sLogs(String profile, String namespace, String name, String container, Integer tail, String cluster) {
    }

    public record CloudProfile(String profile) {
    }

    public record ArmsSearch(
            String profile,
            String application,
            String operation,
            String from,
            String to,
            Integer minDurationMs,
            Integer limit
    ) {
    }

    public record ArmsTrace(String profile, String application, String traceId, String from, String to) {
    }

    public record LbHealth(String profile, String instanceId, Integer listenerPort) {
    }

    public record CdnDomain(String profile, String domain) {
    }

    public record CdnUsage(String profile, String domain, String from, String to, String field) {
    }

    private static String suffix(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 8 ? text : text.substring(text.length() - 8);
    }

    public record SkillRead(String slug, String path) {
    }

    public record CodeRead(String workspace, String path, Integer startLine, Integer maxLines) {
    }

    public record CodeSearch(String workspace, String query, String path, String glob) {
    }

    public record AgentResult(
            boolean modelUsed,
            String content,
            int availableTools,
            int usedToolCalls,
            boolean budgetExhausted,
            int maxToolCalls
    ) {
        static AgentResult completed(
                String content,
                int availableTools,
                int usedToolCalls,
                boolean budgetExhausted,
                int maxToolCalls
        ) {
            return new AgentResult(true, content, availableTools, usedToolCalls, budgetExhausted, maxToolCalls);
        }

        static AgentResult degraded(String content) {
            return new AgentResult(false, content, 0, 0, false, 0);
        }
    }

    static final class ToolBudget {
        private final int max;
        private final AtomicInteger remaining;
        private final AtomicBoolean exhausted = new AtomicBoolean(false);

        ToolBudget(int max) {
            this.max = Math.max(1, max);
            this.remaining = new AtomicInteger(this.max);
        }

        void consume() {
            if (remaining.get() <= 0) {
                exhausted.set(true);
                throw new IllegalStateException(
                        "已达到只读工具调用预算（" + max + " 次）。请基于已有证据总结，等待用户确认是否继续。"
                );
            }
            remaining.decrementAndGet();
        }

        int used() {
            return max - remaining.get();
        }

        boolean exhausted() {
            return exhausted.get();
        }
    }
}
