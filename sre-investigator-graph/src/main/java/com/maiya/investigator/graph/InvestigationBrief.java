package com.maiya.investigator.graph;

import com.maiya.investigator.mcp.MentionParser;
import com.maiya.investigator.mcp.NamedMcpFocus;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InvestigationBrief {
    private static final Pattern POSITION_ID = Pattern.compile(
            "(?i)(?:强插位\\s*id|position[_\\s-]?id|位id)\\s*[:：]*\\s*(\\d+)"
    );
    private static final Pattern USER_ID = Pattern.compile(
            "(?i)(?:用户\\s*id|user[_\\s-]?id)\\s*[:：]*\\s*(\\d+)"
    );
    private InvestigationBrief() {
    }

    static String rewrite(
            String question,
            String environment,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            String mysqlEndpoints,
            String redisEndpoints
    ) {
        return rewrite(
                question,
                environment,
                mysqlProfiles,
                redisProfiles,
                mysqlEndpoints,
                redisEndpoints,
                List.of(),
                List.of(),
                "",
                ""
        );
    }

    static String rewrite(
            String question,
            String environment,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            String mysqlEndpoints,
            String redisEndpoints,
            List<String> logsProfiles,
            List<String> mseProfiles,
            String logsEndpoints,
            String mseEndpoints
    ) {
        return rewrite(
                question,
                environment,
                mysqlProfiles,
                redisProfiles,
                mysqlEndpoints,
                redisEndpoints,
                logsProfiles,
                mseProfiles,
                logsEndpoints,
                mseEndpoints,
                List.of(),
                ""
        );
    }

    static String rewrite(
            String question,
            String environment,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            String mysqlEndpoints,
            String redisEndpoints,
            List<String> logsProfiles,
            List<String> mseProfiles,
            String logsEndpoints,
            String mseEndpoints,
            List<String> k8sProfiles,
            String k8sEndpoints
    ) {
        return rewrite(
                question,
                environment,
                mysqlProfiles,
                redisProfiles,
                mysqlEndpoints,
                redisEndpoints,
                logsProfiles,
                mseProfiles,
                logsEndpoints,
                mseEndpoints,
                k8sProfiles,
                k8sEndpoints,
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                ""
        );
    }

    static String rewrite(
            String question,
            String environment,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            String mysqlEndpoints,
            String redisEndpoints,
            List<String> logsProfiles,
            List<String> mseProfiles,
            String logsEndpoints,
            String mseEndpoints,
            List<String> k8sProfiles,
            String k8sEndpoints,
            List<String> starrocksProfiles,
            String starrocksEndpoints,
            List<String> kafkaProfiles,
            String kafkaEndpoints,
            List<String> rocketmqProfiles,
            String rocketmqEndpoints
    ) {
        return rewrite(
                question,
                environment,
                mysqlProfiles,
                redisProfiles,
                mysqlEndpoints,
                redisEndpoints,
                logsProfiles,
                mseProfiles,
                logsEndpoints,
                mseEndpoints,
                k8sProfiles,
                k8sEndpoints,
                starrocksProfiles,
                starrocksEndpoints,
                kafkaProfiles,
                kafkaEndpoints,
                rocketmqProfiles,
                rocketmqEndpoints,
                List.of(),
                ""
        );
    }

    static String rewrite(
            String question,
            String environment,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            String mysqlEndpoints,
            String redisEndpoints,
            List<String> logsProfiles,
            List<String> mseProfiles,
            String logsEndpoints,
            String mseEndpoints,
            List<String> k8sProfiles,
            String k8sEndpoints,
            List<String> starrocksProfiles,
            String starrocksEndpoints,
            List<String> kafkaProfiles,
            String kafkaEndpoints,
            List<String> rocketmqProfiles,
            String rocketmqEndpoints,
            List<String> mongodbProfiles,
            String mongodbEndpoints
    ) {
        return rewrite(
                question,
                environment,
                mysqlProfiles,
                redisProfiles,
                mysqlEndpoints,
                redisEndpoints,
                logsProfiles,
                mseProfiles,
                logsEndpoints,
                mseEndpoints,
                k8sProfiles,
                k8sEndpoints,
                starrocksProfiles,
                starrocksEndpoints,
                kafkaProfiles,
                kafkaEndpoints,
                rocketmqProfiles,
                rocketmqEndpoints,
                mongodbProfiles,
                mongodbEndpoints,
                null
        );
    }

    static String rewrite(
            String question,
            String environment,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            String mysqlEndpoints,
            String redisEndpoints,
            List<String> logsProfiles,
            List<String> mseProfiles,
            String logsEndpoints,
            String mseEndpoints,
            List<String> k8sProfiles,
            String k8sEndpoints,
            List<String> starrocksProfiles,
            String starrocksEndpoints,
            List<String> kafkaProfiles,
            String kafkaEndpoints,
            List<String> rocketmqProfiles,
            String rocketmqEndpoints,
            List<String> mongodbProfiles,
            String mongodbEndpoints,
            NamedMcpFocus namedFocus
    ) {
        return rewrite(
                question,
                environment,
                mysqlProfiles,
                redisProfiles,
                mysqlEndpoints,
                redisEndpoints,
                logsProfiles,
                mseProfiles,
                logsEndpoints,
                mseEndpoints,
                k8sProfiles,
                k8sEndpoints,
                starrocksProfiles,
                starrocksEndpoints,
                kafkaProfiles,
                kafkaEndpoints,
                rocketmqProfiles,
                rocketmqEndpoints,
                mongodbProfiles,
                mongodbEndpoints,
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                "",
                namedFocus
        );
    }

    static String rewrite(
            String question,
            String environment,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            String mysqlEndpoints,
            String redisEndpoints,
            List<String> logsProfiles,
            List<String> mseProfiles,
            String logsEndpoints,
            String mseEndpoints,
            List<String> k8sProfiles,
            String k8sEndpoints,
            List<String> starrocksProfiles,
            String starrocksEndpoints,
            List<String> kafkaProfiles,
            String kafkaEndpoints,
            List<String> rocketmqProfiles,
            String rocketmqEndpoints,
            List<String> mongodbProfiles,
            String mongodbEndpoints,
            List<String> armsProfiles,
            String armsEndpoints,
            List<String> loadBalancerProfiles,
            String loadBalancerEndpoints,
            List<String> cdnProfiles,
            String cdnEndpoints,
            NamedMcpFocus namedFocus
    ) {
        String raw = question == null ? "" : question.trim();
        List<String> mentions = MentionParser.extract(raw);
        String positionId = firstGroup(POSITION_ID, raw);
        String userId = firstGroup(USER_ID, raw);
        String namedMcp = NamedMcpFocus.parseRequestedProfile(raw).orElse(null);

        StringBuilder text = new StringBuilder();
        text.append("## 改写后的排查任务\n");
        text.append("原始问题：").append(raw.isBlank() ? "（空）" : raw).append('\n');
        if (!mentions.isEmpty()) {
            text.append("入口（仅入口，必须继续顺藤摸瓜）：");
            text.append(String.join("、", mentions.stream().map(item -> "@" + item).toList()));
            text.append('\n');
        }
        text.append("环境：").append(environment == null || environment.isBlank() ? "未指定" : environment).append('\n');
        if (positionId != null) {
            text.append("强插位/配置位 id：").append(positionId).append('\n');
        }
        if (userId != null) {
            text.append("用户 id：").append(userId).append('\n');
        }
        appendAvailable(text, "MySQL", mysqlProfiles, mysqlEndpoints);
        appendAvailable(text, "Redis", redisProfiles, redisEndpoints);
        appendAvailable(text, "SLS", logsProfiles, logsEndpoints);
        appendAvailable(text, "MSE", mseProfiles, mseEndpoints);
        appendAvailable(text, "K8s", k8sProfiles, k8sEndpoints);
        appendAvailable(text, "StarRocks", starrocksProfiles, starrocksEndpoints);
        appendAvailable(text, "Kafka", kafkaProfiles, kafkaEndpoints);
        appendAvailable(text, "RocketMQ", rocketmqProfiles, rocketmqEndpoints);
        appendAvailable(text, "MongoDB", mongodbProfiles, mongodbEndpoints);
        appendAvailable(text, "ARMS", armsProfiles, armsEndpoints);
        appendAvailable(text, "负载均衡", loadBalancerProfiles, loadBalancerEndpoints);
        appendAvailable(text, "CDN", cdnProfiles, cdnEndpoints);
        if (namedFocus != null) {
            text.append("【硬约束】用户点名 MCP ")
                    .append(namedFocus.profile())
                    .append("（")
                    .append(namedFocus.kindLabel())
                    .append("）。本轮禁止调用其他 MCP；不要先读代码猜 Redis/MySQL key。\n");
            text.append(focusedWorkOrder(namedFocus));
        } else {
            if (namedMcp != null) {
                text.append("用户点名的 MCP：")
                        .append(namedMcp)
                        .append("（未在当前环境绑定中解析到，请确认 profile 名）。\n");
            }
            text.append(defaultWorkOrder(
                    mentions,
                    mysqlProfiles,
                    redisProfiles,
                    logsProfiles,
                    mseProfiles,
                    k8sProfiles,
                    starrocksProfiles,
                    kafkaProfiles,
                    rocketmqProfiles,
                    mongodbProfiles,
                    armsProfiles,
                    loadBalancerProfiles,
                    cdnProfiles
            ));
        }
        return text.toString();
    }

    private static void appendAvailable(StringBuilder text, String label, List<String> profiles, String endpoints) {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }
        text.append("本轮可用 ").append(label).append(" MCP：").append(String.join(", ", profiles));
        if (notBlank(endpoints)) {
            text.append("（").append(endpoints).append('）');
        }
        text.append('\n');
    }

    private static String defaultWorkOrder(
            List<String> mentions,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles
    ) {
        List<String> steps = new ArrayList<>();
        boolean needCode = !mentions.isEmpty() || present(mysqlProfiles) || present(redisProfiles);
        if (needCode) {
            steps.add("有入口或库/缓存工具时，先读入口及其 Mapper/DAO/XML，确认真实表名、字段和缓存 key。禁止按业务名猜测表名后查库。");
        }
        if (present(mysqlProfiles) || present(redisProfiles)) {
            steps.add("确认表名/key 后再用 mysql_query_readonly / redis_*，profile 必须用上面的 MCP 名。SHOW/DESCRIBE/EXPLAIN 不要加 LIMIT。");
        }
        if (present(logsProfiles)) {
            steps.add("查日志用 sls_query_readonly：query 只写裸 id 或 traceId: <值>，不要把类名 AND 进去。from/to 默认近 7 天，不要编造年份（例如 2024）；用户没给时间就留空，可用 now / -1h / -7d。");
        }
        if (present(mseProfiles)) {
            steps.add("查网关/Nacos 用 mse_*，不得改路由或发配置。");
        }
        if (present(k8sProfiles)) {
            steps.add("查集群用 k8s_*；cluster 不填则用 Binding 默认集群。不得重启、调容、删资源或 exec。");
        }
        if (present(starrocksProfiles)) {
            steps.add("查数仓用 starrocks_query_readonly，规则同 MySQL 只读 SQL。");
        }
        if (present(kafkaProfiles)) {
            steps.add("查 Kafka 用 kafka_*，不得生产或提交位移。");
        }
        if (present(rocketmqProfiles)) {
            steps.add("查 RocketMQ 用 rocketmq_*，不得发送或 ack。");
        }
        if (present(mongodbProfiles)) {
            steps.add("查 Mongo 用 mongodb_query_readonly，filter 用 JSON，不得写库。");
        }
        if (present(armsProfiles)) {
            steps.add("查调用链用 arms_search_traces / arms_get_trace，必须指定应用；不要把完整 trace 贴进结论。");
        }
        if (present(loadBalancerProfiles)) {
            steps.add("查负载均衡用 lb_list / lb_health，健康检查必须指定实例 ID。");
        }
        if (present(cdnProfiles)) {
            steps.add("查 CDN 用 cdn_domain_detail / cdn_usage，必须指定域名；时间用 ISO-8601 UTC。");
        }
        steps.add("只调用本轮已列出的工具。不要为了「有这类 MCP」就先 list 一遍。预算紧时先写结论。");
        StringBuilder order = new StringBuilder("工作顺序：\n");
        for (int index = 0; index < steps.size(); index++) {
            order.append(index + 1).append(". ").append(steps.get(index)).append('\n');
        }
        return order.toString();
    }

    private static boolean present(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private static String focusedWorkOrder(NamedMcpFocus focus) {
        String profile = focus.profile();
        return switch (focus.toolKind()) {
            case MYSQL -> """
                    工作顺序（点名 MCP）：
                    1. 直接用 mysql_query_readonly 查数据，profile 必须是 %s。
                    2. 禁止调用 redis_* / sls_* / mongodb_* 等其他 MCP；不要先读代码猜表名。
                    3. SHOW/DESCRIBE/EXPLAIN 不要加 LIMIT。
                    """.formatted(profile);
            case REDIS -> """
                    工作顺序（点名 MCP）：
                    1. 直接用 redis_get / redis_inspect 查 key，profile 必须是 %s。
                    2. 禁止调用 mysql_* / sls_* / mongodb_* 等其他 MCP。
                    """.formatted(profile);
            case MONGODB -> """
                    工作顺序（点名 MCP）：
                    1. 用户已给出集合名时，直接 mongodb_query_readonly，profile 必须是 %s。不要 list_databases，不要查 StarRocks/MySQL/Redis。
                    2. filter 直接传 JSON 对象，例如 {"_id": 2010975677848408066}（数字，不要加引号）；不要包成字符串。
                    3. 用户画像集合 ums_user_tag_attr 的主键是 _id（Long）。集合名以用户指定为准，不要加 ums_ns_ 前缀。
                    4. 查到 documents 后立刻用 JSON 代码块输出全文，不要写四段排查报告。
                    5. 「继续」= 把上一轮已查到的文档贴出来。
                    """.formatted(profile);
            case LOGS -> """
                    工作顺序（点名 MCP）：
                    1. 先 sls_list_logstores 再 sls_query_readonly，profile 必须是 %s；query 只写裸 id 或 traceId: <值>，不要把类名 AND 进去。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            case MSE -> """
                    工作顺序（点名 MCP）：
                    1. 用 mse_* 查网关/Nacos，profile 必须是 %s。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            case K8S -> """
                    工作顺序（点名 MCP）：
                    1. 先 k8s_list_clusters，再用 k8s_* 查指定 cluster，profile 必须是 %s；cluster 不填则用 Binding 默认集群。不得重启或 exec。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            case STARROCKS -> """
                    工作顺序（点名 MCP）：
                    1. 用 starrocks_query_readonly，profile 必须是 %s。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            case KAFKA -> """
                    工作顺序（点名 MCP）：
                    1. 先 kafka_list_topics，再 kafka_peek_messages / kafka_consumer_lag，profile 必须是 %s。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            case ROCKETMQ -> """
                    工作顺序（点名 MCP）：
                    1. 先 rocketmq_list_topics，再 rocketmq_peek_messages / rocketmq_consumer_lag，profile 必须是 %s。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            case ARMS -> """
                    工作顺序（点名 MCP）：
                    1. 先 arms_list_apps 确认应用，再用 arms_search_traces / arms_get_trace，profile 必须是 %s。
                    2. 禁止调用其他 MCP。不要输出完整调用链。
                    """.formatted(profile);
            case LOAD_BALANCER -> """
                    工作顺序（点名 MCP）：
                    1. 先 lb_list，再 lb_health，profile 必须是 %s。健康检查必须带实例 ID。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            case CDN -> """
                    工作顺序（点名 MCP）：
                    1. 先 cdn_list_domains，再 cdn_domain_detail / cdn_usage，profile 必须是 %s。必须指定域名。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
            default -> """
                    工作顺序（点名 MCP）：
                    1. 只调用 profile %s 对应的只读工具。
                    2. 禁止调用其他 MCP。
                    """.formatted(profile);
        };
    }

    private static String firstGroup(Pattern pattern, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
