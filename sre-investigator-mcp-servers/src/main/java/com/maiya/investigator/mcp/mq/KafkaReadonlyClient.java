package com.maiya.investigator.mcp.mq;

import com.maiya.investigator.mcp.config.KafkaEndpoint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

@Component
public class KafkaReadonlyClient {
    private static final int TIMEOUT_SEC = 8;

    public Map<String, Object> listTopics(KafkaEndpoint endpoint) {
        try (AdminClient admin = AdminClient.create(endpoint.adminProperties())) {
            Set<String> names = new TreeSet<>(admin.listTopics().names().get(TIMEOUT_SEC, TimeUnit.SECONDS));
            names.removeIf(name -> name.startsWith("__"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("count", names.size());
            result.put("topics", names.stream().limit(200).toList());
            return result;
        } catch (Exception exception) {
            throw wrap("列出 Kafka topic 失败", exception);
        }
    }

    public Map<String, Object> describeTopic(KafkaEndpoint endpoint, String topic) {
        String name = requireTopic(topic);
        try (AdminClient admin = AdminClient.create(endpoint.adminProperties())) {
            TopicDescription description = admin.describeTopics(List.of(name))
                    .allTopicNames()
                    .get(TIMEOUT_SEC, TimeUnit.SECONDS)
                    .get(name);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("topic", name);
            result.put("partitions", description.partitions().size());
            result.put("internal", description.isInternal());
            result.put("replicas", description.partitions().isEmpty() ? 0 : description.partitions().get(0).replicas().size());
            return result;
        } catch (Exception exception) {
            throw wrap("查看 Kafka topic 失败", exception);
        }
    }

    public Map<String, Object> peek(KafkaEndpoint endpoint, String topic, Integer limit) {
        String name = requireTopic(topic);
        int capped = limit == null || limit <= 0 ? 10 : Math.min(limit, 20);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(endpoint.consumerProperties(endpoint.consumerGroup()))) {
            List<TopicPartition> partitions = consumer.partitionsFor(name).stream()
                    .map(info -> new TopicPartition(name, info.partition()))
                    .toList();
            if (partitions.isEmpty()) {
                return Map.of("ok", true, "topic", name, "count", 0, "messages", List.of());
            }
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            for (TopicPartition partition : partitions) {
                long end = consumer.position(partition);
                consumer.seek(partition, Math.max(0, end - capped));
            }
            List<Map<String, Object>> messages = new ArrayList<>();
            consumer.poll(Duration.ofSeconds(3)).forEach(record -> {
                if (messages.size() < capped) {
                    messages.add(messageView(record));
                }
            });
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("topic", name);
            result.put("count", messages.size());
            result.put("committed", false);
            result.put("messages", messages);
            return result;
        } catch (RuntimeException exception) {
            throw wrap("偷看 Kafka 消息失败", exception);
        }
    }

    public Map<String, Object> consumerLag(KafkaEndpoint endpoint, String groupId) {
        String group = groupId == null || groupId.isBlank() ? endpoint.consumerGroup() : groupId.trim();
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("请填写消费组");
        }
        try (AdminClient admin = AdminClient.create(endpoint.adminProperties())) {
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(TIMEOUT_SEC, TimeUnit.SECONDS);
            Map<TopicPartition, OffsetSpec> endSpec = committed.keySet().stream()
                    .collect(Collectors.toMap(item -> item, item -> OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends = endSpec.isEmpty()
                    ? Map.of()
                    : admin.listOffsets(endSpec).all().get(TIMEOUT_SEC, TimeUnit.SECONDS);
            List<Map<String, Object>> rows = new ArrayList<>();
            long totalLag = 0;
            for (Map.Entry<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> entry : committed.entrySet()) {
                long offset = entry.getValue() == null ? -1 : entry.getValue().offset();
                long end = ends.containsKey(entry.getKey()) ? ends.get(entry.getKey()).offset() : -1;
                long lag = end >= 0 && offset >= 0 ? Math.max(0, end - offset) : 0;
                totalLag += lag;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("topic", entry.getKey().topic());
                row.put("partition", entry.getKey().partition());
                row.put("committed", offset);
                row.put("end", end);
                row.put("lag", lag);
                rows.add(row);
            }
            Collection<ConsumerGroupListing> groups = admin.listConsumerGroups().all().get(TIMEOUT_SEC, TimeUnit.SECONDS);
            boolean found = groups.stream().anyMatch(item -> group.equals(item.groupId()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("group", group);
            result.put("knownGroup", found);
            result.put("totalLag", totalLag);
            result.put("partitions", rows);
            return result;
        } catch (Exception exception) {
            throw wrap("查看 Kafka 消费积压失败", exception);
        }
    }

    private static Map<String, Object> messageView(ConsumerRecord<String, String> record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("partition", record.partition());
        view.put("offset", record.offset());
        view.put("timestamp", record.timestamp());
        view.put("key", clip(record.key(), 200));
        view.put("value", clip(record.value(), 800));
        return view;
    }

    private static String requireTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("请填写 topic");
        }
        String name = topic.trim();
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("topic 只能包含字母、数字、点、下划线和短横线");
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
