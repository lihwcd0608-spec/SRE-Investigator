import { useEffect, useMemo, useRef, useState } from "react";
import { IconArrowLeft, IconFolder, IconLayers, IconScroll } from "../icons";

export interface CodeWorkspace {
  name: string;
  rootPath: string;
}

export interface CodeHit {
  workspace: string;
  path: string;
  kind?: string;
  line?: number;
  text?: string;
}

export interface CodeEntry {
  workspace: string;
  name: string;
  path: string;
  kind: "dir" | "file";
}

export type MentionScope =
  | { mode: "repos"; filter: string }
  | { mode: "browse"; workspace: string; dir: string; filter: string };

export type MentionChoice =
  | { action: "repos" }
  | { action: "up"; workspace: string; dir: string }
  | { action: "workspace"; name: string }
  | { action: "repo"; name: string }
  | { action: "dir"; workspace: string; path: string }
  | { action: "file"; workspace: string; path: string };

type MentionItem = MentionChoice & {
  id: string;
  label: string;
  description: string;
  kbd?: string;
};

export function mentionAt(text: string, caret: number) {
  const match = /(?:^|[^@])@([^\s@]*)$/.exec(text.slice(0, caret));
  return match ? match[1] : null;
}

export function mentionScope(query: string, workspaces: CodeWorkspace[]): MentionScope {
  const q = query || "";
  const names = workspaces.map((item) => item.name).sort((left, right) => right.length - left.length);
  for (const name of names) {
    if (q === name || q.startsWith(`${name}/`)) {
      const rest = q === name ? "" : q.slice(name.length + 1);
      if (!rest || rest.endsWith("/")) {
        return { mode: "browse", workspace: name, dir: rest.replace(/\/$/, ""), filter: "" };
      }
      const slash = rest.lastIndexOf("/");
      return {
        mode: "browse",
        workspace: name,
        dir: slash >= 0 ? rest.slice(0, slash) : "",
        filter: slash >= 0 ? rest.slice(slash + 1) : rest,
      };
    }
  }
  return { mode: "repos", filter: q };
}

/** 在当前 scope 下改写筛选段，浏览目录时不丢掉已进入的路径。 */
export function mentionQueryWithFilter(scope: MentionScope, filter: string): string {
  const text = (filter || "").replace(/^@/, "");
  if (scope.mode === "repos") return text;
  const base = scope.dir ? `${scope.workspace}/${scope.dir}/` : `${scope.workspace}/`;
  return `${base}${text}`;
}

function parentDir(dir: string) {
  if (!dir) return null;
  const slash = dir.lastIndexOf("/");
  return slash < 0 ? "" : dir.slice(0, slash);
}

function fileName(path: string) {
  return path.split("/").pop() || path;
}

function matchesWorkspace(item: CodeWorkspace, filter: string) {
  if (!filter) return true;
  const needle = filter.toLowerCase();
  return item.name.toLowerCase().includes(needle) || item.rootPath.toLowerCase().includes(needle);
}

function matchesName(name: string, filter: string) {
  if (!filter) return true;
  return name.toLowerCase().includes(filter.toLowerCase());
}

function buildItems(
  scope: MentionScope,
  workspaces: CodeWorkspace[],
  entries: CodeEntry[],
  hits: CodeHit[],
): MentionItem[] {
  const items: MentionItem[] = [];
  if (scope.mode === "browse") {
    if (scope.dir) {
      items.push({
        id: "up",
        action: "up",
        workspace: scope.workspace,
        dir: parentDir(scope.dir) ?? "",
        label: "上一级",
        description: scope.dir,
        kbd: "⌫",
      });
    }
    for (const entry of entries) {
      if (!matchesName(entry.name, scope.filter)) continue;
      if (entry.kind === "dir") {
        items.push({
          id: `dir:${entry.workspace}:${entry.path}`,
          action: "dir",
          workspace: entry.workspace,
          path: entry.path,
          label: entry.name,
          description: entry.path,
          kbd: "/",
        });
      } else {
        items.push({
          id: `file:${entry.workspace}:${entry.path}`,
          action: "file",
          workspace: entry.workspace,
          path: entry.path,
          label: entry.name,
          description: `${entry.workspace} · ${entry.path}`,
        });
      }
    }
    // 当前仓深层 / 其它仓命中：浏览模式下也展示 search 结果，避免只扫当前目录一层
    for (const hit of hits) {
      if (scope.dir && hit.workspace === scope.workspace && !hit.path.startsWith(`${scope.dir}/`) && hit.path !== scope.dir) {
        continue;
      }
      const key = `file:${hit.workspace}:${hit.path}`;
      if (items.some((item) => item.id === key)) continue;
      items.push({
        id: key,
        action: "file",
        workspace: hit.workspace,
        path: hit.path,
        label: fileName(hit.path),
        description: hit.workspace === scope.workspace
          ? (hit.text ? `${hit.path}:${hit.line}` : hit.path)
          : `${hit.workspace} · ${hit.path}`,
      });
    }
    if (!scope.dir && matchesName(scope.workspace, scope.filter)) {
      items.push({
        id: `repo:${scope.workspace}`,
        action: "repo",
        name: scope.workspace,
        label: `引用整个 @${scope.workspace}`,
        description: workspaces.find((item) => item.name === scope.workspace)?.rootPath || "不进入目录，只点名这个仓",
      });
    }
    return items;
  }

  const matched = workspaces.filter((item) => matchesWorkspace(item, scope.filter));
  for (const item of matched) {
    items.push({
      id: `workspace:${item.name}`,
      action: "workspace",
      name: item.name,
      label: `@${item.name}`,
      description: item.rootPath,
      kbd: "/",
    });
  }
  for (const hit of hits) {
    const key = `file:${hit.workspace}:${hit.path}`;
    if (items.some((item) => item.id === key)) continue;
    items.push({
      id: key,
      action: "file",
      workspace: hit.workspace,
      path: hit.path,
      label: fileName(hit.path),
      description: hit.text
        ? `${hit.workspace} · ${hit.path}:${hit.line}`
        : `${hit.workspace} · ${hit.path}`,
    });
  }
  return items;
}

