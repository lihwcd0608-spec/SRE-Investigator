export type Environment = "test" | "pre" | "prod";
export type ToolKind = "MYSQL" | "REDIS" | "MONGODB" | "ELASTICSEARCH" | "LOGS" | "MSE" | "ARMS" | "LOAD_BALANCER" | "CDN" | "K8S" | "STARROCKS" | "KAFKA" | "ROCKETMQ" | "CODE" | "MCP_SERVER";
export type CatalogKind = "MCP" | "SKILL" | "RULE" | "AGENT";
export type CatalogSource = "CUSTOM" | "OPEN_SOURCE" | "BUILTIN";
export type RunStatus = "RUNNING" | "NEEDS_INPUT" | "COMPLETED" | "FAILED";
export type ConversationModeId = "ASK" | "INVESTIGATE" | "REVIEW" | "CODE";
export type RepoRole = "admin" | "app" | "other";
export type ScheduleKind = "AT" | "EVERY" | "CRON";
export type PayloadKind = "AGENT_TURN" | "K8S_INSPECT";
export type SessionTarget = "ISOLATED" | "THREAD";

export interface Project {
  id: string;
  name: string;
  slug: string;
  topology: string;
  createdAt: string;
}

export interface Repository {
  id: string;
  projectId: string;
  name: string;
  role: string;
  rootPath: string;
  gitUrl?: string | null;
  gitRef?: string | null;
  createdAt: string;
}

