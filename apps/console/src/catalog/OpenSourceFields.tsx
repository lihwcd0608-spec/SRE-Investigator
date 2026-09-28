import type { CatalogKind, CatalogTemplate } from "../types";

interface OpenSourceFieldsProps {
  kind: CatalogKind;
  source: "custom" | "open_source";
  origin: string;
  catalogId: string;
  packageName: string;
  sourceUrl: string;
  gitRef?: string;
  command?: string;
  args?: string;
  templates: CatalogTemplate[];
  importing?: boolean;
  onSourceChange: (source: "custom" | "open_source") => void;
  onPick: (template: CatalogTemplate) => void;
  onChange: (partial: {
    name?: string;
    origin?: string;
    catalogId?: string;
    packageName?: string;
    sourceUrl?: string;
    gitRef?: string;
    command?: string;
    args?: string;
  }) => void;
  onImport?: (url?: string) => void;
}

export function OpenSourceFields({
  kind,
  source,
  origin,
  catalogId,
  packageName,
  sourceUrl,
  gitRef,
  command,
  args,
  templates,
  importing,
  onSourceChange,
  onPick,
  onChange,
  onImport,
}: OpenSourceFieldsProps) {
  const open = source === "open_source";
  const label = kind === "MCP" ? "MCP" : kind === "SKILL" ? "Skill" : "Rule";
  const openTemplates = templates.filter((item) => item.source === "OPEN_SOURCE");
  return (
    <>
      <div className="source-toggle" role="tablist" aria-label={`${label} 来源`}>
        <button type="button" className={!open ? "active" : ""} aria-pressed={!open} onClick={() => onSourceChange("custom")}>
          自建
        </button>
        <button type="button" className={open ? "active" : ""} aria-pressed={open} onClick={() => onSourceChange("open_source")}>
          开源
        </button>
      </div>
      {open ? (
        <>
          <p className="notice">
            {kind === "MCP"
              ? "从开源目录选用，或填 GitHub / npm 地址。不提交密钥。"
              : `从开源目录选用 ${label}，或填 GitHub / npm 地址导入正文。保存时写入本项目，不提交密钥。`}
          </p>
          {openTemplates.length > 0 ? (
            <div className="catalog-pick" role="list" aria-label={`开源 ${label} 目录`}>
              {openTemplates.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={catalogId === item.id ? "active" : ""}
                  onClick={() => onPick(item)}
                >
                  <b>{item.name}</b>
                  <span>{item.description}</span>
                  <small>{item.source === "BUILTIN" ? "内置" : item.origin}</small>
                </button>
              ))}
            </div>
          ) : null}
          <div className="row">
            <label>包名
              <input
                value={packageName}
                onChange={(event) => onChange({ packageName: event.target.value, origin: origin || "npm" })}
                placeholder="@org/package"
              />
            </label>
            <label>Git 分支
              <input value={gitRef || ""} onChange={(event) => onChange({ gitRef: event.target.value })} placeholder="main" />
            </label>
          </div>
          <label>开源地址
            <input
              value={sourceUrl}
              onChange={(event) => onChange({ sourceUrl: event.target.value, origin: origin || "github" })}
              placeholder="https://github.com/org/repo/blob/main/SKILL.md"
            />
          </label>
          {kind === "MCP" ? (
            <div className="row">
              <label>启动命令
                <input value={command || ""} onChange={(event) => onChange({ command: event.target.value })} placeholder="npx" />
              </label>
              <label>参数
                <input value={args || ""} onChange={(event) => onChange({ args: event.target.value })} placeholder="-y @org/mcp" />
              </label>
            </div>
          ) : null}
          {onImport ? (
            <div className="form-actions">
              <button className="btn secondary" type="button" disabled={importing || !sourceUrl.trim()} onClick={() => onImport()}>
                {importing ? "拉取中…" : "从地址导入正文"}
              </button>
            </div>
          ) : null}
        </>
      ) : null}
    </>
  );
}
