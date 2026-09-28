package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.aliyun.SlsLogClient;
import com.maiya.investigator.mcp.config.SlsEndpoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReadonlySlsTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlySlsTool.class);
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final int MAX_RESULT_CHARS = 24_000;
    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final SlsLogClient client;

    public ReadonlySlsTool(BindingConnectionLookup bindings, AuditTrail auditTrail, SlsLogClient client) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> listLogstores(UUID projectId, String environment, String profile) {
        SlsEndpoint endpoint = require(projectId, environment, profile);
        log.info("SLS list logstores profile={} project={}", profile, endpoint.project());
        auditTrail.record("sls.list", profile == null ? "" : profile, Map.of(
                "project", endpoint.project(),
                "regionId", endpoint.regionId()
        ));
        Map<String, Object> result = client.listLogstores(endpoint);
        if (!endpoint.logstores().isEmpty()) {
            // Binding 配置仅为默认入口提示，不限制可查范围
            result.put("defaultLogstores", endpoint.logstores());
            result.put("note", "Binding 中的 logstore 只是默认入口；本列表返回的 logstore 均可查询");
        }
        return clip(result);
    }

    public Map<String, Object> query(
            UUID projectId,
            String environment,
            String profile,
            String query,
            String from,
            String to,
            Integer limit,
            String logstore
    ) {
        SlsEndpoint endpoint = require(projectId, environment, profile);
        String store = endpoint.resolveLogstore(logstore);
        QueryTimeWindow.Range window = QueryTimeWindow.resolve(from, to);
        int capped = QueryTimeWindow.limit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        List<String> candidates = SlsQuerySyntax.candidates(query);
        if (candidates.isEmpty()) {
            candidates = List.of("");
        }
        log.info(
                "SLS 只读查询 profile={} project={} logstore={} from={} to={} limit={} candidates={}",
                profile,
                endpoint.project(),
                store,
                window.fromEpochSec(),
                window.toEpochSec(),
                capped,
                candidates
        );
        auditTrail.record("sls.query", profile == null ? "" : profile, Map.of(
                "project", endpoint.project(),
                "logstore", store,
                "from", window.fromEpochSec(),
                "to", window.toEpochSec(),
                "query", candidates.get(0),
                "candidates", candidates.size()
        ));

        Attempt hit = search(endpoint, store, window, capped, candidates);
        if (hit.count() > 0) {
            return hit.result();
        }
        // 模型常把年份编成 2024 等训练数据年份；时间窗整体落在近 7 天之前且零命中时，改查默认近 7 天
        boolean explicitWindow = (from != null && !from.isBlank()) || (to != null && !to.isBlank());
        if (explicitWindow && QueryTimeWindow.stale(window)) {
            QueryTimeWindow.Range recent = QueryTimeWindow.recent(java.time.Clock.systemDefaultZone());
            log.warn(
                    "SLS 时间窗过旧且零命中，改用近 7 天 originalFrom={} originalTo={} recentFrom={} recentTo={}",
                    window.fromEpochSec(),
                    window.toEpochSec(),
                    recent.fromEpochSec(),
                    recent.toEpochSec()
            );
            Attempt retry = search(endpoint, store, recent, capped, candidates);
            if (retry.count() > 0) {
                Map<String, Object> result = new LinkedHashMap<>(retry.result());
                result.put("note", "原时间窗 "
                        + window.fromEpochSec() + "–" + window.toEpochSec()
                        + " 已过期且零命中，已自动改用近 7 天。用户未指定历史日期时 from/to 请留空。");
                result.put("originalFrom", window.fromEpochSec());
                result.put("originalTo", window.toEpochSec());
                return result;
            }
            if (retry.result() != null) {
                return annotateEmptyWindow(retry.result(), window);
            }
            if (retry.error() != null) {
                throw retry.error();
            }
        }
        if (hit.result() != null) {
            return hit.result();
        }
        if (hit.error() != null) {
            throw hit.error();
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("ok", true);
        empty.put("project", endpoint.project());
        empty.put("logstore", store);
        empty.put("from", window.fromEpochSec());
        empty.put("to", window.toEpochSec());
        empty.put("count", 0);
        empty.put("logs", new ArrayList<>());
        return withQuery(empty, candidates.get(0));
    }

    /**
     * 超过 24h 的窗口从近到远按天扫。SLS GetLogs 一次扫 7 天经常 Incomplete 空结果。
     */
    private Attempt search(
            SlsEndpoint endpoint,
            String store,
            QueryTimeWindow.Range window,
            int capped,
            List<String> candidates
    ) {
        List<QueryTimeWindow.Range> slices = QueryTimeWindow.slices(window);
        Attempt last = new Attempt(null, 0, null);
        boolean sliced = slices.size() > 1;
        for (QueryTimeWindow.Range slice : slices) {
            if (sliced) {
                log.info("SLS 分段查询 logstore={} from={} to={}", store, slice.fromEpochSec(), slice.toEpochSec());
            }
            Attempt hit = tryCandidates(endpoint, store, slice, capped, candidates);
            if (hit.count() > 0) {
                if (sliced) {
                    Map<String, Object> result = new LinkedHashMap<>(hit.result());
                    result.put("windowFrom", window.fromEpochSec());
                    result.put("windowTo", window.toEpochSec());
                    if (result.get("note") == null) {
                        result.put("note", "已按 24 小时从近到远分段查询，本段命中。");
                    }
                    return new Attempt(result, hit.count(), null);
                }
                return hit;
            }
            last = hit;
        }
        if (last.result() != null && sliced) {
            Map<String, Object> copy = new LinkedHashMap<>(last.result());
            copy.put("from", window.fromEpochSec());
            copy.put("to", window.toEpochSec());
            return new Attempt(copy, 0, last.error());
        }
        return last;
    }

    private Attempt tryCandidates(
            SlsEndpoint endpoint,
            String store,
            QueryTimeWindow.Range window,
            int capped,
            List<String> candidates
    ) {
        Map<String, Object> lastEmpty = null;
        IllegalStateException lastError = null;
        String original = candidates.get(0);
        for (String candidate : candidates) {
            try {
                Map<String, Object> result = withQuery(
                        clip(client.getLogs(endpoint, store, candidate, window.fromEpochSec(), window.toEpochSec(), capped)),
                        candidate
                );
                int count = countOf(result);
                if (count > 0) {
                    if (!candidate.equals(original)) {
                        result = new LinkedHashMap<>(result);
                        result.put("note", "原 query 无命中或语法不适配，已自动改用：" + candidate);
                        result.put("originalQuery", original);
                    }
                    return new Attempt(result, count, null);
                }
                if (lastEmpty == null) {
                    lastEmpty = result;
                }
            } catch (IllegalStateException exception) {
                if (!SlsQuerySyntax.needsPhraseFallback(exception.getMessage())) {
                    throw exception;
                }
                log.warn("SLS 查询候选失败，尝试下一写法 query={} err={}", candidate, exception.getMessage());
                lastError = exception;
            }
        }
        return new Attempt(lastEmpty, 0, lastError);
    }

    private static Map<String, Object> annotateEmptyWindow(Map<String, Object> result, QueryTimeWindow.Range original) {
        Map<String, Object> copy = new LinkedHashMap<>(result);
        copy.put("originalFrom", original.fromEpochSec());
        copy.put("originalTo", original.toEpochSec());
        copy.put("note", "原时间窗已过期且近 7 天仍零命中。请核对 logstore / query；from/to 未指定历史日期时请留空。");
        return copy;
    }

    private record Attempt(Map<String, Object> result, int count, IllegalStateException error) {
    }

    private static int countOf(Map<String, Object> result) {
        Object value = result.get("count");
        if (value instanceof Number number) {
            return number.intValue();
        }
        Object logs = result.get("logs");
        if (logs instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private static Map<String, Object> withQuery(Map<String, Object> result, String query) {
        Map<String, Object> copy = new LinkedHashMap<>(result);
        copy.put("query", query);
        return copy;
    }

    private SlsEndpoint require(UUID projectId, String environment, String profile) {
        if (projectId == null) {
            throw new IllegalStateException("SLS 只使用 MCP 页保存的 LOGS 连接，当前没有项目上下文");
        }
        return bindings.sls(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 MCP 日志：" + (profile == null ? "" : profile)
                        + "。请到 MCP 页保存 SLS Binding 并点「测试连接」。"
        ));
    }

    static Map<String, Object> clip(Map<String, Object> result) {
        Object logs = result.get("logs");
        if (logs instanceof List<?> list && list.size() > MAX_LIMIT) {
            result = new LinkedHashMap<>(result);
            result.put("logs", list.subList(0, MAX_LIMIT));
            result.put("truncated", true);
        }
        String json = String.valueOf(result);
        if (json.length() <= MAX_RESULT_CHARS) {
            return result;
        }
        Map<String, Object> clipped = new LinkedHashMap<>(result);
        if (clipped.get("logs") instanceof List<?> list && !list.isEmpty()) {
            int keep = Math.max(1, list.size() / 2);
            clipped.put("logs", list.subList(0, keep));
        }
        clipped.put("truncated", true);
        clipped.put("note", "结果过长，已截断进 prompt");
        return clipped;
    }
}
