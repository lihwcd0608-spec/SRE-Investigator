package com.maiya.investigator.boot.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpFeishuOAuthClient implements FeishuOAuthClient {
    private static final String AUTHORIZE = "https://open.feishu.cn/open-apis/authen/v1/authorize";
    private static final String TOKEN = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";
    private static final String USER_INFO = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    private final AuthProperties properties;
    private final RestClient rest;
    private final ObjectMapper mapper;

    public HttpFeishuOAuthClient(AuthProperties properties, RestClient.Builder builder, ObjectMapper mapper) {
        this.properties = properties;
        this.rest = builder.build();
        this.mapper = mapper;
    }

    @Override
    public boolean configured() {
        return properties.getFeishu().configured();
    }

    @Override
    public String authorizeUrl(String state) {
        return authorizeUrl(state, properties.getFeishu().getRedirectUri());
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        if (!configured()) {
            throw new IllegalStateException("未配置飞书应用");
        }
        AuthProperties.Feishu feishu = properties.getFeishu();
        return AUTHORIZE
                + "?app_id=" + url(feishu.getAppId())
                + "&redirect_uri=" + url(resolveRedirect(redirectUri))
                + "&state=" + url(state);
    }

    @Override
    public FeishuProfile redeem(String code) {
        return redeem(code, properties.getFeishu().getRedirectUri());
    }

    @Override
    public FeishuProfile redeem(String code, String redirectUri) {
        if (!configured()) {
            throw new IllegalStateException("未配置飞书应用");
        }
        AuthProperties.Feishu feishu = properties.getFeishu();
        JsonNode token = read(rest.post()
                .uri(TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", feishu.getAppId(),
                        "client_secret", feishu.getAppSecret(),
                        "code", code,
                        "redirect_uri", resolveRedirect(redirectUri)
                ))
                .retrieve()
                .body(String.class), "换取飞书令牌失败");
        String accessToken = text(token, "access_token");
        if (accessToken.isBlank()) {
            throw new UnauthorizedException("飞书未返回访问令牌");
        }
        JsonNode user = read(rest.get()
                .uri(USER_INFO)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class), "读取飞书用户失败");
        JsonNode data = user.has("data") ? user.get("data") : user;
        String openId = first(data, "open_id", "openId");
        if (openId.isBlank()) {
            throw new UnauthorizedException("飞书未返回 open_id");
        }
        return new FeishuProfile(
                openId,
                first(data, "name", "en_name"),
                first(data, "email", "enterprise_email")
        );
    }

    private JsonNode read(String body, String error) {
        try {
            JsonNode node = mapper.readTree(body == null ? "{}" : body);
            if (node.has("code") && node.get("code").asInt() != 0) {
                throw new UnauthorizedException(error + "：" + text(node, "msg"));
            }
            return node;
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UnauthorizedException(error);
        }
    }

    private static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private String resolveRedirect(String redirectUri) {
        if (redirectUri != null && !redirectUri.isBlank()) {
            return redirectUri.trim();
        }
        return properties.getFeishu().getRedirectUri();
    }

    private static String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
