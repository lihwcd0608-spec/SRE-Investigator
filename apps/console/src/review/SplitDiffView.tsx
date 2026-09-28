import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { IconChevronDown, IconChevronUp, IconColumns, IconRows } from "../icons";
import { changeRowIds, inlineDiff, parseUnifiedToSplit, type SplitRow } from "./splitDiff";
import { marksOnLine, type ReviewMark } from "./reviewMarks";

function diffLineClass(line: string) {
  if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("new file") || line.startsWith("deleted file")) {
    return "meta";
  }
  if (line.startsWith("@@")) return "hunk";
  if (line.startsWith("+")) return "add";
  if (line.startsWith("-")) return "del";
  return "";
}

function segments(text: string | undefined, peer: string | undefined, markKind: "add" | "del") {
  if (text == null) return null;
  if (peer == null) return text;
  return inlineDiff(markKind === "del" ? text : peer, markKind === "del" ? peer : text)[markKind === "del" ? "left" : "right"].map((part, index) => (
    <span key={`${markKind}-${index}-${part.text.slice(0, 12)}`} className={part.mark ? "mark" : undefined}>{part.text}</span>
  ));
}

function lineText(value: string | undefined) {
  return value === "" ? " " : value;
}

function SplitLine({
  row,
  side,
  current,
  notes,
  activeNoteId,
  onActivate,
}: {
  row: SplitRow;
  side: "left" | "right";
  current: boolean;
  notes: ReviewMark[];
  activeNoteId: string | null;
  onActivate: (id: string) => void;
}) {
  const text = side === "left" ? row.left : row.right;
  const peer = side === "left" ? row.right : row.left;
  const no = side === "left" ? row.leftNo : row.rightNo;
  const empty = text == null;
  const lineNotes = marksOnLine(notes, side, no);
  const top = lineNotes[0];
  let tone = "";
  if (row.kind === "add") tone = side === "right" ? "add" : "empty";
  if (row.kind === "del") tone = side === "left" ? "del" : "empty";
  if (row.kind === "chg") tone = side === "left" ? "del" : "add";
  const noted = top ? ` noted noted-${top.severity}${activeNoteId && lineNotes.some((item) => item.id === activeNoteId) ? " noted-active" : ""}` : "";
  return (
    <>
      <td className={`split-ln ${tone}${current ? " current" : ""}${noted}`}>{no || ""}</td>
      <td
        className={`split-code ${tone}${empty ? " empty" : ""}${current ? " current" : ""}${noted}`}
        title={top ? `${top.title}${top.comment ? `：${top.comment}` : ""}` : undefined}
        onClick={() => {
          if (top) onActivate(top.id);
        }}
      >
        {empty ? "\u00a0" : (
          <>
            {row.kind === "chg" ? segments(text, peer, side === "left" ? "del" : "add") : lineText(text)}
            {top ? (
              <span className={`split-note-inline ${top.severity}${activeNoteId === top.id ? " active" : ""}`}>
                {top.title}
              </span>
            ) : null}
          </>
        )}
      </td>
      {side === "right" ? (
        <td className="split-note-gutter">
          {top ? (
            <button
              type="button"
              className={`split-note-pin ${top.severity}${activeNoteId === top.id ? " active" : ""}`}
              title={top.title}
              onClick={() => onActivate(top.id)}
            >
              {notes.indexOf(top) + 1}
            </button>
          ) : null}
        </td>
      ) : null}
    </>
  );
}

