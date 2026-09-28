package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.aliyun.AliyunCloudClient;
import com.maiya.investigator.mcp.config.CdnEndpoint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyCdnTool {
    private static final int MAX_RESULT_CHARS = 24_000;

    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final AliyunCloudClient client;

    public ReadonlyCdnTool(
            BindingConnectionLookup bindings,
            AuditTrail auditTrail,
            AliyunCloudClient client
    ) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> listDomains(UUID projectId, String environment, String profile) {
        CdnEndpoint endpoint = require(projectId, environment, profile);
        auditTrail.record("cdn.list.domains", profile, Map.of("scopedDomains", endpoint.domains().size()));
        if (endpoint.domains().isEmpty()) {
            return clip(client.cdn("DescribeUserDomains", Map.of(
                    "PageNumber", "1",
                    "PageSize", "20"
            )));
        }
        List<Map<String, Object>> domains = new ArrayList<>();
        for (String domain : endpoint.domains()) {
            domains.add(client.cdn("DescribeCdnDomainDetail", Map.of("DomainName", domain)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("count", domains.size());
        result.put("domains", domains);
        return clip(result);
    }

    public Map<String, Object> domainDetail(
            UUID projectId,
            String environment,
            String profile,
            String domain
    ) {
        CdnEndpoint endpoint = require(projectId, environment, profile);
        String checked = requireDomain(endpoint, domain);
        auditTrail.record("cdn.domain.detail", profile, Map.of("domain", checked));
        return clip(client.cdn("DescribeCdnDomainDetail", Map.of("DomainName", checked)));
    }

    public Map<String, Object> usage(
            UUID projectId,
            String environment,
            String profile,
            String domain,
            String from,
            String to,
            String field
    ) {
        CdnEndpoint endpoint = require(projectId, environment, profile);
        String checked = requireDomain(endpoint, domain);
        Instant end = parseInstant(to, Instant.now());
        Instant start = parseInstant(from, end.minus(1, ChronoUnit.HOURS));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("CDN 查询开始时间必须早于结束时间");
        }
        if (start.isBefore(end.minus(7, ChronoUnit.DAYS))) {
            throw new IllegalArgumentException("CDN 单次查询时间窗不能超过 7 天");
        }
        String safeField = field == null || field.isBlank() ? "bps" : field.trim().toLowerCase();
        if (!List.of("bps", "traf", "qps").contains(safeField)) {
            throw new IllegalArgumentException("CDN field 只支持 bps / traf / qps");
        }
        auditTrail.record("cdn.domain.usage", profile, Map.of(
                "domain", checked,
                "from", start,
                "to", end,
                "field", safeField
        ));
        return clip(client.cdn("DescribeDomainUsageData", Map.of(
                "DomainName", checked,
                "StartTime", start.truncatedTo(ChronoUnit.SECONDS).toString(),
                "EndTime", end.truncatedTo(ChronoUnit.SECONDS).toString(),
                "Field", safeField,
                "DataProtocol", "all"
        )));
    }

    private CdnEndpoint require(UUID projectId, String environment, String profile) {
        if (!client.configured()) {
            throw new IllegalStateException("CDN 工具未配置全局阿里云 RAM 只读 AK");
        }
        return bindings.cdn(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 CDN Binding：" + (profile == null ? "" : profile)
        ));
    }

    private static String requireDomain(CdnEndpoint endpoint, String domain) {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("CDN 域名不能为空");
        }
        String checked = domain.trim().toLowerCase();
        endpoint.requireAllowedDomain(checked);
        return checked;
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("CDN 时间必须是 ISO-8601 UTC，例如 2026-09-16T08:00:00Z");
        }
    }

    private static Map<String, Object> clip(Map<String, Object> result) {
        if (String.valueOf(result).length() <= MAX_RESULT_CHARS) {
            return result;
        }
        Map<String, Object> clipped = new LinkedHashMap<>();
        clipped.put("ok", result.getOrDefault("ok", true));
        clipped.put("truncated", true);
        clipped.put("note", "CDN 结果过长，已截断；请指定域名并缩小时间窗");
        return clipped;
    }
}