export interface Binding {
  id: string;
  projectId: string;
  environment: Environment;
  toolKind: ToolKind;
  profile: string;
  enabled: boolean;
  configured?: boolean;
  summary?: string | null;
  tags?: string | null;
  monitoringEnabled?: boolean;
  monitoringMetrics?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MetricPoint {
  key: string;
  label: string;
  value: string;
  unit: string;
}

export interface MetricSnapshot {
  points: MetricPoint[];
}

export interface MetricHistoryPoint {
  value: string;
  collectedAt: string;
}

export interface SlowQueryRow {
  sql: string;
  executedAt: string | null;
  user: string | null;
  seconds: number;
}

export interface SlowQueryPage {
  rows: SlowQueryRow[];
  total: number;
  note: string | null;
}

export interface BindingDetail extends Binding {
  passwordSet: boolean;
  configuration: Record<string, unknown>;
}

export interface OpenSourceSkillSearchHit {
  id: string;
  name: string;
  registry: string;
  origin: string;
  summary: string;
  sourceUrl: string;
  gitRef: string;
  packageName: string;
  installs?: number | null;
}

export interface OpenSourceSkillSearchResponse {
  query: string;
  hits: OpenSourceSkillSearchHit[];
  warnings?: string[];
}

export interface CatalogTemplate {
  id: string;
  kind: CatalogKind;
  source: CatalogSource;
  name: string;
  description: string;
  origin: string;
  packageName?: string | null;
  sourceUrl?: string | null;
  gitRef?: string | null;
  command?: string | null;
  args?: string | null;
  route?: string | null;
  toolKind?: string | null;
  content?: string | null;
}

export interface CatalogAsset {
  id: string;
  projectId: string;
  kind: CatalogKind;
  source: CatalogSource;
  slug: string;
  name: string;
  route?: string | null;
  enabled: boolean;
  origin: string;
  catalogId?: string | null;
  packageName?: string | null;
  sourceUrl?: string | null;
  gitRef?: string | null;
  command?: string | null;
  args?: string | null;
  content?: string | null;
  summary?: string | null;
  tags?: string | null;
  files?: CatalogAssetFile[];
  createdAt: string;
  updatedAt: string;
}

export interface CatalogAssetFile {
  path: string;
  content: string;
}

export interface InvestigationRun {
  id: string;
  projectId: string;
  environment: Environment;
  mode?: ConversationModeId;
  question: string;
  status: RunStatus;
  report: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationSummary {
  id: string;
  projectId: string;
  environment: Environment;
  mode?: ConversationModeId;
  title: string | null;
  createdAt: string;
  updatedAt: string;
  userTurns: number;
  maxTurns: number;
}

export interface ConversationAttachmentPayload {
  name: string;
  mediaType: string;
  text?: string;
  dataBase64?: string;
}

export interface ConversationMessage {
  id: string;
  conversationId: string;
  role: "user" | "assistant";
  content: string;
  runId?: string | null;
  createdAt: string;
  attachments?: ConversationAttachmentPayload[];
}

export interface ConversationDetail extends ConversationSummary {
  memorySummary?: string | null;
  messages: ConversationMessage[];
}

export interface GitBranch {
  name: string;
  fullRef: string;
  remote: boolean;
}

export interface GitFileChange {
  status: string;
  path: string;
  sourceCode?: boolean;
}

export interface GitCompareResult {
  rootPath: string;
  baseRef: string;
  headRef: string;
  baseSha: string;
  headSha: string;
  stat: string;
  files: GitFileChange[];
  unifiedDiff: string;
  truncated: boolean;
  fetchedRemote?: boolean;
  commonAncestor?: boolean;
}

export interface GitFileDiff {
  path: string;
  unifiedDiff: string;
  truncated: boolean;
}

export interface ConversationModeDescriptor {
  id: ConversationModeId;
  label: string;
  description: string;
  enabled: boolean;
}

export interface InvestigationRoute {
  id: string;
  label: string;
  description: string;
  fallback?: boolean;
}

export interface AgentCapabilities {
  maxToolCalls: number;
  maxUserTurns: number;
  modelAvailable?: boolean;
}

export interface AgentBudget {
  maxUserTurns: number;
  maxToolCalls: number;
  defaultUserTurns: number;
  defaultToolCalls: number;
}

export interface FeishuChatBinding {
  id: string;
  projectId: string;
  chatId: string;
  chatName: string;
  environment: Environment;
  environments?: Environment[];
  defaultRoute?: string | null;
  defaultRepositoryId?: string | null;
  lastConversationId?: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Health {
  status: string;
  runtime: string;
}

export interface Me {
  id: string;
  username: string;
  displayName: string;
  email: string | null;
  roles: string[];
  menus: string[];
  permissions: string[];
  projectIds: string[];
  allProjects: boolean;
}

export interface AdminUser {
  id: string;
  username: string;
  displayName: string;
  email: string | null;
  status: "ENABLED" | "DISABLED" | string;
  createdAt: string;
  roleIds: string[];
  roleCodes: string[];
  projectIds: string[];
}

export interface AdminRole {
  id: string;
  code: string;
  name: string;
  builtin: boolean;
  description: string | null;
  permissionCodes: string[];
}

export interface MenuPermission {
  id: string;
  code: string;
  name: string;
  type: string;
  menuPath: string | null;
  sortOrder: number;
}

export interface AutomationJob {
  id: string;
  projectId: string;
  name: string;
  environment: Environment;
  enabled: boolean;
  scheduleKind: ScheduleKind;
  scheduleExpr: string;
  timezone: string;
  payloadKind: PayloadKind;
  sessionTarget: SessionTarget;
  prompt?: string | null;
  route?: string | null;
  conversationId?: string | null;
  deleteAfterRun: boolean;
  timeoutSeconds: number;
  lastRunAt?: string | null;
  nextRunAt?: string | null;
  lastStatus?: string | null;
  consecutiveErrors: number;
  running: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AutomationExecution {
  id: string;
  jobId: string;
  runId?: string | null;
  conversationId?: string | null;
  triggerType: string;
  status: string;
  summary?: string | null;
  errorMessage?: string | null;
  startedAt: string;
  finishedAt?: string | null;
}

export interface AutomationExecutionMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  runId?: string | null;
  createdAt: string;
}

export interface AutomationExecutionDetail {
  job: AutomationJob;
  execution: AutomationExecution;
  conversationId?: string | null;
  messages: AutomationExecutionMessage[];
}

export interface UsageRow {
  userId?: string | null;
  username?: string | null;
  roleCode?: string | null;
  provider: string;
  model: string;
  callCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  tokenIncomplete: boolean;
}

export interface UsageTotals {
  callCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  tokenIncomplete: boolean;
}

export interface UsageReport {
  from: string;
  to: string;
  byUser: UsageRow[];
  byRole: UsageRow[];
  byModel: UsageRow[];
  totals: UsageTotals;
}

export function hasMenu(me: Me | null | undefined, code: string): boolean {
  if (!me) return false;
  return me.roles.includes("SUPER_ADMIN") || me.menus.includes(code);
}

export function hasPermission(me: Me | null | undefined, code: string): boolean {
  if (!me) return false;
  return me.roles.includes("SUPER_ADMIN") || (me.permissions ?? []).includes(code);
}

export interface AuditEvent {
  id: string;
  occurredAt: string;
  category: string;
  action: string;
  target: string;
  actorUserId?: string | null;
  actorUsername?: string | null;
  clientIp?: string | null;
  success?: boolean | null;
  metadata?: Record<string, unknown>;
}

export type ModelProviderId = "dashscope" | "openai" | "anthropic" | "ollama";

export interface ProviderSnapshot {
  label: string;
  configured: boolean;
  apiKeySet: boolean;
  needsApiKey: boolean;
  needsBaseUrl: boolean;
  model: string;
  baseUrl?: string | null;
  models?: string[];
  available?: string[];
  hint: string;
}

export interface ModelSnapshot {
  enabled: boolean;
  provider: ModelProviderId | string;
  providerLabel: string;
  model: string;
  available: boolean;
  message?: string | null;
  providers: Record<string, ProviderSnapshot>;
}

export const ENVIRONMENTS: Environment[] = ["test", "pre", "prod"];

export function parseEnvironment(raw: string | null | undefined): Environment {
  const value = (raw ?? "").trim().toLowerCase();
  if (value === "pre" || value === "gray") return "pre";
  if (value === "prod" || value === "production" || value === "prd") return "prod";
  return "test";
}
export const TOOL_KINDS: ToolKind[] = ["MYSQL", "REDIS", "MONGODB", "ELASTICSEARCH", "LOGS", "MSE", "ARMS", "LOAD_BALANCER", "CDN", "K8S", "STARROCKS", "KAFKA", "ROCKETMQ", "CODE", "MCP_SERVER"];
export const REPO_PORTS: { value: RepoRole; label: string }[] = [
  { value: "admin", label: "管理端" },
  { value: "app", label: "客户端" },
  { value: "other", label: "其他" },
];

export function repoPortLabel(role: string): string {
  return REPO_PORTS.find((item) => item.value === role)?.label ?? role;
}
