package com.maiya.investigator.mcp.mq;

import com.maiya.investigator.mcp.config.RocketMqEndpoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.admin.ConsumeStats;
import org.apache.rocketmq.common.admin.OffsetWrapper;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.body.TopicList;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.stereotype.Component;

@Component
public class RocketMqReadonlyClient {
    public Map<String, Object> listTopics(RocketMqEndpoint endpoint) {
        DefaultMQAdminExt admin = admin(endpoint);
        try {
            admin.start();
            TopicList listed = admin.fetchAllTopicList();
            TreeSet<String> names = new TreeSet<>();
            if (listed != null && listed.getTopicList() != null) {
                for (String name : listed.getTopicList()) {
                    if (name != null && !name.startsWith("%RETRY%") && !name.startsWith("%DLQ%")) {
                        names.add(name);
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("count", names.size());
            result.put("topics", names.stream().limit(200).toList());
            return result;
        } catch (Exception exception) {
            throw wrap("列出 RocketMQ topic 失败", exception);
        } finally {
            admin.shutdown();
        }
    }

    public Map<String, Object> peek(RocketMqEndpoint endpoint, String topic, Integer limit) {
        String name = requireTopic(topic);
        int capped = limit == null || limit <= 0 ? 10 : Math.min(limit, 20);
        DefaultLitePullConsumer consumer = pullConsumer(endpoint);
        DefaultMQAdminExt admin = admin(endpoint);
        try {
            consumer.start();
            admin.start();
            Collection<MessageQueue> queues = consumer.fetchMessageQueues(name);
            if (queues == null || queues.isEmpty()) {
                return Map.of("ok", true, "topic", name, "count", 0, "messages", List.of());
            }
            consumer.assign(queues);
            for (MessageQueue queue : queues) {
                long end = admin.maxOffset(queue);
                long begin = admin.minOffset(queue);
                consumer.seek(queue, Math.max(begin, end - capped));
            }
            List<MessageExt> polled = consumer.poll(3000);
            List<Map<String, Object>> messages = new ArrayList<>();
            if (polled != null) {
                for (MessageExt message : polled) {
                    if (messages.size() >= capped) {
                        break;
                    }
                    messages.add(messageView(message));
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("topic", name);
            result.put("count", messages.size());
            result.put("acked", false);
            result.put("messages", messages);
            return result;
        } catch (Exception exception) {
            throw wrap("偷看 RocketMQ 消息失败", exception);
        } finally {
            consumer.shutdown();
            admin.shutdown();
        }
    }

    public Map<String, Object> consumerLag(RocketMqEndpoint endpoint, String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("请填写消费组");
        }
        String group = groupId.trim();
        DefaultMQAdminExt admin = admin(endpoint);
        try {
            admin.start();
            ConsumeStats stats = admin.examineConsumeStats(group);
            List<Map<String, Object>> rows = new ArrayList<>();
            long totalLag = 0;
            if (stats != null && stats.getOffsetTable() != null) {
                for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                    long broker = entry.getValue().getBrokerOffset();
                    long consumer = entry.getValue().getConsumerOffset();
                    long lag = Math.max(0, broker - consumer);
                    totalLag += lag;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("topic", entry.getKey().getTopic());
                    row.put("queueId", entry.getKey().getQueueId());
                    row.put("brokerOffset", broker);
                    row.put("consumerOffset", consumer);
                    row.put("lag", lag);
                    rows.add(row);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("group", group);
            result.put("totalLag", totalLag);
            result.put("consumeTps", stats == null ? 0 : stats.getConsumeTps());
            result.put("queues", rows);
            return result;
        } catch (Exception exception) {
            throw wrap("查看 RocketMQ 消费积压失败", exception);
        } finally {
            admin.shutdown();
        }
    }

    private DefaultMQAdminExt admin(RocketMqEndpoint endpoint) {
        DefaultMQAdminExt admin = new DefaultMQAdminExt(rpcHook(endpoint));
        admin.setNamesrvAddr(endpoint.namesrvAddr());
        admin.setInstanceName("sre-admin-" + UUID.randomUUID());
        admin.setVipChannelEnabled(false);
        if (endpoint.namespace() != null && !endpoint.namespace().isBlank()) {
            admin.setNamespace(endpoint.namespace());
        }
        return admin;
    }

    private DefaultLitePullConsumer pullConsumer(RocketMqEndpoint endpoint) {
        String namespace = endpoint.namespace() == null || endpoint.namespace().isBlank() ? null : endpoint.namespace();
        DefaultLitePullConsumer consumer = new DefaultLitePullConsumer(
                namespace,
                "sre_investigator_peek_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                rpcHook(endpoint)
        );
        consumer.setNamesrvAddr(endpoint.namesrvAddr());
        consumer.setAutoCommit(false);
        consumer.setVipChannelEnabled(false);
        return consumer;
    }

    private static RPCHook rpcHook(RocketMqEndpoint endpoint) {
        if (!endpoint.aclEnabled()) {
            return null;
        }
        return new AclClientRPCHook(new SessionCredentials(endpoint.accessKey(), endpoint.secretKey() == null ? "" : endpoint.secretKey()));
    }

    private static Map<String, Object> messageView(MessageExt message) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("msgId", message.getMsgId());
        view.put("queueId", message.getQueueId());
        view.put("queueOffset", message.getQueueOffset());
        view.put("bornTimestamp", message.getBornTimestamp());
        view.put("tags", message.getTags());
        view.put("keys", message.getKeys());
        String body = message.getBody() == null ? null : new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        view.put("body", clip(body, 800));
        return view;
    }

    private static String requireTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("请填写 topic");
        }
        String name = topic.trim();
        if (!name.matches("[A-Za-z0-9_%.-]+")) {
            throw new IllegalArgumentException("topic 只能包含字母、数字、点、下划线、短横线和 %");
        }
        return name;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static IllegalStateException wrap(String prefix, Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return new IllegalStateException(prefix + "：" + message, exception);
    }
}