function itemIcon(item: MentionItem) {
  if (item.action === "up" || item.action === "repos") return IconArrowLeft;
  if (item.action === "dir") return IconFolder;
  if (item.action === "file") return IconScroll;
  return IconLayers;
}

export function CodeMentionMenu({
  open,
  query,
  workspaces,
  entries,
  hits,
  loading,
  onSelect,
  onQueryChange,
}: {
  open: boolean;
  query: string;
  workspaces: CodeWorkspace[];
  entries: CodeEntry[];
  hits: CodeHit[];
  loading?: boolean;
  onSelect: (choice: MentionChoice) => void;
  onQueryChange?: (query: string) => void;
}) {
  const scope = useMemo(() => mentionScope(query, workspaces), [query, workspaces]);
  const visible = useMemo(
    () => buildItems(scope, workspaces, entries, hits),
    [scope, workspaces, entries, hits],
  );
  const [active, setActive] = useState(0);
  const searchRef = useRef<HTMLInputElement>(null);
  const filterValue = scope.filter;

  useEffect(() => {
    setActive(0);
  }, [query, open, visible.length]);

  useEffect(() => {
    // 仅在空查询打开（点 @ 按钮）时聚焦筛选框，避免抢走输入框里继续敲 @ 路径的焦点
    if (open && onQueryChange && query === "") {
      const id = window.requestAnimationFrame(() => searchRef.current?.focus());
      return () => window.cancelAnimationFrame(id);
    }
  }, [open, onQueryChange, query]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setActive((index) => (visible.length === 0 ? 0 : (index + 1) % visible.length));
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        setActive((index) => (visible.length === 0 ? 0 : (index - 1 + visible.length) % visible.length));
      } else if (event.key === "Enter" && !event.metaKey && !event.ctrlKey && !event.shiftKey) {
        const item = visible[active];
        if (!item) return;
        event.preventDefault();
        onSelect(item);
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [open, visible, active, onSelect]);

  if (!open) return null;

  const crumbs = scope.mode === "browse"
    ? [scope.workspace, ...(scope.dir ? scope.dir.split("/") : [])]
    : [];

  return (
    <div className="slash-menu mention-popover" role="listbox" aria-label="@ 引用代码">
      <div className="mention-head">
        {scope.mode === "browse" ? (
          <div className="mention-crumb">
            <button type="button" onClick={() => onSelect({ action: "repos" })}>代码仓</button>
            {crumbs.map((part, index) => {
              const dir = crumbs.slice(1, index + 1).join("/");
              return (
                <span key={`${part}-${index}`}>
                  <span className="mention-crumb-sep">/</span>
                  <button
                    type="button"
                    onClick={() => {
                      if (index === 0) onSelect({ action: "workspace", name: scope.workspace });
                      else onSelect({ action: "dir", workspace: scope.workspace, path: dir });
                    }}
                  >
                    {part}
                  </button>
                </span>
              );
            })}
          </div>
        ) : (
          <div className="slash-group">
            <div className="popover-group-label">@ 引用代码</div>
            <p className="slash-group-hint">先选代码仓再进入目录，或直接输入类名 / 文件名搜索。</p>
          </div>
        )}
        {onQueryChange ? (
          <div className="mention-search">
            <input
              ref={searchRef}
              value={filterValue}
              placeholder={scope.mode === "browse" ? "筛选目录，或输入类名跨仓搜索" : "搜索代码仓 / 类名 / 文件名"}
              aria-label={scope.mode === "browse" ? "筛选或搜索代码" : "搜索代码引用"}
              onChange={(event) => onQueryChange(mentionQueryWithFilter(scope, event.target.value))}
              onKeyDown={(event) => {
                if (event.key === "Escape") event.stopPropagation();
              }}
            />
          </div>
        ) : null}
      </div>
      {loading && entries.length === 0 ? <p className="notice">正在列出文件…</p> : null}
      {visible.length === 0 ? (
        <p className="notice">
          {loading
            ? "正在列出文件…"
            : workspaces.length === 0
              ? "还没有代码仓。到「代码仓」页填本机路径后再引用。"
              : scope.mode === "browse"
                ? "没有匹配。类可能在其它代码仓，试试直接搜类名，或不带仓名输入。"
                : "没有匹配的代码仓或文件，继续输入类名搜索。"}
        </p>
      ) : visible.map((item, index) => {
        const Icon = itemIcon(item);
        return (
          <button
            key={item.id}
            type="button"
            role="option"
            aria-selected={index === active}
            className={`slash-item${index === active ? " active" : ""}`}
            onMouseEnter={() => setActive(index)}
            onClick={() => onSelect(item)}
          >
            <span className="slash-item-icon" aria-hidden="true">
              <Icon size={14} />
            </span>
            <span className="slash-item-copy">
              <b>{item.label}</b>
              <span>{item.description}</span>
            </span>
            {item.kbd ? <kbd>{item.kbd}</kbd> : null}
          </button>
        );
      })}
    </div>
  );
}
