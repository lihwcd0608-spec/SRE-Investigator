package com.maiya.investigator.mcp.k8s;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.mcp.config.KuboardEndpoint;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class KuboardClient {
    static final String AUTH_START = "/sso/auth?access_type=offline&client_id=kuboard-sso"
            + "&redirect_uri=%2Fcallback&response_type=code&scope=openid+profile+email+groups"
            + "&state=%2F&connector_id=default";
    static final long TOKEN_TTL_MS = 20 * 60 * 1000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Function<KuboardEndpoint, SessionHttpTransport> transports;
    private final ConcurrentHashMap<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public KuboardClient() {
        this(endpoint -> SessionHttpTransport.jdk(endpoint.insecureSkipTls()));
    }

    public static KuboardClient testing(SessionHttpTransport transport) {
        return new KuboardClient(ignored -> transport);
    }

    private KuboardClient(Function<KuboardEndpoint, SessionHttpTransport> transports) {
        this.transports = transports;
    }

    public Map<String, Object> getJson(KuboardEndpoint endpoint, String k8sPath) {
        return readMap(getText(endpoint, k8sPath, "application/json"));
    }

    public String getText(KuboardEndpoint endpoint, String k8sPath) {
        return getText(endpoint, k8sPath, "*/*");
    }

    public String login(KuboardEndpoint endpoint) {
        return token(endpoint, true);
    }

    public List<String> listClusterNames(KuboardEndpoint endpoint) {
        Object raw = getKuboardJson(endpoint, "/kuboard-api/kind/Cluster");
        List<String> names = new ArrayList<>();
        for (Map<String, Object> item : itemsOf(raw)) {
            Object metadata = item.get("metadata");
            if (metadata instanceof Map<?, ?> map) {
                Object name = map.get("name");
                if (name != null && !String.valueOf(name).isBlank()) {
                    names.add(String.valueOf(name));
                }
            }
        }
        return names;
    }

    private Object getKuboardJson(KuboardEndpoint endpoint, String path) {
        if (path == null || !path.startsWith("/kuboard-api/")) {
            throw new IllegalArgumentException("只允许访问 /kuboard-api/");
        }
        String url = endpoint.origin() + path;
        SessionHttpTransport.Response response = authorizedGet(endpoint, url, "application/json", false);
        // Kuboard 控制面偶发用 403 表示会话失效，可重登一次
        if (response.status() == 401 || response.status() == 403) {
            response = authorizedGet(endpoint, url, "application/json", true);
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException(httpFailure(response.status(), response.body()));
        }
        return readValue(response.body());
    }

    private String getText(KuboardEndpoint endpoint, String k8sPath, String accept) {
        requireSafeGetPath(k8sPath);
        String url = proxyUrl(endpoint, k8sPath);
        SessionHttpTransport.Response response = authorizedGet(endpoint, url, accept, false);
        // K8s API 的 403 多数是 RBAC Forbidden，重登无意义；仅 401 或非 RBAC 的 403 才重登
        if (response.status() == 401
                || (response.status() == 403 && !isKubernetesForbidden(response.body()))) {
            response = authorizedGet(endpoint, url, accept, true);
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException(httpFailure(response.status(), response.body()));
        }
        return response.body();
    }

    static boolean isKubernetesForbidden(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("\"reason\":\"forbidden\"")
                || lower.contains("is forbidden")
                || lower.contains("cannot list")
                || lower.contains("cannot get")
                || lower.contains("cannot watch");
    }

    static String httpFailure(int status, String body) {
        if (status == 403 && isKubernetesForbidden(body)) {
            return describeForbidden(body);
        }
        return "Kuboard HTTP " + status + "：" + clip(body, 300);
    }

    static String describeForbidden(String body) {
        String user = extractField(body, "User");
        String namespace = extractField(body, "namespace");
        String resource = extractField(body, "resource");
        if (resource.isBlank()) {
            resource = "资源";
        }
        StringBuilder text = new StringBuilder("K8s 无权限：");
        if (!user.isBlank()) {
            text.append("用户 ").append(user).append(' ');
        }
        text.append("不能访问 ").append(resource);
        if (!namespace.isBlank()) {
            text.append("（namespace=").append(namespace).append('）');
        }
        text.append("。请换 Binding 默认或业务命名空间，或给该 Kuboard 用户加对应 namespace 的只读 RoleBinding。");
        return text.toString();
    }

    /** 从 K8s Status.message 里抠 User / resource / namespace 的引号字段（兼容 JSON 转义）。 */
    static String extractField(String body, String label) {
        if (body == null || label == null || label.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(label + "\\s*\\\\?\"([^\"\\\\]+)\\\\?\"")
                .matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private SessionHttpTransport.Response authorizedGet(
            KuboardEndpoint endpoint,
            String url,
            String accept,
            boolean forceLogin
    ) {
        String token = token(endpoint, forceLogin);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", accept);
        headers.put("Cookie", "KuboardToken=" + token);
        return transports.apply(endpoint).exchange("GET", url, headers, "");
    }

    private String token(KuboardEndpoint endpoint, boolean force) {
        String key = endpoint.baseUrl() + "|" + endpoint.username();
        if (!force) {
            CachedToken cached = tokens.get(key);
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                return cached.token;
            }
        }
        String token = performLogin(endpoint);
        tokens.put(key, new CachedToken(token, System.currentTimeMillis() + TOKEN_TTL_MS));
        return token;
    }

    private String performLogin(KuboardEndpoint endpoint) {
        SessionHttpTransport transport = transports.apply(endpoint);
        CookieJar cookies = new CookieJar();
        String origin = endpoint.origin();
        String url = origin + AUTH_START;
        boolean posted = false;
        for (int hop = 0; hop < 12; hop++) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Cookie", cookies.header());
            SessionHttpTransport.Response response = transport.exchange("GET", url, headers, "");
            cookies.add(response);
            String token = firstNonBlank(response.cookie("KuboardToken"), cookies.get("KuboardToken"));
            if (!token.isBlank()) {
                return token;
            }
            if (response.redirect()) {
                url = resolve(origin, response.location());
                continue;
            }
            String req = firstNonBlank(queryParam(url, "req"), queryParam(response.body(), "req"));
            boolean loginPage = url.contains("/sso/auth/default")
                    || response.body().toLowerCase(Locale.ROOT).contains("name=\"password\"")
                    || response.body().toLowerCase(Locale.ROOT).contains("name='password'")
                    || response.body().contains("verifyPassword")
                    || response.body().contains("/login/password");
            if (!posted && loginPage && !req.isBlank()) {
                String postUrl = url.contains("/sso/auth/default")
                        ? url
                        : origin + "/sso/auth/default?req=" + URLEncoder.encode(req, StandardCharsets.UTF_8);
                response = transport.exchange("POST", postUrl, Map.of(
                        "Content-Type", "application/x-www-form-urlencoded",
                        "Cookie", cookies.header()
                ), formBody(endpoint));
                posted = true;
                cookies.add(response);
                token = firstNonBlank(response.cookie("KuboardToken"), cookies.get("KuboardToken"));
                if (!token.isBlank()) {
                    return token;
                }
                if (response.redirect()) {
                    url = resolve(origin, response.location());
                    continue;
                }
                throw new IllegalStateException("Kuboard 登录失败：请检查用户名和密码");
            }
            throw new IllegalStateException("Kuboard 登录未返回 KuboardToken，请核对 baseUrl 与现网 SSO 路径");
        }
        throw new IllegalStateException("Kuboard 登录重定向次数过多");
    }

    static String proxyUrl(KuboardEndpoint endpoint, String k8sPath) {
        String path = k8sPath.startsWith("/") ? k8sPath : "/" + k8sPath;
        return endpoint.origin() + "/k8s-api/"
                + URLEncoder.encode(endpoint.cluster(), StandardCharsets.UTF_8)
                + path;
    }

    static void requireSafeGetPath(String k8sPath) {
        if (k8sPath == null || k8sPath.isBlank()) {
            throw new IllegalArgumentException("K8s 路径不能为空");
        }
        String path = k8sPath.startsWith("/") ? k8sPath : "/" + k8sPath;
        if (path.contains("..") || path.contains("\n") || path.contains("\r")) {
            throw new IllegalArgumentException("非法的 K8s 路径");
        }
        if (!path.startsWith("/api/") && !path.startsWith("/apis/")) {
            throw new IllegalArgumentException("只允许访问 Kubernetes GET /api 或 /apis");
        }
    }

    static String resolve(String base, String location) {
        if (location == null || location.isBlank()) {
            return base;
        }
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        return URI.create(base + "/").resolve(location).toString();
    }

    private static String formBody(KuboardEndpoint endpoint) {
        try {
            String secret = MAPPER.writeValueAsString(Map.of(
                    "password", endpoint.password(),
                    "passcode", ""
            ));
            return "login=" + URLEncoder.encode(endpoint.username(), StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构造 Kuboard 登录表单");
        }
    }

    private static String queryParam(String text, String name) {
        if (text == null) {
            return "";
        }
        String needle = name + "=";
        int at = text.indexOf(needle);
        if (at < 0) {
            return "";
        }
        int start = at + needle.length();
        int end = start;
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (ch == '&' || ch == '"' || ch == '\'' || ch == ' ' || ch == '<' || ch == '>') {
                break;
            }
            end++;
        }
        return text.substring(start, end);
    }

    private static Map<String, Object> readMap(String body) {
        Object parsed = readValue(body);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private static Object readValue(String body) {
        if (body == null || body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(body, Object.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Kuboard 返回无法解析：" + clip(body, 200));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> itemsOf(Object raw) {
        Object items = raw;
        if (raw instanceof Map<?, ?> map) {
            items = map.get("items");
        }
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                mapped.add((Map<String, Object>) map);
            }
        }
        return mapped;
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static final class CachedToken {
        private final String token;
        private final long expiresAt;

        private CachedToken(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    static final class CookieJar {
        private final Map<String, String> values = new LinkedHashMap<>();

        void add(SessionHttpTransport.Response response) {
            if (response == null || response.headers() == null) {
                return;
            }
            response.headers().forEach((key, items) -> {
                if (!"set-cookie".equalsIgnoreCase(key) || items == null) {
                    return;
                }
                for (String item : items) {
                    String first = item.split(";", 2)[0];
                    int eq = first.indexOf('=');
                    if (eq > 0) {
                        values.put(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
                    }
                }
            });
        }

        String get(String name) {
            return values.getOrDefault(name, "");
        }

        String header() {
            if (values.isEmpty()) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            values.forEach((key, value) -> {
                if (!text.isEmpty()) {
                    text.append("; ");
                }
                text.append(key).append('=').append(value);
            });
            return text.toString();
        }
    }
}
