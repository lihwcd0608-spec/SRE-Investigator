import { parseTags } from "../catalog/toolMeta";
import type { BindingDetail, Environment, ToolKind } from "../types";

export type ToolCategory = "all" | "skill" | "rule" | "mcp";

export const MCP_KINDS: ToolKind[] = ["MYSQL", "REDIS", "MONGODB", "ELASTICSEARCH", "LOGS", "MSE", "ARMS", "LOAD_BALANCER", "CDN", "K8S", "STARROCKS", "KAFKA", "ROCKETMQ", "CODE"];

export const MCP_KIND_LABELS: Record<ToolKind, string> = {
  MYSQL: "MySQL",
  REDIS: "Redis",
  MONGODB: "MongoDB",
  ELASTICSEARCH: "Elasticsearch",
  LOGS: "SLS 日志",
  MSE: "MSE",
  ARMS: "ARMS 调用链",
  LOAD_BALANCER: "负载均衡",
  CDN: "CDN",
  K8S: "K8s",
  STARROCKS: "StarRocks",
  KAFKA: "Kafka",
  ROCKETMQ: "RocketMQ",
  CODE: "代码目录",
  MCP_SERVER: "开源 MCP",
};

export function mcpKindLabel(kind: string): string {
  return MCP_KIND_LABELS[kind as ToolKind] ?? kind;
}

export interface McpForm {
  environment: Environment;
  toolKind: ToolKind;
  profile: string;
  source: "custom" | "open_source";
  origin: string;
  catalogId: string;
  packageName: string;
  sourceUrl: string;
  gitRef: string;
  command: string;
  args: string;
  host: string;
  port: string;
  database: string;
  username: string;
  password: string;
  parameters: string;
  uri: string;
  rootPath: string;
  regionId: string;
  project: string;
  logstore: string;
  accessKeyId: string;
  accessKeySecret: string;
  endpoint: string;
  gatewayUniqueId: string;
  nacosInstanceId: string;
  baseUrl: string;
  cluster: string;
  clusters: string[];
  namespace: string;
  insecureSkipTls: boolean;
  serviceType: string;
  applications: string;
  instanceIds: string;
  domains: string;
  summary: string;
  tags: string[];
  monitoringEnabled: boolean;
  monitoringMetrics: string[];
}

export interface ConnectionTestResult {
  ok: boolean;
  message: string;
  clusters?: string[];
}

function defaultPort(toolKind: ToolKind): string {
  if (toolKind === "REDIS") return "6379";
  if (toolKind === "MONGODB") return "27017";
  if (toolKind === "ELASTICSEARCH") return "9200";
  if (toolKind === "STARROCKS") return "9030";
  if (toolKind === "KAFKA") return "9092";
  if (toolKind === "ROCKETMQ") return "9876";
  return "3306";
}

export function emptyMcpForm(environment: Environment): McpForm {
  return {
    environment,
    toolKind: "MYSQL",
    profile: "test",
    source: "custom",
    origin: "local",
    catalogId: "",
    packageName: "",
    sourceUrl: "",
    gitRef: "",
    command: "",
    args: "",
    host: "127.0.0.1",
    port: "3306",
    database: "",
    username: "readonly",
    password: "",
    parameters: "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
    uri: "",
    rootPath: "",
    regionId: "cn-hangzhou",
    project: "",
    logstore: "",
    accessKeyId: "",
    accessKeySecret: "",
    endpoint: "",
    gatewayUniqueId: "",
    nacosInstanceId: "",
    baseUrl: "",
    cluster: "",
    clusters: [],
    namespace: "",
    insecureSkipTls: false,
    serviceType: "ALB",
    applications: "",
    instanceIds: "",
    domains: "",
    summary: "",
    tags: [],
    monitoringEnabled: false,
    monitoringMetrics: [],
  };
}

export function openSourceFields(form: McpForm) {
  return {
    source: form.source,
    origin: form.origin,
    catalogId: form.catalogId,
    packageName: form.packageName,
    sourceUrl: form.sourceUrl,
    gitRef: form.gitRef,
    command: form.command,
    args: form.args,
  };
}

