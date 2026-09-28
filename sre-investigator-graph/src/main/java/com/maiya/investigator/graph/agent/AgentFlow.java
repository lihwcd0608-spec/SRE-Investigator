package com.maiya.investigator.graph.agent;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子 Agent 上的业务流程图。系统编排仍是 route → load_context → investigate → synthesize；
 * 本图只描述业务步骤。运行时按拓扑序分阶执行，第一期不跑并行和回边。
 */
public record AgentFlow(List<Node> nodes, List<Edge> edges) {
    public AgentFlow {
        nodes = copyNodes(nodes);
        edges = copyEdges(edges);
    }

    public static AgentFlow empty() {
        return new AgentFlow(new ArrayList<>(), new ArrayList<>());
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public List<Node> topologicalOrder() {
        List<Node> ordered = new ArrayList<>();
        if (nodes.isEmpty()) {
            return ordered;
        }
        Map<String, Node> byId = new LinkedHashMap<>();
        for (Node node : nodes) {
            byId.putIfAbsent(node.id(), node);
        }
        if (edges.isEmpty()) {
            ordered.addAll(byId.values());
            return ordered;
        }
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (String id : byId.keySet()) {
            indegree.put(id, 0);
            outgoing.put(id, new ArrayList<>());
        }
        for (Edge edge : edges) {
            if (!byId.containsKey(edge.from()) || !byId.containsKey(edge.to())) {
                continue;
            }
            if (edge.from().equals(edge.to())) {
                throw new IllegalArgumentException("业务流程图存在环，无法按步骤执行");
            }
            List<String> next = outgoing.get(edge.from());
            if (next.contains(edge.to())) {
                continue;
            }
            next.add(edge.to());
            indegree.put(edge.to(), indegree.get(edge.to()) + 1);
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (String id : byId.keySet()) {
            if (indegree.get(id) == 0) {
                ready.add(id);
            }
        }
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            ordered.add(byId.get(id));
            for (String next : outgoing.get(id)) {
                int left = indegree.get(next) - 1;
                indegree.put(next, left);
                if (left == 0) {
                    ready.add(next);
                }
            }
        }
        if (ordered.size() != byId.size()) {
            throw new IllegalArgumentException("业务流程图存在环，无法按步骤执行");
        }
        return ordered;
    }

    public boolean cyclic() {
        try {
            topologicalOrder();
            return false;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private static List<Node> copyNodes(List<Node> raw) {
        List<Node> copy = new ArrayList<>();
        if (raw == null) {
            return copy;
        }
        for (Node item : raw) {
            if (item != null && !item.id().isBlank()) {
                copy.add(item);
            }
        }
        return copy;
    }

    private static List<Edge> copyEdges(List<Edge> raw) {
        List<Edge> copy = new ArrayList<>();
        if (raw == null) {
            return copy;
        }
        for (Edge item : raw) {
            if (item != null && !item.from().isBlank() && !item.to().isBlank()) {
                copy.add(item);
            }
        }
        return copy;
    }

    public record Node(
            String id,
            String title,
            String instruction,
            List<ToolKind> toolKinds,
            List<String> skillSlugs,
            List<Mcp> mcps,
            double x,
            double y
    ) {
        public Node {
            id = id == null ? "" : id.trim();
            title = title == null ? "" : title.trim();
            instruction = instruction == null ? "" : instruction.trim();
            toolKinds = copyKinds(toolKinds);
            skillSlugs = copyStrings(skillSlugs);
            mcps = copyMcps(mcps);
        }

        public boolean usesKind(ToolKind kind) {
            if (kind == null) {
                return false;
            }
            for (ToolKind item : toolKinds) {
                if (item == kind) {
                    return true;
                }
            }
            for (Mcp mcp : mcps) {
                if (mcp.toolKind() == kind) {
                    return true;
                }
            }
            return false;
        }

        public List<String> profilesForKind(ToolKind kind) {
            List<String> profiles = new ArrayList<>();
            for (Mcp mcp : mcps) {
                if (mcp.toolKind() == kind && !profiles.contains(mcp.profile())) {
                    profiles.add(mcp.profile());
                }
            }
            return profiles;
        }

        public String singleProfileOrBlank() {
            if (mcps.size() != 1) {
                return "";
            }
            return mcps.get(0).profile();
        }

        public String pinnedProfileOrBlank() {
            if (mcps.size() != 1) {
                return "";
            }
            ToolKind only = mcps.get(0).toolKind();
            for (ToolKind kind : ToolKind.values()) {
                if (kind != only && usesKind(kind)) {
                    return "";
                }
            }
            return mcps.get(0).profile();
        }

        private static List<ToolKind> copyKinds(List<ToolKind> raw) {
            List<ToolKind> copy = new ArrayList<>();
            if (raw == null) {
                return copy;
            }
            for (ToolKind item : raw) {
                if (item != null && !copy.contains(item)) {
                    copy.add(item);
                }
            }
            return copy;
        }

        private static List<String> copyStrings(List<String> raw) {
            List<String> copy = new ArrayList<>();
            if (raw == null) {
                return copy;
            }
            for (String item : raw) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                String value = item.trim();
                if (!copy.contains(value)) {
                    copy.add(value);
                }
            }
            return copy;
        }

        private static List<Mcp> copyMcps(List<Mcp> raw) {
            List<Mcp> copy = new ArrayList<>();
            if (raw == null) {
                return copy;
            }
            for (Mcp item : raw) {
                if (item != null && !item.profile().isBlank() && !copy.contains(item)) {
                    copy.add(item);
                }
            }
            return copy;
        }
    }

    public record Edge(String from, String to) {
        public Edge {
            from = from == null ? "" : from.trim();
            to = to == null ? "" : to.trim();
        }
    }

    public record Mcp(ToolKind toolKind, String profile) {
        public Mcp {
            profile = profile == null ? "" : profile.trim();
        }
    }
}
