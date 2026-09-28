package com.maiya.investigator.domain.control;

import java.util.ArrayList;
import java.util.List;

public final class ToolCatalog {
    private ToolCatalog() {
    }

    public static List<String> lines(List<ToolBinding> bindings, List<CatalogAsset> assets) {
        List<String> lines = new ArrayList<>();
        if (bindings != null) {
            for (ToolBinding binding : bindings) {
                if (!binding.enabled()) {
                    continue;
                }
                lines.add(ToolAnnotations.catalogLine(
                        "MCP " + ToolAnnotations.kindLabel(binding.toolKind()),
                        binding.profile(),
                        binding.summary(),
                        binding.tags()
                ));
            }
        }
        if (assets != null) {
            for (CatalogAsset asset : assets) {
                if (!asset.enabled() || asset.kind() == CatalogKind.AGENT || asset.kind() == CatalogKind.MCP) {
                    continue;
                }
                String kind = asset.kind() == CatalogKind.RULE ? "RULE" : "SKILL";
                lines.add(ToolAnnotations.catalogLine(kind, asset.name(), asset.summary(), asset.tags()));
            }
        }
        return lines;
    }
}
