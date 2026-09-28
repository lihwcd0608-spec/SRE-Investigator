package com.maiya.investigator.graph.route;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InvestigationRouteCatalog {
    static final List<InvestigationRoute> BUILTIN = List.of(
            new InvestigationRoute(
                    "http-5xx",
                    "接口/可用性",
                    "HTTP 5xx、超时、网关、下游不可用",
                    List.of("5xx", "超时", "网关", "接口报错", "不可用"),
                    List.of(ToolKind.CODE, ToolKind.LOGS, ToolKind.MSE, ToolKind.ARMS, ToolKind.LOAD_BALANCER, ToolKind.MYSQL, ToolKind.K8S),
                    true
            ),
            new InvestigationRoute(
                    "order-payment",
                    "订单/支付",
                    "订单创建、支付回调、幂等、状态不一致",
                    List.of("订单", "支付", "回调", "幂等", "退款"),
                    List.of(ToolKind.CODE, ToolKind.MYSQL, ToolKind.REDIS),
                    false
            )
    );

    private final List<InvestigationRoute> routes;
    private final InvestigationRoute fallback;

    public InvestigationRouteCatalog(InvestigationRouteProperties properties) {
        this.routes = List.copyOf(resolve(properties));
        this.fallback = pickFallback(properties == null ? "http-5xx" : properties.getFallback(), routes);
    }

    public List<InvestigationRoute> all() {
        return routes;
    }

    public InvestigationRoute fallback() {
        return fallback;
    }

    public String fallbackId() {
        return fallback.id();
    }

    public List<RouteDescriptor> descriptors() {
        return routes.stream()
                .map(item -> new RouteDescriptor(item.id(), item.label(), item.description(), item.fallback()))
                .toList();
    }

    public Optional<InvestigationRoute> find(String id) {
        if (id == null || id.isBlank() || isAuto(id)) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return routes.stream().filter(item -> item.id().equals(normalized)).findFirst();
    }

    public static boolean isAuto(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "auto".equals(normalized) || "automatic".equals(normalized) || "自动".equals(value.trim());
    }

    private static List<InvestigationRoute> resolve(InvestigationRouteProperties properties) {
        List<InvestigationRouteProperties.Item> items = properties == null ? List.of() : properties.getItems();
        if (items == null || items.isEmpty()) {
            return BUILTIN;
        }
        String fallbackId = properties.getFallback() == null ? "" : properties.getFallback().trim().toLowerCase(Locale.ROOT);
        Map<String, InvestigationRoute> byId = new LinkedHashMap<>();
        for (InvestigationRouteProperties.Item item : items) {
            if (item == null || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            String id = item.getId().trim().toLowerCase(Locale.ROOT);
            if ("always".equals(id) || byId.containsKey(id)) {
                continue;
            }
            String label = item.getLabel().isBlank() ? id : item.getLabel();
            List<String> keywords = new ArrayList<>();
            if (item.getKeywords() != null) {
                for (String keyword : item.getKeywords()) {
                    if (keyword != null && !keyword.isBlank()) {
                        keywords.add(keyword.trim());
                    }
                }
            }
            byId.put(id, new InvestigationRoute(
                    id,
                    label,
                    item.getDescription(),
                    keywords,
                    parseKinds(item.getToolKinds()),
                    id.equals(fallbackId)
            ));
        }
        return byId.isEmpty() ? BUILTIN : List.copyOf(byId.values());
    }

    private static List<ToolKind> parseKinds(List<String> raw) {
        List<ToolKind> kinds = new ArrayList<>();
        if (raw == null) {
            return kinds;
        }
        for (String item : raw) {
            ToolKind kind = ToolKindGate.parseKind(item);
            if (kind != null && !kinds.contains(kind)) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    private static InvestigationRoute pickFallback(String requested, List<InvestigationRoute> routes) {
        return routes.stream()
                .filter(InvestigationRoute::fallback)
                .findFirst()
                .or(() -> routes.stream()
                        .filter(item -> item.id().equalsIgnoreCase(requested == null ? "" : requested.trim()))
                        .findFirst())
                .orElse(routes.get(0));
    }
}
