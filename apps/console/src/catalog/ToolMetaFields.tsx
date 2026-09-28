import { useState } from "react";
import { IconX } from "../icons";
import { SUGGESTED_TAGS, SUMMARY_MAX, TAG_MAX_COUNT, TAG_MAX_LEN, normalizeTags } from "./toolMeta";

interface ToolMetaFieldsProps {
  summary: string;
  tags: string[];
  onChange: (partial: { summary?: string; tags?: string[] }) => void;
}

export function ToolMetaFields({ summary, tags, onChange }: ToolMetaFieldsProps) {
  const [draft, setDraft] = useState("");
  const remaining = TAG_MAX_COUNT - tags.length;
  const suggestions = SUGGESTED_TAGS.filter((item) => !tags.includes(item));

  const commitDraft = (raw: string) => {
    const next = normalizeTags([...tags, ...raw.split(/[,，;；]+/)]);
    if (next.join(",") !== tags.join(",")) {
      onChange({ tags: next });
    }
    setDraft("");
  };

  return (
    <div className="tool-meta-fields">
      <label>
        <span className="field-head">
          <span>简介</span>
          <small>{summary.length}/{SUMMARY_MAX}</small>
        </span>
        <textarea
          className="summary-input"
          value={summary}
          maxLength={SUMMARY_MAX}
          rows={2}
          placeholder="一句话说清这把工具查什么，排查时会给模型看"
          onChange={(event) => onChange({ summary: event.target.value })}
        />
      </label>
      <div className="field">
        <span className="field-head">
          <span>标签</span>
          <small>最多 {TAG_MAX_COUNT} 个，每个不超过 {TAG_MAX_LEN} 字</small>
        </span>
        <div className="tag-editor">
          {tags.map((tag) => (
            <button
              key={tag}
              type="button"
              className="tag-chip"
              onClick={() => onChange({ tags: tags.filter((item) => item !== tag) })}
            >
              {tag}
              <IconX size={11} />
            </button>
          ))}
          {remaining > 0 ? (
            <input
              className="tag-draft"
              value={draft}
              placeholder={tags.length === 0 ? "回车或逗号添加，如 订单" : "继续添加"}
              aria-label="添加标签"
              onChange={(event) => {
                const value = event.target.value;
                if (/[,，;；]/.test(value)) {
                  commitDraft(value);
                  return;
                }
                setDraft(value);
              }}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === "Tab") {
                  if (!draft.trim()) return;
                  event.preventDefault();
                  commitDraft(draft);
                }
                if (event.key === "Backspace" && !draft && tags.length > 0) {
                  onChange({ tags: tags.slice(0, -1) });
                }
              }}
              onBlur={() => {
                if (draft.trim()) commitDraft(draft);
              }}
            />
          ) : null}
        </div>
        {suggestions.length > 0 && remaining > 0 ? (
          <div className="tag-suggest" aria-label="建议标签">
            {suggestions.map((tag) => (
              <button
                key={tag}
                type="button"
                className="tag-suggest-item"
                onClick={() => onChange({ tags: normalizeTags([...tags, tag]) })}
              >
                {tag}
              </button>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}
