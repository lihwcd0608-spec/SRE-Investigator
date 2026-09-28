package com.maiya.investigator.graph.route;

public record RouteDescriptor(
        String id,
        String label,
        String description,
        boolean fallback
) {
}
