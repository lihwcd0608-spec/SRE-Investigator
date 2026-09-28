import { FormEvent, useEffect, useMemo, useState, type CSSProperties } from "react";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import { api } from "../api";
import {
  emptyAgentForm,
  formFromPlaza,
  flowHasCycle,
  plazaFromAsset,
  plazaFromTemplate,
  stringifyAgentSpec,
  declaredToolKinds,
  type AgentForm,
  type PlazaAgent,
} from "../catalog/agentSpec";
import { AgentFlowEditor } from "../catalog/AgentFlowEditor";
import { sourceLabel } from "../catalog/catalog";
import { mcpKindLabel } from "../mcp/connection";
import {
  IconBot,
  IconFolder,
  IconPencil,
  IconPlus,
  IconSearch,
  IconSparkle,
  IconTrash,
  IconX,
} from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import { TOOL_KINDS, type Binding, type CatalogAsset, type CatalogTemplate, type Repository } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { AgentPlayground } from "./AgentPlayground";

type FilterId = "all" | "official" | "installed" | "custom";

const FILTERS: { id: FilterId; label: string }[] = [
  { id: "all", label: "全部" },
  { id: "official", label: "官方" },
  { id: "installed", label: "已安装" },
  { id: "custom", label: "自定义" },
];

const DOMAIN_TAGS = ["mysql", "logs", "k8s", "code", "user", "investigate"];

