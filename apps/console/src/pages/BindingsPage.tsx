import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { api, apiForm } from "../api";
import { OpenSourceFields } from "../catalog/OpenSourceFields";
import { OpenSourceSkillImport } from "../catalog/OpenSourceSkillImport";
import { SkillFileEditor } from "../catalog/SkillFileEditor";
import { ToolMetaFields } from "../catalog/ToolMetaFields";
import { applyTemplate, assetKindLabel, assetSceneLabel, composeAssetRoute, emptyAssetForm, emptyOpenSourceAssetForm, formFromAsset, RULE_USAGES, ruleUsageLabel, sourceLabel, type AssetForm, type RuleUsageId } from "../catalog/catalog";
import { annotationText, parseTags } from "../catalog/toolMeta";
import {
  IconArrowRight,
  IconBook,
  IconCompass,
  IconDatabase,
  IconFolder,
  IconGitBranch,
  IconLayers,
  IconLeaf,
  IconPencil,
  IconPlugs,
  IconPlus,
  IconPulse,
  IconScale,
  IconScroll,
  IconSearch,
  IconShare,
  IconTrash,
  IconX,
} from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import {
  applyDiscoveredK8sClusters,
  applyKindDefaults,
  buildMcpConfiguration,
  emptyMcpForm,
  formFromDetail,
  maskedConfigurationJson,
  MCP_KINDS,
  mcpKindLabel,
  parseClusterNames,
  testPayload,
  type ConnectionTestResult,
  type McpForm,
  type ToolCategory,
} from "../mcp/connection";
import { ENVIRONMENTS, parseEnvironment, type Binding, type BindingDetail, type CatalogAsset, type CatalogTemplate, type InvestigationRoute, type ToolKind } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { SearchSelect } from "../ui/SearchSelect";
import { BindingMetricsPanel } from "./BindingMetricsPanel";

const CATEGORIES: { id: ToolCategory; label: string }[] = [
  { id: "all", label: "全部" },
  { id: "mcp", label: "MCPs" },
  { id: "skill", label: "Skills" },
  { id: "rule", label: "Rules" },
];

const KIND_TILES: { id: ToolKind; hint: string }[] = [
  { id: "MYSQL", hint: "只读库表、慢查询" },
  { id: "REDIS", hint: "缓存 key、TTL" },
  { id: "MONGODB", hint: "文档与集合" },
  { id: "ELASTICSEARCH", hint: "检索集群" },
  { id: "LOGS", hint: "SLS 只读查日志" },
  { id: "MSE", hint: "网关 / Nacos / 治理" },
  { id: "ARMS", hint: "调用链，全局 RAM AK" },
  { id: "LOAD_BALANCER", hint: "ALB / CLB 健康检查" },
  { id: "CDN", hint: "域名、回源与流量" },
  { id: "K8S", hint: "Kuboard 只读查集群" },
  { id: "STARROCKS", hint: "数仓只读 SQL" },
  { id: "KAFKA", hint: "列 topic / 偷看 / 积压" },
  { id: "ROCKETMQ", hint: "列 topic / 偷看 / 积压" },
  { id: "CODE", hint: "本机代码目录" },
  { id: "MCP_SERVER", hint: "开源 MCP 命令与来源" },
];

function kindIcon(kind: string) {
  if (kind === "REDIS") return IconLayers;
  if (kind === "MONGODB") return IconLeaf;
  if (kind === "ELASTICSEARCH") return IconSearch;
  if (kind === "LOGS") return IconScroll;
  if (kind === "MSE") return IconShare;
  if (kind === "ARMS") return IconPulse;
  if (kind === "LOAD_BALANCER") return IconShare;
  if (kind === "CDN") return IconCompass;
  if (kind === "K8S") return IconGitBranch;
  if (kind === "STARROCKS") return IconDatabase;
  if (kind === "KAFKA") return IconLayers;
  if (kind === "ROCKETMQ") return IconShare;
  if (kind === "CODE") return IconFolder;
  if (kind === "SKILL") return IconBook;
  if (kind === "RULE") return IconScale;
  if (kind === "MCP_SERVER") return IconPlugs;
  return IconDatabase;
}

function kindTone(kind: string) {
  return kind.toLowerCase();
}

function formatTime(value: string) {
  return new Date(value).toLocaleString();
}

function ToolNotes({ summary, tags }: { summary?: string | null; tags?: string | string[] | null }) {
  const parsed = parseTags(tags);
  if (!summary && parsed.length === 0) return null;
  return (
    <>
      {summary ? <p className="tool-summary">{summary}</p> : null}
      {parsed.length > 0 ? (
        <div className="binding-meta user-tags">
          {parsed.map((tag) => (
            <span className="tag user" key={tag}>{tag}</span>
          ))}
        </div>
      ) : null}
    </>
  );
}

function countByKind(items: Binding[], kind: ToolKind) {
  return items.filter((item) => item.toolKind === kind).length;
}

const MONITORED_KINDS = new Set<ToolKind>(["MYSQL", "REDIS", "ELASTICSEARCH", "MONGODB"]);

const KIND_MONITORING_METRICS: Record<string, { key: string; label: string; desc: string }[]> = {
  MYSQL: [
    { key: "cpu_usage", label: "CPU 占用率", desc: "主机级 CPU%（仅本机实例，远程实例显示不可用）" },
    { key: "os_memory", label: "内存占用率", desc: "主机级内存%（仅本机实例，远程实例显示不可用）" },
    { key: "connections", label: "连接数", desc: "当前连接数 / max_connections" },
    { key: "threads_running", label: "活跃线程", desc: "并发活跃线程数（负载代理）" },
    { key: "memory_usage", label: "缓冲池使用率", desc: "InnoDB 缓冲池使用率" },
    { key: "slow_queries", label: "慢查询", desc: "慢查询累计次数 + 慢 SQL 明细" },
    { key: "qps", label: "QPS", desc: "每秒查询数（需两次以上采集）" },
    { key: "innodb_disk_reads", label: "InnoDB 磁盘读", desc: "缓冲池未命中的磁盘读取次数" },
  ],
  REDIS: [
    { key: "cpu_usage", label: "CPU 占用率", desc: "主机级 CPU%（仅本机实例）" },
    { key: "os_memory", label: "内存占用率", desc: "主机级内存%（仅本机实例）" },
    { key: "connections", label: "连接数", desc: "connected_clients / maxclients" },
    { key: "memory_usage", label: "内存使用率", desc: "used_memory / maxmemory" },
    { key: "memory_rss", label: "物理内存占用", desc: "used_memory_rss" },
    { key: "ops_per_sec", label: "OPS", desc: "每秒命令执行数" },
    { key: "hit_rate", label: "缓存命中率", desc: "keyspace_hits / (hits + misses)" },
    { key: "total_keys", label: "Key 总数", desc: "DBSIZE" },
    { key: "evicted_keys", label: "驱逐 Key 累计", desc: "内存不足被驱逐的 key 数" },
    { key: "rejected_connections", label: "拒绝连接累计", desc: "超过 maxclients 被拒绝的连接" },
  ],
  ELASTICSEARCH: [
    { key: "cluster_status", label: "集群状态", desc: "green / yellow / red" },
    { key: "nodes_count", label: "节点数", desc: "number_of_nodes" },
    { key: "cpu_usage", label: "CPU 占用率", desc: "各节点峰值（_nodes/stats）" },
    { key: "os_memory", label: "内存占用率", desc: "各节点峰值（_nodes/stats）" },
    { key: "heap_usage", label: "JVM 堆内存", desc: "各节点峰值 heap_used_percent" },
    { key: "disk_usage", label: "磁盘使用率", desc: "各节点峰值（fs 统计）" },
    { key: "active_shards", label: "活跃分片", desc: "active_shards" },
    { key: "unassigned_shards", label: "未分配分片", desc: "大于 0 需要关注" },
    { key: "docs_count", label: "文档总数", desc: "indices.docs.count" },
  ],
  MONGODB: [
    { key: "cpu_usage", label: "CPU 占用率", desc: "主机级 CPU%（仅本机实例）" },
    { key: "os_memory", label: "内存占用率", desc: "主机级内存%（仅本机实例）" },
    { key: "connections", label: "连接数", desc: "当前连接 / 可用容量" },
    { key: "memory_resident", label: "物理内存占用", desc: "mongod 进程 resident 内存（serverStatus）" },
    { key: "ops_query", label: "查询累计", desc: "opcounters.query" },
    { key: "network_io", label: "网络吞吐累计", desc: "bytesIn + bytesOut" },
    { key: "db_objects", label: "文档总数", desc: "默认库 dbStats.objects" },
    { key: "db_size", label: "数据大小", desc: "默认库 dataSize（MB）" },
    { key: "storage_size", label: "存储占用", desc: "默认库 storageSize（MB）" },
    { key: "index_size", label: "索引大小", desc: "默认库 indexSize（MB）" },
    { key: "replica_set", label: "部署形态", desc: "单机 / 副本集" },
    { key: "uptime", label: "运行时长", desc: "mongod 启动至今" },
  ],
};

function composerFitsCategory(category: ToolCategory, kind: "mcp" | "skill" | "rule") {
  return category === "all" || category === kind;
}

