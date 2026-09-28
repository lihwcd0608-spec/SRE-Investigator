package com.maiya.investigator.boot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.OpenSourceSkillSearchHit;
import com.maiya.investigator.api.OpenSourceSkillSearchResponse;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenSourceSkillSearchService {
    private static final Logger log = LoggerFactory.getLogger(OpenSourceSkillSearchService.class);
    private static final int PER_SOURCE = 8;
    private static final int MAX_HITS = 24;
    private static final String GITHUB_REPO = "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$";
    private final HttpClient http = newPublicClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "open-source-skill-search");
        thread.setDaemon(true);
        return thread;
    });
    private final ObjectMapper mapper;

    public OpenSourceSkillSearchService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public OpenSourceSkillSearchResponse search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2) {
            throw new IllegalArgumentException("搜索词至少 2 个字符");
        }
        if (query.length() > 60) {
            throw new IllegalArgumentException("搜索词过长");
        }
        String[] registries = { "skills.sh", "clawhub", "github" };
        List<CompletableFuture<SourceOutcome>> tasks = new ArrayList<>();
        tasks.add(supply(registries[0], () -> searchSkillsSh(query)));
        tasks.add(supply(registries[1], () -> searchClawhub(query)));
        tasks.add(supply(registries[2], () -> searchGithub(query)));
        long started = System.nanoTime();
        CompletableFuture<?>[] all = tasks.toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.anyOf(all).get(8, TimeUnit.SECONDS);
            long usedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            long remainMs = Math.min(4000, Math.max(0, 8000 - usedMs));
            if (remainMs > 0) {
                CompletableFuture.allOf(all).get(remainMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("开源 Skill 搜索被中断");
        } catch (Exception exception) {
            log.warn("开源 Skill 部分来源未完成：{}", exception.getMessage());
        }
        Map<String, OpenSourceSkillSearchHit> unique = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            CompletableFuture<SourceOutcome> task = tasks.get(index);
            if (!task.isDone()) {
                task.cancel(true);
                warnings.add(registries[index] + " 超时，已跳过");
                continue;
            }
            try {
                SourceOutcome outcome = task.join();
                if (outcome.warning != null && !outcome.warning.isBlank()) {
                    warnings.add(outcome.warning);
                }
                for (OpenSourceSkillSearchHit hit : outcome.hits) {
                    if (hit.sourceUrl() == null || hit.sourceUrl().isBlank()) {
                        continue;
                    }
                    unique.putIfAbsent(hit.sourceUrl(), hit);
                    if (unique.size() >= MAX_HITS) {
                        break;
                    }
                }
            } catch (Exception exception) {
                log.warn("开源 Skill 搜索汇总失败：{}", exception.getMessage());
            }
        }
        return new OpenSourceSkillSearchResponse(query, new ArrayList<>(unique.values()), warnings);
    }

    private CompletableFuture<SourceOutcome> supply(String registry, SourceSearch search) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new SourceOutcome(search.run(), null);
            } catch (Exception exception) {
                log.warn("开源 Skill 搜索失败 registry={}：{}", registry, exception.getMessage());
                return new SourceOutcome(new ArrayList<OpenSourceSkillSearchHit>(), warningFor(registry, exception));
            }
        }, executor).orTimeout(9, TimeUnit.SECONDS).exceptionally(error -> {
            log.warn("开源 Skill 搜索超时 registry={}", registry);
            return new SourceOutcome(new ArrayList<OpenSourceSkillSearchHit>(), registry + " 超时，已跳过");
        });
    }

    List<OpenSourceSkillSearchHit> parseSkillsSh(String body) throws Exception {
        List<OpenSourceSkillSearchHit> hits = new ArrayList<>();
        JsonNode skills = mapper.readTree(body).path("skills");
        if (!skills.isArray()) {
            return hits;
        }
        for (JsonNode skill : skills) {
            if (hits.size() >= PER_SOURCE) {
                break;
            }
            String source = text(skill, "source");
            String skillId = firstNonBlank(text(skill, "skillId"), text(skill, "name"));
            if (source == null || !source.matches(GITHUB_REPO) || skillId == null || skillId.isBlank()) {
                continue;
            }
            String name = firstNonBlank(text(skill, "name"), skillId);
            hits.add(new OpenSourceSkillSearchHit(
                    firstNonBlank(text(skill, "id"), source + "/" + skillId),
                    name,
                    "skills.sh",
                    "github",
                    source + " / " + skillId,
                    "https://github.com/" + source + "/blob/main/skills/" + skillId + "/SKILL.md",
                    "main",
                    source,
                    skill.path("installs").isNumber() ? skill.path("installs").intValue() : null
            ));
        }
        return hits;
    }

    List<OpenSourceSkillSearchHit> parseClawhub(String body) throws Exception {
        List<OpenSourceSkillSearchHit> hits = new ArrayList<>();
        JsonNode results = mapper.readTree(body).path("results");
        if (!results.isArray()) {
            return hits;
        }
        for (JsonNode item : results) {
            if (hits.size() >= PER_SOURCE) {
                break;
            }
            String slug = text(item, "slug");
            if (slug == null || slug.isBlank()) {
                continue;
            }
            String owner = firstNonBlank(text(item, "ownerHandle"), text(item.path("owner"), "handle"));
            hits.add(new OpenSourceSkillSearchHit(
                    firstNonBlank(text(item, "id"), "clawhub:" + slug),
                    firstNonBlank(text(item, "displayName"), slug),
                    "clawhub",
                    "clawhub",
                    firstNonBlank(text(item, "summary"), ""),
                    "https://clawhub.ai/api/v1/skills/" + slug + "/file?path=SKILL.md",
                    "main",
                    owner == null ? slug : owner + "/" + slug,
                    item.path("downloads").isNumber() ? item.path("downloads").intValue() : null
            ));
        }
        return hits;
    }

    List<OpenSourceSkillSearchHit> parseGithub(String body) throws Exception {
        List<OpenSourceSkillSearchHit> hits = new ArrayList<>();
        JsonNode items = mapper.readTree(body).path("items");
        if (!items.isArray()) {
            return hits;
        }
        for (JsonNode item : items) {
            if (hits.size() >= PER_SOURCE) {
                break;
            }
            String fullName = text(item, "full_name");
            if (fullName == null || !fullName.matches(GITHUB_REPO)) {
                continue;
            }
            String branch = firstNonBlank(text(item, "default_branch"), "main");
            hits.add(new OpenSourceSkillSearchHit(
                    "github:" + fullName,
                    firstNonBlank(text(item, "name"), fullName),
                    "github",
                    "github",
                    firstNonBlank(text(item, "description"), fullName),
                    "https://github.com/" + fullName + "/blob/" + branch + "/SKILL.md",
                    branch,
                    fullName,
                    item.path("stargazers_count").isNumber() ? item.path("stargazers_count").intValue() : null
            ));
        }
        return hits;
    }

    private List<OpenSourceSkillSearchHit> searchSkillsSh(String query) throws Exception {
        return parseSkillsSh(getJson("https://skills.sh/api/search?q=" + encode(query)));
    }

    private List<OpenSourceSkillSearchHit> searchClawhub(String query) throws Exception {
        return parseClawhub(getJson("https://clawhub.ai/api/v1/search?q=" + encode(query) + "&limit=" + PER_SOURCE + "&nonSuspiciousOnly=true"));
    }

    private List<OpenSourceSkillSearchHit> searchGithub(String query) throws Exception {
        return parseGithub(getJson(
                "https://api.github.com/search/repositories?per_page=" + PER_SOURCE
                        + "&q=" + encode(query + " SKILL.md")
        ));
    }

    private String getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (compatible; SRE-Investigator/1.0)")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body() == null ? "{}" : response.body();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static HttpClient newPublicClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL);
        ProxySelector selector = ProxySelector.getDefault();
        if (selector != null) {
            builder.proxy(selector);
        }
        return builder.build();
    }

    static String warningFor(String registry, Exception exception) {
        if (exception instanceof HttpTimeoutException) {
            return registry + " 超时，已跳过";
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("timed out") || message.contains("timeout")) {
            return registry + " 超时，已跳过";
        }
        return registry + " 暂时连不上，已跳过";
    }

    @FunctionalInterface
    private interface SourceSearch {
        List<OpenSourceSkillSearchHit> run() throws Exception;
    }

    private static final class SourceOutcome {
        private final List<OpenSourceSkillSearchHit> hits;
        private final String warning;

        private SourceOutcome(List<OpenSourceSkillSearchHit> hits, String warning) {
            this.hits = hits;
            this.warning = warning;
        }
    }
}
