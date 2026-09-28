package com.maiya.investigator.mcp.aliyun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.mcp.config.SlsEndpoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlsLogClient {
    private static final Logger log = LoggerFactory.getLogger(SlsLogClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_INCOMPLETE_ATTEMPTS = 12;
    private static final TypeReference<List<Map<String, Object>>> LOG_LIST = new TypeReference<>() {
    };
    private final SignedHttpTransport transport;
    private final long incompletePauseMs;

    public SlsLogClient() {
        this(SignedHttpTransport.jdk(), 200L);
    }

    public static SlsLogClient testing(SignedHttpTransport transport) {
        return new SlsLogClient(transport, 0L);
    }

    private SlsLogClient(SignedHttpTransport transport, long incompletePauseMs) {
        this.transport = transport;
        this.incompletePauseMs = incompletePauseMs;
    }

    public Map<String, Object> listLogstores(SlsEndpoint endpoint) {
        Map<String, String> queryParams = new TreeMap<>();
        queryParams.put("offset", "0");
        queryParams.put("size", "500");
        SignedHttpTransport.Response response = get(endpoint, "/logstores", queryParams);
        Map<String, Object> parsed = readMap(response.body());
        parsed.put("ok", true);
        parsed.put("project", endpoint.project());
        parsed.put("regionId", endpoint.regionId());
        return parsed;
    }

    public Map<String, Object> getLogs(
            SlsEndpoint endpoint,
            String logstore,
            String query,
            long fromEpochSec,
            long toEpochSec,
            int limit
    ) {
        Map<String, String> queryParams = new TreeMap<>();
        queryParams.put("type", "log");
        queryParams.put("from", String.valueOf(fromEpochSec));
        queryParams.put("to", String.valueOf(toEpochSec));
        queryParams.put("query", query == null ? "" : query);
        queryParams.put("line", String.valueOf(limit));
        queryParams.put("offset", "0");
        queryParams.put("reverse", "true");
        List<Map<String, Object>> logs = new ArrayList<>();
        String progress = "";
        int attempts = 0;
        // 大时间窗 / 多 shard 时首次 GetLogs 常是 Incomplete + 空数组；必须轮询到 Complete
        while (attempts < MAX_INCOMPLETE_ATTEMPTS) {
            attempts++;
            SignedHttpTransport.Response response = get(endpoint, "/logstores/" + logstore, queryParams);
            logs = readList(response.body());
            progress = header(response.headers(), "x-log-progress");
            if (!incomplete(progress)) {
                break;
            }
            log.info(
                    "SLS GetLogs 未完成，继续拉取 logstore={} progress={} count={} attempt={}",
                    logstore,
                    progress,
                    logs.size(),
                    attempts
            );
            pause();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("project", endpoint.project());
        result.put("logstore", logstore);
        result.put("from", fromEpochSec);
        result.put("to", toEpochSec);
        result.put("count", logs.size());
        result.put("progress", progress);
        if (incomplete(progress)) {
            result.put("incomplete", true);
        }
        result.put("logs", logs);
        return result;
    }

    private void pause() {
        if (incompletePauseMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(incompletePauseMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SLS 查询被中断");
        }
    }

    static boolean incomplete(String progress) {
        return progress != null && progress.toLowerCase(Locale.ROOT).contains("incomplete");
    }

    static String header(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }

    private SignedHttpTransport.Response get(SlsEndpoint endpoint, String path, Map<String, String> queryParams) {
        String date = AliyunSigner.rfc822Date(Instant.now());
        String signResource = AliyunSigner.slsCanonicalResource(path, queryParams);
        String encodedQuery = AliyunSigner.canonicalQuery(queryParams);
        String requestPath = encodedQuery.isEmpty() ? path : path + "?" + encodedQuery;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Date", date);
        headers.put("Accept", "application/json");
        headers.put("Host", endpoint.host());
        headers.put("x-log-apiversion", "0.6.0");
        headers.put("x-log-signaturemethod", "hmac-sha1");
        headers.put("x-log-bodyrawsize", "0");
        headers.put("Authorization", AliyunSigner.slsAuthorization(
                "GET",
                signResource,
                date,
                headers,
                endpoint.accessKeyId(),
                endpoint.accessKeySecret()
        ));
        SignedHttpTransport.Response response = transport.execute(
                "GET",
                "https://" + endpoint.host() + requestPath,
                headers,
                ""
        );
        if (!response.ok()) {
            String hint = response.status() == 401
                    ? "签名不匹配（查询条件含冒号/空格时，签名必须用未编码参数；与系统时钟无关）"
                    : ("SLS HTTP " + response.status());
            throw new IllegalStateException(hint + "：" + clip(response.body(), 400));
        }
        return response;
    }

    private static List<Map<String, Object>> readList(String body) {
        if (body == null || body.isBlank() || "null".equals(body.trim())) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node == null || node.isNull()) {
                return new ArrayList<>();
            }
            if (node.isArray()) {
                List<Map<String, Object>> parsed = MAPPER.convertValue(node, LOG_LIST);
                return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
            }
            if (node.isObject()) {
                JsonNode logs = node.get("logs");
                if (logs == null || logs.isNull()) {
                    logs = node.get("data");
                }
                if (logs != null && logs.isArray()) {
                    List<Map<String, Object>> parsed = MAPPER.convertValue(logs, LOG_LIST);
                    return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
                }
            }
            throw new IllegalStateException("SLS 返回无法解析：" + clip(body, 200));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("SLS 返回无法解析：" + clip(body, 200));
        }
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
            throw new IllegalStateException("SLS 返回无法解析：" + clip(body, 200));
        }
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
