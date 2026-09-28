package com.maiya.investigator.graph.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogTemplate;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.OpenSourceCatalog;
import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AgentCatalog {
    private AgentCatalog() {
    }

    public static AgentDefinition resolve(ControlPlaneStore store, UUID projectId, String slug) {
        String wanted = slug == null ? "" : slug.trim();
        if (wanted.isBlank()) {
            return null;
        }
        if (store != null && projectId != null) {
            for (CatalogAsset asset : store.assets(projectId)) {
                if (asset.kind() != CatalogKind.AGENT) {
                    continue;
                }
                if (wanted.equalsIgnoreCase(asset.slug()) || wanted.equalsIgnoreCase(asset.catalogId())) {
                    return fromAsset(asset);
                }
            }
        }
        for (CatalogTemplate template : OpenSourceCatalog.all()) {
            if (template.kind() != CatalogKind.AGENT) {
                continue;
            }
            if (wanted.equalsIgnoreCase(template.route()) || wanted.equalsIgnoreCase(template.id())) {
                return fromTemplate(template);
            }
        }
        return null;
    }

    public static AgentDefinition parse(String slug, String name, String content) {
        Spec spec = parseSpec(content);
        return new AgentDefinition(slug, name, spec.systemPrompt, spec.kinds, spec.skillSlugs, spec.starters, spec.flow);
    }

    private static AgentDefinition fromAsset(CatalogAsset asset) {
        return parse(asset.slug(), asset.name(), asset.content());
    }

    private static AgentDefinition fromTemplate(CatalogTemplate template) {
        String slug = template.route() == null || template.route().isBlank() ? template.id() : template.route();
        return parse(slug, template.name(), template.content());
    }

    private static Spec parseSpec(String content) {
        String raw = content == null ? "" : content.trim();
        if (raw.isBlank()) {
            return new Spec("", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), AgentFlow.empty());
        }
        try {
            JsonElement root = JsonParser.parseString(raw);
            if (!root.isJsonObject()) {
                return new Spec(raw, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), AgentFlow.empty());
            }
            JsonObject object = root.getAsJsonObject();
            String prompt = text(object, "systemPrompt");
            if (prompt.isBlank()) {
                prompt = raw;
            }
            return new Spec(
                    prompt,
                    toolKinds(object.get("allowedToolKinds")),
                    strings(object.get("skillSlugs")),
                    strings(object.get("starters")),
                    flow(object.get("flow"))
            );
        } catch (RuntimeException ignored) {
            return new Spec(raw, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), AgentFlow.empty());
        }
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        JsonElement value = object.get(key);
        return value.isJsonPrimitive() ? value.getAsString().trim() : "";
    }

    private static List<ToolKind> toolKinds(JsonElement element) {
        List<ToolKind> kinds = new ArrayList<>();
        for (String raw : strings(element)) {
            try {
                ToolKind kind = ToolKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (!kinds.contains(kind)) {
                    kinds.add(kind);
                }
            } catch (RuntimeException ignored) {
                // 忽略未知工具类型
            }
        }
        return kinds;
    }

    private static List<String> strings(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return values;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive()) {
                continue;
            }
            String value = item.getAsString().trim();
            if (!value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static AgentFlow flow(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return AgentFlow.empty();
        }
        JsonObject object = element.getAsJsonObject();
        List<AgentFlow.Node> nodes = new ArrayList<>();
        if (object.has("nodes") && object.get("nodes").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("nodes");
            for (int i = 0; i < array.size(); i++) {
                JsonElement item = array.get(i);
                if (item == null || !item.isJsonObject()) {
                    continue;
                }
                JsonObject node = item.getAsJsonObject();
                nodes.add(new AgentFlow.Node(
                        text(node, "id"),
                        text(node, "title"),
                        text(node, "instruction"),
                        toolKinds(node.get("toolKinds")),
                        strings(node.get("skillSlugs")),
                        mcps(node.get("mcps")),
                        number(node, "x"),
                        number(node, "y")
                ));
            }
        }
        List<AgentFlow.Edge> edges = new ArrayList<>();
        if (object.has("edges") && object.get("edges").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("edges");
            for (int i = 0; i < array.size(); i++) {
                JsonElement item = array.get(i);
                if (item == null || !item.isJsonObject()) {
                    continue;
                }
                JsonObject edge = item.getAsJsonObject();
                edges.add(new AgentFlow.Edge(text(edge, "from"), text(edge, "to")));
            }
        }
        return new AgentFlow(nodes, edges);
    }

    private static List<AgentFlow.Mcp> mcps(JsonElement element) {
        List<AgentFlow.Mcp> values = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return values;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonObject()) {
                continue;
            }
            JsonObject object = item.getAsJsonObject();
            ToolKind kind = parseKind(text(object, "toolKind"));
            if (kind == null) {
                continue;
            }
            values.add(new AgentFlow.Mcp(kind, text(object, "profile")));
        }
        return values;
    }

    private static ToolKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ToolKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static double number(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return 0;
        }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return value.getAsDouble();
    }

    private record Spec(
            String systemPrompt,
            List<ToolKind> kinds,
            List<String> skillSlugs,
            List<String> starters,
            AgentFlow flow
    ) {
    }
}