export function buildMcpConfiguration(form: McpForm): Record<string, unknown> {
  if (form.toolKind === "MCP_SERVER") {
    return {
      kind: "mcp_server",
      ...openSourceFields(form),
    };
  }
  if (form.toolKind === "MYSQL") {
    return {
      kind: "mysql",
      ...openSourceFields(form),
      host: form.host.trim(),
      port: Number(form.port) || 3306,
      database: form.database.trim(),
      username: form.username.trim(),
      password: form.password,
      parameters: form.parameters.trim(),
    };
  }
  if (form.toolKind === "REDIS") {
    return {
      kind: "redis",
      ...openSourceFields(form),
      host: form.host.trim(),
      port: Number(form.port) || 6379,
      database: form.database.trim() || "0",
      password: form.password,
      uri: form.uri.trim(),
    };
  }
  if (form.toolKind === "MONGODB") {
    return {
      kind: "mongodb",
      ...openSourceFields(form),
      host: form.host.trim(),
      port: Number(form.port) || 27017,
      database: form.database.trim(),
      username: form.username.trim(),
      password: form.password,
      uri: form.uri.trim(),
    };
  }
  if (form.toolKind === "ELASTICSEARCH") {
    return {
      kind: "elasticsearch",
      ...openSourceFields(form),
      host: form.host.trim(),
      port: Number(form.port) || 9200,
      username: form.username.trim(),
      password: form.password,
      uri: form.uri.trim(),
    };
  }
  if (form.toolKind === "CODE") {
    return {
      kind: "code",
      ...openSourceFields(form),
      rootPath: form.rootPath.trim(),
    };
  }
  if (form.toolKind === "LOGS") {
    return {
      kind: "logs",
      ...openSourceFields(form),
      regionId: form.regionId.trim(),
      project: form.project.trim(),
      logstore: form.logstore.trim(),
      accessKeyId: form.accessKeyId.trim(),
      accessKeySecret: form.accessKeySecret,
      endpoint: form.endpoint.trim(),
    };
  }
  if (form.toolKind === "MSE") {
    return {
      kind: "mse",
      ...openSourceFields(form),
      regionId: form.regionId.trim(),
      accessKeyId: form.accessKeyId.trim(),
      accessKeySecret: form.accessKeySecret,
      endpoint: form.endpoint.trim(),
      gatewayUniqueId: form.gatewayUniqueId.trim(),
      nacosInstanceId: form.nacosInstanceId.trim(),
    };
  }
  if (form.toolKind === "ARMS") {
    return {
      kind: "arms",
      ...openSourceFields(form),
      regionId: form.regionId.trim(),
      applications: parseClusterNames(form.applications),
    };
  }
  if (form.toolKind === "LOAD_BALANCER") {
    return {
      kind: "load_balancer",
      ...openSourceFields(form),
      regionId: form.regionId.trim(),
      serviceType: form.serviceType.trim() || "ALB",
      instanceIds: parseClusterNames(form.instanceIds),
    };
  }
  if (form.toolKind === "CDN") {
    return {
      kind: "cdn",
      ...openSourceFields(form),
      domains: parseClusterNames(form.domains),
    };
  }
  if (form.toolKind === "K8S") {
    const clusters = parseClusterNames(form.clusters, form.cluster);
    return {
      kind: "kuboard",
      ...openSourceFields(form),
      baseUrl: form.baseUrl.trim(),
      username: form.username.trim(),
      password: form.password,
      cluster: clusters[0] ?? form.cluster.trim(),
      clusters,
      namespace: form.namespace.trim(),
      insecureSkipTls: form.insecureSkipTls,
    };
  }
  if (form.toolKind === "STARROCKS") {
    return {
      kind: "starrocks",
      ...openSourceFields(form),
      host: form.host.trim(),
      port: Number(form.port) || 9030,
      database: form.database.trim(),
      username: form.username.trim(),
      password: form.password,
      parameters: form.parameters.trim(),
    };
  }
  if (form.toolKind === "KAFKA") {
    return {
      kind: "kafka",
      ...openSourceFields(form),
      host: form.host.trim(),
      bootstrapServers: form.host.trim(),
      username: form.username.trim(),
      password: form.password,
      parameters: form.parameters.trim() || "PLAINTEXT",
      securityProtocol: form.parameters.trim() || "PLAINTEXT",
      namespace: form.namespace.trim() || "PLAIN",
      saslMechanism: form.namespace.trim() || "PLAIN",
      cluster: form.cluster.trim(),
      consumerGroup: form.cluster.trim(),
    };
  }
  if (form.toolKind === "ROCKETMQ") {
    return {
      kind: "rocketmq",
      ...openSourceFields(form),
      host: form.host.trim() || form.endpoint.trim(),
      namesrvAddr: form.host.trim() || form.endpoint.trim(),
      endpoint: form.endpoint.trim(),
      accessKeyId: form.accessKeyId.trim() || form.username.trim(),
      accessKeySecret: form.accessKeySecret || form.password,
      cluster: form.cluster.trim(),
      namespace: form.namespace.trim(),
    };
  }
  return { kind: "unknown", ...openSourceFields(form) };
}

