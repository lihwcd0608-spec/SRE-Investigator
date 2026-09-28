package com.maiya.investigator.mcp.aliyun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.mcp.config.MseEndpoint;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MseRpcClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VERSION = "2019-05-31";
    private final SignedHttpTransport transport;

    public MseRpcClient() {
        this(SignedHttpTransport.jdk());
    }

    public static MseRpcClient testing(SignedHttpTransport transport) {
        return new MseRpcClient(transport);
    }

    private MseRpcClient(SignedHttpTransport transport) {
        this.transport = transport;
    }

    public Map<String, Object> call(MseEndpoint endpoint, String action, Map<String, String> business) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Format", "JSON");
        params.put("Version", VERSION);
        params.put("AccessKeyId", endpoint.accessKeyId());
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("Timestamp", AliyunSigner.iso8601(Instant.now()));
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Action", action);
        params.put("RegionId", endpoint.regionId());
        if (business != null) {
            business.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    params.put(key, value);
                }
            });
        }
        params.put("Signature", AliyunSigner.rpcSignature("GET", params, endpoint.accessKeySecret()));
        String url = "https://" + endpoint.host() + "/?" + AliyunSigner.canonicalQuery(params);
        SignedHttpTransport.Response response = transport.execute("GET", url, Map.of("Accept", "application/json"), "");
        Map<String, Object> parsed = readMap(response.body());
        if (!response.ok() || Boolean.FALSE.equals(parsed.get("Success"))) {
            Object message = parsed.getOrDefault("Message", parsed.getOrDefault("message", clip(response.body(), 400)));
            throw new IllegalStateException("MSE " + action + " HTTP " + response.status() + "：" + message);
        }
        parsed.put("ok", true);
        parsed.put("action", action);
        return parsed;
    }

    private static Map<String, Object> readMap(String body) {
        if (body == null || body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(body, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception exception) {
            throw new IllegalStateException("MSE 返回无法解析：" + clip(body, 200));
        }
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
