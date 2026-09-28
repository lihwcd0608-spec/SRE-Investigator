package com.maiya.investigator.boot.catalog;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenSourceContentFetcher {
    static final int MAX_BYTES = 80_000;
    private static final Logger log = LoggerFactory.getLogger(OpenSourceContentFetcher.class);
    private final HttpClient http = newPublicClient();

    private static HttpClient newPublicClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL);
        ProxySelector selector = ProxySelector.getDefault();
        if (selector != null) {
            builder.proxy(selector);
        }
        return builder.build();
    }

    public String fetchMarkdown(String rawUrl) {
        List<URI> candidates = candidates(rawUrl);
        Exception last = null;
        for (URI uri : candidates) {
            try {
                return readBody(uri);
            } catch (IllegalArgumentException exception) {
                last = exception;
                if (isHardReject(exception)) {
                    throw exception;
                }
            } catch (Exception exception) {
                last = exception;
                log.warn("拉取开源内容失败 host={}：{}", hostOf(uri), exception.getMessage());
            }
        }
        String detail = last == null || last.getMessage() == null ? "无法下载 SKILL.md" : last.getMessage();
        if (detail.contains("404")) {
            throw new IllegalArgumentException("仓库里没有找到 SKILL.md，可换一条结果或改用地址导入");
        }
        throw new IllegalArgumentException("下载 SKILL.md 失败：" + detail);
    }

    static List<URI> candidates(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("请填写开源地址");
        }
        List<URI> out = new ArrayList<>();
        GithubRef github = parseGithub(rawUrl.trim());
        if (github != null) {
            for (String path : pathVariants(github.path)) {
                out.add(githubApiUri(github.owner, github.repo, github.ref, path));
            }
            for (String path : pathVariants(github.path)) {
                out.add(URI.create("https://cdn.jsdelivr.net/gh/" + github.owner + "/" + github.repo + "@" + github.ref + "/" + path));
            }
            for (String path : pathVariants(github.path)) {
                out.add(URI.create("https://github.com/" + github.owner + "/" + github.repo + "/raw/" + github.ref + "/" + path));
            }
        }
        URI raw = toRawUri(rawUrl);
        out.add(raw);
        URI nested = nextGithubSkillUri(raw);
        if (nested != null) {
            out.add(nested);
        }
        return unique(out);
    }

    static GithubRef parseGithub(String rawUrl) {
        URI uri = URI.create(rawUrl.trim());
        String host = hostOf(uri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.split("/");
        if ("github.com".equals(host) && parts.length >= 6 && ("blob".equals(parts[3]) || "raw".equals(parts[3]))) {
            return new GithubRef(parts[1], parts[2], parts[4], joinPath(parts, 5));
        }
        if ("raw.githubusercontent.com".equals(host) && parts.length >= 5) {
            return new GithubRef(parts[1], parts[2], parts[3], joinPath(parts, 4));
        }
        return null;
    }

    static List<String> pathVariants(String path) {
        List<String> variants = new ArrayList<>();
        String normalized = path == null ? "" : path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.isBlank()) {
            variants.add(normalized);
        }
        if (normalized.endsWith("/SKILL.md") && normalized.contains("/skills/")) {
            variants.add(normalized.replace("/skills/", "/"));
        } else if (normalized.endsWith("SKILL.md") && !normalized.contains("/skills/")) {
            if ("SKILL.md".equals(normalized)) {
                variants.add("skills/SKILL.md");
            } else {
                int slash = normalized.lastIndexOf('/', normalized.length() - "SKILL.md".length() - 1);
                if (slash >= 0) {
                    variants.add(normalized.substring(0, slash) + "/skills" + normalized.substring(slash));
                }
            }
        }
        return uniqueStrings(variants);
    }

    static URI githubApiUri(String owner, String repo, String ref, String path) {
        return URI.create("https://api.github.com/repos/" + owner + "/" + repo
                + "/contents/" + encodePath(path) + "?ref=" + encode(ref));
    }

    private String readBody(URI uri) throws IOException, InterruptedException {
        assertPublicHttps(uri);
        boolean githubApi = "api.github.com".equals(hostOf(uri));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 (compatible; SRE-Investigator/1.0)")
                .GET();
        if (githubApi) {
            request.header("Accept", "application/vnd.github.raw");
        } else {
            request.header("Accept", "text/plain, text/markdown, text/*, */*");
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("HTTP " + response.statusCode());
        }
        String body = response.body() == null ? "" : response.body();
        if (body.length() > MAX_BYTES) {
            throw new IllegalArgumentException("开源内容超过 " + MAX_BYTES + " 字符");
        }
        if (body.isBlank()) {
            throw new IllegalArgumentException("开源地址没有可读内容");
        }
        return body;
    }

    static URI toRawUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("请填写开源地址");
        }
        String trimmed = rawUrl.trim();
        URI uri = URI.create(trimmed);
        String host = hostOf(uri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if ("github.com".equals(host) && path.contains("/blob/")) {
            String[] parts = path.split("/", 6);
            if (parts.length >= 6) {
                String owner = parts[1];
                String repo = parts[2];
                String rest = parts[5];
                String ref = parts[4];
                return URI.create("https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + ref + "/" + rest);
            }
        }
        return uri;
    }

    static URI nextGithubSkillUri(URI uri) {
        String host = hostOf(uri);
        if (!"raw.githubusercontent.com".equals(host) && !"github.com".equals(host)) {
            return null;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.endsWith("/skills/") || !path.endsWith("/SKILL.md")) {
            return null;
        }
        if (path.contains("/skills/") && path.endsWith("/SKILL.md")) {
            return URI.create(uri.toString().replace("/skills/", "/"));
        }
        int skill = path.lastIndexOf('/', path.length() - "/SKILL.md".length() - 1);
        if (skill > 0) {
            String parent = path.substring(0, skill);
            if (!parent.endsWith("/skills")) {
                return URI.create(uri.getScheme() + "://" + uri.getHost() + parent + "/skills" + path.substring(skill));
            }
        }
        return null;
    }

    static void assertPublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("开源地址只允许 https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("开源地址缺少主机名");
        }
        String normalized = host.toLowerCase();
        if ("localhost".equals(normalized) || normalized.endsWith(".local") || normalized.endsWith(".internal")) {
            throw new IllegalArgumentException("不允许拉取内网地址");
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("不允许拉取内网地址");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析开源地址：" + host);
        }
    }

    private static boolean isHardReject(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        return message.contains("只允许 https") || message.contains("内网") || message.contains("缺少主机名");
    }

    private static String hostOf(URI uri) {
        return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
    }

    private static String joinPath(String[] parts, int from) {
        StringBuilder path = new StringBuilder();
        for (int index = from; index < parts.length; index++) {
            if (parts[index] == null || parts[index].isBlank()) {
                continue;
            }
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(parts[index]);
        }
        return path.toString();
    }

    private static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (encoded.length() > 0) {
                encoded.append('/');
            }
            encoded.append(encode(segment));
        }
        return encoded.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static List<URI> unique(List<URI> source) {
        Set<String> seen = new LinkedHashSet<>();
        List<URI> out = new ArrayList<>();
        for (URI uri : source) {
            if (uri != null && seen.add(uri.toString())) {
                out.add(uri);
            }
        }
        return out;
    }

    private static List<String> uniqueStrings(List<String> source) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String item : source) {
            if (item != null && !item.isBlank() && seen.add(item)) {
                out.add(item);
            }
        }
        return out;
    }

    record GithubRef(String owner, String repo, String ref, String path) {
    }
}