export function maskedConfigurationJson(form: McpForm): string {
  const payload = buildMcpConfiguration(form);
  if (typeof payload.password === "string" && payload.password) {
    payload.password = "••••••";
  }
  if (typeof payload.accessKeySecret === "string" && payload.accessKeySecret) {
    payload.accessKeySecret = "••••••";
  }
  return JSON.stringify(payload, null, 2);
}

export function testPayload(form: McpForm) {
  return {
    toolKind: form.toolKind,
    host: form.host.trim() || null,
    port: Number(form.port) || null,
    database: form.database.trim() || null,
    username: form.username.trim() || null,
    password: form.password || null,
    parameters: form.parameters.trim() || null,
    uri: form.uri.trim() || null,
    rootPath: form.rootPath.trim() || null,
    regionId: form.regionId.trim() || null,
    project: form.project.trim() || null,
    logstore: form.logstore.trim() || null,
    accessKeyId: form.accessKeyId.trim() || null,
    accessKeySecret: form.accessKeySecret || null,
    endpoint: form.endpoint.trim() || null,
    gatewayUniqueId: form.gatewayUniqueId.trim() || null,
    nacosInstanceId: form.nacosInstanceId.trim() || null,
    baseUrl: form.baseUrl.trim() || null,
    cluster: form.cluster.trim() || null,
    namespace: form.namespace.trim() || null,
    insecureSkipTls: form.insecureSkipTls || null,
    serviceType: form.serviceType.trim() || null,
    applications: form.applications.trim() || null,
    instanceIds: form.instanceIds.trim() || null,
    domains: form.domains.trim() || null,
  };
}

