import { useEffect, useMemo, useState } from "react";
import { IconBook, IconCompass, IconPlugs, IconScale, IconSparkle } from "../icons";
import { mcpKindLabel } from "../mcp/connection";
import { ruleUsageLabel, assetSceneLabel } from "../catalog/catalog";
import type { Binding, CatalogAsset, ConversationModeDescriptor, ConversationModeId, InvestigationRoute } from "../types";

export type InvestigateFocus =
  | { kind: "auto" }
  | { kind: "tool"; routeId: string; label: string }
  | { kind: "skill"; slug: string; name: string; route?: string | null };

export type RuleFocus = { slug: string; name: string } | null;

export type McpFocus = {
  id: string;
  profile: string;
  toolKind: string;
};

export type SlashGroup = "mode" | "tool" | "skill" | "rule" | "mcp";

export const COMPOSER_SLASH_GROUPS: SlashGroup[] = ["skill", "rule", "mcp"];
export const REVIEW_SLASH_GROUPS: SlashGroup[] = ["skill", "rule"];

export interface SlashCommand {
  id: string;
  group: SlashGroup;
  label: string;
  description: string;
  command: string;
  disabled?: boolean;
  active?: boolean;
}

const GROUP_ORDER: SlashGroup[] = ["skill", "rule", "mcp", "mode", "tool"];

const GROUP_LABEL: Record<SlashGroup, string> = {
  mode: "模式",
  tool: "工具",
  skill: "Skill",
  rule: "Rule",
  mcp: "MCP",
};

const GROUP_HINT: Record<SlashGroup, string> = {
  mode: "决定这一轮怎么答。自定义 Agent 以后会放在这里。",
  tool: "指定这一轮排查方向。没选则自动判断。",
  skill: "只用这一份已启用 Skill。没选则按类型和场景装载。",
  rule: "点名后额外装载。类型为「其他」的 Rule 不会自动装。",
  mcp: "指定这次用哪条已启用连接。选中后会写入「使用 {profile} mcp」。",
};

const GROUP_ICON = {
  skill: IconBook,
  rule: IconScale,
  mcp: IconPlugs,
  tool: IconCompass,
  mode: IconSparkle,
};

export function isComposerSlashPicker(groups: SlashGroup[]): boolean {
  return groups.includes("skill") || groups.includes("rule") || groups.includes("mcp");
}

export function namedMcpMention(profile: string): string {
  return `使用 ${profile} mcp `;
}

function stripNamedMcpPhrases(text: string): string {
  return text.replace(/(?:使用|用)\s*[A-Za-z][\w-]*\s*mcp\s*/gi, "");
}

export function parseNamedMcpProfile(text: string): string | null {
  const match = /(?:使用|用)\s*([A-Za-z][\w-]*)\s*mcp/i.exec(text || "");
  return match ? match[1] : null;
}

export function ensureNamedMcpMention(text: string, profile?: string | null): string {
  if (!profile) return text;
  const escaped = profile.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  if (new RegExp(`(?:使用|用)\\s*${escaped}\\s*mcp`, "i").test(text)) return text;
  const trimmed = text.trim();
  return trimmed ? `使用 ${profile} mcp ${trimmed}` : `使用 ${profile} mcp`;
}

export function applyNamedMcpMention(text: string, caret: number, profile: string): { text: string; caret: number } {
  const mention = namedMcpMention(profile);
  const before = stripNamedMcpPhrases(text.slice(0, caret));
  const after = stripNamedMcpPhrases(text.slice(caret));
  return { text: before + mention + after, caret: before.length + mention.length };
}

export function slashTokenAt(text: string, caret: number): string | null {
  const match = /(?:^|[^/])\/([^\s/]*)$/.exec(text.slice(0, caret));
  return match ? match[1] : null;
}

