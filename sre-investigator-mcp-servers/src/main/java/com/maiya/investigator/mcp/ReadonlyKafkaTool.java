package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.config.KafkaEndpoint;
import com.maiya.investigator.mcp.mq.KafkaReadonlyClient;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyKafkaTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyKafkaTool.class);
    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final KafkaReadonlyClient client;

    public ReadonlyKafkaTool(BindingConnectionLookup bindings, AuditTrail auditTrail, KafkaReadonlyClient client) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> listTopics(UUID projectId, String environment, String profile) {
        KafkaEndpoint endpoint = require(projectId, environment, profile);
        log.info("Kafka list topics profile={} bootstrap={}", profile, endpoint.bootstrapServers());
        auditTrail.record("kafka.list", profile == null ? "" : profile, Map.of("bootstrap", endpoint.bootstrapServers()));
        return client.listTopics(endpoint);
    }

    public Map<String, Object> describeTopic(UUID projectId, String environment, String profile, String topic) {
        KafkaEndpoint endpoint = require(projectId, environment, profile);
        auditTrail.record("kafka.describe", profile == null ? "" : profile, Map.of("topic", topic == null ? "" : topic));
        return client.describeTopic(endpoint, topic);
    }

    public Map<String, Object> peek(UUID projectId, String environment, String profile, String topic, Integer limit) {
        KafkaEndpoint endpoint = require(projectId, environment, profile);
        log.info("Kafka peek profile={} topic={} limit={}", profile, topic, limit);
        auditTrail.record("kafka.peek", profile == null ? "" : profile, Map.of(
                "topic", topic == null ? "" : topic,
                "limit", limit == null ? 10 : limit
        ));
        return client.peek(endpoint, topic, limit);
    }

    public Map<String, Object> consumerLag(UUID projectId, String environment, String profile, String groupId) {
        KafkaEndpoint endpoint = require(projectId, environment, profile);
        auditTrail.record("kafka.lag", profile == null ? "" : profile, Map.of("group", groupId == null ? "" : groupId));
        return client.consumerLag(endpoint, groupId);
    }

    private KafkaEndpoint require(UUID projectId, String environment, String profile) {
        if (projectId == null) {
            throw new IllegalStateException("Kafka 只读工具必须绑定项目");
        }
        return bindings.kafka(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 Kafka：" + (profile == null ? "" : profile)
                        + "。请到工具页保存 Bootstrap 并测试连接。"
        ));
    }
}
