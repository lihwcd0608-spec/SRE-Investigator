package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.config.RocketMqEndpoint;
import com.maiya.investigator.mcp.mq.RocketMqReadonlyClient;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyRocketMqTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyRocketMqTool.class);
    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final RocketMqReadonlyClient client;

    public ReadonlyRocketMqTool(BindingConnectionLookup bindings, AuditTrail auditTrail, RocketMqReadonlyClient client) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> listTopics(UUID projectId, String environment, String profile) {
        RocketMqEndpoint endpoint = require(projectId, environment, profile);
        log.info("RocketMQ list topics profile={} namesrv={}", profile, endpoint.namesrvAddr());
        auditTrail.record("rocketmq.list", profile == null ? "" : profile, Map.of("namesrv", endpoint.namesrvAddr()));
        return client.listTopics(endpoint);
    }

    public Map<String, Object> peek(UUID projectId, String environment, String profile, String topic, Integer limit) {
        RocketMqEndpoint endpoint = require(projectId, environment, profile);
        log.info("RocketMQ peek profile={} topic={} limit={}", profile, topic, limit);
        auditTrail.record("rocketmq.peek", profile == null ? "" : profile, Map.of(
                "topic", topic == null ? "" : topic,
                "limit", limit == null ? 10 : limit
        ));
        return client.peek(endpoint, topic, limit);
    }

    public Map<String, Object> consumerLag(UUID projectId, String environment, String profile, String groupId) {
        RocketMqEndpoint endpoint = require(projectId, environment, profile);
        auditTrail.record("rocketmq.lag", profile == null ? "" : profile, Map.of("group", groupId == null ? "" : groupId));
        return client.consumerLag(endpoint, groupId);
    }

    private RocketMqEndpoint require(UUID projectId, String environment, String profile) {
        if (projectId == null) {
            throw new IllegalStateException("RocketMQ 只读工具必须绑定项目");
        }
        return bindings.rocketmq(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 RocketMQ：" + (profile == null ? "" : profile)
                        + "。请到工具页保存 NameServer 并测试连接。"
        ));
    }
}
