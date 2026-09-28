package com.maiya.investigator.mcp.aliyun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.mcp.config.AliyunCloudProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliyunCloudClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Set<String>> ALLOWED_ACTIONS = Map.of(
            "ARMS", Set.of("ListTraceApps", "SearchTraces", "GetTrace"),
            "ALB", Set.of("ListLoadBalancers", "GetListenerHealthStatus"),
            "CLB", Set.of("DescribeLoadBalancers", "DescribeHealthStatus"),
            "CDN", Set.of("DescribeUserDomains", "DescribeCdnDomainDetail", "DescribeDomainUsageData")
    );

    private final AliyunCloudProperties properties;
    private final SignedHttpTransport transport;

    @Autowired
    public AliyunCloudClient(AliyunCloudProperties properties) {
        this(properties, SignedHttpTransport.jdk());
    }

    public static AliyunCloudClient testing(
            AliyunCloudProperties properties,
            SignedHttpTransport transport
    ) {
        return new AliyunCloudClient(properties, transport);
    }

    private AliyunCloudClient(AliyunCloudProperties properties, SignedHttpTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    public boolean configured() {
        return properties.configured();
    }

    public Map<String, Object> arms(String regionId, String action, Map<String, String> business) {
        return call("ARMS", "2019-08-08", "arms." + regionId + ".aliyuncs.com", regionId, action, business);
    }

    public Map<String, Object> alb(String regionId, String action, Map<String, String> business) {
        return call("ALB", "2020-06-16", "alb." + regionId + ".aliyuncs.com", regionId, action, business);
    }

    public Map<String, Object> clb(String regionId, String action, Map<String, String> business) {
        return call("CLB", "2014-05-15", "slb.aliyuncs.com", regionId, action, business);
    }

    public Map<String, Object> cdn(String action, Map<String, String> business) {
        return call("CDN", "2018-05-10", "cdn.aliyuncs.com", null, action, business);
    }

    private Map<String, Object> call(
            String service,
            String version,
            String host,
            String regionId,
            String action,
            Map<String, String> business
    ) {
        requireConfigured();
        if (!ALLOWED_ACTIONS.getOrDefault(service, Set.of()).contains(action)) {
            throw new IllegalArgumentException("禁止调用非白名单阿里云 Action：" + service + "/" + action);
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Format", "JSON");
        params.put("Version", version);
        params.put("AccessKeyId", properties.getAccessKeyId());
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("Timestamp", AliyunSigner.iso8601(Instant.now()));
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Action", action);
        if (regionId != null && !regionId.isBlank()) {
            params.put("RegionId", regionId);
        }
        if (business != null) {
            business.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    params.put(key, value);
                }
            });
        }
        params.put("Signature", AliyunSigner.rpcSignature("GET", params, properties.getAccessKeySecret()));
        String url = "https://" + host + "/?" + AliyunSigner.canonicalQuery(params);
        SignedHttpTransport.Response response = transport.execute(
                "GET",
                url,
                Map.of("Accept", "application/json"),
                ""
        );
        Map<String, Object> parsed = readMap(response.body());
        if (!response.ok() || isError(parsed)) {
            Object message = parsed.getOrDefault("Message", clip(response.body(), 400));
            throw new IllegalStateException(service + " " + action + " HTTP " + response.status() + "：" + message);
        }
        parsed.put("ok", true);
        parsed.put("service", service);
        parsed.put("action", action);
        return parsed;
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException(
                    "未配置全局阿里云 RAM 只读 AK：ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET"
            );
        }
    }

    private static boolean isError(Map<String, Object> parsed) {
        Object success = parsed.get("Success");
        if (success instanceof Boolean flag && flag) {
            return false;
        }
        Object code = parsed.get("Code");
        if (code == null) {
            return false;
        }
        String text = String.valueOf(code).trim();
        return !text.isBlank()
                && !"200".equals(text)
                && !"OK".equalsIgnoreCase(text)
                && !"Success".equalsIgnoreCase(text);
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
            throw new IllegalStateException("阿里云返回无法解析：" + clip(body, 200));
        }
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
