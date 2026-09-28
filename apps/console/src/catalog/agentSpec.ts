import type { Binding, CatalogAsset, CatalogSource, CatalogTemplate, ToolKind } from "../types";
import { TOOL_KINDS } from "../types";
import { parseTags } from "./toolMeta";

export interface AgentFlowMcp {
  toolKind: ToolKind;
  profile: string;
}

export interface AgentFlowNode {
  id: string;
  title: string;
  instruction: string;
  toolKinds: ToolKind[];
  skillSlugs: string[];
  mcps: AgentFlowMcp[];
  x: number;
  y: number;
}

export interface AgentFlowEdge {
  from: string;
  to: string;
}

export interface AgentFlowSpec {
  nodes: AgentFlowNode[];
  edges: AgentFlowEdge[];
}

export interface AgentSpec {
  systemPrompt: string;
  allowedToolKinds: ToolKind[];
  skillSlugs: string[];
  mcpSlugs: string[];
  starters: string[];
  flow: AgentFlowSpec;
}

export interface AgentForm {
  name: string;
  slug: string;
  summary: string;
  tags: string[];
  enabled: boolean;
  source: CatalogSource;
  catalogId: string;
  spec: AgentSpec;
}

export interface PlazaAgent {
  key: string;
  templateId?: string;
  assetId?: string;
  slug: string;
  name: string;
  summary: string;
  tags: string[];
  source: CatalogSource;
  origin: string;
  installed: boolean;
  enabled: boolean;
  spec: AgentSpec;
}

const TOOL_KIND_SET = new Set<string>(TOOL_KINDS);

export function emptyAgentFlow(): AgentFlowSpec {
  return { nodes: [], edges: [] };
}

export function emptyAgentSpec(): AgentSpec {
  return {
    systemPrompt: "",
    allowedToolKinds: [],
    skillSlugs: [],
    mcpSlugs: [],
    starters: [],
    flow: emptyAgentFlow(),
  };
}

export function emptyAgentForm(): AgentForm {
  return {
    name: "",
    slug: "",
    summary: "",
    tags: [],
    enabled: true,
    source: "CUSTOM",
    catalogId: "",
    spec: emptyAgentSpec(),
  };
}

export function cloneAgentFlow(flow?: AgentFlowSpec | null): AgentFlowSpec {
  const source = flow ?? emptyAgentFlow();
  return {
    nodes: source.nodes.map((node) => ({
      id: node.id,
      title: node.title,
      instruction: node.instruction,
      toolKinds: [...node.toolKinds],
      skillSlugs: [...node.skillSlugs],
      mcps: node.mcps.map((mcp) => ({ toolKind: mcp.toolKind, profile: mcp.profile })),
      x: node.x,
      y: node.y,
    })),
    edges: source.edges.map((edge) => ({ from: edge.from, to: edge.to })),
  };
}

export function parseAgentSpec(content: string | null | undefined): AgentSpec {
  const raw = (content ?? "").trim();
  if (!raw) return emptyAgentSpec();
  try {
    const parsed = JSON.parse(raw) as Partial<AgentSpec> & { systemPrompt?: unknown; flow?: unknown };
    if (parsed && typeof parsed === "object" && (parsed.systemPrompt != null || parsed.allowedToolKinds || parsed.flow)) {
      return {
        systemPrompt: typeof parsed.systemPrompt === "string" ? parsed.systemPrompt : "",
        allowedToolKinds: normalizeToolKinds(parsed.allowedToolKinds),
        skillSlugs: stringList(parsed.skillSlugs),
        mcpSlugs: stringList(parsed.mcpSlugs),
        starters: stringList((parsed as { starters?: unknown }).starters),
        flow: parseFlow(parsed.flow),
      };
    }
  } catch {
    // 纯文本提示词也当正文
  }
  return { ...emptyAgentSpec(), systemPrompt: raw };
}

export function stringifyAgentSpec(spec: AgentSpec): string {
  const flow = normalizeFlow(spec.flow);
  return JSON.stringify({
    systemPrompt: spec.systemPrompt,
    allowedToolKinds: declaredToolKinds({ ...spec, flow }),
    skillSlugs: declaredSkillSlugs({ ...spec, flow }),
    mcpSlugs: declaredMcpSlugs({ ...spec, flow }),
    starters: spec.starters,
    flow,
  });
}

