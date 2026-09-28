package com.maiya.investigator.mcp.config;

import java.util.List;

public record CdnEndpoint(List<String> domains) {
    public CdnEndpoint {
        domains = domains == null ? List.of() : List.copyOf(domains);
    }

    public boolean configured() {
        return true;
    }

    public void requireAllowedDomain(String domain) {
        if (!domains.isEmpty() && (domain == null || domains.stream().noneMatch(domain::equalsIgnoreCase))) {
            throw new IllegalArgumentException("CDN Binding 未开放域名：" + domain);
        }
    }

    public String summary() {
        return domains.isEmpty() ? "全部 CDN 域名" : domains.size() + " 个 CDN 域名";
    }
}
