import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import { IconSearch } from "../icons";
import type { CatalogTemplate, OpenSourceSkillSearchHit, OpenSourceSkillSearchResponse } from "../types";
import type { AssetForm } from "./catalog";

interface OpenSourceSkillImportProps {
  form: AssetForm;
  templates: CatalogTemplate[];
  importing?: boolean;
  saving?: boolean;
  error?: string;
  onPatch: (partial: Partial<AssetForm>) => void;
  onPickTemplate: (template: CatalogTemplate) => void;
  onImport: (url?: string) => Promise<boolean>;
  onSwitchCustom: () => void;
}

function registryLabel(registry: string) {
  if (registry === "clawhub") return "ClawHub";
  if (registry === "github") return "GitHub";
  if (registry === "catalog") return "目录";
  return "skills.sh";
}

function hitTitle(hit: OpenSourceSkillSearchHit) {
  return hit.name?.trim() || hit.packageName?.trim() || "未命名 Skill";
}

function hitMeta(hit: OpenSourceSkillSearchHit) {
  const pack = hit.packageName?.trim();
  if (hit.installs == null) return pack || hit.sourceUrl;
  const count = hit.registry === "github" ? `${hit.installs} ★` : `${hit.installs} 次`;
  return pack ? `${pack} · ${count}` : count;
}

