export type SplitRowKind = "ctx" | "add" | "del" | "chg" | "hunk" | "meta";

export interface SplitRow {
  id: string;
  kind: SplitRowKind;
  leftNo?: number;
  rightNo?: number;
  left?: string;
  right?: string;
  hunk?: string;
}

export interface TextSegment {
  text: string;
  mark: boolean;
}

const HUNK = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/;

function isMeta(line: string) {
  return (
    line.startsWith("diff ")
    || line.startsWith("index ")
    || line.startsWith("new file")
    || line.startsWith("deleted file")
    || line.startsWith("--- ")
    || line.startsWith("+++ ")
    || line.startsWith("old mode")
    || line.startsWith("new mode")
    || line.startsWith("similarity")
    || line.startsWith("rename ")
    || line.startsWith("copy ")
    || line.startsWith("dissimilarity")
  );
}

export function parseUnifiedToSplit(unified: string): SplitRow[] {
  const rows: SplitRow[] = [];
  if (!unified) return rows;
  const lines = unified.replaceAll("\r\n", "\n").split("\n");
  let leftNo = 0;
  let rightNo = 0;
  let pendingDel: string[] = [];
  let pendingAdd: string[] = [];
  let seq = 0;

  const flush = () => {
    const paired = Math.min(pendingDel.length, pendingAdd.length);
    for (let index = 0; index < paired; index += 1) {
      rows.push({
        id: `r${seq++}`,
        kind: "chg",
        leftNo: leftNo++,
        rightNo: rightNo++,
        left: pendingDel[index],
        right: pendingAdd[index],
      });
    }
    for (let index = paired; index < pendingDel.length; index += 1) {
      rows.push({
        id: `r${seq++}`,
        kind: "del",
        leftNo: leftNo++,
        left: pendingDel[index],
      });
    }
    for (let index = paired; index < pendingAdd.length; index += 1) {
      rows.push({
        id: `r${seq++}`,
        kind: "add",
        rightNo: rightNo++,
        right: pendingAdd[index],
      });
    }
    pendingDel = [];
    pendingAdd = [];
  };

  for (let index = 0; index < lines.length; index += 1) {
    const raw = lines[index];
    if (index === lines.length - 1 && raw === "") continue;
    if (raw.startsWith("\\")) {
      flush();
      continue;
    }
    const hunk = raw.match(HUNK);
    if (hunk) {
      flush();
      leftNo = Number(hunk[1]);
      rightNo = Number(hunk[2]);
      rows.push({ id: `r${seq++}`, kind: "hunk", hunk: raw });
      continue;
    }
    if (isMeta(raw)) {
      flush();
      continue;
    }
    if (raw.startsWith("Binary files") || raw.startsWith("…")) {
      flush();
      rows.push({ id: `r${seq++}`, kind: "meta", hunk: raw });
      continue;
    }
    if (raw.startsWith("-")) {
      pendingDel.push(raw.slice(1));
      continue;
    }
    if (raw.startsWith("+")) {
      pendingAdd.push(raw.slice(1));
      continue;
    }
    flush();
    const text = raw.startsWith(" ") ? raw.slice(1) : raw;
    const left = leftNo > 0 ? leftNo++ : undefined;
    const right = rightNo > 0 ? rightNo++ : undefined;
    rows.push({
      id: `r${seq++}`,
      kind: "ctx",
      leftNo: left,
      rightNo: right,
      left: text,
      right: text,
    });
  }
  flush();
  return rows;
}

export function changeRowIds(rows: SplitRow[]) {
  return rows.filter((row) => row.kind === "add" || row.kind === "del" || row.kind === "chg").map((row) => row.id);
}

export function inlineDiff(left: string, right: string): { left: TextSegment[]; right: TextSegment[] } {
  if (left === right) {
    return {
      left: left ? [{ text: left, mark: false }] : [],
      right: right ? [{ text: right, mark: false }] : [],
    };
  }
  let start = 0;
  const shared = Math.min(left.length, right.length);
  while (start < shared && left[start] === right[start]) start += 1;
  let end = 0;
  const remain = Math.min(left.length - start, right.length - start);
  while (end < remain && left[left.length - 1 - end] === right[right.length - 1 - end]) end += 1;
  const leftMid = left.slice(start, left.length - end);
  const rightMid = right.slice(start, right.length - end);
  return {
    left: [
      { text: left.slice(0, start), mark: false },
      { text: leftMid, mark: leftMid.length > 0 },
      { text: left.slice(left.length - end), mark: false },
    ].filter((item) => item.text.length > 0),
    right: [
      { text: right.slice(0, start), mark: false },
      { text: rightMid, mark: rightMid.length > 0 },
      { text: right.slice(right.length - end), mark: false },
    ].filter((item) => item.text.length > 0),
  };
}