export function consumeSlashToken(text: string, caret: number): { text: string; caret: number } {
  const before = text.slice(0, caret).replace(/(^|[^/])\/[^\s/]*$/, "$1");
  return { text: before + text.slice(caret), caret: before.length };
}

export function routeForFocus(mode: ConversationModeId, focus: InvestigateFocus): string | undefined {
  if (mode !== "INVESTIGATE") return undefined;
  if (focus.kind === "tool") return focus.routeId;
  return undefined;
}

export function reviewSkillSlug(focus: InvestigateFocus): string | undefined {
  return focus.kind === "skill" ? focus.slug : undefined;
}

export function reviewRuleSlug(focus: RuleFocus): string | undefined {
  return focus?.slug;
}

export function buildSlashCommands(input: {
  modes: ConversationModeDescriptor[];
  mode: ConversationModeId;
  routes: InvestigationRoute[];
  skills: CatalogAsset[];
  rules?: CatalogAsset[];
  bindings?: Binding[];
  mcpFocus?: McpFocus | null;
  ruleFocus?: RuleFocus;
  focus: InvestigateFocus;
  groups?: SlashGroup[];
}): SlashCommand[] {
  const allow = new Set(input.groups ?? ["mode", "tool", "skill", "mcp"]);
  const commands: SlashCommand[] = [];
  if (allow.has("mode")) for (const item of input.modes) {
    commands.push({
      id: `mode:${item.id}`,
      group: "mode",
      label: item.label,
      description: item.description,
      command: `/${item.label.replace(/\s+/g, "")}`,
      disabled: !item.enabled,
      active: item.id === input.mode,
    });
  }
  if (allow.has("tool")) {
    commands.push({
      id: "tool:auto",
      group: "tool",
      label: "自动判断",
      description: "先分类排查方向，不钉死某个 Skill。",
      command: "/自动判断",
      active: input.focus.kind === "auto",
    });
    for (const item of input.routes) {
      commands.push({
        id: `tool:${item.id}`,
        group: "tool",
        label: item.label,
        description: item.description || item.id,
        command: `/${item.id}`,
        active: input.focus.kind === "tool" && input.focus.routeId === item.id,
      });
    }
  }
  if (allow.has("skill") && input.skills.length === 0) {
    commands.push({
      id: "skill:empty",
      group: "skill",
      label: "还没有启用的 Skill",
      description: "到工具页写一份或导入 Skill 包后再从这里选。",
      command: "/skill",
      disabled: true,
    });
  } else if (allow.has("skill")) {
    for (const item of input.skills) {
      commands.push({
        id: `skill:${item.slug}`,
        group: "skill",
        label: item.name,
        description: item.summary || [ruleUsageLabel(item.route), assetSceneLabel(item.route)].filter(Boolean).join(" / ") || item.slug,
        command: `/${item.slug}`,
        active: input.focus.kind === "skill" && input.focus.slug === item.slug,
      });
    }
  }
  if (allow.has("rule") && (input.rules ?? []).length === 0) {
    commands.push({
      id: "rule:empty",
      group: "rule",
      label: "还没有启用的 Rule",
      description: "到工具页写一份 Rule 后再从这里点名。",
      command: "/rule",
      disabled: true,
    });
  } else if (allow.has("rule")) {
    for (const item of input.rules ?? []) {
      commands.push({
        id: `rule:${item.slug}`,
        group: "rule",
        label: item.name,
        description: item.summary || [ruleUsageLabel(item.route), assetSceneLabel(item.route)].filter(Boolean).join(" / ") || item.slug,
        command: `/${item.slug}`,
        active: input.ruleFocus?.slug === item.slug,
      });
    }
  }
  if (allow.has("mcp")) {
    const bindings = input.bindings ?? [];
    if (bindings.length === 0) {
      commands.push({
        id: "mcp:empty",
        group: "mcp",
        label: "当前环境没有启用的 MCP",
        description: "到工具页给这个环境加一条连接后再从这里选。",
        command: "/mcp",
        disabled: true,
      });
    } else {
      for (const item of bindings) {
        const kind = mcpKindLabel(item.toolKind);
        const extra = [kind, item.summary, item.tags].filter(Boolean).join(" · ");
        commands.push({
          id: `mcp:${item.id}`,
          group: "mcp",
          label: item.profile,
          description: extra || item.toolKind,
          command: `/${item.profile}`,
          active: input.mcpFocus?.id === item.id,
        });
      }
    }
  }
  return commands;
}