export function AgentsPage() {
  const { project, projectsReady, environment, hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.agents.write");
  const canDelete = hasPermission("action.agents.delete");
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const filter = parseFilter(searchParams.get("filter"));
  const selectedKey = searchParams.get("agent") ?? "";
  const playSlug = searchParams.get("play") ?? "";
  const creating = searchParams.get("new") === "1";

  const [assets, setAssets] = useState<CatalogAsset[]>([]);
  const [templates, setTemplates] = useState<CatalogTemplate[]>([]);
  const [skills, setSkills] = useState<CatalogAsset[]>([]);
  const [bindings, setBindings] = useState<Binding[]>([]);
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<AgentForm>(emptyAgentForm);
  const [pendingDelete, setPendingDelete] = useState<PlazaAgent | null>(null);
  const [searchFocused, setSearchFocused] = useState(false);
  const editing = creating || searchParams.get("edit") === "1";

  const load = async () => {
    if (!project) return;
    const [nextAssets, nextTemplates, nextBindings, nextRepos] = await Promise.all([
      api<CatalogAsset[]>(`/api/projects/${project.id}/assets`),
      api<CatalogTemplate[]>("/api/catalog?kind=AGENT"),
      api<Binding[]>(`/api/projects/${project.id}/bindings`).catch(() => []),
      api<Repository[]>(`/api/projects/${project.id}/repositories`).catch(() => []),
    ]);
    setAssets(nextAssets.filter((item) => item.kind === "AGENT"));
    setSkills(nextAssets.filter((item) => item.kind === "SKILL" && item.enabled));
    setTemplates(nextTemplates.filter((item) => item.kind === "AGENT"));
    setBindings(nextBindings);
    setRepositories(nextRepos);
  };

  useEffect(() => {
    void load().catch((cause: Error) => setError(cause.message));
  }, [project?.id]);

  const cards = useMemo(() => mergePlaza(templates, assets), [templates, assets]);

  const visible = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return cards.filter((card) => {
      if (filter === "official" && card.source !== "BUILTIN") return false;
      if (filter === "custom" && card.source !== "CUSTOM") return false;
      if (filter === "installed" && !card.installed) return false;
      if (!keyword) return true;
      return [card.name, card.slug, card.summary, card.tags.join(" "), card.spec.allowedToolKinds.join(" "), card.spec.flow.nodes.map((node) => node.title).join(" ")]
        .join(" ")
        .toLowerCase()
        .includes(keyword);
    });
  }, [cards, filter, query]);

  const suggestions = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (keyword.length < 1) return [];
    return cards
      .filter((card) => card.name.toLowerCase().includes(keyword) || card.slug.toLowerCase().includes(keyword))
      .slice(0, 5);
  }, [cards, query]);

  const selected = creating ? null : cards.find((card) => card.key === selectedKey || card.slug === selectedKey) ?? null;

  useEffect(() => {
    if (creating) {
      setForm(emptyAgentForm());
      return;
    }
    if (selected) {
      setForm(formFromPlaza(selected));
    }
  }, [creating, selected?.key, selected?.assetId]);

  const patchParams = (next: {
    q?: string;
    filter?: FilterId;
    agent?: string;
    play?: string;
    create?: boolean;
    edit?: boolean;
  }) => {
    const params = new URLSearchParams(searchParams);
    if (next.q != null) {
      if (next.q.trim()) params.set("q", next.q);
      else params.delete("q");
    }
    if (next.filter) {
      if (next.filter === "all") params.delete("filter");
      else params.set("filter", next.filter);
    }
    if (next.create) {
      params.set("new", "1");
      params.delete("agent");
      params.delete("edit");
    } else {
      if (next.create === false) params.delete("new");
      if (next.agent != null) {
        params.delete("new");
        if (next.agent) params.set("agent", next.agent);
        else {
          params.delete("agent");
          params.delete("edit");
        }
      }
    }
    if (next.edit === true) params.set("edit", "1");
    if (next.edit === false) params.delete("edit");
    if (next.play != null) {
      if (next.play) params.set("play", next.play);
      else params.delete("play");
    }
    setSearchParams(params, { replace: true });
  };

  const playing = playSlug ? cards.find((card) => card.slug === playSlug) ?? null : null;

  if (!project) {
    return (
      <div className="empty empty-hero">
        <span className="empty-icon"><IconFolder size={22} /></span>
        <h3>{projectsReady ? "还没有选中项目" : "正在加载项目…"}</h3>
        <p>
          {projectsReady
            ? <><Link to="/projects">回到项目列表</Link> 后再打开领域专家编排。</>
            : "正在读取项目上下文。"}
        </p>
      </div>
    );
  }

  const onInstall = async (card: PlazaAgent) => {
    setSaving(true);
    setError("");
    try {
      await api(`/api/projects/${project.id}/assets`, {
        method: "POST",
        body: JSON.stringify(payloadFromForm(formFromPlaza({ ...card, enabled: true }))),
      });
      await load();
      patchParams({ agent: card.slug });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "安装失败");
    } finally {
      setSaving(false);
    }
  };

  const onSave = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.name.trim() || !form.spec.systemPrompt.trim()) {
      setError("请填写名称和系统提示词");
      return;
    }
    if (flowHasCycle(form.spec.flow)) {
      setError("业务流程图存在环，无法保存。请先删掉回边。");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const targetId = creating ? undefined : selected?.assetId;
      await api(
        targetId
          ? `/api/projects/${project.id}/assets/${targetId}`
          : `/api/projects/${project.id}/assets`,
        {
          method: targetId ? "PUT" : "POST",
          body: JSON.stringify(payloadFromForm(form)),
        },
      );
      await load();
      patchParams({ agent: form.slug.trim() || selected?.slug, create: false, edit: false });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const onToggle = async (card: PlazaAgent, enabled: boolean) => {
    if (!card.assetId) return;
    setSaving(true);
    setError("");
    try {
      await api(`/api/projects/${project.id}/assets/${card.assetId}`, {
        method: "PATCH",
        body: JSON.stringify({ enabled }),
      });
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "更新失败");
    } finally {
      setSaving(false);
    }
  };

  const onDelete = async () => {
    if (!pendingDelete?.assetId) return;
    setSaving(true);
    setError("");
    try {
      await api(`/api/projects/${project.id}/assets/${pendingDelete.assetId}`, { method: "DELETE" });
      setPendingDelete(null);
      await load();
      patchParams({ agent: "" });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除失败");
    } finally {
      setSaving(false);
    }
  };

  const openPlay = (card: PlazaAgent) => {
    patchParams({ play: card.slug });
  };

  const showSuggestions = searchFocused && suggestions.length > 0;
  const showEditor = creating || editing;

  const openEdit = (card: PlazaAgent) => {
    patchParams({ agent: card.slug, edit: true });
  };

  const requestDelete = (card: PlazaAgent) => {
    if (!card.installed || !card.assetId) return;
    setPendingDelete(card);
  };

  return (
    <div className="agent-plaza">
      <header className="plaza-hero">
        <div>
          <p className="kicker">领域专家</p>
          <h2>领域专家编排</h2>
          <p>点卡片直接开聊。人设写在卡上；业务步骤用流程图，每步只打开该步 MCP 和 Skill。官方卡不用安装也能用。</p>
        </div>
        <div className="page-header-actions">
          {canWrite ? (
          <button className="btn" type="button" onClick={() => patchParams({ create: true })}>
            <IconPlus size={16} /> 创建 Agent
          </button>
          ) : null}
        </div>
      </header>

      <div className="plaza-search-wrap">
        <label className="plaza-search" htmlFor="agent-search">
          <IconSearch size={18} />
          <input
            id="agent-search"
            value={query}
            placeholder="搜索名称、slug、工具或标签，例如 mysql、排障"
            autoComplete="off"
            onChange={(event) => patchParams({ q: event.target.value })}
            onFocus={() => setSearchFocused(true)}
            onBlur={() => window.setTimeout(() => setSearchFocused(false), 120)}
          />
          {query ? (
            <button className="btn ghost plaza-search-clear" type="button" aria-label="清空搜索" onClick={() => patchParams({ q: "" })}>
              <IconX size={14} />
            </button>
          ) : null}
        </label>
        {showSuggestions ? (
          <ul className="plaza-suggest" role="listbox" aria-label="搜索建议">
            {suggestions.map((item) => (
              <li key={item.key}>
                <button
                  type="button"
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => {
                    patchParams({ q: item.name, play: item.slug });
                    setSearchFocused(false);
                  }}
                >
                  <span>{item.name}</span>
                  <code>{item.slug}</code>
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>

      <div className="chip-row plaza-filters">
        {FILTERS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={`chip${filter === item.id ? " active" : ""}`}
            aria-pressed={filter === item.id}
            onClick={() => patchParams({ filter: item.id })}
          >
            {item.label}
            <span className="chip-count">{countFor(cards, item.id)}</span>
          </button>
        ))}
        {DOMAIN_TAGS.map((tag) => (
          <button
            key={tag}
            type="button"
            className={`chip${query.toLowerCase() === tag ? " active" : ""}`}
            onClick={() => patchParams({ q: query.toLowerCase() === tag ? "" : tag })}
          >
            {tag}
          </button>
        ))}
      </div>

      {error ? <p className="error" role="alert">{error}</p> : null}

      <div className={`plaza-body${selected || creating ? " has-detail" : ""}${showEditor ? " has-flow-editor" : ""}`}>
        {visible.length === 0 ? (
          <div className="empty empty-hero">
            <span className="empty-icon"><IconSparkle size={22} /></span>
            <h3>没有匹配的 Agent</h3>
            <p>试试搜索「排障」「MySQL」，或清空筛选后从官方卡片安装。</p>
            <div className="page-header-actions">
              <button className="btn secondary" type="button" onClick={() => patchParams({ q: "", filter: "all" })}>清空筛选</button>
              <button className="btn" type="button" onClick={() => patchParams({ create: true })}>创建自定义 Agent</button>
            </div>
          </div>
        ) : (
          <div className="plaza-grid">
            {visible.map((card) => {
              const kinds = declaredToolKinds(card.spec);
              const metaTags = [
                sourceLabel(card.source),
                ...(card.spec.flow.nodes.length > 0 ? [`${card.spec.flow.nodes.length} 步`] : []),
                ...kinds.map((kind) => mcpKindLabel(kind)),
                ...card.tags,
              ];
              const shownTags = metaTags.slice(0, 2);
              const extraCount = Math.max(0, metaTags.length - shownTags.length);
              return (
              <article
                key={card.key}
                className={`plaza-card${selected?.key === card.key ? " selected" : ""}`}
                style={{ "--plaza-hue": String(hueOf(card.slug)) } as CSSProperties}
              >
                <button type="button" className="plaza-card-main" onClick={() => openPlay(card)}>
                  <span className="plaza-avatar" aria-hidden="true">
                    <IconBot size={18} />
                  </span>
                  <div className="plaza-card-copy">
                    <div className="plaza-card-title">
                      <h3>{card.name}</h3>
                      <span className={`badge${card.installed ? "" : " faint"}`}>{card.installed ? (card.enabled ? "已启用" : "已停用") : "未安装"}</span>
                    </div>
                    <code className="plaza-slug">{card.slug}</code>
                    <p>{card.summary || "暂无简介"}</p>
                  </div>
                </button>
                <div className="plaza-card-tags">
                  {shownTags.map((tag, index) => (
                    <span className="tag" key={`${tag}-${index}`}>{tag}</span>
                  ))}
                  {extraCount > 0 ? (
                    <button
                      type="button"
                      className="tag plaza-card-more"
                      aria-label={`查看其余 ${extraCount} 项`}
                      onClick={() => openEdit(card)}
                    >
                      +{extraCount}
                    </button>
                  ) : null}
                </div>
                <div className="plaza-card-foot">
                  <button className="btn plaza-card-use" type="button" onClick={() => openPlay(card)}>
                    使用
                  </button>
                  <div className="plaza-card-manage">
                    {canWrite ? (
                    <button className="btn secondary" type="button" onClick={() => openEdit(card)}>
                      <IconPencil size={13} aria-hidden="true" />
                      修改
                    </button>
                    ) : null}
                    {canDelete ? (
                    <button
                      className="btn danger"
                      type="button"
                      disabled={!card.installed || saving}
                      aria-label={card.installed ? `删除 ${card.name}` : `${card.name} 未安装到本项目，无需删除`}
                      title={card.installed ? "从本项目删除" : "未安装到本项目，无需删除"}
                      onClick={() => requestDelete(card)}
                    >
                      <IconTrash size={13} aria-hidden="true" />
                      删除
                    </button>
                    ) : null}
                  </div>
                </div>
              </article>
              );
            })}
          </div>
        )}

        {creating || selected ? (
          <aside className="plaza-detail" aria-label="Agent 详情">
            <div className="plaza-detail-head">
              <div>
                <p className="kicker">{creating ? "New Agent" : selected?.slug}</p>
                <h3>{creating ? "创建自定义 Agent" : selected?.name}</h3>
              </div>
              <button className="btn ghost" type="button" aria-label="关闭详情" onClick={() => patchParams({ agent: "", edit: false })}>
                <IconX size={16} />
              </button>
            </div>

            {showEditor ? (
              <form className="form plaza-form" onSubmit={onSave}>
                <label>
                  名称
                  <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required placeholder="标准排查" />
                </label>
                <label>
                  Slug
                  <input className="mono" value={form.slug} onChange={(event) => setForm({ ...form, slug: event.target.value })} placeholder="sre-incident-flow" />
                </label>
                <label>
                  简介
                  <textarea className="summary-input" value={form.summary} onChange={(event) => setForm({ ...form, summary: event.target.value })} placeholder="一句话说明这个 Agent 解决什么问题" />
                </label>
                <label>
                  标签
                  <input value={form.tags.join(", ")} onChange={(event) => setForm({ ...form, tags: event.target.value.split(/[,，]/).map((item) => item.trim()).filter(Boolean) })} placeholder="official, logs" />
                </label>
                <AgentFlowEditor
                  flow={form.spec.flow}
                  bindings={bindings.filter((item) => item.enabled && item.environment === environment)}
                  skills={skills}
                  onChange={(flow) => setForm({ ...form, spec: { ...form.spec, flow } })}
                />
                {form.spec.flow.nodes.length === 0 ? (
                  <>
                    <fieldset className="check-list">
                      <legend>允许的工具</legend>
                      {TOOL_KINDS.map((kind) => (
                        <label className="check-item" key={kind}>
                          <input
                            type="checkbox"
                            checked={form.spec.allowedToolKinds.includes(kind)}
                            onChange={() => setForm({
                              ...form,
                              spec: {
                                ...form.spec,
                                allowedToolKinds: toggleItem(form.spec.allowedToolKinds, kind),
                              },
                            })}
                          />
                          {mcpKindLabel(kind)}
                          <span className="mono">{kind}</span>
                        </label>
                      ))}
                    </fieldset>
                    {skills.length > 0 ? (
                      <fieldset className="check-list">
                        <legend>绑定 Skill</legend>
                        {skills.map((skill) => (
                          <label className="check-item" key={skill.id}>
                            <input
                              type="checkbox"
                              checked={form.spec.skillSlugs.includes(skill.slug)}
                              onChange={() => setForm({
                                ...form,
                                spec: {
                                  ...form.spec,
                                  skillSlugs: toggleItem(form.spec.skillSlugs, skill.slug),
                                },
                              })}
                            />
                            {skill.name}
                            <span className="mono">{skill.slug}</span>
                          </label>
                        ))}
                      </fieldset>
                    ) : null}
                  </>
                ) : (
                  <p className="hint">有流程图时，工具和 Skill 写在节点上；卡级提示词只保留人设和只读边界。</p>
                )}
                <label>
                  系统提示词
                  <textarea
                    className="plaza-prompt"
                    value={form.spec.systemPrompt}
                    onChange={(event) => setForm({ ...form, spec: { ...form.spec, systemPrompt: event.target.value } })}
                    required
                    placeholder="写清只读边界、先查什么、结论必须带什么证据"
                  />
                </label>
                <label className="check-item">
                  <input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} />
                  安装后启用
                </label>
                <div className="modal-actions">
                  {selected?.installed && !creating ? (
                    <button className="btn ghost" type="button" onClick={() => patchParams({ edit: false })}>取消</button>
                  ) : null}
                  <button className="btn" type="submit" disabled={saving}>{saving ? "保存中…" : "保存到项目"}</button>
                </div>
              </form>
            ) : selected ? (
              <div className="plaza-readme">
                <p>{selected.summary}</p>
                <div className="chip-row">
                  <span className="tag">{sourceLabel(selected.source)}</span>
                  {declaredToolKinds(selected.spec).map((kind) => <span className="tag" key={kind}>{mcpKindLabel(kind)}</span>)}
                  {selected.tags.map((tag) => <span className="tag" key={tag}>{tag}</span>)}
                </div>
                <h4>系统提示词</h4>
                <pre className="plaza-prompt-view">{selected.spec.systemPrompt || "（空）"}</pre>
                {selected.spec.flow.nodes.length > 0 ? (
                  <>
                    <h4>业务步骤</h4>
                    <ol className="flow-readme">
                      {selected.spec.flow.nodes.map((node) => (
                        <li key={node.id}>
                          <strong>{node.title || node.id}</strong>
                          <span>{nodeSummary(node)}</span>
                        </li>
                      ))}
                    </ol>
                  </>
                ) : null}
                {selected.spec.skillSlugs.length > 0 ? (
                  <>
                    <h4>Skill</h4>
                    <p className="hint">{selected.spec.skillSlugs.join(" · ")}</p>
                  </>
                ) : null}
                <div className="plaza-detail-actions">
                  <button className="btn" type="button" onClick={() => openPlay(selected)}>
                    使用
                  </button>
                  {selected.installed ? (
                    <>
                      {canWrite ? (
                      <button className="btn secondary" type="button" onClick={() => patchParams({ edit: true })}>
                        <IconPencil size={14} aria-hidden="true" /> 修改
                      </button>
                      ) : null}
                      {canWrite ? (
                      <button className="btn secondary" type="button" disabled={saving} onClick={() => void onToggle(selected, !selected.enabled)}>
                        {selected.enabled ? "停用" : "启用"}
                      </button>
                      ) : null}
                      {canDelete ? (
                      <button className="btn danger" type="button" onClick={() => setPendingDelete(selected)}>
                        <IconTrash size={14} aria-hidden="true" /> 删除
                      </button>
                      ) : null}
                    </>
                  ) : (
                    <>
                      {canWrite ? (
                      <button className="btn secondary" type="button" disabled={saving} onClick={() => void onInstall(selected)}>
                        {saving ? "安装中…" : "安装到本项目"}
                      </button>
                      ) : null}
                      {canWrite ? (
                      <button className="btn secondary" type="button" onClick={() => patchParams({ edit: true })}>
                        <IconPencil size={14} aria-hidden="true" /> 修改后安装
                      </button>
                      ) : null}
                    </>
                  )}
                </div>
              </div>
            ) : null}
          </aside>
        ) : null}
      </div>

      {pendingDelete ? (
        <ConfirmDialog
          title="删除 Agent"
          message={`将从本项目删除「${pendingDelete.name}」。官方卡片仍可再次安装。`}
          confirmLabel="删除"
          busy={saving}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}

      {playing ? (
        <AgentPlayground
          projectId={project.id}
          environment={environment}
          card={playing}
          bindings={bindings}
          repositories={repositories}
          onClose={() => patchParams({ play: "" })}
        />
      ) : null}
    </div>
  );
}

function mergePlaza(templates: CatalogTemplate[], assets: CatalogAsset[]): PlazaAgent[] {
  const used = new Set<string>();
  const cards: PlazaAgent[] = [];
  for (const template of templates) {
    const slug = template.route || template.id;
    const asset = assets.find((item) => item.catalogId === template.id || item.slug === slug);
    if (asset) used.add(asset.id);
    cards.push(plazaFromTemplate(template, asset));
  }
  for (const asset of assets) {
    if (used.has(asset.id)) continue;
    cards.push(plazaFromAsset(asset));
  }
  return cards;
}

function countFor(cards: PlazaAgent[], filter: FilterId): number {
  if (filter === "official") return cards.filter((card) => card.source === "BUILTIN").length;
  if (filter === "custom") return cards.filter((card) => card.source === "CUSTOM").length;
  if (filter === "installed") return cards.filter((card) => card.installed).length;
  return cards.length;
}

function parseFilter(raw: string | null): FilterId {
  if (raw === "official" || raw === "installed" || raw === "custom") return raw;
  return "all";
}

function hueOf(slug: string): number {
  let hash = 0;
  for (let i = 0; i < slug.length; i += 1) {
    hash = (hash * 31 + slug.charCodeAt(i)) | 0;
  }
  const hues = [172, 198, 142, 38, 262, 210];
  return hues[Math.abs(hash) % hues.length];
}

function toggleItem<T>(list: T[], item: T): T[] {
  return list.includes(item) ? list.filter((value) => value !== item) : [...list, item];
}

function nodeSummary(node: PlazaAgent["spec"]["flow"]["nodes"][number]): string {
  const parts = [
    ...node.mcps.map((mcp) => mcp.profile),
    ...node.toolKinds,
    ...node.skillSlugs,
  ];
  return parts.length > 0 ? parts.join(" · ") : "未选 MCP / Skill";
}

function payloadFromForm(form: AgentForm) {
  return {
    kind: "AGENT",
    source: form.source,
    slug: form.slug,
    name: form.name.trim(),
    enabled: form.enabled,
    origin: form.source === "CUSTOM" ? "local" : "official",
    catalogId: form.catalogId || null,
    content: stringifyAgentSpec(form.spec),
    summary: form.summary.trim() || null,
    tags: form.tags,
  };
}
