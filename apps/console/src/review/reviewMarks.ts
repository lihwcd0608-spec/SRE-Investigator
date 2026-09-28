export type ReviewSeverity = "high" | "medium" | "low";

export interface ReviewMark {
  id: string;
  path: string;
  line: number | null;
  endLine: number | null;
  side: "left" | "right";
  severity: ReviewSeverity;
  title: string;
  comment: string;
}

const FENCE = /```review-marks\s*\r?\n([\s\S]*?)```/i;
const FILE_LINE = /(?:^|[`\s])([\w./\\-]+\.(?:java|kt|kts|xml|yml|yaml|properties))\s*[:：]\s*(\d+)/gi;

function severityOf(value: unknown): ReviewSeverity {
  const text = String(value || "").trim().toLowerCase();
  if (text === "high" || text === "高" || text === "blocker" || text === "error") return "high";
  if (text === "low" || text === "低" || text === "info" || text === "nit") return "low";
  return "medium";
}

function sideOf(value: unknown): "left" | "right" {
  const text = String(value || "").trim().toLowerCase();
  return text === "left" || text === "base" || text === "基准" ? "left" : "right";
}

function lineOf(value: unknown): number | null {
  const num = typeof value === "number" ? value : Number.parseInt(String(value || ""), 10);
  return Number.isFinite(num) && num > 0 ? num : null;
}

function asMark(raw: Record<string, unknown>, index: number): ReviewMark | null {
  const path = String(raw.path || raw.file || "").replaceAll("\\", "/").trim();
  if (!path) return null;
  const line = lineOf(raw.line ?? raw.startLine ?? raw.lineNumber);
  const endLine = lineOf(raw.endLine) ?? line;
  return {
    id: String(raw.id || `m${index}`),
    path,
    line,
    endLine: endLine && line ? Math.max(line, endLine) : endLine,
    side: sideOf(raw.side),
    severity: severityOf(raw.severity || raw.level),
    title: String(raw.title || raw.summary || "审查批注").trim() || "审查批注",
    comment: String(raw.comment || raw.detail || raw.message || "").trim(),
  };
}

function parseJsonMarks(payload: string, offset: number): ReviewMark[] {
  try {
    const parsed = JSON.parse(payload) as unknown;
    const rows = Array.isArray(parsed) ? parsed : Array.isArray((parsed as { marks?: unknown }).marks)
      ? (parsed as { marks: unknown[] }).marks
      : [];
    const marks: ReviewMark[] = [];
    rows.forEach((row, index) => {
      if (!row || typeof row !== "object") return;
      const mark = asMark(row as Record<string, unknown>, offset + index);
      if (mark) marks.push(mark);
    });
    return marks;
  } catch {
    return [];
  }
}

function parseFallbackMarks(report: string, offset: number): ReviewMark[] {
  const marks: ReviewMark[] = [];
  const seen = new Set<string>();
  FILE_LINE.lastIndex = 0;
  let match: RegExpExecArray | null = FILE_LINE.exec(report);
  while (match) {
    const path = match[1].replaceAll("\\", "/");
    const line = Number.parseInt(match[2], 10);
    const key = `${path}:${line}`;
    if (!seen.has(key)) {
      seen.add(key);
      const around = report.slice(Math.max(0, match.index - 80), match.index + 160);
      marks.push({
        id: `f${offset + marks.length}`,
        path,
        line,
        endLine: line,
        side: "right",
        severity: severityOf(around),
        title: "审查批注",
        comment: around.replace(/\s+/g, " ").trim().slice(0, 180),
      });
    }
    match = FILE_LINE.exec(report);
  }
  return marks;
}

export function parseReviewMarks(report: string): ReviewMark[] {
  if (!report) return [];
  const fence = FENCE.exec(report);
  if (fence) {
    return parseJsonMarks(fence[1].trim(), 0);
  }
  return parseFallbackMarks(report, 0);
}

export function stripReviewMarks(report: string) {
  if (!report) return report;
  return report.replace(FENCE, "").trim();
}

export function pathMatchesMark(markPath: string, filePath: string) {
  const left = markPath.replaceAll("\\", "/").replace(/^\/+/, "").toLowerCase();
  const right = filePath.replaceAll("\\", "/").replace(/^\/+/, "").toLowerCase();
  if (!left || !right) return false;
  if (left === right || right.endsWith(`/${left}`) || left.endsWith(`/${right}`)) return true;
  const leftName = left.slice(left.lastIndexOf("/") + 1);
  const rightName = right.slice(right.lastIndexOf("/") + 1);
  return Boolean(leftName) && leftName === rightName;
}

export function marksForPath(marks: ReviewMark[], filePath: string) {
  return marks.filter((item) => pathMatchesMark(item.path, filePath));
}

export function notedPaths(marks: ReviewMark[]) {
  return new Set(marks.map((item) => item.path.replaceAll("\\", "/")));
}

export function marksOnLine(marks: ReviewMark[], side: "left" | "right", line: number | undefined) {
  if (!line) return [];
  return marks.filter((item) => {
    if ((item.side || "right") !== side || item.line == null) return false;
    const end = item.endLine ?? item.line;
    return line >= item.line && line <= end;
  });
}