export function SplitDiffView({
  path,
  unifiedDiff,
  truncated,
  baseRef,
  headRef,
  marks = [],
}: {
  path: string;
  unifiedDiff: string;
  truncated: boolean;
  baseRef: string;
  headRef: string;
  marks?: ReviewMark[];
}) {
  const [mode, setMode] = useState<"split" | "unified">("split");
  const [cursor, setCursor] = useState(0);
  const [activeNoteId, setActiveNoteId] = useState<string | null>(null);
  const leftRef = useRef<HTMLDivElement>(null);
  const rightRef = useRef<HTMLDivElement>(null);
  const syncing = useRef(false);
  const rows = useMemo(() => parseUnifiedToSplit(unifiedDiff), [unifiedDiff]);
  const changes = useMemo(() => changeRowIds(rows), [rows]);
  const fileMarks = marks;
  const activeNote = fileMarks.find((item) => item.id === activeNoteId) ?? null;
  const currentId = changes.length === 0 ? "" : changes[Math.min(cursor, changes.length - 1)];
  const firstNoteId = fileMarks.find((item) => item.line != null)?.id ?? null;

  useEffect(() => {
    setCursor(0);
    setActiveNoteId(firstNoteId);
  }, [path, unifiedDiff, firstNoteId]);

  useLayoutEffect(() => {
    if (mode !== "split") return;
    const left = leftRef.current;
    const right = rightRef.current;
    const leftTable = left?.querySelector("table");
    const rightTable = right?.querySelector("table");
    if (!left || !right || !leftTable || !rightTable) return;
    leftTable.style.minWidth = "100%";
    rightTable.style.minWidth = "100%";
    const width = Math.max(leftTable.scrollWidth, rightTable.scrollWidth, left.clientWidth, right.clientWidth);
    leftTable.style.minWidth = `${width}px`;
    rightTable.style.minWidth = `${width}px`;
    right.scrollLeft = left.scrollLeft;
  }, [mode, path, unifiedDiff, fileMarks.length]);

  useEffect(() => {
    if (mode !== "split") return;
    const body = rightRef.current || leftRef.current;
    if (!body) return;
    if (activeNote?.line) {
      const node = body.querySelector<HTMLElement>(`[data-line-right="${activeNote.line}"]`);
      if (node) {
        const top = node.offsetTop - body.clientHeight / 2 + node.offsetHeight / 2;
        body.scrollTop = Math.max(0, top);
        return;
      }
    }
    if (changes.length === 0) return;
    const id = changes[Math.min(cursor, changes.length - 1)];
    const node = body.querySelector<HTMLElement>(`[data-row-id="${id}"]`);
    if (!node) return;
    const top = node.offsetTop - body.clientHeight / 2 + node.offsetHeight / 2;
    body.scrollTop = Math.max(0, top);
  }, [cursor, changes, mode, path, activeNote]);

  const go = (step: number) => {
    if (changes.length === 0) return;
    setCursor((current) => (current + step + changes.length) % changes.length);
  };

  const syncScroll = (source: HTMLDivElement, target: HTMLDivElement | null) => {
    if (!target || syncing.current) return;
    syncing.current = true;
    target.scrollTop = source.scrollTop;
    target.scrollLeft = source.scrollLeft;
    requestAnimationFrame(() => {
      syncing.current = false;
    });
  };

  const empty = !unifiedDiff.trim() || (rows.length === 0 && !unifiedDiff.includes("Binary files"));

  const activateNote = (id: string) => {
    setActiveNoteId((current) => (current === id ? null : id));
  };

  const renderRows = (side: "left" | "right") => rows.map((row) => {
    if (row.kind === "hunk" || row.kind === "meta") {
      return (
        <tr key={row.id} data-row-id={row.id}>
          <td className="split-hunk" colSpan={side === "right" ? 3 : 2}>{row.hunk}</td>
        </tr>
      );
    }
    const current = row.id === currentId;
    return (
      <tr
        key={row.id}
        data-row-id={row.id}
        data-line-left={row.leftNo || undefined}
        data-line-right={row.rightNo || undefined}
        className={current ? "is-current" : undefined}
      >
        <SplitLine
          row={row}
          side={side}
          current={current}
          notes={fileMarks}
          activeNoteId={activeNoteId}
          onActivate={activateNote}
        />
      </tr>
    );
  });

  return (
    <div className="split-diff">
      <div className="review-file-view-head">
        <b title={path}>{path}</b>
        {truncated ? <span className="badge warn">已截断</span> : null}
        {fileMarks.length > 0 ? <span className="badge warn">{fileMarks.length} 条批注</span> : null}
        <div className="split-diff-actions">
          {mode === "split" && changes.length > 0 ? (
            <div className="split-diff-nav">
              <button type="button" className="icon-btn" aria-label="上一个差异" onClick={() => go(-1)}>
                <IconChevronUp size={14} />
              </button>
              <span>{Math.min(cursor + 1, changes.length)}/{changes.length}</span>
              <button type="button" className="icon-btn" aria-label="下一个差异" onClick={() => go(1)}>
                <IconChevronDown size={14} />
              </button>
            </div>
          ) : null}
          <button
            type="button"
            className={`chip-btn${mode === "split" ? " active" : ""}`}
            onClick={() => setMode("split")}
          >
            <IconColumns size={13} />
            左右对照
          </button>
          <button
            type="button"
            className={`chip-btn${mode === "unified" ? " active" : ""}`}
            onClick={() => setMode("unified")}
          >
            <IconRows size={13} />
            统一 diff
          </button>
        </div>
      </div>

      {fileMarks.length > 0 ? (
        <div className="split-note-list" aria-label="本文件审查批注">
          {fileMarks.map((item, index) => (
            <button
              key={item.id}
              type="button"
              className={`split-note-chip ${item.severity}${activeNoteId === item.id ? " active" : ""}`}
              onClick={() => activateNote(item.id)}
            >
              <b>{index + 1}</b>
              <span>{item.title}{item.line ? ` · L${item.line}` : ""}</span>
            </button>
          ))}
        </div>
      ) : null}

      <div className="split-diff-main">
        {empty ? (
          <div className="empty">
            <h3>该文件没有文本差异</h3>
            <p>可能是二进制、空文件，或只改了文件模式。</p>
          </div>
        ) : mode === "unified" ? (
          <pre className="review-file-diff">
            {unifiedDiff.split("\n").map((line, index) => (
              <span key={`${index}-${line.slice(0, 24)}`} className={diffLineClass(line)}>{line === "" ? " " : line}{"\n"}</span>
            ))}
          </pre>
        ) : (
          <div className="split-diff-panes">
            <div className="split-diff-pane">
              <div className="split-diff-heads">
                <div>
                  <small>基准</small>
                  <b>{baseRef}</b>
                </div>
              </div>
              <div
                ref={leftRef}
                className="split-diff-pane-body"
                tabIndex={0}
                aria-label={`${baseRef} 基准侧`}
                onScroll={(event) => syncScroll(event.currentTarget, rightRef.current)}
              >
                <table>
                  <tbody>{renderRows("left")}</tbody>
                </table>
              </div>
            </div>
            <div className="split-diff-pane">
              <div className="split-diff-heads">
                <div>
                  <small>对比</small>
                  <b>{headRef}</b>
                </div>
              </div>
              <div
                ref={rightRef}
                className="split-diff-pane-body"
                tabIndex={0}
                role="region"
                aria-label={`${headRef} 对比侧`}
                onScroll={(event) => syncScroll(event.currentTarget, leftRef.current)}
                onKeyDown={(event) => {
                  if (event.altKey && event.key === "ArrowDown") {
                    event.preventDefault();
                    go(1);
                  }
                  if (event.altKey && event.key === "ArrowUp") {
                    event.preventDefault();
                    go(-1);
                  }
                }}
              >
                <table>
                  <tbody>{renderRows("right")}</tbody>
                </table>
              </div>
              {activeNote ? (
                <aside className={`split-note-card ${activeNote.severity}`}>
                  <div>
                    <b>{activeNote.title}</b>
                    {activeNote.line ? <small>L{activeNote.line}{activeNote.endLine && activeNote.endLine !== activeNote.line ? `–${activeNote.endLine}` : ""}</small> : null}
                  </div>
                  <p>{activeNote.comment || "审查认为这里有问题。"}</p>
                </aside>
              ) : null}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
