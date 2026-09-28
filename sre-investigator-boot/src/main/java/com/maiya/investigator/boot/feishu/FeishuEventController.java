package com.maiya.investigator.boot.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feishu")
public class FeishuEventController {
    private static final Logger log = LoggerFactory.getLogger(FeishuEventController.class);
    private final FeishuBotService bot;
    private final ObjectMapper mapper;

    public FeishuEventController(FeishuBotService bot, ObjectMapper mapper) {
        this.bot = bot;
        this.mapper = mapper;
    }

    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> events(@RequestBody JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            log.warn("飞书事件请求体为空");
            return Map.of("ok", true);
        }
        if (payload.hasNonNull("encrypt")) {
            log.warn("飞书事件是密文，当前实现无法处理。请在开放平台关闭事件加密，encrypt-key 留空 payload={}",
                    redactedPreview(payload));
            return Map.of("ok", true);
        }
        if ("url_verification".equals(text(payload, "type"))) {
            String challenge = text(payload, "challenge");
            log.info("飞书 URL 校验 challengeChars={}", challenge.length());
            return Map.of("challenge", challenge);
        }
        JsonNode header = payload.path("header");
        JsonNode event = payload.path("event");
        JsonNode message = event.path("message");
        String eventType = firstNonBlank(text(header, "event_type"), text(event, "type"));
        log.info(
                "飞书事件到达 botEnabled={} schema={} eventId={} appId={} eventType={} tokenInHeader={} "
                        + "chatType={} chatId={} messageId={} messageType={} mentionCount={} mentions={} "
                        + "senderType={} senderOpenId={} createTime={} payload={}",
                bot.enabled(),
                dash(text(payload, "schema")),
                dash(text(header, "event_id")),
                dash(text(header, "app_id")),
                dash(eventType),
                header.hasNonNull("token") || payload.hasNonNull("token"),
                dash(text(message, "chat_type")),
                dash(text(message, "chat_id")),
                dash(text(message, "message_id")),
                dash(text(message, "message_type")),
                mentionCount(message.path("mentions")),
                mentionSummary(message.path("mentions")),
                dash(text(event.path("sender"), "sender_type")),
                dash(text(event.path("sender").path("sender_id"), "open_id")),
                dash(firstNonBlank(text(message, "create_time"), text(header, "create_time"))),
                redactedPreview(payload)
        );
        if (bot.enabled()) {
            try {
                bot.handleEvent(payload);
            } catch (Exception exception) {
                log.error("处理飞书事件失败 eventType={} chatId={} messageId={}：{}",
                        dash(eventType),
                        dash(text(message, "chat_id")),
                        dash(text(message, "message_id")),
                        exception.getMessage(),
                        exception);
            }
        } else {
            log.warn("已收到飞书事件，但 bot-enabled 未打开或缺少 app-id/app-secret，已忽略 eventType={}",
                    dash(eventType));
        }
        return Map.of("ok", true);
    }

    private String redactedPreview(JsonNode payload) {
        try {
            JsonNode copy = mapper.readTree(payload.toString());
            redactSecrets(copy);
            return FeishuText.preview(copy.toString(), 1500);
        } catch (Exception exception) {
            log.warn("飞书事件脱敏失败：{}", exception.getMessage());
            return FeishuText.preview(String.valueOf(payload), 400);
        }
    }

    private static void redactSecrets(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> fields = new ArrayList<>();
            Iterator<String> names = object.fieldNames();
            while (names.hasNext()) {
                fields.add(names.next());
            }
            for (String field : fields) {
                if (secretField(field)) {
                    object.put(field, "***");
                    continue;
                }
                redactSecrets(object.get(field));
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                redactSecrets(child);
            }
        }
    }

    private static boolean secretField(String field) {
        String name = field == null ? "" : field.toLowerCase(Locale.ROOT);
        return name.contains("token")
                || name.contains("secret")
                || name.contains("encrypt")
                || name.contains("password");
    }

    private static int mentionCount(JsonNode mentions) {
        if (mentions == null || !mentions.isArray()) {
            return 0;
        }
        return mentions.size();
    }

    private static String mentionSummary(JsonNode mentions) {
        if (mentions == null || !mentions.isArray() || mentions.isEmpty()) {
            return "无";
        }
        List<String> items = new ArrayList<>();
        for (JsonNode mention : mentions) {
            String key = dash(text(mention, "key"));
            String name = dash(text(mention, "name"));
            String openId = dash(text(mention.path("id"), "open_id"));
            items.add(key + "/" + name + "/" + openId);
        }
        return String.join(", ", items);
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }
}
