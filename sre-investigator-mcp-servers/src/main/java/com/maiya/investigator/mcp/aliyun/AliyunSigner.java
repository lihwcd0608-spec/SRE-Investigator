package com.maiya.investigator.mcp.aliyun;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AliyunSigner {
    private static final DateTimeFormatter RFC822 = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ISO8601 = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private AliyunSigner() {
    }

    public static String rfc822Date(Instant instant) {
        return RFC822.format(instant);
    }

    public static String iso8601(Instant instant) {
        return ISO8601.format(instant);
    }

    public static String slsAuthorization(
            String method,
            String canonicalResource,
            String date,
            Map<String, String> headers,
            String accessKeyId,
            String accessKeySecret
    ) {
        String contentMd5 = header(headers, "Content-MD5");
        String contentType = header(headers, "Content-Type");
        StringBuilder canonicalHeaders = new StringBuilder();
        headers.entrySet().stream()
                .filter(entry -> {
                    String key = entry.getKey().toLowerCase(Locale.ROOT);
                    return key.startsWith("x-log-") || key.startsWith("x-acs-");
                })
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> canonicalHeaders
                        .append(entry.getKey().toLowerCase(Locale.ROOT))
                        .append(':')
                        .append(entry.getValue() == null ? "" : entry.getValue())
                        .append('\n'));
        String stringToSign = method.toUpperCase(Locale.ROOT) + "\n"
                + contentMd5 + "\n"
                + contentType + "\n"
                + date + "\n"
                + canonicalHeaders
                + canonicalResource;
        return "LOG " + accessKeyId + ":" + hmacSha1Base64(accessKeySecret, stringToSign);
    }

    public static String rpcSignature(String method, Map<String, String> params, String accessKeySecret) {
        String canonical = params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank() && !"Signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String stringToSign = method.toUpperCase(Locale.ROOT) + "&" + percentEncode("/") + "&" + percentEncode(canonical);
        return hmacSha1Base64(accessKeySecret + "&", stringToSign);
    }

    public static String slsCanonicalResource(String path, Map<String, String> params) {
        String resource = path == null || path.isBlank() ? "/" : path;
        Map<String, String> sorted = slsQueryParams(params);
        if (sorted.isEmpty()) {
            return resource;
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            // SLS HMAC-SHA1 的 CanonicalizedResource 用原始 query 值，不能百分号编码。
            query.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return resource + "?" + query;
    }

    public static String canonicalQuery(Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : slsQueryParams(params).entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(percentEncode(entry.getKey())).append('=').append(percentEncode(entry.getValue()));
        }
        return query.toString();
    }

    private static Map<String, String> slsQueryParams(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>();
        if (params == null || params.isEmpty()) {
            return sorted;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    public static String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    public static String hmacSha1Base64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("计算签名失败", exception);
        }
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }
}
