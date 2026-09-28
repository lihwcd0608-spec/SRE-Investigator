import type { CatalogAsset, CatalogAssetFile, CatalogKind, CatalogSource, CatalogTemplate } from "../types";
import { parseTags } from "./toolMeta";

export const RULE_USAGES = [
  { id: "investigate", label: "对话 / 排查" },
  { id: "review", label: "代码审查" },
  { id: "other", label: "其他" },
] as const;

export type RuleUsageId = (typeof RULE_USAGES)[number]["id"];

export interface AssetForm {
  kind: "SKILL" | "RULE";
  source: CatalogSource;
  name: string;
  slug: string;
  route: RuleUsageId;
  scene: string;
  origin: string;
  catalogId: string;
  packageName: string;
  sourceUrl: string;
  gitRef: string;
  content: string;
  summary: string;
  tags: string[];
  enabled: boolean;
  files: CatalogAssetFile[];
}

export function parseAssetRoute(route?: string | null): { usage: RuleUsageId; scene: string } {
  const raw = (route || "").trim();
  if (!raw) return { usage: "investigate", scene: "" };
  const colon = raw.indexOf(":");
  const head = (colon >= 0 ? raw.slice(0, colon) : raw).trim().toLowerCase();
  const tail = colon >= 0 ? raw.slice(colon + 1).trim() : "";
  if (head === "review" || head === "code-review") return { usage: "review", scene: tail };
  if (head === "other") return { usage: "other", scene: tail };
  if (head === "investigate" || head === "always" || head === "conversation") {
    return { usage: "investigate", scene: tail };
  }
  return { usage: "investigate", scene: raw };
}

export function composeAssetRoute(usage: RuleUsageId, scene: string): string {
  const extra = (scene || "").trim();
  if (usage === "investigate") return extra || "investigate";
  if (!extra) return usage;
  return `${usage}:${extra}`;
}

export function normalizeRuleUsage(route?: string | null): RuleUsageId {
  return parseAssetRoute(route).usage;
}

export function ruleUsageLabel(route?: string | null): string {
  const id = normalizeRuleUsage(route);
  return RULE_USAGES.find((item) => item.id === id)?.label || "对话 / 排查";
}

export function assetSceneLabel(route?: string | null): string {
  return parseAssetRoute(route).scene;
}

export function emptyAssetForm(kind: "SKILL" | "RULE"): AssetForm {
  return {
    kind,
    source: "CUSTOM",
    name: "",
    slug: "",
    route: "investigate",
    scene: "",
    origin: "local",
    catalogId: "",
    packageName: "",
    sourceUrl: "",
    gitRef: "",
    content: "",
    summary: "",
    tags: [],
    enabled: true,
    files: [{ path: "SKILL.md", content: "" }],
  };
}

export function emptyOpenSourceAssetForm(kind: "SKILL" | "RULE"): AssetForm {
  return {
    ...emptyAssetForm(kind),
    source: "OPEN_SOURCE",
    origin: "github",
  };
}

export function formFromAsset(asset: CatalogAsset): AssetForm {
  const parsed = parseAssetRoute(asset.route);
  return {
    kind: asset.kind === "RULE" ? "RULE" : "SKILL",
    source: asset.source,
    name: asset.name,
    slug: asset.slug,
    route: parsed.usage,
    scene: parsed.scene,
    origin: asset.origin || "local",
    catalogId: asset.catalogId || "",
    packageName: asset.packageName || "",
    sourceUrl: asset.sourceUrl || "",
    gitRef: asset.gitRef || "",
    content: asset.content || "",
    summary: asset.summary || "",
    tags: parseTags(asset.tags),
    enabled: asset.enabled,
    files: [
      { path: "SKILL.md", content: asset.content || "" },
      ...(asset.files ?? []).filter((file) => file.path.toLowerCase() !== "skill.md"),
    ],
  };
}

export function applyTemplate(form: AssetForm, template: CatalogTemplate): AssetForm {
  const parsed = parseAssetRoute(template.route);
  return {
    ...form,
    kind: template.kind === "RULE" ? "RULE" : "SKILL",
    source: template.source,
    name: template.name,
    slug: template.id,
    route: parsed.usage,
    scene: parsed.scene,
    origin: template.origin,
    catalogId: template.id,
    packageName: template.packageName || "",
    sourceUrl: template.sourceUrl || "",
    gitRef: template.gitRef || "",
    content: template.content || form.content,
    summary: form.summary || template.description || "",
    files: [{ path: "SKILL.md", content: template.content || form.content || "" }],
  };
}

export function assetKindLabel(kind: CatalogKind | string) {
  if (kind === "SKILL") return "Skill";
  if (kind === "RULE") return "Rule";
  if (kind === "AGENT") return "Agent";
  return "MCP";
}

export function sourceLabel(source: CatalogSource | string) {
  if (source === "OPEN_SOURCE") return "开源";
  if (source === "BUILTIN") return "内置";
  return "自建";
}