export function declaredToolKinds(spec: AgentSpec): ToolKind[] {
  const kinds: ToolKind[] = [];
  const add = (kind: ToolKind) => {
    if (!kinds.includes(kind)) kinds.push(kind);
  };
  for (const kind of spec.allowedToolKinds) add(kind);
  for (const node of spec.flow.nodes) {
    for (const kind of node.toolKinds) add(kind);
    for (const mcp of node.mcps) add(mcp.toolKind);
  }
  return kinds;
}

export function declaredSkillSlugs(spec: AgentSpec): string[] {
  const slugs: string[] = [];
  const add = (slug: string) => {
    if (slug && !slugs.includes(slug)) slugs.push(slug);
  };
  for (const slug of spec.skillSlugs) add(slug);
  for (const node of spec.flow.nodes) {
    for (const slug of node.skillSlugs) add(slug);
  }
  return slugs;
}

export function declaredMcpSlugs(spec: AgentSpec): string[] {
  const slugs: string[] = [];
  const add = (slug: string) => {
    if (slug && !slugs.includes(slug)) slugs.push(slug);
  };
  for (const slug of spec.mcpSlugs) add(slug);
  for (const node of spec.flow.nodes) {
    for (const mcp of node.mcps) add(mcp.profile);
  }
  return slugs;
}

export function flowHasCycle(flow?: AgentFlowSpec | null): boolean {
  const nodes = flow?.nodes ?? [];
  const edges = flow?.edges ?? [];
  if (nodes.length === 0) return false;
  const ids: string[] = [];
  for (const node of nodes) {
    if (node.id && !ids.includes(node.id)) ids.push(node.id);
  }
  if (edges.some((edge) => edge.from === edge.to)) return true;
  const indegree = new Map<string, number>();
  const outgoing = new Map<string, string[]>();
  for (const id of ids) {
    indegree.set(id, 0);
    outgoing.set(id, []);
  }
  for (const edge of edges) {
    if (!indegree.has(edge.from) || !indegree.has(edge.to)) continue;
    const next = outgoing.get(edge.from) ?? [];
    if (next.includes(edge.to)) continue;
    next.push(edge.to);
    outgoing.set(edge.from, next);
    indegree.set(edge.to, (indegree.get(edge.to) ?? 0) + 1);
  }
  const ready = ids.filter((id) => indegree.get(id) === 0);
  let seen = 0;
  while (ready.length > 0) {
    const id = ready.shift();
    if (!id) break;
    seen += 1;
    for (const next of outgoing.get(id) ?? []) {
      const left = (indegree.get(next) ?? 0) - 1;
      indegree.set(next, left);
      if (left === 0) ready.push(next);
    }
  }
  return seen !== ids.length;
}

export function tagsOfTemplate(template: CatalogTemplate): string[] {
  return parseTags(template.packageName);
}

export function plazaFromTemplate(template: CatalogTemplate, asset?: CatalogAsset): PlazaAgent {
  const spec = parseAgentSpec(asset?.content || template.content);
  const tags = asset ? parseTags(asset.tags) : tagsOfTemplate(template);
  return {
    key: asset?.id ?? template.id,
    templateId: template.id,
    assetId: asset?.id,
    slug: asset?.slug || template.route || template.id,
    name: asset?.name || template.name,
    summary: asset?.summary || template.description,
    tags,
    source: asset?.source ?? template.source,
    origin: asset?.origin || template.origin || "official",
    installed: Boolean(asset),
    enabled: asset?.enabled ?? false,
    spec,
  };
}

export function plazaFromAsset(asset: CatalogAsset): PlazaAgent {
  return {
    key: asset.id,
    templateId: asset.catalogId || undefined,
    assetId: asset.id,
    slug: asset.slug,
    name: asset.name,
    summary: asset.summary || "",
    tags: parseTags(asset.tags),
    source: asset.source,
    origin: asset.origin || "local",
    installed: true,
    enabled: asset.enabled,
    spec: parseAgentSpec(asset.content),
  };
}

export function formFromPlaza(card: PlazaAgent): AgentForm {
  return {
    name: card.name,
    slug: card.slug,
    summary: card.summary,
    tags: [...card.tags],
    enabled: card.installed ? card.enabled : true,
    source: card.source,
    catalogId: card.templateId || "",
    spec: {
      systemPrompt: card.spec.systemPrompt,
      allowedToolKinds: [...card.spec.allowedToolKinds],
      skillSlugs: [...card.spec.skillSlugs],
      mcpSlugs: [...card.spec.mcpSlugs],
      starters: [...card.spec.starters],
      flow: cloneAgentFlow(card.spec.flow),
    },
  };
}

