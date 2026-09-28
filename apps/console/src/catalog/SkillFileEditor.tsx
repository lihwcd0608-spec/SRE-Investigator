import { useState } from "react";
import type { CatalogAssetFile } from "../types";

const ENTRY = "SKILL.md";
const TEXT_EXT = new Set(["md", "txt", "yml", "yaml", "json", "csv"]);
const MAX_FILES = 20;

export function SkillFileEditor({
  files,
  onChange,
}: {
  files: CatalogAssetFile[];
  onChange: (files: CatalogAssetFile[]) => void;
}) {
  const ordered = [...files].sort((left, right) => {
    if (left.path === ENTRY) return -1;
    if (right.path === ENTRY) return 1;
    return left.path.localeCompare(right.path);
  });
  const [activePath, setActivePath] = useState(ordered[0]?.path || ENTRY);
  const [draftPath, setDraftPath] = useState("");
  const active = ordered.find((file) => file.path === activePath) ?? ordered[0];

  const updateContent = (content: string) => {
    onChange(ordered.map((file) => file.path === active.path ? { ...file, content } : file));
  };

  const addFile = () => {
    const path = draftPath.replace(/\\/g, "/").replace(/^\//, "").trim();
    const ext = path.includes(".") ? path.slice(path.lastIndexOf(".") + 1).toLowerCase() : "";
    if (!path || path.toLowerCase() === "skill.md" || path.includes("..") || path.startsWith("/")) return;
    if (!TEXT_EXT.has(ext) || ordered.length >= MAX_FILES) return;
    if (ordered.some((file) => file.path === path)) return;
    const next = [...ordered, { path, content: "" }];
    onChange(next);
    setActivePath(path);
    setDraftPath("");
  };

  const removeFile = (path: string) => {
    if (path === ENTRY) return;
    const next = ordered.filter((file) => file.path !== path);
    onChange(next);
    if (activePath === path) setActivePath(ENTRY);
  };

  return (
    <div className="skill-files">
      <div className="skill-file-nav">
        <p className="hint">最多 20 个文本文件；SKILL.md 是入口，不可删。</p>
        <ul className="skill-file-list">
          {ordered.map((file) => (
            <li key={file.path}>
              <button
                type="button"
                className={`skill-file-item${file.path === active.path ? " active" : ""}`}
                onClick={() => setActivePath(file.path)}
              >
                {file.path}
              </button>
              {file.path !== ENTRY ? (
                <button className="btn ghost danger" type="button" onClick={() => removeFile(file.path)}>
                  删除
                </button>
              ) : null}
            </li>
          ))}
        </ul>
        <div className="skill-file-add">
          <input
            value={draftPath}
            onChange={(event) => setDraftPath(event.target.value)}
            placeholder="references/playbook.md"
          />
          <button className="btn ghost" type="button" onClick={addFile} disabled={!draftPath.trim() || ordered.length >= MAX_FILES}>
            添加文件
          </button>
        </div>
      </div>
      <label>
        {active?.path || ENTRY}
        <textarea
          value={active?.content ?? ""}
          onChange={(event) => updateContent(event.target.value)}
          required={active?.path === ENTRY}
          rows={12}
          placeholder={active?.path === ENTRY ? "Markdown，会注入排查上下文。不要写密钥。" : "附属文本，排查时用 skill_read 按需读取。"}
        />
      </label>
    </div>
  );
}