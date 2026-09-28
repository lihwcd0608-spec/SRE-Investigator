package com.maiya.investigator.graph.route;

public record RouteDecision(InvestigationRoute route, RouteSource source) {
    public String id() {
        return route.id();
    }

    public String label() {
        return route.label();
    }

    public String thought() {
        return switch (source) {
            case USER -> "已按所选方向：" + label();
            case MODEL -> "模型判断方向：" + label();
            case KEYWORD -> "已按关键词判断方向：" + label();
            case DEFAULT -> "未匹配到方向，使用默认：" + label();
        };
    }
}