export function missingToolKinds(
  spec: AgentSpec,
  bindings: Binding[],
  repoCount: number,
  environment: string,
): ToolKind[] {
  const enabled = bindings.filter((item) => item.enabled && item.environment === environment);
  const missing: ToolKind[] = [];
  for (const kind of declaredToolKinds(spec)) {
    if (kind === "CODE") {
      if (repoCount <= 0 && !enabled.some((item) => item.toolKind === "CODE") && !missing.includes(kind)) {
        missing.push(kind);
      }
      continue;
    }
    if (!enabled.some((item) => item.toolKind === kind) && !missing.includes(kind)) {
      missing.push(kind);
    }
  }
  return missing;
}

function parseFlow(value: unknown): AgentFlowSpec {
  if (!value || typeof value !== "object") return emptyAgentFlow();
  const raw = value as { nodes?: unknown; edges?: unknown };
  const nodes: AgentFlowNode[] = [];
  if (Array.isArray(raw.nodes)) {
    for (const item of raw.nodes) {
      if (!item || typeof item !== "object") continue;
      const node = item as Partial<AgentFlowNode> & { mcps?: unknown };
      const id = typeof node.id === "string" ? node.id.trim() : "";
      if (!id || nodes.some((existing) => existing.id === id)) continue;
      nodes.push({
        id,
        title: typeof node.title === "string" ? node.title.trim() : "",
        instruction: typeof node.instruction === "string" ? node.instruction : "",
        toolKinds: normalizeToolKinds(node.toolKinds),
        skillSlugs: stringList(node.skillSlugs),
        mcps: parseMcps(node.mcps),
        x: numberOr(node.x, 24 + (nodes.length % 3) * 200),
        y: numberOr(node.y, 24 + Math.floor(nodes.length / 3) * 120),
      });
    }
  }
  const edges: AgentFlowEdge[] = [];
  if (Array.isArray(raw.edges)) {
    for (const item of raw.edges) {
      if (!item || typeof item !== "object") continue;
      const edge = item as Partial<AgentFlowEdge>;
      const from = typeof edge.from === "string" ? edge.from.trim() : "";
      const to = typeof edge.to === "string" ? edge.to.trim() : "";
      if (!from || !to) continue;
      if (!nodes.some((node) => node.id === from) || !nodes.some((node) => node.id === to)) continue;
      if (edges.some((existing) => existing.from === from && existing.to === to)) continue;
      edges.push({ from, to });
    }
  }
  return { nodes, edges };
}

function parseMcps(value: unknown): AgentFlowMcp[] {
  if (!Array.isArray(value)) return [];
  const items: AgentFlowMcp[] = [];
  for (const item of value) {
    if (!item || typeof item !== "object") continue;
    const mcp = item as { toolKind?: unknown; profile?: unknown };
    const kinds = normalizeToolKinds(typeof mcp.toolKind === "string" ? [mcp.toolKind] : mcp.toolKind);
    if (kinds.length === 0) continue;
    const profile = typeof mcp.profile === "string" ? mcp.profile.trim() : "";
    if (!profile) continue;
    if (items.some((existing) => existing.toolKind === kinds[0] && existing.profile === profile)) continue;
    items.push({ toolKind: kinds[0], profile });
  }
  return items;
}

function normalizeFlow(flow?: AgentFlowSpec | null): AgentFlowSpec {
  return parseFlow(flow ?? emptyAgentFlow());
}

function numberOr(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function normalizeToolKinds(value: unknown): ToolKind[] {
  if (!Array.isArray(value)) return [];
  const kinds: ToolKind[] = [];
  for (const item of value) {
    if (typeof item !== "string") continue;
    const kind = item.trim().toUpperCase();
    if (TOOL_KIND_SET.has(kind) && !kinds.includes(kind as ToolKind)) {
      kinds.push(kind as ToolKind);
    }
  }
  return kinds;
}

function stringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  const items: string[] = [];
  for (const item of value) {
    if (typeof item !== "string") continue;
    const next = item.trim();
    if (next && !items.includes(next)) items.push(next);
  }
  return items;
}