export function OpenSourceSkillImport({
  form,
  templates,
  importing,
  saving,
  error,
  onPatch,
  onPickTemplate,
  onImport,
  onSwitchCustom,
}: OpenSourceSkillImportProps) {
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<OpenSourceSkillSearchHit[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [searched, setSearched] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [urlOpen, setUrlOpen] = useState(false);
  const [manualUrl, setManualUrl] = useState(form.sourceUrl);
  const searchSeq = useRef(0);
  const imported = form.content.trim().length > 0;

  const runSearch = async (raw: string) => {
    const q = raw.trim();
    if (q.length < 2) {
      setSearchError("搜索词至少 2 个字符");
      return;
    }
    const seq = ++searchSeq.current;
    setSearchError("");
    setSearching(true);
    try {
      const result = await api<OpenSourceSkillSearchResponse>(`/api/catalog/skills/search?q=${encodeURIComponent(q)}`);
      if (seq !== searchSeq.current) return;
      setHits(result.hits ?? []);
      setWarnings(result.warnings ?? []);
      setSearched(result.query || q);
    } catch (cause) {
      if (seq !== searchSeq.current) return;
      setHits([]);
      setWarnings([]);
      setSearched(q);
      setSearchError(cause instanceof Error ? cause.message : "搜索失败");
    } finally {
      if (seq === searchSeq.current) setSearching(false);
    }
  };

  useEffect(() => {
    const q = query.trim();
    if (q.length < 2 || q === searched) return;
    const timer = window.setTimeout(() => void runSearch(q), 450);
    return () => window.clearTimeout(timer);
  }, [query, searched]);

  const pickHit = async (hit: OpenSourceSkillSearchHit) => {
    onPatch({
      name: hitTitle(hit),
      origin: hit.origin || "github",
      catalogId: hit.id,
      packageName: hit.packageName || "",
      sourceUrl: hit.sourceUrl,
      gitRef: hit.gitRef || "main",
      summary: hit.summary || "",
    });
    await onImport(hit.sourceUrl);
  };

  const pickTemplate = (template: CatalogTemplate) => {
    onPickTemplate(template);
    if (!template.content && template.sourceUrl) {
      void onImport(template.sourceUrl);
    }
  };

  const importManual = async () => {
    const url = manualUrl.trim();
    if (!url) return;
    onPatch({ sourceUrl: url, origin: form.origin || "github" });
    await onImport(url);
  };

  if (imported) {
    return (
      <div className="skill-import">
        <SourceToggle open onSwitchCustom={onSwitchCustom} />
        <ol className="skill-import-steps" aria-label="导入步骤">
          <li>搜索</li>
          <li className="on" aria-current="step">确认导入</li>
        </ol>
        <article className="skill-import-picked">
          <div className="skill-import-hit-top">
            <b>{form.name || "未命名 Skill"}</b>
            <span className="skill-import-reg">{registryLabel(form.origin)}</span>
          </div>
          {form.packageName ? <small className="skill-import-meta">{form.packageName}</small> : null}
          {form.sourceUrl ? <small className="skill-import-meta">{form.sourceUrl}</small> : null}
        </article>
        <div className="row">
          <label htmlFor="skill-import-name">名称
            <input
              id="skill-import-name"
              value={form.name}
              onChange={(event) => onPatch({ name: event.target.value })}
              required
              placeholder="HTTP 5xx 排查"
            />
          </label>
          <label htmlFor="skill-import-slug">标识
            <input
              id="skill-import-slug"
              value={form.slug}
              onChange={(event) => onPatch({ slug: event.target.value })}
              placeholder="http-5xx，可留空"
            />
          </label>
        </div>
        <label htmlFor="skill-import-preview">已导入 SKILL.md
          <textarea
            id="skill-import-preview"
            className="skill-import-preview"
            value={form.content}
            readOnly
            rows={10}
          />
        </label>
        {error ? <p className="error" role="alert">{error}</p> : null}
        <div className="form-actions">
          <button
            className="btn secondary"
            type="button"
            onClick={() => onPatch({ content: "", files: [{ path: "SKILL.md", content: "" }] })}
          >
            换一个
          </button>
          <button className="btn" type="submit" disabled={saving || !form.name.trim() || !form.content.trim()}>
            {saving ? "保存中…" : "保存 Skill"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="skill-import">
      <SourceToggle open onSwitchCustom={onSwitchCustom} />
      <ol className="skill-import-steps" aria-label="导入步骤">
        <li className="on" aria-current="step">搜索</li>
        <li>确认导入</li>
      </ol>
      <p className="notice">搜公开目录，点一条即可导入正文。标签和附件可在保存后再改。</p>
      <label htmlFor="skill-import-q">搜索公开 Skill
        <div className="skill-search-row">
          <span className="search-wrap">
            <IconSearch size={15} />
            <input
              id="skill-import-q"
              className="search-input"
              value={query}
              aria-busy={searching}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  void runSearch(query);
                }
              }}
              placeholder="例如 superpowers、code-review"
            />
          </span>
          <button className="btn secondary" type="button" disabled={searching} onClick={() => void runSearch(query)}>
            {searching ? "搜索中…" : "搜索"}
          </button>
        </div>
      </label>
      {searchError ? <p className="error" role="alert">{searchError}</p> : null}
      {warnings.length > 0 ? (
        <p className="notice warn" role="status" aria-atomic="true">{warnings.join("；")}。GitHub 结果仍可用。</p>
      ) : null}
      {searching ? (
        <div className="skill-import-hits" aria-hidden="true">
          <div className="skill-import-skel" />
          <div className="skill-import-skel" />
          <div className="skill-import-skel" />
        </div>
      ) : null}
      {!searching && searched && hits.length === 0 && !searchError ? (
        <div className="skill-import-empty">
          <p>没有匹配「{searched}」的公开 Skill。</p>
          <p className="notice">换个词，或改用下方地址导入。</p>
        </div>
      ) : null}
      {!searching && hits.length > 0 ? (
        <div className="skill-import-hits" role="list" aria-label="搜索结果">
          {hits.map((hit) => (
            <button
              key={hit.id + hit.sourceUrl}
              type="button"
              className={`skill-import-hit${form.sourceUrl === hit.sourceUrl ? " active" : ""}`}
              disabled={importing}
              onClick={() => void pickHit(hit)}
            >
              <div className="skill-import-hit-top">
                <b>{hitTitle(hit)}</b>
                <span className="skill-import-reg">{registryLabel(hit.registry)}</span>
              </div>
              {hit.summary ? <p className="skill-import-sum">{hit.summary}</p> : null}
              <small className="skill-import-meta">{hitMeta(hit)}</small>
            </button>
          ))}
        </div>
      ) : null}
      {!searched && templates.length > 0 ? (
        <div>
          <p className="skill-import-label">项目目录推荐</p>
          <div className="skill-import-hits" role="list" aria-label="推荐模板">
            {templates.map((item) => (
              <button
                key={item.id}
                type="button"
                className="skill-import-hit"
                disabled={importing}
                onClick={() => pickTemplate(item)}
              >
                <div className="skill-import-hit-top">
                  <b>{item.name}</b>
                  <span className="skill-import-reg">{registryLabel("catalog")}</span>
                </div>
                {item.description ? <p className="skill-import-sum">{item.description}</p> : null}
                <small className="skill-import-meta">{item.origin}</small>
              </button>
            ))}
          </div>
        </div>
      ) : null}
      <div className="skill-import-url">
        <button className="btn ghost" type="button" onClick={() => setUrlOpen((open) => !open)}>
          {urlOpen ? "收起地址导入" : "用 GitHub 地址导入"}
        </button>
        {urlOpen ? (
          <div className="skill-search-row">
            <input
              value={manualUrl}
              onChange={(event) => setManualUrl(event.target.value)}
              placeholder="https://github.com/org/repo/blob/main/SKILL.md"
              aria-label="开源地址"
            />
            <button className="btn secondary" type="button" disabled={importing || !manualUrl.trim()} onClick={() => void importManual()}>
              {importing ? "拉取中…" : "导入"}
            </button>
          </div>
        ) : null}
      </div>
      {importing ? <p className="notice" role="status">正在拉取正文…</p> : null}
      {error ? <p className="error" role="alert">{error}</p> : null}
    </div>
  );
}

function SourceToggle({ open, onSwitchCustom }: { open: boolean; onSwitchCustom: () => void }) {
  return (
    <div className="source-toggle" role="tablist" aria-label="Skill 来源">
      <button type="button" className={!open ? "active" : ""} aria-pressed={!open} onClick={onSwitchCustom}>
        自建
      </button>
      <button type="button" className={open ? "active" : ""} aria-pressed={open}>
        开源
      </button>
    </div>
  );
}
