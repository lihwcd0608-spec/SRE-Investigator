import { useMemo, useState } from "react";
import { IconChevronDown, IconFolder, IconLayers } from "../icons";
import type { GitFileChange } from "../types";
import { classLabel, groupChangesByPackage, packageLabel } from "./packageTree";

function statusKind(status: string) {
  const code = (status || "").trim().charAt(0).toUpperCase();
  if (code === "A") return "A";
  if (code === "D") return "D";
  if (code === "R") return "R";
  return "M";
}

function fileName(path: string) {
  const parts = path.split("/");
  return parts[parts.length - 1] || path;
}

export function PackageFileTree({
  files,
  selectedPath,
  onSelect,
  flagged,
}: {
  files: GitFileChange[];
  selectedPath: string;
  onSelect: (item: GitFileChange) => void;
  flagged?: (path: string) => boolean;
}) {
  const groups = useMemo(() => groupChangesByPackage(files), [files]);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const toggle = (key: string) => {
    setCollapsed((current) => ({ ...current, [key]: !current[key] }));
  };

  if (files.length === 0) return null;

  return (
    <>
      {groups.map((mod) => {
        const moduleKey = `m:${mod.module}`;
        const moduleClosed = Boolean(collapsed[moduleKey]);
        const fileCount = mod.packages.reduce((sum, item) => sum + item.files.length, 0);
        return (
          <div key={mod.module} className="review-pkg-block">
            <button
              type="button"
              className={`review-pkg-row module${moduleClosed ? " collapsed" : ""}`}
              onClick={() => toggle(moduleKey)}
              aria-expanded={!moduleClosed}
            >
              <IconChevronDown size={12} />
              <IconFolder size={13} />
              <b>{mod.module}</b>
              <span className="badge">{fileCount}</span>
            </button>
            {moduleClosed ? null : mod.packages.map((pack) => {
              const packKey = `p:${mod.module}:${pack.kind}:${pack.pkg}`;
              const packClosed = Boolean(collapsed[packKey]);
              return (
                <div key={packKey} className="review-pkg-pack">
                  <button
                    type="button"
                    className={`review-pkg-row package${packClosed ? " collapsed" : ""}`}
                    onClick={() => toggle(packKey)}
                    aria-expanded={!packClosed}
                    title={packageLabel(pack)}
                  >
                    <IconChevronDown size={12} />
                    <IconLayers size={13} />
                    <span>{packageLabel(pack)}</span>
                    <span className="badge">{pack.files.length}</span>
                  </button>
                  {packClosed ? null : pack.files.map((item) => (
                    <button
                      key={`${item.status}-${item.path}`}
                      type="button"
                      role="option"
                      aria-selected={selectedPath === item.path}
                      title={item.path}
                      className={`review-file-item review-pkg-class${selectedPath === item.path ? " active" : ""}${flagged?.(item.path) ? " noted" : ""}`}
                      onClick={() => onSelect(item)}
                    >
                      <span className={`review-file-status ${statusKind(item.status)}`}>{statusKind(item.status)}</span>
                      <b>{classLabel(fileName(item.path))}</b>
                      {flagged?.(item.path) ? <i className="review-file-flag" title="有审查批注" /> : null}
                    </button>
                  ))}
                </div>
              );
            })}
          </div>
        );
      })}
    </>
  );
}
