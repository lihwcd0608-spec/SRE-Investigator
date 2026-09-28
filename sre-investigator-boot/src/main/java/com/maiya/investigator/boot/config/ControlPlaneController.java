package com.maiya.investigator.boot.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.McpBindingDetail;
import com.maiya.investigator.api.McpConnectionTestRequest;
import com.maiya.investigator.api.McpConnectionTestResponse;
import com.maiya.investigator.api.RepositoryRequest;
import com.maiya.investigator.api.ToolBindingEnabledRequest;
import com.maiya.investigator.api.ToolBindingRequest;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.ForbiddenException;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.boot.monitor.BindingMetricsService;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.domain.control.ToolAnnotations;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.infra.security.SecretCipher;
import com.maiya.investigator.mcp.GitRepositoryCloner;
import com.maiya.investigator.mcp.GitUrl;
import com.maiya.investigator.mcp.McpConnectionTester;
import com.maiya.investigator.mcp.MetricSnapshot;
import com.maiya.investigator.mcp.MysqlMetricsCollector;
import jakarta.validation.Valid;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ControlPlaneController {
    private final ControlPlaneStore controlPlane;
    private final SecretCipher cipher;
    private final AuditTrail auditTrail;
    private final McpConnectionTester connectionTester;
    private final GitRepositoryCloner gitCloner;
    private final ObjectMapper objectMapper;
    private final AccessGuard access;
    private final BindingMetricsService metricsService;

    public ControlPlaneController(
            ControlPlaneStore controlPlane,
            SecretCipher cipher,
            AuditTrail auditTrail,
            McpConnectionTester connectionTester,
            GitRepositoryCloner gitCloner,
            ObjectMapper objectMapper,
            AccessGuard access,
            BindingMetricsService metricsService
    ) {
        this.controlPlane = controlPlane;
        this.cipher = cipher;
        this.auditTrail = auditTrail;
        this.connectionTester = connectionTester;
        this.gitCloner = gitCloner;
        this.objectMapper = objectMapper;
        this.access = access;
        this.metricsService = metricsService;
    }

    @GetMapping("/repositories")
    @RequirePermission(Permissions.REPOSITORIES)
    public List<RepositoryConfig> repositories(@PathVariable UUID projectId) {
        requireProject(projectId);
        return controlPlane.repositories(projectId);
    }

    @PostMapping("/repositories")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.REPOSITORIES_WRITE)
    public RepositoryConfig createRepository(
            @PathVariable UUID projectId,
            @RequestBody RepositoryRequest request
    ) {
        requireProject(projectId);
        String role = blank(request.role()) ? "admin" : request.role().trim();
        String gitUrl = blank(request.gitUrl()) ? null : GitUrl.normalize(request.gitUrl());
        String gitRef = blank(request.gitRef()) ? null : request.gitRef().trim();
        String name;
        String rootPath;
        if (gitUrl != null) {
            name = blank(request.name()) ? GitUrl.repositoryName(gitUrl) : request.name().trim();
            rootPath = gitCloner.checkout(request.rootPath(), gitUrl, gitRef, request.username(), request.token(), request.sshIdentity()).toString();
        } else {
            name = blank(request.name()) ? null : request.name().trim();
            if (blank(name)) {
                throw new IllegalArgumentException("请填写仓库名称");
            }
            rootPath = requireLocalRoot(request.rootPath());
        }
        ensureRepositoryNameUnique(projectId, null, name);
        RepositoryConfig repository = controlPlane.saveRepository(
                RepositoryConfig.create(projectId, name, role, rootPath, gitUrl, gitRef)
        );
        auditTrail.record("repository.create", repository.id().toString(), Map.of(
                "projectId", projectId,
                "name", repository.name(),
                "source", gitUrl == null ? "local" : "git"
        ));
        return repository;
    }

    @PutMapping("/repositories/{repositoryId}")
    @RequirePermission(Permissions.REPOSITORIES_WRITE)
    public RepositoryConfig updateRepository(
            @PathVariable UUID projectId,
            @PathVariable UUID repositoryId,
            @RequestBody RepositoryRequest request
    ) {
        requireProject(projectId);
        RepositoryConfig current = controlPlane.repository(projectId, repositoryId);
        String role = blank(request.role()) ? current.role() : request.role().trim();
        String gitUrl = request.gitUrl() == null
                ? current.gitUrl()
                : (blank(request.gitUrl()) ? null : GitUrl.normalize(request.gitUrl()));
        String gitRef = request.gitRef() == null
                ? current.gitRef()
                : (blank(request.gitRef()) ? null : request.gitRef().trim());
        String name = blank(request.name()) ? current.name() : request.name().trim();
        ensureRepositoryNameUnique(projectId, repositoryId, name);
        String rootPath = resolveUpdatedRoot(current, request, gitUrl, gitRef);
        RepositoryConfig saved = controlPlane.saveRepository(
                current.withDetails(name, role, rootPath, gitUrl, gitRef)
        );
        auditTrail.record("repository.update", saved.id().toString(), Map.of(
                "projectId", projectId,
                "name", saved.name()
        ));
        return saved;
    }

    @DeleteMapping("/repositories/{repositoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.REPOSITORIES_DELETE)
    public void deleteRepository(@PathVariable UUID projectId, @PathVariable UUID repositoryId) {
        requireProject(projectId);
        RepositoryConfig current = controlPlane.repository(projectId, repositoryId);
        controlPlane.deleteRepository(projectId, repositoryId);
        auditTrail.record("repository.delete", repositoryId.toString(), Map.of(
                "projectId", projectId,
                "name", current.name()
        ));
    }

    @PostMapping("/repositories/{repositoryId}/pull")
    @RequirePermission(Permissions.REPOSITORIES_WRITE)
    public RepositoryConfig pullRepository(@PathVariable UUID projectId, @PathVariable UUID repositoryId) {
        requireProject(projectId);
        RepositoryConfig repository = controlPlane.repository(projectId, repositoryId);
        Path root = Path.of(repository.rootPath());
        if (!repository.fromGit() && !Files.isDirectory(root.resolve(".git"))) {
            throw new IllegalArgumentException("该仓库不是 Git 来源，无法拉取更新");
        }
        gitCloner.pull(root, repository.gitUrl(), repository.gitRef(), null, null);
        auditTrail.record("repository.pull", repository.id().toString(), Map.of(
                "projectId", projectId,
                "name", repository.name()
        ));
        return repository;
    }

    @GetMapping("/bindings")
    @RequirePermission(Permissions.BINDINGS)
    public List<ToolBinding> bindings(@PathVariable UUID projectId) {
        requireProject(projectId);
        return controlPlane.bindings(projectId);
    }

    @GetMapping("/bindings/{bindingId}")
    @RequirePermission(Permissions.BINDINGS)
    public McpBindingDetail bindingDetail(@PathVariable UUID projectId, @PathVariable UUID bindingId) {
        requireProject(projectId);
        ToolBinding binding = controlPlane.binding(projectId, bindingId);
        RevealedConfiguration revealed = reveal(controlPlane.encryptedBindingConfiguration(bindingId));
        return new McpBindingDetail(
                binding.id(),
                binding.projectId(),
                binding.environment(),
                binding.toolKind().name(),
                binding.profile(),
                binding.enabled(),
                binding.configured(),
                revealed.passwordSet(),
                revealed.configuration(),
                binding.summary(),
                binding.tags(),
                binding.createdAt(),
                binding.updatedAt()
        );
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.BINDINGS_WRITE)
    public ToolBinding createBinding(
            @PathVariable UUID projectId,
            @Valid @RequestBody ToolBindingRequest request
    ) {
        requireProject(projectId);
        ToolKind toolKind;
        try {
            toolKind = ToolKind.valueOf(request.toolKind().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 toolKind：" + request.toolKind());
        }
        Instant now = Instant.now();
        ToolBinding binding = ToolBinding.create(
                projectId,
                request.environment(),
                toolKind,
                request.profile(),
                now
        ).withDetails(
                request.environment(),
                toolKind,
                request.profile(),
                now,
                false,
                ToolAnnotations.clipSummary(request.summary()),
                ToolAnnotations.joinTags(request.tags())
        );
        binding = applyMonitoring(binding, request, now);
        String encryptedConfiguration = request.configuration() == null || request.configuration().isBlank()
                ? null
                : cipher.encrypt(request.configuration());
        ensureBindingUnique(projectId, null, request.environment(), toolKind, request.profile());
        ToolBinding saved = controlPlane.saveBinding(binding, encryptedConfiguration);
        auditTrail.record("binding.create", saved.id().toString(), Map.of(
                "projectId", projectId,
                "environment", saved.environment(),
                "toolKind", saved.toolKind(),
                "profile", saved.profile(),
                "hasConfiguration", encryptedConfiguration != null
        ));
        return saved;
    }

    @PutMapping("/bindings/{bindingId}")
    @RequirePermission(Permissions.BINDINGS_WRITE)
    public ToolBinding updateBinding(
            @PathVariable UUID projectId,
            @PathVariable UUID bindingId,
            @Valid @RequestBody ToolBindingRequest request
    ) {
        requireProject(projectId);
        ToolBinding current = controlPlane.binding(projectId, bindingId);
        ToolKind toolKind;
        try {
            toolKind = ToolKind.valueOf(request.toolKind().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 toolKind：" + request.toolKind());
        }
        ensureBindingUnique(projectId, bindingId, request.environment(), toolKind, request.profile());
        String encryptedConfiguration = mergeEncryptedConfiguration(
                controlPlane.encryptedBindingConfiguration(bindingId),
                request.configuration()
        );
        String summary = request.summary() == null
                ? current.summary()
                : ToolAnnotations.clipSummary(request.summary());
        String tags = request.tags() == null
                ? current.tags()
                : ToolAnnotations.joinTags(request.tags());
        Instant now = Instant.now();
        ToolBinding updated = current.withDetails(
                request.environment(),
                toolKind,
                request.profile(),
                now,
                encryptedConfiguration != null,
                summary,
                tags
        );
        updated = applyMonitoring(updated, request, now);
        ToolBinding saved = controlPlane.saveBinding(updated, encryptedConfiguration);
        auditTrail.record("binding.update", saved.id().toString(), Map.of(
                "projectId", projectId,
                "environment", saved.environment(),
                "toolKind", saved.toolKind(),
                "profile", saved.profile()
        ));
        return saved;
    }

    @DeleteMapping("/bindings/{bindingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.BINDINGS_DELETE)
    public void deleteBinding(@PathVariable UUID projectId, @PathVariable UUID bindingId) {
        requireProject(projectId);
        ToolBinding current = controlPlane.binding(projectId, bindingId);
        controlPlane.deleteBinding(projectId, bindingId);
        auditTrail.record("binding.delete", bindingId.toString(), Map.of(
                "projectId", projectId,
                "profile", current.profile(),
                "toolKind", current.toolKind()
        ));
    }

    @PatchMapping("/bindings/{bindingId}")
    @RequirePermission(Permissions.BINDINGS_WRITE)
    public ToolBinding setBindingEnabled(
            @PathVariable UUID projectId,
            @PathVariable UUID bindingId,
            @RequestBody ToolBindingEnabledRequest request
    ) {
        requireProject(projectId);
        ToolBinding saved = controlPlane.setBindingEnabled(projectId, bindingId, request.enabled());
        auditTrail.record("binding.enabled", bindingId.toString(), Map.of(
                "projectId", projectId,
                "enabled", saved.enabled()
        ));
        return saved;
    }

    /** 按需采集指定 binding 的当前指标快照。支持 MySQL / Redis / Elasticsearch。 */
    @GetMapping("/bindings/{bindingId}/metrics")
    @RequirePermission(Permissions.BINDINGS_MONITOR)
    public MetricSnapshot bindingMetrics(
            @PathVariable UUID projectId,
            @PathVariable UUID bindingId
    ) {
        requireProject(projectId);
        return metricsService.snapshot(projectId, bindingId);
    }

    /** 查询某指标的最近历史数据点。 */
    @GetMapping("/bindings/{bindingId}/metrics/history")
    @RequirePermission(Permissions.BINDINGS_MONITOR)
    public List<BindingMetricsService.MetricHistoryPoint> bindingMetricsHistory(
            @PathVariable UUID projectId,
            @PathVariable UUID bindingId,
            @RequestParam String metricKey,
            @RequestParam(defaultValue = "60") int limit
    ) {
        requireProject(projectId);
        return metricsService.history(projectId, bindingId, metricKey, limit);
    }

    /** 分页查询最近慢 SQL 明细（按耗时倒排）。 */
    @GetMapping("/bindings/{bindingId}/metrics/slow-queries")
    @RequirePermission(Permissions.BINDINGS_MONITOR)
    public MysqlMetricsCollector.SlowQueryPage bindingSlowQueries(
            @PathVariable UUID projectId,
            @PathVariable UUID bindingId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "100") int minMillis
    ) {
        requireProject(projectId);
        return metricsService.slowQueries(projectId, bindingId, page, size, minMillis);
    }

    /**
     * 若请求中携带监控字段，则校验监控权限后应用；否则原样返回。
     * 监控配置必须显式声明，不随普通保存隐式开启。
     */
    private ToolBinding applyMonitoring(ToolBinding binding, ToolBindingRequest request, Instant now) {
        if (request.monitoringEnabled() == null) {
            return binding;
        }
        if (!access.current().hasPermission(Permissions.BINDINGS_MONITOR)) {
            throw new ForbiddenException("没有开启监控的权限");
        }
        String metricsJson = binding.monitoringMetrics();
        if (request.monitoringMetrics() != null) {
            try {
                metricsJson = objectMapper.writeValueAsString(request.monitoringMetrics());
            } catch (Exception exception) {
                throw new IllegalArgumentException("监控指标配置序列化失败：" + exception.getMessage());
            }
        }
        return binding.withMonitoring(request.monitoringEnabled(), metricsJson, now);
    }

    @PostMapping("/bindings/test")
    @RequirePermission(Permissions.BINDINGS_WRITE)
    public McpConnectionTestResponse testBinding(
            @PathVariable UUID projectId,
            @Valid @RequestBody McpConnectionTestRequest request
    ) {
        requireProject(projectId);
        McpConnectionTestResponse result = connectionTester.test(request);
        auditTrail.record("binding.test", projectId.toString(), Map.of(
                "toolKind", request.toolKind(),
                "ok", result.ok()
        ));
        return result;
    }

    @PostMapping("/bindings/{bindingId}/test")
    @RequirePermission(Permissions.BINDINGS_WRITE)
    public McpConnectionTestResponse testSavedBinding(
            @PathVariable UUID projectId,
            @PathVariable UUID bindingId,
            @RequestBody(required = false) McpConnectionTestRequest request
    ) {
        requireProject(projectId);
        ToolBinding binding = controlPlane.binding(projectId, bindingId);
        Map<String, Object> configuration = overlayTestFields(
                decryptConfiguration(controlPlane.encryptedBindingConfiguration(bindingId)),
                request
        );
        McpConnectionTestResponse result = connectionTester.test(toTestRequest(binding.toolKind().name(), configuration));
        auditTrail.record("binding.test.saved", bindingId.toString(), Map.of(
                "projectId", projectId,
                "toolKind", binding.toolKind(),
                "ok", result.ok()
        ));
        return result;
    }

    private RevealedConfiguration reveal(String encrypted) {
        Map<String, Object> raw = decryptConfiguration(encrypted);
        boolean passwordSet = hasText(raw.get("password"))
                || uriHasPassword(raw.get("uri"))
                || hasText(raw.get("accessKeySecret"));
        Map<String, Object> visible = new LinkedHashMap<>(raw);
        if (visible.containsKey("password")) {
            visible.put("password", null);
        }
        if (visible.containsKey("accessKeySecret")) {
            visible.put("accessKeySecret", null);
        }
        if (visible.get("uri") instanceof String uri) {
            visible.put("uri", maskUri(uri));
        }
        return new RevealedConfiguration(passwordSet, visible);
    }

    private Map<String, Object> decryptConfiguration(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return Map.of();
        }
        String json = cipher.decrypt(encrypted);
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception exception) {
            return Map.of("note", json);
        }
    }

    private static Map<String, Object> overlayTestFields(Map<String, Object> saved, McpConnectionTestRequest request) {
        if (request == null) {
            return saved;
        }
        Map<String, Object> merged = new LinkedHashMap<>(saved == null ? Map.of() : saved);
        putIfText(merged, "host", request.host());
        putIfText(merged, "database", request.database());
        putIfText(merged, "username", request.username());
        putIfText(merged, "password", request.password());
        putIfText(merged, "parameters", request.parameters());
        putIfText(merged, "uri", request.uri());
        putIfText(merged, "rootPath", request.rootPath());
        putIfText(merged, "regionId", request.regionId());
        putIfText(merged, "project", request.project());
        putIfText(merged, "logstore", request.logstore());
        putIfText(merged, "accessKeyId", request.accessKeyId());
        putIfText(merged, "accessKeySecret", request.accessKeySecret());
        putIfText(merged, "endpoint", request.endpoint());
        putIfText(merged, "gatewayUniqueId", request.gatewayUniqueId());
        putIfText(merged, "nacosInstanceId", request.nacosInstanceId());
        putIfText(merged, "baseUrl", request.baseUrl());
        putIfText(merged, "cluster", request.cluster());
        putIfText(merged, "namespace", request.namespace());
        putIfText(merged, "serviceType", request.serviceType());
        putIfText(merged, "applications", request.applications());
        putIfText(merged, "instanceIds", request.instanceIds());
        putIfText(merged, "domains", request.domains());
        if (request.port() != null) {
            merged.put("port", request.port());
        }
        if (request.insecureSkipTls() != null) {
            merged.put("insecureSkipTls", request.insecureSkipTls());
        }
        return merged;
    }

    private static void putIfText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static McpConnectionTestRequest toTestRequest(String toolKind, Map<String, Object> configuration) {
        return new McpConnectionTestRequest(
                toolKind,
                text(configuration.get("host")),
                integer(configuration.get("port")),
                text(configuration.get("database")),
                text(configuration.get("username")),
                text(configuration.get("password")),
                text(configuration.get("parameters")),
                text(configuration.get("uri")),
                text(configuration.get("rootPath")),
                text(configuration.get("regionId")),
                text(configuration.get("project")),
                text(first(configuration, "logstore", "logstores")),
                text(first(configuration, "accessKeyId", "accessKey")),
                text(configuration.get("accessKeySecret")),
                text(configuration.get("endpoint")),
                text(configuration.get("gatewayUniqueId")),
                text(configuration.get("nacosInstanceId")),
                text(first(configuration, "baseUrl", "uri")),
                text(first(configuration, "cluster", "clusterName")),
                text(first(configuration, "namespace", "defaultNamespace")),
                bool(configuration.get("insecureSkipTls")),
                text(first(configuration, "serviceType", "loadBalancerType")),
                csv(first(configuration, "applications", "applicationNames")),
                csv(first(configuration, "instanceIds", "resources")),
                csv(first(configuration, "domains", "domainNames"))
        );
    }

    private static boolean uriHasPassword(Object uri) {
        if (!(uri instanceof String value) || value.isBlank()) {
            return false;
        }
        int scheme = value.indexOf("://");
        int at = value.indexOf('@');
        return scheme >= 0 && at > scheme && value.substring(scheme + 3, at).contains(":");
    }

    private static String maskUri(String uri) {
        int scheme = uri.indexOf("://");
        int at = uri.indexOf('@');
        if (scheme < 0 || at <= scheme) {
            return uri;
        }
        String userinfo = uri.substring(scheme + 3, at);
        int colon = userinfo.indexOf(':');
        if (colon < 0) {
            return uri;
        }
        return uri.substring(0, scheme + 3) + userinfo.substring(0, colon) + ":••••••" + uri.substring(at);
    }

    private static String csv(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> items) {
            List<String> parts = new ArrayList<>();
            for (Object item : items) {
                if (item != null && !item.toString().isBlank()) {
                    parts.add(item.toString().trim());
                }
            }
            return parts.isEmpty() ? null : String.join(",", parts);
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Object first(Map<String, Object> configuration, String... keys) {
        for (String key : keys) {
            Object value = configuration.get(key);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (!hasText(value)) {
            return null;
        }
        return Boolean.parseBoolean(value.toString().trim());
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record RevealedConfiguration(boolean passwordSet, Map<String, Object> configuration) {
    }

    private String resolveUpdatedRoot(
            RepositoryConfig current,
            RepositoryRequest request,
            String gitUrl,
            String gitRef
    ) {
        if (blank(request.rootPath())) {
            return current.rootPath();
        }
        Path dest = Path.of(request.rootPath().trim());
        boolean destGit = Files.isDirectory(dest.resolve(".git"));
        if (gitUrl != null && !destGit) {
            return gitCloner.checkout(request.rootPath(), gitUrl, gitRef, request.username(), request.token(), request.sshIdentity()).toString();
        }
        return requireLocalRoot(request.rootPath());
    }

    private void ensureRepositoryNameUnique(UUID projectId, UUID excludeId, String name) {
        boolean taken = controlPlane.repositories(projectId).stream()
                .anyMatch(item -> item.name().equals(name) && (excludeId == null || !item.id().equals(excludeId)));
        if (taken) {
            throw new IllegalArgumentException("同项目下仓库名已存在：" + name);
        }
    }

    private void ensureBindingUnique(
            UUID projectId,
            UUID excludeId,
            com.maiya.investigator.api.Environment environment,
            ToolKind toolKind,
            String profile
    ) {
        boolean taken = controlPlane.bindings(projectId).stream().anyMatch(item ->
                item.environment() == environment
                        && item.toolKind() == toolKind
                        && item.profile().equals(profile)
                        && (excludeId == null || !item.id().equals(excludeId)));
        if (taken) {
            throw new IllegalArgumentException("同环境已有相同类型和连接名的 MCP：" + profile);
        }
    }

    private String mergeEncryptedConfiguration(String existingEncrypted, String incomingJson) {
        if (blank(incomingJson)) {
            return existingEncrypted;
        }
        Map<String, Object> incoming = parseConfiguration(incomingJson);
        Map<String, Object> existing = decryptConfiguration(existingEncrypted);
        if (!hasText(incoming.get("password")) && hasText(existing.get("password"))) {
            incoming.put("password", existing.get("password"));
        }
        if (!hasText(incoming.get("accessKeySecret")) && hasText(existing.get("accessKeySecret"))) {
            incoming.put("accessKeySecret", existing.get("accessKeySecret"));
        }
        if (incoming.get("uri") instanceof String uri && uri.contains("••••") && existing.get("uri") instanceof String original) {
            incoming.put("uri", original);
        }
        try {
            return cipher.encrypt(objectMapper.writeValueAsString(incoming));
        } catch (Exception exception) {
            throw new IllegalArgumentException("连接配置不是合法 JSON");
        }
    }

    private Map<String, Object> parseConfiguration(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception exception) {
            throw new IllegalArgumentException("连接配置不是合法 JSON");
        }
    }

    private static String requireLocalRoot(String rootPath) {
        if (blank(rootPath)) {
            throw new IllegalArgumentException("请填写本机绝对路径，或改用 Git 拉取");
        }
        Path path = Path.of(rootPath.trim());
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("根路径必须是绝对路径");
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("路径不存在或不是目录：" + path);
        }
        return path.toAbsolutePath().normalize().toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void requireProject(UUID projectId) {
        access.requireProject(projectId);
    }
}
