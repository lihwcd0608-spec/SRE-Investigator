package com.maiya.investigator.boot.automation;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.mcp.BindingConnectionLookup;
import com.maiya.investigator.mcp.ReadonlyK8sTool;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class K8sInspectionScanner {
    public static final String DEFAULT_PROMPT = "定时只读巡检：NotReady 节点、CrashLoop/OOM、重启次数、Warning 事件。";
    static final int MAX_NAMESPACES = 12;
    static final int MAX_CANDIDATES = 40;
    static final int RESTART_THRESHOLD = 5;
    static final List<String> POD_FAILURES = List.of(
            "CrashLoopBackOff",
            "OOMKilled",
            "ImagePullBackOff",
            "ErrImagePull"
    );
    private static final Set<String> DEFERRED_NS = Set.of(
            "kube-system",
            "kube-public",
            "kube-node-lease",
            "ack-csi-fuse",
            "arms-prom",
            "istio-system",
            "monitoring"
    );
    private static final Logger log = LoggerFactory.getLogger(K8sInspectionScanner.class);

    private final ControlPlaneStore controlPlane;
    private final ReadonlyK8sTool k8s;
    private final BindingConnectionLookup lookup;

    public K8sInspectionScanner(ControlPlaneStore controlPlane, ReadonlyK8sTool k8s) {
        this(controlPlane, k8s, null);
    }

    @Autowired
    public K8sInspectionScanner(
            ControlPlaneStore controlPlane,
            ReadonlyK8sTool k8s,
            BindingConnectionLookup lookup
    ) {
        this.controlPlane = controlPlane;
        this.k8s = k8s;
        this.lookup = lookup;
    }

    public Report scan(UUID projectId, Environment environment) {
        List<ToolBinding> bindings = controlPlane.bindings(projectId).stream()
                .filter(ToolBinding::enabled)
                .filter(item -> item.toolKind() == ToolKind.K8S)
                .filter(item -> item.environment() == environment)
                .toList();
        if (bindings.isEmpty()) {
            throw new IllegalStateException("当前环境没有已启用的 K8s Binding，请先在工具页保存 Kuboard 并测试连接。");
        }
        List<String> sections = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int nodes = 0;
        int namespaces = 0;
        for (ToolBinding binding : bindings) {
            ClusterScan cluster = scanBinding(projectId, environment, binding);
            nodes += cluster.nodes();
            namespaces += cluster.namespaces();
            findings.addAll(cluster.findings());
            skipped.addAll(cluster.skipped());
            sections.add(cluster.section());
        }
        return render(environment, bindings.size(), nodes, namespaces, findings, sections, skipped);
    }

    private ClusterScan scanBinding(UUID projectId, Environment environment, ToolBinding binding) {
        String profile = binding.profile();
        String env = environment.code();
        String cluster = profile;
        List<Finding> findings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int nodeCount = 0;
        int scanned = 0;
        try {
            Map<String, Object> nodeResult = k8s.listNodes(projectId, env, profile);
            cluster = text(nodeResult.get("cluster"), profile);
            List<Map<String, Object>> nodes = maps(nodeResult.get("nodes"));
            nodeCount = nodes.size();
            findings.addAll(inspectNodes(nodes));
        } catch (RuntimeException exception) {
            if (isForbidden(exception)) {
                skipped.add("nodes");
            } else {
                findings.add(new Finding("error", "节点列表失败", exception.getMessage()));
            }
            log.warn("巡检列节点失败 profile={}: {}", profile, exception.getMessage());
        }
        List<String> namespaces = listNamespaces(projectId, env, profile);
        if (namespaces.isEmpty()) {
            try {
                Map<String, Object> podResult = k8s.listPods(projectId, env, profile, null, null, null);
                findings.addAll(inspectPods("(default)", maps(podResult.get("pods"))));
                scanned = 1;
            } catch (RuntimeException exception) {
                if (isForbidden(exception)) {
                    skipped.add(preferredNamespace(projectId, env, profile));
                } else {
                    findings.add(new Finding("error", "无法列出命名空间或默认 Pod", exception.getMessage()));
                }
            }
        }
        for (String namespace : namespaces) {
            if (scanned >= MAX_NAMESPACES) {
                break;
            }
            boolean readable = scanNamespace(projectId, env, profile, namespace, findings, skipped);
            if (readable) {
                scanned++;
            }
        }
        String section = "## " + cluster + "（" + profile + "）\n"
                + "节点 " + nodeCount + "，已扫命名空间 " + scanned + "。";
        return new ClusterScan(cluster, nodeCount, scanned, findings, skipped, section);
    }

    private boolean scanNamespace(
            UUID projectId,
            String env,
            String profile,
            String namespace,
            List<Finding> findings,
            List<String> skipped
    ) {
        try {
            Map<String, Object> podResult = k8s.listPods(projectId, env, profile, namespace, null, null);
            findings.addAll(inspectPods(namespace, maps(podResult.get("pods"))));
        } catch (RuntimeException exception) {
            if (isForbidden(exception)) {
                skipped.add(namespace);
                log.info("巡检跳过无权限命名空间 {}：{}", namespace, clip(exception.getMessage(), 120));
                return false;
            }
            findings.add(new Finding("error", "Pod 列表失败 · " + namespace, exception.getMessage()));
            return true;
        }
        try {
            Map<String, Object> eventResult = k8s.listEvents(projectId, env, profile, namespace);
            findings.addAll(inspectEvents(namespace, maps(eventResult.get("events"))));
        } catch (RuntimeException exception) {
            if (isForbidden(exception)) {
                log.info("巡检跳过无权限事件 {}：{}", namespace, clip(exception.getMessage(), 120));
            } else {
                findings.add(new Finding("error", "事件列表失败 · " + namespace, exception.getMessage()));
            }
        }
        return true;
    }

    private List<String> listNamespaces(UUID projectId, String environment, String profile) {
        try {
            Map<String, Object> result = k8s.listNamespaces(projectId, environment, profile);
            List<String> names = new ArrayList<>();
            for (Map<String, Object> item : maps(result.get("namespaces"))) {
                String name = text(item.get("name"), "");
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            return capNamespaces(names, preferredNamespace(projectId, environment, profile));
        } catch (RuntimeException exception) {
            log.warn("巡检列命名空间失败 profile={}，尝试 Binding 默认命名空间：{}", profile, exception.getMessage());
            String preferred = preferredNamespace(projectId, environment, profile);
            return preferred.isBlank() ? List.of() : List.of(preferred);
        }
    }

    private String preferredNamespace(UUID projectId, String environment, String profile) {
        if (lookup == null) {
            return "";
        }
        return lookup.k8s(projectId, environment, profile)
                .map(endpoint -> endpoint.namespace() == null ? "" : endpoint.namespace().trim())
                .orElse("");
    }

    static List<String> capNamespaces(List<String> names) {
        return capNamespaces(names, "");
    }

    static List<String> capNamespaces(List<String> names, String preferred) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (preferred != null && !preferred.isBlank() && names.contains(preferred)) {
            ordered.add(preferred);
        }
        names.stream().filter(name -> !deferredNamespace(name)).forEach(ordered::add);
        names.stream().filter(K8sInspectionScanner::deferredNamespace).forEach(ordered::add);
        List<String> list = new ArrayList<>(ordered);
        return list.size() > MAX_CANDIDATES ? List.copyOf(list.subList(0, MAX_CANDIDATES)) : List.copyOf(list);
    }

    static boolean deferredNamespace(String name) {
        return DEFERRED_NS.contains(name) || name.startsWith("kube-");
    }

    static boolean isForbidden(Throwable exception) {
        String message = exception == null || exception.getMessage() == null ? "" : exception.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        return message.contains("K8s 无权限")
                || message.contains("403")
                || lower.contains("forbidden")
                || lower.contains("cannot list");
    }

    static List<Finding> inspectNodes(List<Map<String, Object>> nodes) {
        List<Finding> findings = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String ready = text(node.get("ready"), "Unknown");
            if (!"True".equalsIgnoreCase(ready)) {
                findings.add(new Finding(
                        "warning",
                        "节点 NotReady",
                        text(node.get("name"), "?") + " ready=" + ready
                ));
            }
        }
        return findings;
    }

    static List<Finding> inspectPods(String namespace, List<Map<String, Object>> pods) {
        List<Finding> findings = new ArrayList<>();
        for (Map<String, Object> pod : pods) {
            String name = namespace + "/" + text(pod.get("name"), "?");
            int restarts = number(pod.get("restarts"));
            List<String> reasons = podReasons(pod.get("containers"));
            if (!reasons.isEmpty()) {
                findings.add(new Finding("warning", "Pod 异常 · " + reasons.get(0), name + " " + String.join(", ", reasons)
                        + (restarts > 0 ? " restarts=" + restarts : "")));
            } else if (restarts >= RESTART_THRESHOLD) {
                findings.add(new Finding("warning", "重启次数偏高", name + " restarts=" + restarts));
            }
        }
        return findings;
    }

    static List<Finding> inspectEvents(String namespace, List<Map<String, Object>> events) {
        List<Finding> findings = new ArrayList<>();
        for (Map<String, Object> event : events) {
            if (!"Warning".equalsIgnoreCase(text(event.get("type"), ""))) {
                continue;
            }
            String reason = text(event.get("reason"), "Warning");
            String object = text(event.get("object"), "");
            String message = text(event.get("message"), "");
            findings.add(new Finding(
                    "warning",
                    "Warning · " + reason,
                    namespace + (object.isBlank() ? "" : " " + object) + (message.isBlank() ? "" : " — " + clip(message, 180))
            ));
            if (findings.size() >= 20) {
                break;
            }
        }
        return findings;
    }

    static List<String> podReasons(Object containers) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (containers instanceof List<?> list) {
            for (Object item : list) {
                String hint = String.valueOf(item);
                int eq = hint.indexOf('=');
                String reason = eq >= 0 ? hint.substring(eq + 1) : hint;
                for (String failure : POD_FAILURES) {
                    if (reason.toLowerCase(Locale.ROOT).contains(failure.toLowerCase(Locale.ROOT))) {
                        reasons.add(failure);
                    }
                }
            }
        }
        return List.copyOf(reasons);
    }

    static Report render(
            Environment environment,
            int bindings,
            int nodes,
            int namespaces,
            List<Finding> findings,
            List<String> sections
    ) {
        return render(environment, bindings, nodes, namespaces, findings, sections, List.of());
    }

    static Report render(
            Environment environment,
            int bindings,
            int nodes,
            int namespaces,
            List<Finding> findings,
            List<String> sections,
            List<String> skipped
    ) {
        int count = findings.size();
        boolean hasFindings = count > 0;
        String summary = hasFindings ? "发现 " + count + " 项" : "未见异常";
        StringBuilder markdown = new StringBuilder();
        markdown.append("# K8s 只读巡检\n\n");
        markdown.append("环境 **").append(environment).append("**，Binding ").append(bindings)
                .append(" 个，节点 ").append(nodes).append("，已扫命名空间 ").append(namespaces).append("。\n\n");
        for (String section : sections) {
            markdown.append(section).append("\n\n");
        }
        if (hasFindings) {
            markdown.append("## 发现（").append(count).append("）\n\n");
            for (Finding finding : findings) {
                markdown.append("- **").append(finding.title()).append("**：").append(finding.detail()).append("\n");
            }
        } else {
            markdown.append("## 正常\n\n节点全部 Ready，未见 CrashLoop / OOM / ImagePull、高重启或 Warning 事件。\n");
        }
        if (skipped != null && !skipped.isEmpty()) {
            markdown.append("\n## 跳过（账号无权限）\n\n");
            markdown.append("只读账号不能 list 这些命名空间的 Pod/事件，已跳过，不算故障：")
                    .append(String.join("、", skipped)).append("。\n");
        }
        markdown.append("\n只读 GET，未执行重启、调容或 exec。\n");
        return new Report(markdown.toString(), hasFindings, count, summary);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                items.add((Map<String, Object>) map);
            }
        }
        return items;
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equals(text) ? fallback : text;
    }

    private static int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public record Report(String markdown, boolean hasFindings, int findingCount, String summary) {
    }

    public record Finding(String severity, String title, String detail) {
    }

    private record ClusterScan(
            String cluster,
            int nodes,
            int namespaces,
            List<Finding> findings,
            List<String> skipped,
            String section
    ) {
    }
}
