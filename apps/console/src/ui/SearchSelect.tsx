import { KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import { IconSearch } from "../icons";

export interface SearchSelectOption {
  value: string;
  label: string;
  hint?: string;
}

function matchScore(query: string, text: string): number {
  const needle = query.trim().toLowerCase();
  const hay = text.toLowerCase();
  if (!needle) return 1;
  if (hay === needle) return 100;
  if (hay.startsWith(needle)) return 80;
  const index = hay.indexOf(needle);
  if (index >= 0) return 60 - Math.min(index, 20);
  let cursor = 0;
  for (const ch of hay) {
    if (ch === needle[cursor]) cursor += 1;
    if (cursor === needle.length) return 25;
  }
  return 0;
}

export function SearchSelect({
  value,
  options,
  onChange,
  disabled,
  placeholder = "选择",
  emptyLabel = "没有匹配项",
  searchPlaceholder = "搜索",
  allowCustom = false,
  align = "start",
}: {
  value: string;
  options: SearchSelectOption[];
  onChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
  emptyLabel?: string;
  searchPlaceholder?: string;
  allowCustom?: boolean;
  align?: "start" | "end";
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const selected = options.find((item) => item.value === value);
  const display = selected?.label || (value ? value : "");
  const filtered = useMemo(() => {
    return options
      .map((item) => ({
        item,
        score: matchScore(query, `${item.label} ${item.hint ?? ""} ${item.value}`),
      }))
      .filter((entry) => entry.score > 0)
      .sort((left, right) => right.score - left.score || left.item.label.localeCompare(right.item.label))
      .map((entry) => entry.item);
  }, [options, query]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    setQuery("");
    setActive(0);
    requestAnimationFrame(() => inputRef.current?.focus());
  }, [open]);

  useEffect(() => {
    setActive(0);
  }, [query]);

  useEffect(() => {
    if (!open) return;
    const node = listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`);
    node?.scrollIntoView({ block: "nearest" });
  }, [active, open]);

  const pick = (next: string) => {
    onChange(next);
    setOpen(false);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((index) => Math.min(index + 1, Math.max(filtered.length - 1, 0)));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      const hit = filtered[active];
      if (hit) {
        pick(hit.value);
        return;
      }
      if (allowCustom && query.trim()) pick(query.trim());
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      setOpen(false);
    }
  };

  return (
    <div className={`search-select${open ? " open" : ""}`} ref={rootRef}>
      <button
        type="button"
        className="search-select-trigger"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => {
          if (!disabled) setOpen((next) => !next);
        }}
      >
        <span className={display ? "" : "muted"}>{display || placeholder}</span>
        {selected?.hint ? <small>{selected.hint}</small> : null}
      </button>
      {open ? (
        <div className={`search-select-panel ${align}`}>
          <label className="search-select-query">
            <IconSearch size={14} />
            <input
              ref={inputRef}
              value={query}
              placeholder={searchPlaceholder}
              aria-label={searchPlaceholder}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={onKeyDown}
            />
          </label>
          <div className="search-select-list" role="listbox" ref={listRef}>
            {filtered.length === 0 && !(allowCustom && query.trim()) ? (
              <p className="notice">{emptyLabel}</p>
            ) : (
              <>
                {filtered.map((item, index) => (
                  <button
                    key={item.value || "empty"}
                    type="button"
                    role="option"
                    data-index={index}
                    aria-selected={item.value === value}
                    className={`search-select-option${item.value === value ? " active" : ""}${index === active ? " focused" : ""}`}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => pick(item.value)}
                  >
                    <b>{item.label}</b>
                    {item.hint ? <span>{item.hint}</span> : null}
                  </button>
                ))}
                {allowCustom && query.trim() && !options.some((item) => item.value === query.trim() || item.label === query.trim()) ? (
                  <button
                    type="button"
                    role="option"
                    className="search-select-option"
                    onClick={() => pick(query.trim())}
                  >
                    <b>使用「{query.trim()}」</b>
                    <span>手动填写场景</span>
                  </button>
                ) : null}
              </>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
