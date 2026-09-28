export const SUMMARY_MAX = 280;
export const TAG_MAX_LEN = 16;
export const TAG_MAX_COUNT = 8;

export const SUGGESTED_TAGS = ["订单", "支付", "网关", "缓存", "日志", "代码", "只读", "5xx", "超时", "慢查询"];

export function parseTags(raw?: string | string[] | null): string[] {
  if (!raw) return [];
  return normalizeTags(Array.isArray(raw) ? raw : raw.split(/[,，;；\s]+/));
}

export function normalizeTags(tags: Iterable<string | null | undefined>): string[] {
  const unique: string[] = [];
  const seen = new Set<string>();
  for (const tag of tags) {
    if (!tag) continue;
    const value = tag.trim().replace(/\s+/g, "").slice(0, TAG_MAX_LEN);
    if (!value || seen.has(value)) continue;
    seen.add(value);
    unique.push(value);
    if (unique.length >= TAG_MAX_COUNT) break;
  }
  return unique;
}

export function annotationText(summary?: string | null, tags?: string | string[] | null): string {
  return [summary, ...parseTags(tags)].filter(Boolean).join(" ").toLowerCase();
}
