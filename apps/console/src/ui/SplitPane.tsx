import { Children, useCallback, useEffect, useRef, useState, type PointerEvent, type ReactNode } from "react";

export function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const onChange = () => setMatches(media.matches);
    onChange();
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [query]);
  return matches;
}

function readStored(key: string, fallback: number) {
  try {
    const raw = localStorage.getItem(key);
    const value = raw ? Number(raw) : fallback;
    return Number.isFinite(value) ? value : fallback;
  } catch {
    return fallback;
  }
}

export function SplitPane({
  axis,
  sized = "end",
  defaultSize,
  minStart,
  minEnd,
  storageKey,
  disabled = false,
  className = "",
  children,
}: {
  axis: "horizontal" | "vertical";
  sized?: "start" | "end";
  defaultSize: number;
  minStart: number;
  minEnd: number;
  storageKey: string;
  disabled?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const panes = Children.toArray(children);
  const start = panes[0] ?? null;
  const end = panes[1] ?? null;
  const rootRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState(() => readStored(storageKey, defaultSize));
  const dragRef = useRef<{ startPos: number; startSize: number } | null>(null);

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, String(Math.round(size)));
    } catch {
      /* ignore quota */
    }
  }, [size, storageKey]);

  const clamp = useCallback((next: number) => {
    const root = rootRef.current;
    const total = axis === "horizontal" ? root?.clientWidth ?? 0 : root?.clientHeight ?? 0;
    if (total <= 0) return next;
    const max = Math.max(minStart, total - minEnd);
    const min = sized === "end" ? minEnd : minStart;
    const cap = sized === "end" ? Math.min(max, total - minStart) : Math.min(max, total - minEnd);
    return Math.max(min, Math.min(cap, next));
  }, [axis, minEnd, minStart, sized]);

  useEffect(() => {
    const onResize = () => setSize((current) => clamp(current));
    window.addEventListener("resize", onResize);
    onResize();
    return () => window.removeEventListener("resize", onResize);
  }, [clamp]);

  const onPointerDown = (event: PointerEvent<HTMLButtonElement>) => {
    if (disabled) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      startPos: axis === "horizontal" ? event.clientX : event.clientY,
      startSize: size,
    };
    document.body.classList.add("is-resizing");
  };

  const onPointerMove = (event: PointerEvent<HTMLButtonElement>) => {
    if (!dragRef.current) return;
    const pos = axis === "horizontal" ? event.clientX : event.clientY;
    const delta = pos - dragRef.current.startPos;
    const next = sized === "end" ? dragRef.current.startSize - delta : dragRef.current.startSize + delta;
    setSize(clamp(next));
  };

  const stopDrag = () => {
    dragRef.current = null;
    document.body.classList.remove("is-resizing");
  };

  const nudge = (step: number) => {
    setSize((current) => clamp(sized === "end" ? current - step : current + step));
  };

  const startStyle = sized === "start" && !disabled ? { flexBasis: `${size}px`, flexGrow: 0, flexShrink: 0 } : undefined;
  const endStyle = sized === "end" && !disabled ? { flexBasis: `${size}px`, flexGrow: 0, flexShrink: 0 } : undefined;

  return (
    <div
      ref={rootRef}
      className={`split-pane ${axis}${disabled ? " stacked" : ""} ${className}`.trim()}
    >
      <div className="split-pane-start" style={startStyle}>{start}</div>
      <button
        type="button"
        className="split-handle"
        role="separator"
        aria-orientation={axis}
        aria-label="拖动调整模块大小"
        disabled={disabled}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={stopDrag}
        onPointerCancel={stopDrag}
        onKeyDown={(event) => {
          if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
            event.preventDefault();
            nudge(-16);
          }
          if (event.key === "ArrowRight" || event.key === "ArrowDown") {
            event.preventDefault();
            nudge(16);
          }
        }}
      />
      <div className="split-pane-end" style={endStyle}>{end}</div>
    </div>
  );
}
