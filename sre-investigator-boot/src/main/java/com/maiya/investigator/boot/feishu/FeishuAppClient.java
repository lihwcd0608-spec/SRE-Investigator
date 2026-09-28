package com.maiya.investigator.boot.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.boot.auth.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FeishuAppClient {
    private static final Logger log = LoggerFactory.getLogger(FeishuAppClient.class);
    private static final String TENANT_TOKEN = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String REPLY = "https://open.feishu.cn/open-apis/im/v1/messages/%s/reply";
    private static final String SEND = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";

    private final AuthProperties properties;
    private final RestClient rest;
    private final ObjectMapper mapper;
    private volatile CachedToken token = CachedToken.empty();

    public FeishuAppClient(AuthProperties properties, RestClient.Builder builder, ObjectMapper mapper) {
        this.properties = properties;
        this.rest = builder.build();
        this.mapper = mapper;
    }

    public boolean botConfigured() {
        return properties.getFeishu().botConfigured();
    }

    public void replyText(String messageId, String text) {
        List<String> chunks = FeishuText.split(text);
        log.info("飞书回复拆分 message={} chunks={} chars={}", messageId, chunks.size(), text == null ? 0 : text.length());
        for (int i = 0; i < chunks.size(); i++) {
            postReply(messageId, chunks.get(i), i + 1, chunks.size());
        }
    }

    public void sendTextToChat(String chatId, String text) {
        List<String> chunks = FeishuText.split(text);
        log.info("飞书主动发消息 chat={} chunks={} chars={}", chatId, chunks.size(), text == null ? 0 : text.length());
        for (int i = 0; i < chunks.size(); i++) {
            postSend(chatId, chunks.get(i), i + 1, chunks.size());
        }
    }

    private void postReply(String messageId, String text, int chunk, int total) {
        log.info("飞书调用回复接口 message={} chunk={}/{} content={}",
                messageId, chunk, total, FeishuText.preview(text, 400));
        JsonNode response = post(
                String.format(REPLY, messageId),
                Map.of(
                        "msg_type", "text",
                        "content", FeishuText.textContent(mapper, text)
                ),
                "发送飞书回复失败"
        );
        log.info("飞书回复接口成功 message={} chunk={}/{} feishuCode={} messageIdOut={}",
                messageId,
                chunk,
                total,
                response.path("code").asInt(0),
                text(response.path("data"), "message_id"));
    }

    private void postSend(String chatId, String text, int chunk, int total) {
        log.info("飞书调用发消息接口 chat={} chunk={}/{} content={}",
                chatId, chunk, total, FeishuText.preview(text, 400));
        JsonNode response = post(
                SEND,
                Map.of(
                        "receive_id", chatId,
                        "msg_type", "text",
                        "content", FeishuText.textContent(mapper, text)
                ),
                "发送飞书群消息失败"
        );
        log.info("飞书发消息接口成功 chat={} chunk={}/{} feishuCode={} messageIdOut={}",
                chatId,
                chunk,
                total,
                response.path("code").asInt(0),
                text(response.path("data"), "message_id"));
    }

    private JsonNode post(String uri, Map<String, Object> body, String error) {
        if (!botConfigured()) {
            throw new IllegalStateException("未启用飞书 Bot");
        }
        try {
            String response = rest.post()
                    .uri(uri)
                    .header("Authorization", "Bearer " + tenantAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        String errorBody = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("飞书接口 HTTP 失败 uri={} http={} body={}",
                                request.getURI(),
                                httpResponse.getStatusCode().value(),
                                FeishuText.preview(errorBody, 800));
                        throw new IllegalStateException(error + " HTTP " + httpResponse.getStatusCode().value()
                                + "：" + FeishuText.preview(errorBody, 400));
                    })
                    .body(String.class);
            log.info("飞书接口 HTTP 成功 uri={} response={}", uri, FeishuText.preview(response, 400));
            return ensureOk(response, error);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("飞书接口异常 uri={}：{}", uri, exception.getMessage(), exception);
            throw new IllegalStateException(error + "：" + exception.getMessage(), exception);
        }
    }

    private String tenantAccessToken() {
        CachedToken current = token;
        if (current.valid()) {
            return current.value();
        }
        synchronized (this) {
            current = token;
            if (current.valid()) {
                return current.value();
            }
            AuthProperties.Feishu feishu = properties.getFeishu();
            log.info("飞书正在获取 tenant_access_token appId={}", maskId(feishu.getAppId()));
            String response;
            try {
                response = rest.post()
                        .uri(TENANT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "app_id", feishu.getAppId(),
                                "app_secret", feishu.getAppSecret()
                        ))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                            String errorBody = new String(httpResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            log.error("飞书获取 tenant token HTTP 失败 http={} body={}",
                                    httpResponse.getStatusCode().value(),
                                    FeishuText.preview(errorBody, 800));
                            throw new IllegalStateException("获取飞书 tenant token 失败 HTTP "
                                    + httpResponse.getStatusCode().value() + "：" + FeishuText.preview(errorBody, 400));
                        })
                        .body(String.class);
            } catch (IllegalStateException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error("飞书获取 tenant token 异常：{}", exception.getMessage(), exception);
                throw new IllegalStateException("获取飞书 tenant token 失败：" + exception.getMessage(), exception);
            }
            JsonNode node = ensureOk(response, "获取飞书 tenant token 失败");
            JsonNode data = node.has("tenant_access_token") ? node : node.path("data");
            String value = text(data, "tenant_access_token");
            int expire = data.has("expire") ? data.get("expire").asInt(7200) : 7200;
            if (value == null || value.isBlank()) {
                log.error("飞书 tenant token 响应缺字段 response={}", FeishuText.preview(response, 400));
                throw new IllegalStateException("获取飞书 tenant token 失败：响应缺少 tenant_access_token");
            }
            token = new CachedToken(value, Instant.now().plusSeconds(Math.max(60, expire - 120)));
            log.info("飞书 tenant token 已获取 expire={}s", expire);
            return value;
        }
    }

    private JsonNode ensureOk(String body, String error) {
        try {
            JsonNode node = mapper.readTree(body == null ? "{}" : body);
            if (node.has("code") && node.get("code").asInt() != 0) {
                log.error("飞书业务失败 code={} msg={} body={}",
                        node.get("code").asInt(),
                        text(node, "msg"),
                        FeishuText.preview(body, 800));
                throw new IllegalStateException(error + "：" + text(node, "msg"));
            }
            return node;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("飞书响应不是 JSON body={}：{}", FeishuText.preview(body, 400), exception.getMessage());
            throw new IllegalStateException(error);
        }
    }

    private static String maskId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        if (value.length() <= 8) {
            return value.charAt(0) + "***";
        }
        return value.substring(0, 6) + "***" + value.substring(value.length() - 4);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private record CachedToken(String value, Instant expiresAt) {
        static CachedToken empty() {
            return new CachedToken("", Instant.EPOCH);
        }

        boolean valid() {
            return value != null && !value.isBlank() && expiresAt.isAfter(Instant.now());
        }
    }
}