export function formFromDetail(detail: BindingDetail): McpForm {
  const config = detail.configuration ?? {};
  const text = (key: string) => String(config[key] ?? "");
  const clusterText = text("cluster") || text("clusterName");
  const clusters = detail.toolKind === "K8S" ? parseClusterNames(config.clusters, clusterText) : [];
  return {
    environment: detail.environment,
    toolKind: detail.toolKind,
    profile: detail.profile,
    source: text("source") === "open_source" ? "open_source" : "custom",
    origin: text("origin") || "local",
    catalogId: text("catalogId"),
    packageName: text("packageName"),
    sourceUrl: text("sourceUrl"),
    gitRef: text("gitRef"),
    command: text("command"),
    args: text("args"),
    host: text("bootstrapServers") || text("namesrvAddr") || text("host") || "127.0.0.1",
    port: text("port") || defaultPort(detail.toolKind),
    database: text("database"),
    username: text("username"),
    password: "",
    parameters: text("parameters"),
    uri: text("uri"),
    rootPath: text("rootPath"),
    regionId: text("regionId") || "cn-hangzhou",
    project: text("project"),
    logstore: text("logstore") || text("logstores"),
    accessKeyId: text("accessKeyId"),
    accessKeySecret: "",
    endpoint: text("endpoint"),
    gatewayUniqueId: text("gatewayUniqueId"),
    nacosInstanceId: text("nacosInstanceId"),
    baseUrl: text("baseUrl") || text("uri"),
    cluster: detail.toolKind === "K8S" ? (clusters[0] ?? "") : clusterText,
    clusters,
    namespace: text("namespace") || text("defaultNamespace"),
    insecureSkipTls: config.insecureSkipTls === true || String(config.insecureSkipTls ?? "") === "true",
    serviceType: text("serviceType") || text("loadBalancerType") || "ALB",
    applications: parseClusterNames(config.applications ?? config.applicationNames).join(", "),
    instanceIds: parseClusterNames(config.instanceIds ?? config.resources).join(", "),
    domains: parseClusterNames(config.domains ?? config.domainNames).join(", "),
    summary: detail.summary || "",
    tags: parseTags(detail.tags),
    monitoringEnabled: detail.monitoringEnabled === true,
    monitoringMetrics: parseMetricList(detail.monitoringMetrics),
  };
}

function parseMetricList(raw: string | null | undefined): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
  } catch {
    return [];
  }
}

export function applyKindDefaults(form: McpForm, toolKind: ToolKind): McpForm {
  if (toolKind === "MYSQL") {
    return { ...form, toolKind, port: form.port && form.toolKind === "MYSQL" ? form.port : "3306" };
  }
  if (toolKind === "REDIS") {
    return { ...form, toolKind, port: "6379", database: form.database || "0" };
  }
  if (toolKind === "MONGODB") {
    return { ...form, toolKind, port: "27017" };
  }
  if (toolKind === "ELASTICSEARCH") {
    return { ...form, toolKind, port: "9200" };
  }
  if (toolKind === "STARROCKS") {
    return {
      ...form,
      toolKind,
      port: form.port && form.toolKind === "STARROCKS" ? form.port : "9030",
      parameters: form.parameters || "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
    };
  }
  if (toolKind === "KAFKA") {
    return { ...form, toolKind, port: "9092", parameters: form.parameters || "PLAINTEXT", namespace: form.namespace || "PLAIN" };
  }
  if (toolKind === "ROCKETMQ") {
    return { ...form, toolKind, port: "9876" };
  }
  if (toolKind === "ARMS") {
    return { ...form, toolKind, regionId: form.regionId || "cn-hangzhou" };
  }
  if (toolKind === "LOAD_BALANCER") {
    return { ...form, toolKind, regionId: form.regionId || "cn-hangzhou", serviceType: form.serviceType || "ALB" };
  }
  if (toolKind === "CDN") {
    return { ...form, toolKind };
  }
  if (toolKind === "MCP_SERVER") {
    return { ...form, toolKind, source: "open_source", origin: form.origin || "npm" };
  }
  return { ...form, toolKind };
}

export function parseClusterNames(raw: unknown, fallback = ""): string[] {
  const names: string[] = [];
  const push = (value: string) => {
    const name = value.trim();
    if (name && !names.includes(name)) names.push(name);
  };
  const walk = (value: unknown) => {
    if (value == null || value === "") return;
    if (Array.isArray(value)) {
      value.forEach(walk);
      return;
    }
    String(value).split(/[,，]+/).forEach(push);
  };
  walk(raw);
  const def = fallback.trim();
  if (def) {
    const index = names.indexOf(def);
    if (index >= 0) names.splice(index, 1);
    names.unshift(def);
  }
  return names;
}

export function applyDiscoveredK8sClusters(form: McpForm, discovered: string[]): McpForm {
  const live = parseClusterNames(discovered);
  if (live.length === 0) return form;
  const selected = parseClusterNames(form.clusters, form.cluster);
  if (selected.length > 0) {
    return form;
  }
  return { ...form, clusters: live, cluster: live[0] ?? "" };
}
