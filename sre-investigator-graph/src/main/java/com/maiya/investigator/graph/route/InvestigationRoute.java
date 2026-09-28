package com.maiya.investigator.graph.route;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.List;

public record InvestigationRoute(
        String id,
        String label,
        String description,
        List<String> keywords,
        List<ToolKind> toolKinds,
        boolean fallback
) {
    public InvestigationRoute {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        toolKinds = copyKinds(toolKinds);
    }

    private static List<ToolKind> copyKinds(List<ToolKind> raw) {
        List<ToolKind> copy = new ArrayList<>();
        if (raw != null) {
            for (ToolKind kind : raw) {
                if (kind != null && !copy.contains(kind)) {
                    copy.add(kind);
                }
            }
        }
        if (copy.isEmpty()) {
            copy.add(ToolKind.CODE);
            copy.add(ToolKind.MYSQL);
        }
        return List.copyOf(copy);
    }
}