function matchesQuery(item: SlashCommand, query: string) {
  if (!query) return true;
  const hay = `${item.label} ${item.description} ${item.command} ${item.id}`.toLowerCase();
  return hay.includes(query.toLowerCase());
}

function emptySlashNotice(groups?: SlashGroup[]) {
  if (groups?.length === 1 && groups[0] === "skill") return "没有匹配的 Skill。";
  if (groups?.length === 1 && groups[0] === "rule") return "没有匹配的 Rule。";
  if (groups?.length === 1 && groups[0] === "mcp") return "没有匹配的 MCP。";
  if (groups?.length === 1 && groups[0] === "mode") return "没有可用的模式。";
  if (groups && isComposerSlashPicker(groups) && !groups.includes("mode") && !groups.includes("tool")) {
    return "没有匹配的 Skill、Rule 或 MCP。";
  }
  return "没有匹配项。";
}

export function SlashCommandMenu({
  open,
  query,
  commands,
  groups,
  onSelect,
}: {
  open: boolean;
  query: string;
  commands: SlashCommand[];
  groups?: SlashGroup[];
  onSelect: (item: SlashCommand) => void;
}) {
  const visible = useMemo(() => {
    const matched = commands.filter((item) => matchesQuery(item, query));
    const ordered: SlashCommand[] = [];
    for (const group of GROUP_ORDER) {
      for (const item of matched) {
        if (item.group === group) ordered.push(item);
      }
    }
    return ordered;
  }, [commands, query]);
  const [active, setActive] = useState(0);

  useEffect(() => {
    setActive(0);
  }, [query, open, visible.length]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setActive((index) => (visible.length === 0 ? 0 : (index + 1) % visible.length));
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        setActive((index) => (visible.length === 0 ? 0 : (index - 1 + visible.length) % visible.length));
      } else if (event.key === "Enter" && !event.metaKey && !event.ctrlKey) {
        const item = visible[active];
        if (!item || item.disabled) return;
        event.preventDefault();
        onSelect(item);
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [open, visible, active, onSelect]);

  if (!open) return null;

  const shown = new Set<SlashGroup>();
  return (
    <div className="slash-menu" role="listbox" aria-label="斜杠命令">
      {visible.length === 0 ? (
        <p className="notice">{emptySlashNotice(groups)}</p>
      ) : visible.map((item, index) => {
        const heading = !shown.has(item.group);
        shown.add(item.group);
        const Icon = GROUP_ICON[item.group];
        return (
          <div key={item.id}>
            {heading ? (
              <div className="slash-group">
                <div className="popover-group-label">{GROUP_LABEL[item.group]}</div>
                <p className="slash-group-hint">{GROUP_HINT[item.group]}</p>
              </div>
            ) : null}
            <button
              type="button"
              role="option"
              aria-selected={index === active}
              disabled={item.disabled}
              className={`slash-item${index === active ? " active" : ""}${item.active ? " current" : ""}`}
              onMouseEnter={() => setActive(index)}
              onClick={() => {
                if (!item.disabled) onSelect(item);
              }}
            >
              <span className="slash-item-icon" aria-hidden="true">
                <Icon size={14} />
              </span>
              <span className="slash-item-copy">
                <b>{item.label}</b>
                <span>{item.description}</span>
              </span>
              <kbd>{item.command}</kbd>
            </button>
          </div>
        );
      })}
    </div>
  );
}