export function BindingsPage() {
  const { project, environment, setEnvironment, projectsReady, hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.bindings.write");
  const canDelete = hasPermission("action.bindings.delete");
  const canMonitor = hasPermission("action.bindings.monitor");
  const [bindings, setBindings] = useState<Binding[]>([]);
  const [metricsBinding, setMetricsBinding] = useState<Binding | null>(null);
  const [assets, setAssets] = useState<CatalogAsset[]>([]);
  const [templates, setTemplates] = useState<CatalogTemplate[]>([]);
  const [category, setCategory] = useState<ToolCategory>("mcp");
  const [query, setQuery] = useState("");
  const [form, setForm] = useState<McpForm>(() => emptyMcpForm(environment));
  const [assetForm, setAssetForm] = useState<AssetForm>(() => emptyAssetForm("SKILL"));
  const [error, setError] = useState("");
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false);
  const [composerKind, setComposerKind] = useState<"mcp" | "skill" | "rule">("mcp");
  const [catalogOpen, setCatalogOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [passwordSet, setPasswordSet] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [discoveredK8sClusters, setDiscoveredK8sClusters] = useState<string[]>([]);
  const [k8sDraft, setK8sDraft] = useState("");
  const [importing, setImporting] = useState(false);
  const [importingBundle, setImportingBundle] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<{ type: "mcp"; item: Binding } | { type: "asset"; item: CatalogAsset } | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [sceneRoutes, setSceneRoutes] = useState<InvestigationRoute[]>([]);
  const zipInput = useRef<HTMLInputElement>(null);
  const folderInput = useRef<HTMLInputElement>(null);

  const load = async () => {
    if (!project) return;
    const [nextBindings, nextAssets, nextTemplates] = await Promise.all([
      api<Binding[]>(`/api/projects/${project.id}/bindings`),
      api<CatalogAsset[]>(`/api/projects/${project.id}/assets`),
      api<CatalogTemplate[]>("/api/catalog"),
    ]);
    setBindings(nextBindings);
    setAssets(nextAssets.filter((item) => item.kind !== "AGENT"));
    setTemplates(nextTemplates);
  };

  useEffect(() => {
    void load().catch((cause: Error) => setError(cause.message));
  }, [project?.id]);

  useEffect(() => {
    void api<InvestigationRoute[]>("/api/agent/routes").then(setSceneRoutes).catch(() => setSceneRoutes([]));
  }, []);

  useEffect(() => {
    setForm((current) => ({ ...current, environment }));
  }, [environment]);

  useEffect(() => {
    if (!selectedId || composerKind !== "mcp") return;
    const selected = bindings.find((item) => item.id === selectedId);
    if (selected && parseEnvironment(selected.environment) !== environment) {
      setComposerOpen(false);
      setSelectedId(null);
    }
  }, [environment, selectedId, bindings, composerKind]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      if (catalogOpen) {
        setCatalogOpen(false);
        return;
      }
      if (composerOpen) {
        setComposerOpen(false);
        setSelectedId(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [catalogOpen, composerOpen]);

  const envBindings = useMemo(
    () => bindings.filter((item) => parseEnvironment(item.environment) === environment),
    [bindings, environment],
  );

  const visibleBindings = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return envBindings.filter((item) => {
      if (category === "skill" || category === "rule") return false;
      if (!keyword) return true;
      return [item.profile, item.toolKind, item.environment, item.toolKind === "MCP_SERVER" ? "开源" : "", annotationText(item.summary, item.tags)].some((value) => value.toLowerCase().includes(keyword));
    });
  }, [envBindings, category, query]);

  const visibleAssets = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return assets.filter((item) => {
      if (item.kind === "AGENT") return false;
      if (category === "mcp") return false;
      if (category === "skill" && item.kind !== "SKILL") return false;
      if (category === "rule" && item.kind !== "RULE") return false;
      if (!keyword) return true;
      return [item.name, item.slug, item.kind, item.source, item.route, annotationText(item.summary, item.tags)].some((value) => (value || "").toLowerCase().includes(keyword));
    });
  }, [assets, category, query]);

  const metrics = useMemo(() => {
    const enabledHere = envBindings.filter((item) => item.enabled).length;
    const kinds = new Set(envBindings.map((item) => item.toolKind)).size;
    const skillItems = assets.filter((item) => item.kind === "SKILL");
    const ruleItems = assets.filter((item) => item.kind === "RULE");
    return {
      enabledHere,
      kinds,
      skills: skillItems.length,
      rules: ruleItems.length,
      enabledSkills: skillItems.filter((item) => item.enabled).length,
      enabledRules: ruleItems.filter((item) => item.enabled).length,
    };
  }, [envBindings, assets]);

  const composerVisible = composerOpen && composerFitsCategory(category, composerKind);
  const hasQuery = query.trim().length > 0;
  const coveredKinds = KIND_TILES.filter((item) => countByKind(envBindings, item.id) > 0)
    .map((item) => mcpKindLabel(item.id));

  const mcpTemplates = useMemo(() => templates.filter((item) => item.kind === "MCP"), [templates]);
  const skillTemplates = useMemo(() => templates.filter((item) => item.kind === "SKILL"), [templates]);
  const ruleTemplates = useMemo(() => templates.filter((item) => item.kind === "RULE"), [templates]);
  const builtinSkillTemplates = useMemo(() => skillTemplates.filter((item) => item.source === "BUILTIN"), [skillTemplates]);
  const openSkillTemplates = useMemo(() => skillTemplates.filter((item) => item.source === "OPEN_SOURCE"), [skillTemplates]);
  const builtinRuleTemplates = useMemo(() => ruleTemplates.filter((item) => item.source === "BUILTIN"), [ruleTemplates]);
  const openRuleTemplates = useMemo(() => ruleTemplates.filter((item) => item.source === "OPEN_SOURCE"), [ruleTemplates]);
  const sceneOptions = useMemo(() => {
    const items = [
      { value: "", label: "不限场景", hint: "该类型下都会自动装" },
    ];
    for (const item of sceneRoutes) {
      items.push({ value: item.id, label: item.label, hint: item.id });
    }
    if (assetForm.scene && !items.some((item) => item.value === assetForm.scene)) {
      items.push({ value: assetForm.scene, label: assetForm.scene, hint: "手动填写" });
    }
    return items;
  }, [sceneRoutes, assetForm.scene]);

  if (!project) {
    return (
      <div className="empty empty-hero">
        <span className="empty-icon"><IconPlugs size={22} /></span>
        <h3>{projectsReady ? "还没有选中项目" : "正在加载项目…"}</h3>
        <p>
          {projectsReady
            ? <><Link to="/projects">回到项目列表</Link> 后再配置工具。</>
            : "正在读取项目上下文。"}
        </p>
      </div>
    );
  }

  const patch = (partial: Partial<McpForm>) => {
    setTestResult(null);
    setForm((current) => ({ ...current, ...partial }));
  };

  const toggleK8sCluster = (name: string) => {
    const selected = form.clusters.includes(name)
      ? form.clusters.filter((item) => item !== name)
      : [...form.clusters, name];
    if (selected.length === 0) {
      patch({ clusters: [], cluster: "" });
      return;
    }
    patch({
      clusters: selected,
      cluster: selected.includes(form.cluster) ? form.cluster : selected[0],
    });
  };

  const addK8sDraftCluster = () => {
    const name = k8sDraft.trim();
    if (!name) return;
    const clusters = form.clusters.includes(name) ? form.clusters : [...form.clusters, name];
    patch({ clusters, cluster: form.cluster || name });
    setDiscoveredK8sClusters((current) => (current.includes(name) ? current : [...current, name]));
    setK8sDraft("");
  };

  const visibleK8sClusters = parseClusterNames([...discoveredK8sClusters, ...form.clusters], form.cluster);

  const patchAsset = (partial: Partial<AssetForm>) => {
    setAssetForm((current) => ({ ...current, ...partial }));
  };

  const closeComposer = () => {
    setComposerOpen(false);
    setSelectedId(null);
  };

  const selectCategory = (next: ToolCategory) => {
    setCategory(next);
    if (composerOpen && !composerFitsCategory(next, composerKind)) {
      closeComposer();
    }
  };

  const openAddCatalog = (target?: ToolCategory) => {
    if (target) selectCategory(target);
    setCatalogOpen(true);
  };

  const chooseCatalogCategory = (next: "mcp" | "skill" | "rule") => {
    selectCategory(next);
  };

  const openCreate = (toolKind?: ToolKind) => {
    setCatalogOpen(false);
    setCategory("mcp");
    setComposerKind("mcp");
    setSelectedId(null);
    setPasswordSet(false);
    setTestResult(null);
    setError("");
    const next = emptyMcpForm(environment);
    setForm(toolKind ? applyKindDefaults(next, toolKind) : next);
    setDiscoveredK8sClusters([]);
    setK8sDraft("");
    setComposerOpen(true);
  };

  const openAssetCreate = (kind: "SKILL" | "RULE", template?: CatalogTemplate, mode: "custom" | "open_source" = "custom") => {
    setCatalogOpen(false);
    setCategory(kind === "SKILL" ? "skill" : "rule");
    setComposerKind(kind === "SKILL" ? "skill" : "rule");
    setSelectedId(null);
    setError("");
    setAssetForm(
      template
        ? applyTemplate(emptyAssetForm(kind), template)
        : mode === "open_source"
          ? emptyOpenSourceAssetForm(kind)
          : emptyAssetForm(kind),
    );
    setComposerOpen(true);
  };

  const openDetail = async (binding: Binding) => {
    setEnvironment(parseEnvironment(binding.environment));
    setCategory("mcp");
    setComposerKind("mcp");
    setSelectedId(binding.id);
    setComposerOpen(true);
    setTestResult(null);
    setError("");
    setLoadingDetail(true);
    try {
      const detail = await api<BindingDetail>(`/api/projects/${project.id}/bindings/${binding.id}`);
      const next = formFromDetail(detail);
      setForm(next);
      setDiscoveredK8sClusters(next.clusters);
      setK8sDraft("");
      setPasswordSet(detail.passwordSet);
    } catch (cause) {
      setForm({
        ...emptyMcpForm(binding.environment),
        environment: binding.environment,
        toolKind: binding.toolKind,
        profile: binding.profile,
        summary: binding.summary || "",
        tags: parseTags(binding.tags),
      });
      setPasswordSet(false);
      setError(cause instanceof Error ? cause.message : "加载连接失败");
    } finally {
      setLoadingDetail(false);
    }
  };

  const openAssetDetail = (asset: CatalogAsset) => {
    setCategory(asset.kind === "RULE" ? "rule" : "skill");
    setComposerKind(asset.kind === "RULE" ? "rule" : "skill");
    setSelectedId(asset.id);
    setComposerOpen(true);
    setError("");
    setAssetForm(formFromAsset(asset));
  };

  const onTest = async () => {
    setError("");
    setTesting(true);
    try {
      const secretBlank = form.toolKind === "LOGS" || form.toolKind === "MSE" ? !form.accessKeySecret : !form.password;
      const payload = testPayload({ ...form, environment });
      const result = selectedId && secretBlank
        ? await api<ConnectionTestResult>(`/api/projects/${project.id}/bindings/${selectedId}/test`, {
            method: "POST",
            body: JSON.stringify(payload),
          })
        : await api<ConnectionTestResult>(`/api/projects/${project.id}/bindings/test`, {
            method: "POST",
            body: JSON.stringify(payload),
          });
      setTestResult(result);
      if (form.toolKind === "K8S" && result.clusters && result.clusters.length > 0) {
        setDiscoveredK8sClusters(result.clusters);
        setForm((current) => applyDiscoveredK8sClusters(current, result.clusters ?? []));
      }
    } catch (cause) {
      setTestResult({
        ok: false,
        message: cause instanceof Error ? cause.message : "测试失败",
      });
    } finally {
      setTesting(false);
    }
  };

  const selected = bindings.find((item) => item.id === selectedId);
  const selectedAsset = assets.find((item) => item.id === selectedId);

  const onToggle = async (binding: Binding, enabled: boolean) => {
    setError("");
    try {
      const saved = await api<Binding>(`/api/projects/${project.id}/bindings/${binding.id}`, {
        method: "PATCH",
        body: JSON.stringify({ enabled }),
      });
      setBindings((current) => current.map((item) => (item.id === saved.id ? { ...item, ...saved } : item)));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "更新开关失败");
    }
  };

  const onToggleAsset = async (asset: CatalogAsset, enabled: boolean) => {
    setError("");
    try {
      const saved = await api<CatalogAsset>(`/api/projects/${project.id}/assets/${asset.id}`, {
        method: "PATCH",
        body: JSON.stringify({ enabled }),
      });
      setAssets((current) => current.map((item) => (item.id === saved.id ? saved : item)));
      if (selectedId === saved.id) setAssetForm((current) => ({ ...current, enabled: saved.enabled }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "更新开关失败");
    }
  };

  const onSave = async () => {
    setError("");
    if (form.toolKind === "K8S" && parseClusterNames(form.clusters, form.cluster).length === 0) {
      setError("请至少勾选一个 K8s 集群");
      return;
    }
    setSaving(true);
    try {
      await api(
        selectedId
          ? `/api/projects/${project.id}/bindings/${selectedId}`
          : `/api/projects/${project.id}/bindings`,
        {
          method: selectedId ? "PUT" : "POST",
          body: JSON.stringify({
            environment,
            toolKind: form.toolKind,
            profile: form.profile.trim(),
            configuration: JSON.stringify(buildMcpConfiguration(form)),
            summary: form.summary.trim() || null,
            tags: form.tags,
            monitoringEnabled: form.monitoringEnabled,
            monitoringMetrics: form.monitoringMetrics,
          }),
        },
      );
      setForm(emptyMcpForm(environment));
      setSelectedId(null);
      setPasswordSet(false);
      setTestResult(null);
      setDiscoveredK8sClusters([]);
      setK8sDraft("");
      setComposerOpen(false);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const onSaveAsset = async () => {
    setError("");
    setSaving(true);
    try {
      await api(
        selectedId
          ? `/api/projects/${project.id}/assets/${selectedId}`
          : `/api/projects/${project.id}/assets`,
        {
          method: selectedId ? "PUT" : "POST",
          body: JSON.stringify({
            kind: assetForm.kind,
            source: assetForm.source,
            slug: assetForm.slug,
            name: assetForm.name.trim(),
            route: composeAssetRoute(assetForm.route, assetForm.scene),
            enabled: assetForm.enabled,
            origin: assetForm.origin,
            catalogId: assetForm.catalogId,
            packageName: assetForm.packageName,
            sourceUrl: assetForm.sourceUrl,
            gitRef: assetForm.gitRef,
            content: assetForm.kind === "SKILL"
              ? (assetForm.files.find((file) => file.path.toLowerCase() === "skill.md")?.content ?? assetForm.content)
              : assetForm.content,
            summary: assetForm.summary.trim() || null,
            tags: assetForm.tags,
            files: assetForm.kind === "SKILL" ? assetForm.files : undefined,
          }),
        },
      );
      setComposerOpen(false);
      setSelectedId(null);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const onImportAsset = async (url?: string): Promise<boolean> => {
    const sourceUrl = (url ?? assetForm.sourceUrl).trim();
    if (!sourceUrl) return false;
    setError("");
    setImporting(true);
    try {
      const result = await api<{ content: string }>(`/api/projects/${project.id}/assets/import`, {
        method: "POST",
        body: JSON.stringify({ url: sourceUrl }),
      });
      patchAsset({
        content: result.content,
        source: "OPEN_SOURCE",
        origin: assetForm.origin || "github",
        sourceUrl,
        files: [{ path: "SKILL.md", content: result.content }, ...assetForm.files.filter((file) => file.path.toLowerCase() !== "skill.md")],
      });
      return true;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "导入失败");
      return false;
    } finally {
      setImporting(false);
    }
  };

  const onImportBundle = async (kind: "zip" | "folder", fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    setError("");
    setImportingBundle(true);
    try {
      const body = new FormData();
      if (kind === "zip") {
        body.append("zip", fileList[0], fileList[0].name);
      } else {
        Array.from(fileList).forEach((file) => {
          const relative = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name;
          body.append("files", file, relative);
        });
      }
      const preview = await apiForm<{
        name: string;
        summary?: string | null;
        files: { path: string; content: string }[];
      }>(`/api/projects/${project.id}/assets/import-bundle`, body);
      const entry = preview.files.find((file) => file.path.toLowerCase() === "skill.md");
      openAssetCreate("SKILL");
      setAssetForm((current) => ({
        ...current,
        name: preview.name || current.name,
        summary: preview.summary || current.summary,
        content: entry?.content || current.content,
        files: preview.files.length ? preview.files : current.files,
      }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "导入 Skill 包失败");
    } finally {
      setImportingBundle(false);
    }
  };

  const onDelete = async () => {
    if (!pendingDelete) return;
    setError("");
    setDeleting(true);
    try {
      if (pendingDelete.type === "mcp") {
        await api(`/api/projects/${project.id}/bindings/${pendingDelete.item.id}`, { method: "DELETE" });
      } else {
        await api(`/api/projects/${project.id}/assets/${pendingDelete.item.id}`, { method: "DELETE" });
      }
      if (selectedId === pendingDelete.item.id) closeComposer();
      setPendingDelete(null);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">{project.slug}</p>
          <h2>工具配置</h2>
          <p>MCP、Skill、Rule 都可自建或从开源目录接入。每条可写简介和标签，排查时会注入模型，方便选工具。</p>
        </div>
        <Link className="btn secondary" to={`/projects/${project.id}/investigate`}>
          去对话
          <IconArrowRight size={16} />
        </Link>
      </div>

      <div className="tools-toolbar">
        <div className="search-wrap">
          <IconSearch size={16} />
          <input
            className="search-input"
            aria-label="搜索工具"
            placeholder="搜索名称、简介或标签…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        {canWrite || catalogOpen ? (
        <button
          className="btn"
          type="button"
          onClick={() => (catalogOpen ? setCatalogOpen(false) : openAddCatalog())}
        >
          {catalogOpen ? <IconX size={16} /> : <IconPlus size={16} />}
          {catalogOpen
            ? "收起"
            : category === "skill" ? "新增 Skill" : category === "rule" ? "新增 Rule" : category === "all" ? "新增" : "新增 MCP"}
        </button>
        ) : null}
        {category === "skill" ? (
          <>
            <input
              ref={zipInput}
              type="file"
              accept=".zip,application/zip"
              hidden
              onChange={(event) => {
                void onImportBundle("zip", event.target.files);
                event.target.value = "";
              }}
            />
            <input
              ref={folderInput}
              type="file"
              hidden
              multiple
              {...{ webkitdirectory: "", directory: "" }}
              onChange={(event) => {
                void onImportBundle("folder", event.target.files);
                event.target.value = "";
              }}
            />
            {canWrite ? (
            <>
            <button className="btn secondary" type="button" disabled={importingBundle} onClick={() => zipInput.current?.click()}>
              {importingBundle ? "导入中…" : "导入 zip"}
            </button>
            <button className="btn secondary" type="button" disabled={importingBundle} onClick={() => folderInput.current?.click()}>
              导入文件夹
            </button>
            </>
            ) : null}
          </>
        ) : null}
      </div>

      <div className="chip-row">
        {CATEGORIES.map((item) => {
          const count = item.id === "skill"
            ? metrics.skills
            : item.id === "rule"
              ? metrics.rules
              : item.id === "mcp"
                ? envBindings.length
                : envBindings.length + assets.length;
          return (
            <button
              key={item.id}
              type="button"
              className={`chip${category === item.id ? " active" : ""}`}
              aria-pressed={category === item.id}
              aria-current={category === item.id ? "true" : undefined}
              onClick={() => selectCategory(item.id)}
            >
              {item.label}
              <span className="chip-count">{count}</span>
            </button>
          );
        })}
      </div>

      {catalogOpen ? (
        <section className="add-catalog" aria-label="添加类型">
          <div className="add-catalog-head">
            <div>
              <h3>
                {category === "all"
                  ? "要添加哪一类"
                  : category === "mcp"
                    ? "选择 MCP 类型"
                    : category === "skill"
                      ? "新增 Skill"
                      : "新增 Rule"}
              </h3>
              <p>
                {category === "all"
                  ? "先选 MCP、Skill 或 Rule，再选具体类型。"
                  : category === "mcp"
                    ? "选一种连接后填写，先测试再保存。"
                    : category === "skill"
                      ? "空白撰写、套用模板，或导入含 SKILL.md 的 zip / 文件夹。"
                      : "空白撰写、套用内置模板，或从开源目录选用。"}
              </p>
            </div>
            <button className="btn ghost" type="button" onClick={() => setCatalogOpen(false)}>
              <IconX size={16} />
              收起
            </button>
          </div>
          <div className="kind-catalog">
            {category === "all" ? (
              <>
                <button type="button" className="kind-tile" onClick={() => chooseCatalogCategory("mcp")}>
                  <span className="kind-mark mysql" aria-hidden="true"><IconPlugs size={18} /></span>
                  <b>MCPs</b>
                  <span>只读库、缓存、检索和代码目录</span>
                  <small>{envBindings.length > 0 ? `当前环境已有 ${envBindings.length} 条` : "当前环境还没有"}</small>
                </button>
                <button type="button" className="kind-tile" onClick={() => chooseCatalogCategory("skill")}>
                  <span className="kind-mark skill" aria-hidden="true"><IconBook size={18} /></span>
                  <b>Skills</b>
                  <span>排查策略，可写正文或导入开源</span>
                  <small>{metrics.skills > 0 ? `已有 ${metrics.skills} 条` : "还没有"}</small>
                </button>
                <button type="button" className="kind-tile" onClick={() => chooseCatalogCategory("rule")}>
                  <span className="kind-mark rule" aria-hidden="true"><IconScale size={18} /></span>
                  <b>Rules</b>
                  <span>只读边界与证据规则</span>
                  <small>{metrics.rules > 0 ? `已有 ${metrics.rules} 条` : "还没有"}</small>
                </button>
              </>
            ) : null}
            {category === "mcp" ? KIND_TILES.map((item) => {
              const Icon = kindIcon(item.id);
              const count = countByKind(envBindings, item.id);
              return (
                <button
                  key={item.id}
                  type="button"
                  className="kind-tile"
                  onClick={() => openCreate(item.id)}
                >
                  <span className={`kind-mark ${kindTone(item.id)}`} aria-hidden="true">
                    <Icon size={18} />
                  </span>
                  <b>{mcpKindLabel(item.id)}</b>
                  <span>{item.hint}</span>
                  <small>{count > 0 ? `已有 ${count} 条` : "还没有"}</small>
                </button>
              );
            }) : null}
            {category === "skill" || category === "rule" ? (
              <>
                <button
                  type="button"
                  className="kind-tile"
                  onClick={() => openAssetCreate(category === "skill" ? "SKILL" : "RULE")}
                >
                  <span className={`kind-mark ${category}`} aria-hidden="true">
                    {category === "skill" ? <IconBook size={18} /> : <IconScale size={18} />}
                  </span>
                  <b>空白{category === "skill" ? " Skill" : " Rule"}</b>
                  <span>自己写正文，保存后注入排查上下文</span>
                  <small>从零开始</small>
                </button>
                {category === "skill" ? (
                  <button
                    type="button"
                    className="kind-tile"
                    onClick={() => zipInput.current?.click()}
                  >
                    <span className="kind-mark skill" aria-hidden="true"><IconFolder size={18} /></span>
                    <b>导入 Skill 包</b>
                    <span>zip 或文件夹，须含 SKILL.md，可带附属文本</span>
                    <small>{importingBundle ? "正在解析…" : "最多 20 个文件 / 256KB"}</small>
                  </button>
                ) : null}
                <button
                  type="button"
                  className="kind-tile"
                  onClick={() => openAssetCreate(category === "skill" ? "SKILL" : "RULE", undefined, "open_source")}
                >
                  <span className="kind-mark open-source" aria-hidden="true">
                    <IconGitBranch size={18} />
                  </span>
                  <b>开源{category === "skill" ? " Skill" : " Rule"}</b>
                  <span>{category === "skill" ? "搜索公开目录后导入，和自建表单分开" : "从开源目录选用，或填地址导入"}</span>
                  <small>
                    {(category === "skill" ? openSkillTemplates : openRuleTemplates).length > 0
                      ? `${(category === "skill" ? openSkillTemplates : openRuleTemplates).length} 个目录项`
                      : "填地址导入"}
                  </small>
                </button>
              </>
            ) : null}
          </div>
          {category === "skill" || category === "rule" ? (
            <>
              {(category === "skill" ? builtinSkillTemplates : builtinRuleTemplates).length > 0 ? (
                <div className="add-catalog-group">
                  <p className="add-catalog-label">内置模板</p>
                  <div className="kind-catalog">
                    {(category === "skill" ? builtinSkillTemplates : builtinRuleTemplates).map((template) => (
                      <button
                        key={template.id}
                        type="button"
                        className="kind-tile"
                        onClick={() => openAssetCreate(category === "skill" ? "SKILL" : "RULE", template)}
                      >
                        <span className={`kind-mark ${category}`} aria-hidden="true">
                          {category === "skill" ? <IconBook size={18} /> : <IconScale size={18} />}
                        </span>
                        <b>{template.name}</b>
                        <span>{template.description || sourceLabel(template.source)}</span>
                        <small>{sourceLabel(template.source)}</small>
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
              {(category === "skill" ? openSkillTemplates : openRuleTemplates).length > 0 ? (
                <div className="add-catalog-group">
                  <p className="add-catalog-label">开源目录</p>
                  <div className="kind-catalog">
                    {(category === "skill" ? openSkillTemplates : openRuleTemplates).map((template) => (
                      <button
                        key={template.id}
                        type="button"
                        className="kind-tile"
                        onClick={() => openAssetCreate(category === "skill" ? "SKILL" : "RULE", template)}
                      >
                        <span className="kind-mark open-source" aria-hidden="true">
                          <IconGitBranch size={18} />
                        </span>
                        <b>{template.name}</b>
                        <span>{template.description || sourceLabel(template.source)}</span>
                        <small>{sourceLabel(template.source)}</small>
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
            </>
          ) : null}
        </section>
      ) : null}

      <div className="tools-page">
        <section className="tools-context" aria-label="当前筛选概览">
          {category === "skill" ? (
            <>
              <span><b>{metrics.skills}</b> 条 Skill</span>
              <span><b>{metrics.enabledSkills}</b> 已启用</span>
              <span><b>{openSkillTemplates.length}</b> 个开源目录</span>
            </>
          ) : category === "rule" ? (
            <>
              <span><b>{metrics.rules}</b> 条 Rule</span>
              <span><b>{metrics.enabledRules}</b> 已启用</span>
              <span><b>{openRuleTemplates.length}</b> 个开源目录</span>
            </>
          ) : category === "mcp" ? (
            <>
              <span><b>{envBindings.length}</b> 条 MCP</span>
              <span><b>{metrics.enabledHere}</b> 个在 {environment} 启用</span>
              <span><b>{metrics.kinds}</b> 种类型{coveredKinds.length ? ` · ${coveredKinds.join(" / ")}` : ""}</span>
            </>
          ) : (
            <>
              <span><b>{envBindings.length + assets.length}</b> 条工具</span>
              <span><b>{envBindings.length}</b> MCP · <b>{metrics.skills}</b> Skill · <b>{metrics.rules}</b> Rule</span>
              <span><b>{metrics.enabledHere}</b> 个 MCP 在 {environment} 启用</span>
            </>
          )}
        </section>

        <div className={`grid${composerVisible ? " tools-grid" : ""}`}>
          <section className="card">
            <div className="card-head">
              <h3>
                {category === "skill"
                  ? `Skills ${visibleAssets.length}`
                  : category === "rule"
                    ? `Rules ${visibleAssets.length}`
                    : category === "all"
                      ? `全部 ${visibleBindings.length + visibleAssets.length}`
                      : `已连接 ${visibleBindings.length}`}
              </h3>
              <span className="badge">{environment}</span>
            </div>
            {category !== "mcp" && category !== "all" ? (
              visibleAssets.length === 0 ? (
                <div className="empty empty-hero">
                  <span className="empty-icon">{category === "skill" ? <IconBook size={22} /> : <IconScale size={22} />}</span>
                  <h3>{hasQuery ? `没有匹配的 ${category === "skill" ? "Skill" : "Rule"}` : `还没有 ${category === "skill" ? "Skill" : "Rule"}`}</h3>
                  <p>{hasQuery ? "换个关键词，或清空搜索后再新增。" : "点右上角新增，先选写法再填写。"}</p>
                  <div className="empty-hints">
                    {hasQuery ? (
                      <button className="btn secondary" type="button" onClick={() => setQuery("")}>清空搜索</button>
                    ) : (
                      <button className="btn" type="button" onClick={() => openAddCatalog(category)}>
                        <IconPlus size={15} />
                        新增 {category === "skill" ? "Skill" : "Rule"}
                      </button>
                    )}
                  </div>
                </div>
              ) : (
                <div className={`binding-grid${composerVisible ? " compact" : ""}`}>
                  {visibleAssets.map((asset) => {
                    const Icon = kindIcon(asset.kind);
                    return (
                      <article className={`binding-card${selectedId === asset.id ? " selected" : ""}`} key={asset.id}>
                        <button className="binding-main" type="button" onClick={() => openAssetDetail(asset)}>
                          <span className={`kind-mark ${asset.kind.toLowerCase()}`} aria-hidden="true">
                            <Icon size={18} />
                          </span>
                          <div className="tool-copy">
                            <div className="tool-title">
                              <b>{asset.name}</b>
                              <span className={`status-pill${asset.enabled ? " on" : ""}`}>
                                {asset.enabled ? "已启用" : "已停用"}
                              </span>
                            </div>
                            <div className="binding-meta">
                              <span className="tag">{assetKindLabel(asset.kind)}</span>
                              <span className="tag faint">{sourceLabel(asset.source)}</span>
                              <span className="tag faint">{ruleUsageLabel(asset.route)}</span>
                              {assetSceneLabel(asset.route) ? <span className="tag faint">{assetSceneLabel(asset.route)}</span> : null}
                              {asset.kind === "SKILL" && (asset.files?.length ?? 0) > 0 ? (
                                <span className="tag faint">{(asset.files?.length ?? 0) + 1} 个文件</span>
                              ) : null}
                            </div>
                            <ToolNotes summary={asset.summary} tags={asset.tags} />
                            <span>更新于 {formatTime(asset.updatedAt)}</span>
                          </div>
                        </button>
                        <div className="binding-foot">
                          <div className="item-actions">
                            <button className="btn ghost" type="button" onClick={() => openAssetDetail(asset)}>
                              <IconPencil size={13} />
                              修改
                            </button>
                            {canDelete ? (
                            <button className="btn ghost danger" type="button" onClick={() => setPendingDelete({ type: "asset", item: asset })}>
                              <IconTrash size={13} />
                              删除
                            </button>
                            ) : null}
                          </div>
                          <label className="switch" title={asset.enabled ? "停用" : "启用"}>
                            <input
                              type="checkbox"
                              checked={asset.enabled}
                              aria-label={asset.enabled ? `停用 ${asset.name}` : `启用 ${asset.name}`}
                              disabled={!canWrite}
                              onChange={(event) => void onToggleAsset(asset, event.target.checked)}
                            />
                            <span className="slider" />
                          </label>
                        </div>
                      </article>
                    );
                  })}
                </div>
              )
            ) : visibleBindings.length === 0 && (category !== "all" || visibleAssets.length === 0) ? (
              <div className="empty empty-hero">
                <span className="empty-icon"><IconDatabase size={22} /></span>
                <h3>{hasQuery ? "没有匹配的 MCP" : `${environment} 还没有 MCP`}</h3>
                <p>{hasQuery ? "换个关键词，或清空搜索后再新增。" : "当前列表只显示顶栏选中的环境。切到其他环境，或点右上角新增。"}</p>
                <div className="empty-hints">
                  {hasQuery ? (
                    <button className="btn secondary" type="button" onClick={() => setQuery("")}>清空搜索</button>
                  ) : (
                    <button className="btn" type="button" onClick={() => openAddCatalog("mcp")}>
                      <IconPlus size={15} />
                      新增 MCP
                    </button>
                  )}
                </div>
              </div>
            ) : (
              <div className={`binding-grid${composerVisible ? " compact" : ""}`}>
                {visibleBindings.map((binding) => {
                  const Icon = kindIcon(binding.toolKind);
                  return (
                    <article
                      className={`binding-card${selectedId === binding.id ? " selected" : ""}`}
                      key={binding.id}
                    >
                      <button className="binding-main" type="button" onClick={() => void openDetail(binding)}>
                        <span className={`kind-mark ${kindTone(binding.toolKind)}`} aria-hidden="true">
                          <Icon size={18} />
                        </span>
                        <div className="tool-copy">
                          <div className="tool-title">
                            <b>{binding.profile}</b>
                            <span className={`status-pill${binding.enabled ? " on" : ""}`}>
                              {binding.enabled ? "已启用" : "已停用"}
                            </span>
                            {binding.monitoringEnabled ? <span className="status-pill monitor">监控中</span> : null}
                          </div>
                          <div className="binding-meta">
                            <span className="tag">{mcpKindLabel(binding.toolKind)}</span>
                            <span className="tag faint">{parseEnvironment(binding.environment)}</span>
                            <span className="tag faint">{binding.configured ? "配置已保存" : "未保存连接"}</span>
                          </div>
                          <ToolNotes summary={binding.summary} tags={binding.tags} />
                          <span>更新于 {formatTime(binding.updatedAt)}</span>
                        </div>
                      </button>
                      <div className="binding-foot">
                        <div className="item-actions">
                          <button className="btn ghost" type="button" onClick={() => void openDetail(binding)}>
                            <IconPencil size={13} />
                            修改
                          </button>
                          {binding.monitoringEnabled && canMonitor ? (
                          <button className="btn ghost" type="button" onClick={() => setMetricsBinding(binding)}>
                            <IconPulse size={13} />
                            查看监控
                          </button>
                          ) : null}
                          {canDelete ? (
                          <button className="btn ghost danger" type="button" onClick={() => setPendingDelete({ type: "mcp", item: binding })}>
                            <IconTrash size={13} />
                            删除
                          </button>
                          ) : null}
                        </div>
                        <label className="switch" title={binding.enabled ? "停用" : "启用"}>
                          <input
                            type="checkbox"
                            checked={binding.enabled}
                            aria-label={binding.enabled ? `停用 ${binding.profile}` : `启用 ${binding.profile}`}
                            onChange={(event) => void onToggle(binding, event.target.checked)}
                          />
                          <span className="slider" />
                        </label>
                      </div>
                    </article>
                  );
                })}
                {visibleAssets.map((asset) => {
                  const Icon = kindIcon(asset.kind);
                  return (
                    <article className={`binding-card${selectedId === asset.id ? " selected" : ""}`} key={asset.id}>
                      <button className="binding-main" type="button" onClick={() => openAssetDetail(asset)}>
                        <span className={`kind-mark ${asset.kind.toLowerCase()}`} aria-hidden="true">
                          <Icon size={18} />
                        </span>
                        <div className="tool-copy">
                          <div className="tool-title">
                            <b>{asset.name}</b>
                            <span className={`status-pill${asset.enabled ? " on" : ""}`}>
                              {asset.enabled ? "已启用" : "已停用"}
                            </span>
                          </div>
                          <div className="binding-meta">
                            <span className="tag">{assetKindLabel(asset.kind)}</span>
                            <span className="tag faint">{sourceLabel(asset.source)}</span>
                            <span className="tag faint">{ruleUsageLabel(asset.route)}</span>
                            {assetSceneLabel(asset.route) ? <span className="tag faint">{assetSceneLabel(asset.route)}</span> : null}
                            {asset.kind === "SKILL" && (asset.files?.length ?? 0) > 0 ? (
                              <span className="tag faint">{(asset.files?.length ?? 0) + 1} 个文件</span>
                            ) : null}
                          </div>
                          <ToolNotes summary={asset.summary} tags={asset.tags} />
                          <span>更新于 {formatTime(asset.updatedAt)}</span>
                        </div>
                      </button>
                      <div className="binding-foot">
                        <div className="item-actions">
                          <button className="btn ghost" type="button" onClick={() => openAssetDetail(asset)}>
                            <IconPencil size={13} />
                            修改
                          </button>
                          {canDelete ? (
                          <button className="btn ghost danger" type="button" onClick={() => setPendingDelete({ type: "asset", item: asset })}>
                            <IconTrash size={13} />
                            删除
                          </button>
                          ) : null}
                        </div>
                        <label className="switch" title={asset.enabled ? "停用" : "启用"}>
                          <input
                            type="checkbox"
                            checked={asset.enabled}
                            aria-label={asset.enabled ? `停用 ${asset.name}` : `启用 ${asset.name}`}
                            disabled={!canWrite}
                              onChange={(event) => void onToggleAsset(asset, event.target.checked)}
                          />
                          <span className="slider" />
                        </label>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </section>

          {composerVisible && composerKind === "mcp" ? (
            <section className="card composer-panel" aria-label={selectedId ? "修改 MCP" : "新增 MCP"}>
              <div className="card-head">
                <div className="composer-title">
                  <span className="tag">MCP</span>
                  <h3>{selectedId ? "修改连接" : "新增连接"}</h3>
                </div>
                <button className="btn ghost" type="button" onClick={closeComposer}>收起</button>
              </div>
              <form
                className="form navicat"
                onSubmit={(event) => {
                  event.preventDefault();
                  void onSave();
                }}
              >
                <OpenSourceFields
                  kind="MCP"
                  source={form.source}
                  origin={form.origin}
                  catalogId={form.catalogId}
                  packageName={form.packageName}
                  sourceUrl={form.sourceUrl}
                  gitRef={form.gitRef}
                  command={form.command}
                  args={form.args}
                  templates={mcpTemplates}
                  onSourceChange={(source) => patch({ source, origin: source === "open_source" ? (form.origin || "npm") : "local" })}
                  onPick={(template) => patch({
                    source: "open_source",
                    origin: template.origin,
                    catalogId: template.id,
                    packageName: template.packageName || "",
                    sourceUrl: template.sourceUrl || "",
                    gitRef: template.gitRef || "",
                    command: template.command || "",
                    args: template.args || "",
                    toolKind: (template.toolKind as ToolKind) || "MCP_SERVER",
                    profile: form.profile || template.id,
                    summary: form.summary || template.description || "",
                  })}
                  onChange={(partial) => patch(partial)}
                />
                <div className="row">
                  <label>连接名
                    <input value={form.profile} onChange={(event) => patch({ profile: event.target.value })} required placeholder="test" />
                  </label>
                  <label>环境
                    <select
                      value={environment}
                      onChange={(event) => setEnvironment(event.target.value as McpForm["environment"])}
                    >
                      {ENVIRONMENTS.map((item) => <option key={item} value={item}>{item}</option>)}
                    </select>
                  </label>
                </div>
                <label>类型
                  <select
                    value={form.toolKind}
                    onChange={(event) => patch(applyKindDefaults(form, event.target.value as McpForm["toolKind"]))}
                  >
                    {MCP_KINDS.map((item) => <option key={item} value={item}>{mcpKindLabel(item)}</option>)}
                    <option value="MCP_SERVER">开源 MCP</option>
                  </select>
                </label>
                <ToolMetaFields
                  summary={form.summary}
                  tags={form.tags}
                  onChange={(partial) => patch(partial)}
                />

                {form.toolKind === "MYSQL" ? (
                  <>
                    <div className="row">
                      <label>主机
                        <input value={form.host} onChange={(event) => patch({ host: event.target.value })} required placeholder="127.0.0.1" />
                      </label>
                      <label>端口
                        <input value={form.port} onChange={(event) => patch({ port: event.target.value })} required placeholder="3306" />
                      </label>
                    </div>
                    <label>数据库
                      <input value={form.database} onChange={(event) => patch({ database: event.target.value })} required placeholder="your_db" />
                    </label>
                    <div className="row">
                      <label>用户名
                        <input value={form.username} onChange={(event) => patch({ username: event.target.value })} required placeholder="readonly" />
                      </label>
                      <label>密码
                        <input type="password" value={form.password} onChange={(event) => patch({ password: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "只读账号密码"} />
                      </label>
                    </div>
                    <label>连接参数
                      <input value={form.parameters} onChange={(event) => patch({ parameters: event.target.value })} />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "REDIS" ? (
                  <>
                    <div className="row">
                      <label>主机
                        <input value={form.host} onChange={(event) => patch({ host: event.target.value })} placeholder="127.0.0.1" />
                      </label>
                      <label>端口
                        <input value={form.port} onChange={(event) => patch({ port: event.target.value })} placeholder="6379" />
                      </label>
                    </div>
                    <div className="row">
                      <label>数据库
                        <input value={form.database} onChange={(event) => patch({ database: event.target.value })} placeholder="0" />
                      </label>
                      <label>密码
                        <input type="password" value={form.password} onChange={(event) => patch({ password: event.target.value })} />
                      </label>
                    </div>
                    <label>或直接填 URI
                      <input value={form.uri} onChange={(event) => patch({ uri: event.target.value })} placeholder="redis://127.0.0.1:6379/0" />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "MONGODB" ? (
                  <>
                    <div className="row">
                      <label>主机
                        <input value={form.host} onChange={(event) => patch({ host: event.target.value })} placeholder="127.0.0.1" />
                      </label>
                      <label>端口
                        <input value={form.port} onChange={(event) => patch({ port: event.target.value })} placeholder="27017" />
                      </label>
                    </div>
                    <label>数据库
                      <input value={form.database} onChange={(event) => patch({ database: event.target.value })} placeholder="your_db" />
                    </label>
                    <div className="row">
                      <label>用户名
                        <input value={form.username} onChange={(event) => patch({ username: event.target.value })} placeholder="只读账号，可空" />
                      </label>
                      <label>密码
                        <input type="password" value={form.password} onChange={(event) => patch({ password: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "可空"} />
                      </label>
                    </div>
                    <label>或直接填 URI
                      <input value={form.uri} onChange={(event) => patch({ uri: event.target.value })} placeholder="mongodb://127.0.0.1:27017/your_db" />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "ELASTICSEARCH" ? (
                  <>
                    <div className="row">
                      <label>主机
                        <input value={form.host} onChange={(event) => patch({ host: event.target.value })} placeholder="127.0.0.1" />
                      </label>
                      <label>端口
                        <input value={form.port} onChange={(event) => patch({ port: event.target.value })} placeholder="9200" />
                      </label>
                    </div>
                    <div className="row">
                      <label>用户名
                        <input value={form.username} onChange={(event) => patch({ username: event.target.value })} placeholder="可空" />
                      </label>
                      <label>密码
                        <input type="password" value={form.password} onChange={(event) => patch({ password: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "可空"} />
                      </label>
                    </div>
                    <label>或直接填 URI
                      <input value={form.uri} onChange={(event) => patch({ uri: event.target.value })} placeholder="http://127.0.0.1:9200" />
                    </label>
                  </>
                ) : null}

                {MONITORED_KINDS.has(form.toolKind) && canMonitor ? (
                  <fieldset className="monitoring-section">
                    <legend>监控（仅超级管理员可开启）</legend>
                    <label className="switch-row">
                      <span>开启监控</span>
                      <input
                        type="checkbox"
                        checked={form.monitoringEnabled}
                        onChange={(event) => patch({ monitoringEnabled: event.target.checked })}
                      />
                      <span className="slider" />
                    </label>
                    {form.monitoringEnabled ? (
                      <div className="monitoring-metrics">
                        <p className="hint">勾选要采集的指标（只读采集，默认每 60 秒一次）：</p>
                        {(KIND_MONITORING_METRICS[form.toolKind] ?? []).map((metric) => (
                          <label key={metric.key} className="checkbox-row">
                            <input
                              type="checkbox"
                              checked={form.monitoringMetrics.includes(metric.key)}
                              onChange={(event) => {
                                const next = event.target.checked
                                  ? [...form.monitoringMetrics, metric.key]
                                  : form.monitoringMetrics.filter((item) => item !== metric.key);
                                patch({ monitoringMetrics: next });
                              }}
                            />
                            <span>
                              <b>{metric.label}</b>
                              <small>{metric.desc}</small>
                            </span>
                          </label>
                        ))}
                      </div>
                    ) : null}
                  </fieldset>
                ) : null}

                {form.toolKind === "LOGS" ? (
                  <>
                    <div className="row">
                      <label>地域
                        <input value={form.regionId} onChange={(event) => patch({ regionId: event.target.value })} required placeholder="cn-hangzhou" />
                      </label>
                      <label>Project
                        <input value={form.project} onChange={(event) => patch({ project: event.target.value })} required placeholder="your-sls-project" />
                      </label>
                    </div>
                    <label>默认 Logstore（入口，可多个逗号分隔；不限制只能查这些）
                      <input value={form.logstore} onChange={(event) => patch({ logstore: event.target.value })} placeholder="app-log,gateway-log" />
                    </label>
                    <div className="row">
                      <label>AccessKeyId
                        <input value={form.accessKeyId} onChange={(event) => patch({ accessKeyId: event.target.value })} required placeholder="只读 RAM 子账号" />
                      </label>
                      <label>AccessKeySecret
                        <input type="password" value={form.accessKeySecret} onChange={(event) => patch({ accessKeySecret: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "只读密钥"} />
                      </label>
                    </div>
                    <label>Endpoint（可空，默认 region.log.aliyuncs.com）
                      <input value={form.endpoint} onChange={(event) => patch({ endpoint: event.target.value })} placeholder="cn-hangzhou.log.aliyuncs.com" />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "MSE" ? (
                  <>
                    <label>地域
                      <input value={form.regionId} onChange={(event) => patch({ regionId: event.target.value })} required placeholder="cn-hangzhou" />
                    </label>
                    <div className="row">
                      <label>AccessKeyId
                        <input value={form.accessKeyId} onChange={(event) => patch({ accessKeyId: event.target.value })} required placeholder="只读 RAM 子账号" />
                      </label>
                      <label>AccessKeySecret
                        <input type="password" value={form.accessKeySecret} onChange={(event) => patch({ accessKeySecret: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "只读密钥"} />
                      </label>
                    </div>
                    <label>网关 UniqueId（查路由/服务时必填）
                      <input value={form.gatewayUniqueId} onChange={(event) => patch({ gatewayUniqueId: event.target.value })} placeholder="gw-xxxxxxxx" />
                    </label>
                    <label>Nacos 实例 ID（查注册配置时必填）
                      <input value={form.nacosInstanceId} onChange={(event) => patch({ nacosInstanceId: event.target.value })} placeholder="mse-xxxxxxxx" />
                    </label>
                    <label>Endpoint（可空，默认 mse.region.aliyuncs.com）
                      <input value={form.endpoint} onChange={(event) => patch({ endpoint: event.target.value })} placeholder="mse.cn-hangzhou.aliyuncs.com" />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "ARMS" || form.toolKind === "LOAD_BALANCER" || form.toolKind === "CDN" ? (
                  <p className="hint">凭据来自环境变量 <code>ALIBABA_CLOUD_ACCESS_KEY_ID</code> / <code>ALIBABA_CLOUD_ACCESS_KEY_SECRET</code>，本卡只保存地域和允许访问的资源范围，不要填 AK。</p>
                ) : null}

                {form.toolKind === "ARMS" ? (
                  <>
                    <label>地域
                      <input value={form.regionId} onChange={(event) => patch({ regionId: event.target.value })} required placeholder="cn-hangzhou" />
                    </label>
                    <label>开放的应用名（可空，逗号分隔；空则该地域全部 TRACE 应用）
                      <input value={form.applications} onChange={(event) => patch({ applications: event.target.value })} placeholder="demo-app-order, demo-app-user" />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "LOAD_BALANCER" ? (
                  <>
                    <div className="row">
                      <label>地域
                        <input value={form.regionId} onChange={(event) => patch({ regionId: event.target.value })} required placeholder="cn-hangzhou" />
                      </label>
                      <label>类型
                        <select value={form.serviceType || "ALB"} onChange={(event) => patch({ serviceType: event.target.value })}>
                          <option value="ALB">ALB</option>
                          <option value="CLB">CLB</option>
                        </select>
                      </label>
                    </div>
                    <label>开放的实例 ID（可空，逗号分隔；空则该地域全部实例）
                      <input value={form.instanceIds} onChange={(event) => patch({ instanceIds: event.target.value })} placeholder="alb-xxxxxxxx 或 lb-xxxxxxxx" />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "CDN" ? (
                  <label>开放的加速域名（可空，逗号分隔；空则可列账号下域名）
                    <input value={form.domains} onChange={(event) => patch({ domains: event.target.value })} placeholder="cdn.example.com" />
                  </label>
                ) : null}

                {form.toolKind === "STARROCKS" ? (
                  <>
                    <div className="row">
                      <label>主机
                        <input value={form.host} onChange={(event) => patch({ host: event.target.value })} required placeholder="fe-xxx.starrocks.aliyuncs.com" />
                      </label>
                      <label>查询端口
                        <input value={form.port} onChange={(event) => patch({ port: event.target.value })} required placeholder="9030" />
                      </label>
                    </div>
                    <label>数据库
                      <input value={form.database} onChange={(event) => patch({ database: event.target.value })} required placeholder="your_db" />
                    </label>
                    <div className="row">
                      <label>用户名
                        <input value={form.username} onChange={(event) => patch({ username: event.target.value })} required placeholder="readonly" />
                      </label>
                      <label>密码
                        <input type="password" value={form.password} onChange={(event) => patch({ password: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "只读账号密码"} />
                      </label>
                    </div>
                    <label>连接参数
                      <input value={form.parameters} onChange={(event) => patch({ parameters: event.target.value })} />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "KAFKA" ? (
                  <>
                    <label>Bootstrap Servers
                      <input value={form.host} onChange={(event) => patch({ host: event.target.value })} required placeholder="broker1:9092,broker2:9092" />
                    </label>
                    <div className="row">
                      <label>安全协议
                        <select value={form.parameters || "PLAINTEXT"} onChange={(event) => patch({ parameters: event.target.value })}>
                          <option value="PLAINTEXT">PLAINTEXT</option>
                          <option value="SASL_PLAINTEXT">SASL_PLAINTEXT</option>
                          <option value="SASL_SSL">SASL_SSL</option>
                          <option value="SSL">SSL</option>
                        </select>
                      </label>
                      <label>SASL 机制
                        <select value={form.namespace || "PLAIN"} onChange={(event) => patch({ namespace: event.target.value })}>
                          <option value="PLAIN">PLAIN</option>
                          <option value="SCRAM-SHA-256">SCRAM-SHA-256</option>
                          <option value="SCRAM-SHA-512">SCRAM-SHA-512</option>
                        </select>
                      </label>
                    </div>
                    <div className="row">
                      <label>SASL 用户名
                        <input value={form.username} onChange={(event) => patch({ username: event.target.value })} placeholder="可空" />
                      </label>
                      <label>SASL 密码
                        <input type="password" value={form.password} onChange={(event) => patch({ password: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "可空"} />
                      </label>
                    </div>
                    <label>默认消费组（看积压时可空）
                      <input value={form.cluster} onChange={(event) => patch({ cluster: event.target.value })} placeholder="your-consumer-group" />
                    </label>
                  </>
                ) : null}

                {form.toolKind === "ROCKETMQ" ? (
                  <>
                    <label>NameServer
                      <input value={form.host} onChange={(event) => patch({ host: event.target.value })} required placeholder="namesrv.example.com:9876" />
                    </label>
                    <div className="row">
                      <label>AccessKey（ACL 可空）
                        <input value={form.accessKeyId} onChange={(event) => patch({ accessKeyId: event.target.value })} placeholder="只读账号" />
                      </label>
                      <label>SecretKey
                        <input type="password" value={form.accessKeySecret} onChange={(event) => patch({ accessKeySecret: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "可空"} />
                      </label>
                    </div>
                    <div className="row">
                      <label>Cluster（可空）
                        <input value={form.cluster} onChange={(event) => patch({ cluster: event.target.value })} placeholder="DefaultCluster" />
                      </label>
                      <label>Namespace（可空）
                        <input value={form.namespace} onChange={(event) => patch({ namespace: event.target.value })} />
                      </label>
                    </div>
                  </>
                ) : null}

                {form.toolKind === "K8S" ? (
                  <>
                    <label>Kuboard 地址
                      <input value={form.baseUrl} onChange={(event) => patch({ baseUrl: event.target.value })} required placeholder="http://kuboard.example.com" />
                    </label>
                    <p className="notice">填站点根地址，例如 <code>http://kuboard-test.netshort.net</code>，不要带 <code>/kubernetes</code>。集群名必须是 Kuboard 里的集群名，不是本页的 MCP 连接名。点「测试连接」会自动拉取现网集群，勾选要开放的即可。</p>
                    <div className="row">
                      <label>用户名
                        <input value={form.username} onChange={(event) => patch({ username: event.target.value })} required placeholder="只读账号，不要用 admin" />
                      </label>
                      <label>密码
                        <input type="password" value={form.password} onChange={(event) => patch({ password: event.target.value })} placeholder={passwordSet ? "已保存，不回显" : "Kuboard 密码"} />
                      </label>
                    </div>
                    <label>
                      <span>开放集群</span>
                      {visibleK8sClusters.length === 0 ? (
                        <p className="notice">先点「测试连接」从 Kuboard 拉取，或在下方手动添加。</p>
                      ) : (
                        <div className="chip-row">
                          {visibleK8sClusters.map((name) => (
                            <button
                              key={name}
                              type="button"
                              className={`chip-btn${form.clusters.includes(name) ? " active" : ""}`}
                              aria-pressed={form.clusters.includes(name)}
                              onClick={() => toggleK8sCluster(name)}
                            >
                              {name}
                            </button>
                          ))}
                        </div>
                      )}
                    </label>
                    <div className="row">
                      <label>手动添加集群名
                        <input
                          value={k8sDraft}
                          onChange={(event) => setK8sDraft(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              event.preventDefault();
                              addK8sDraftCluster();
                            }
                          }}
                          placeholder="Kuboard 里的集群名"
                        />
                      </label>
                      <div className="form-actions" style={{ alignSelf: "end" }}>
                        <button className="btn secondary" type="button" onClick={addK8sDraftCluster} disabled={!k8sDraft.trim()}>
                          添加
                        </button>
                      </div>
                    </div>
                    <div className="row">
                      <label>默认集群
                        <select
                          value={form.cluster}
                          onChange={(event) => patch({ cluster: event.target.value })}
                          required={form.clusters.length > 0}
                        >
                          {form.clusters.length === 0 ? <option value="">请先勾选开放集群</option> : null}
                          {form.clusters.map((name) => (
                            <option key={name} value={name}>{name}</option>
                          ))}
                        </select>
                      </label>
                      <label>默认命名空间（可空）
                        <input value={form.namespace} onChange={(event) => patch({ namespace: event.target.value })} placeholder="空则工具参数必填" />
                      </label>
                    </div>
                    <label className="enable-row">
                      <span>跳过 TLS 校验</span>
                      <label className="switch">
                        <input
                          type="checkbox"
                          checked={form.insecureSkipTls}
                          onChange={(event) => patch({ insecureSkipTls: event.target.checked })}
                        />
                        <span className="slider" />
                      </label>
                      <small>仅自签证书时勾选</small>
                    </label>
                  </>
                ) : null}

                {form.toolKind === "CODE" ? (
                  <label>本地目录
                    <input value={form.rootPath} onChange={(event) => patch({ rootPath: event.target.value })} required placeholder="/Users/you/IdeaProjects/app" />
                  </label>
                ) : null}

                {form.toolKind === "MCP_SERVER" ? (
                  <p className="notice">开源 MCP 只保存启动命令，当前排查不会自动拉起外部进程。</p>
                ) : null}

                <div className="json-preview">
                  <div className="group-title" style={{ marginTop: 0 }}>{selectedId ? "已保存 JSON（密码已隐藏）" : "将保存为 JSON"}</div>
                  <pre>{maskedConfigurationJson(form)}</pre>
                </div>

                <p className="notice warn">
                  {form.toolKind === "MCP_SERVER"
                    ? "开源 MCP 只保存命令、包名和来源地址，当前排查不会自动拉起外部进程。"
                    : selectedId
                      ? "密码不会回显。留空则不改已保存密码。测试连接会用上面表单里的地址/集群名，密码留空则用已保存密码。K8s 点测试连接会拉取 Kuboard 现网集群，勾选后保存。"
                      : "只允许只读账号。保存时 AES-GCM 加密，页面不会回显密码。"}
                </p>
                {loadingDetail ? <p className="notice">正在加载已保存配置…</p> : null}
                {selected ? (
                  <label className="enable-row">
                    <span>启用此 MCP</span>
                    <label className="switch">
                      <input
                        type="checkbox"
                        checked={selected.enabled}
                        onChange={(event) => void onToggle(selected, event.target.checked)}
                      />
                      <span className="slider" />
                    </label>
                    <small>{selected.enabled ? "排查时会调用" : "停用后排查不会调用"}</small>
                  </label>
                ) : null}

                <div className="form-actions">
                  {canWrite ? (
                  <button className="btn secondary" type="button" disabled={testing || loadingDetail} onClick={() => void onTest()}>
                    {testing ? "测试中…" : "测试连接"}
                  </button>
                  ) : null}
                  {canWrite ? (
                  <button className="btn" disabled={saving || loadingDetail}>
                    {saving ? "保存中…" : selectedId ? "保存修改" : "保存 MCP"}
                  </button>
                  ) : (
                    <p className="notice">当前账号仅可查看工具配置，修改需管理员权限。</p>
                  )}
                  {selectedId && canWrite ? (
                    <>
                      <button className="btn secondary" type="button" onClick={() => openAddCatalog("mcp")}>新增 MCP</button>
                    </>
                  ) : null}
                  {selectedId && canDelete ? (
                      <button className="btn danger" type="button" onClick={() => selected && setPendingDelete({ type: "mcp", item: selected })}>
                        <IconTrash size={13} />
                        删除
                      </button>
                  ) : null}
                </div>
                {testResult ? (
                  <p className={testResult.ok ? "notice ok" : "error"}>{testResult.message}</p>
                ) : null}
                {error ? <p className="error">{error}</p> : null}
              </form>
            </section>
          ) : null}

          {composerVisible && composerKind !== "mcp" ? (
            <section className="card composer-panel" aria-label={selectedId ? `修改 ${assetKindLabel(assetForm.kind)}` : `新增 ${assetKindLabel(assetForm.kind)}`}>
              <div className="card-head">
                <div className="composer-title">
                  <span className="tag">{assetKindLabel(assetForm.kind)}</span>
                  <h3>
                    {selectedId
                      ? "修改正文"
                      : assetForm.source === "OPEN_SOURCE" && assetForm.kind === "SKILL"
                        ? "导入开源 Skill"
                        : assetForm.source === "OPEN_SOURCE"
                          ? "从开源目录新增"
                          : "新增正文"}
                  </h3>
                </div>
                <button className="btn ghost" type="button" onClick={closeComposer}>收起</button>
              </div>
              <form
                className="form navicat"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (!selectedId && assetForm.source === "OPEN_SOURCE" && assetForm.kind === "SKILL" && !assetForm.content.trim()) {
                    return;
                  }
                  void onSaveAsset();
                }}
              >
                {!selectedId && assetForm.kind === "SKILL" && assetForm.source === "OPEN_SOURCE" ? (
                  <OpenSourceSkillImport
                    form={assetForm}
                    templates={openSkillTemplates}
                    importing={importing}
                    saving={saving}
                    error={error}
                    onPatch={patchAsset}
                    onPickTemplate={(template) => patchAsset(applyTemplate(assetForm, template))}
                    onImport={onImportAsset}
                    onSwitchCustom={() => {
                      setError("");
                      setAssetForm(emptyAssetForm("SKILL"));
                    }}
                  />
                ) : (
                  <>
                <OpenSourceFields
                  kind={assetForm.kind}
                  source={assetForm.source === "OPEN_SOURCE" ? "open_source" : "custom"}
                  origin={assetForm.origin}
                  catalogId={assetForm.catalogId}
                  packageName={assetForm.packageName}
                  sourceUrl={assetForm.sourceUrl}
                  gitRef={assetForm.gitRef}
                  templates={assetForm.kind === "SKILL" ? skillTemplates : ruleTemplates}
                  importing={importing}
                  onSourceChange={(source) => {
                    if (!selectedId) {
                      setError("");
                      setAssetForm(source === "open_source" ? emptyOpenSourceAssetForm(assetForm.kind) : emptyAssetForm(assetForm.kind));
                      return;
                    }
                    patchAsset({
                      source: source === "open_source" ? "OPEN_SOURCE" : "CUSTOM",
                      origin: source === "open_source" ? (assetForm.origin || "github") : "local",
                    });
                  }}
                  onPick={(template) => patchAsset(applyTemplate(assetForm, template))}
                  onChange={(partial) => patchAsset(partial)}
                  onImport={(url) => void onImportAsset(url)}
                />
                <div className="row">
                  <label>名称
                    <input value={assetForm.name} onChange={(event) => patchAsset({ name: event.target.value })} required placeholder={assetForm.kind === "SKILL" ? "HTTP 5xx 排查" : "只读边界"} />
                  </label>
                  <label>标识
                    <input value={assetForm.slug} onChange={(event) => patchAsset({ slug: event.target.value })} placeholder={assetForm.kind === "SKILL" ? "http-5xx，可留空" : "readonly，可留空"} />
                  </label>
                </div>
                <div className="row">
                  <label>
                    <span>类型</span>
                    <select value={assetForm.route} onChange={(event) => patchAsset({ route: event.target.value as RuleUsageId })}>
                      {RULE_USAGES.map((item) => (
                        <option key={item.id} value={item.id}>{item.label}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span>场景</span>
                    <SearchSelect
                      value={assetForm.scene}
                      options={sceneOptions}
                      onChange={(value) => patchAsset({ scene: value })}
                      allowCustom
                      placeholder={assetForm.route === "other" ? "手动填写，如 文风" : "可选，不填则该类型都会装"}
                      searchPlaceholder="搜索或输入场景"
                      emptyLabel="没有匹配项，回车使用输入的场景"
                    />
                  </label>
                </div>
                <p className="notice">
                  对话排查只自动装「对话 / 排查」，代码审查只自动装「代码审查」。填了场景则只在对应方向装载，不填则该类型都会装。「其他」不会自动装，需用 / 点名。
                </p>
                <ToolMetaFields
                  summary={assetForm.summary}
                  tags={assetForm.tags}
                  onChange={(partial) => patchAsset(partial)}
                />
                {assetForm.kind === "SKILL" ? (
                  <SkillFileEditor
                    files={assetForm.files}
                    onChange={(files) => patchAsset({
                      files,
                      content: files.find((file) => file.path.toLowerCase() === "skill.md")?.content ?? "",
                    })}
                  />
                ) : (
                  <label>正文
                    <textarea
                      value={assetForm.content}
                      onChange={(event) => patchAsset({ content: event.target.value })}
                      required
                      rows={10}
                      placeholder="Markdown，会注入排查上下文。不要写密钥。"
                    />
                  </label>
                )}
                {selectedAsset ? (
                  <label className="enable-row">
                    <span>启用此 {assetKindLabel(assetForm.kind)}</span>
                    <label className="switch">
                      <input
                        type="checkbox"
                        checked={assetForm.enabled}
                        disabled={!canWrite}
                              onChange={(event) => void onToggleAsset(selectedAsset, event.target.checked)}
                      />
                      <span className="slider" />
                    </label>
                    <small>
                      {assetForm.enabled
                        ? (assetForm.route === "other"
                          ? "不会自动装，需在对话里用 / 点名"
                          : (assetForm.scene
                            ? `会在「${ruleUsageLabel(assetForm.route)} / ${assetForm.scene}」时装载，无场景的同类也会带上`
                            : "会按类型在对话排查或代码审查时装载"))
                        : "停用后不会注入模型"}
                    </small>
                  </label>
                ) : null}
                <div className="form-actions">
                  {canWrite ? (
                  <button className="btn" disabled={saving}>
                    {saving ? "保存中…" : selectedId ? "保存修改" : `保存 ${assetKindLabel(assetForm.kind)}`}
                  </button>
                  ) : (
                    <p className="notice">当前账号仅可查看，修改需管理员权限。</p>
                  )}
                  {selectedId && selectedAsset && canDelete ? (
                    <button className="btn danger" type="button" onClick={() => setPendingDelete({ type: "asset", item: selectedAsset })}>
                      <IconTrash size={13} />
                      删除
                    </button>
                  ) : null}
                </div>
                {error ? <p className="error">{error}</p> : null}
                  </>
                )}
              </form>
            </section>
          ) : null}
        </div>
      </div>
      {pendingDelete ? (
        <ConfirmDialog
          title={pendingDelete.type === "mcp" ? "删除 MCP" : `删除 ${assetKindLabel(pendingDelete.item.kind)}`}
          message={
            pendingDelete.type === "mcp"
              ? `将删除连接「${pendingDelete.item.profile} / ${mcpKindLabel(pendingDelete.item.toolKind)}」。排查时不再使用这条配置。`
              : `将删除「${pendingDelete.item.name}」。排查时不再装载这条配置。`
          }
          busy={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}
      {metricsBinding ? (
        <BindingMetricsPanel
          projectId={project.id}
          binding={metricsBinding}
          onClose={() => setMetricsBinding(null)}
        />
      ) : null}
    </>
  );
}
