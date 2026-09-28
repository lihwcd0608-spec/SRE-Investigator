import { type ReactNode } from "react";

export type MarkdownBlock =
  | { type: "heading"; level: 1 | 2 | 3; text: string }
  | { type: "paragraph"; text: string }
  | { type: "list"; ordered: boolean; items: string[] }
  | { type: "table"; headers: string[]; rows: string[][] }
  | { type: "code"; lang: string; text: string }
  | { type: "quote"; text: string };

function splitRow(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  return trimmed.split("|").map((cell) => cell.trim());
}

function isSeparator(line: string) {
  return /^\s*\|?\s*:?-{3,}.*$/.test(line) && line.includes("-");
}

function isFence(line: string) {
  return /^\s*```/.test(line);
}

function isTableLine(line: string) {
  return /^\s*\|/.test(line) || (/\|/.test(line) && /^\s*[^|]+\|/.test(line));
}

function isSpecial(line: string) {
  const text = line.trim();
  if (!text) return true;
  if (isFence(text) || isTableLine(text)) return true;
  if (/^#{1,3}\s+/.test(text) || /^[-*]\s+/.test(text) || /^\d+\.\s+/.test(text) || /^>\s?/.test(text)) {
    return true;
  }
  return false;
}

export function parseMarkdown(source: string): MarkdownBlock[] {
  const lines = (source || "").replace(/\r\n/g, "\n").split("\n");
  const blocks: MarkdownBlock[] = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index += 1;
      continue;
    }
    if (isFence(line)) {
      const lang = line.trim().slice(3).trim();
      const body: string[] = [];
      index += 1;
      while (index < lines.length && !isFence(lines[index])) {
        body.push(lines[index]);
        index += 1;
      }
      if (index < lines.length) index += 1;
      blocks.push({ type: "code", lang, text: body.join("\n") });
      continue;
    }
    if (isTableLine(line)) {
      const collected: string[][] = [];
      while (index < lines.length && isTableLine(lines[index])) {
        if (!isSeparator(lines[index])) collected.push(splitRow(lines[index]));
        index += 1;
      }
      if (collected.length > 0) {
        blocks.push({ type: "table", headers: collected[0], rows: collected.slice(1) });
      }
      continue;
    }
    const heading = /^(#{1,3})\s+(.+)$/.exec(line.trim());
    if (heading) {
      blocks.push({ type: "heading", level: heading[1].length as 1 | 2 | 3, text: heading[2].trim() });
      index += 1;
      continue;
    }
    if (/^\s*[-*]\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length && /^\s*[-*]\s+/.test(lines[index])) {
        items.push(lines[index].replace(/^\s*[-*]\s+/, ""));
        index += 1;
      }
      blocks.push({ type: "list", ordered: false, items });
      continue;
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length && /^\s*\d+\.\s+/.test(lines[index])) {
        items.push(lines[index].replace(/^\s*\d+\.\s+/, ""));
        index += 1;
      }
      blocks.push({ type: "list", ordered: true, items });
      continue;
    }
    if (/^\s*>\s?/.test(line)) {
      const quoted: string[] = [];
      while (index < lines.length && /^\s*>\s?/.test(lines[index])) {
        quoted.push(lines[index].replace(/^\s*>\s?/, ""));
        index += 1;
      }
      blocks.push({ type: "quote", text: quoted.join(" ") });
      continue;
    }
    const para: string[] = [];
    while (index < lines.length && lines[index].trim() && !isSpecial(lines[index])) {
      para.push(lines[index].trim());
      index += 1;
    }
    if (para.length > 0) {
      blocks.push({ type: "paragraph", text: para.join(" ") });
    } else {
      index += 1;
    }
  }
  return blocks;
}

function inlineNodes(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0;
  let match: RegExpExecArray | null = pattern.exec(text);
  let key = 0;
  while (match) {
    if (match.index > last) nodes.push(text.slice(last, match.index));
    const token = match[0];
    if (token.startsWith("**")) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>);
    } else {
      nodes.push(<code key={key}>{token.slice(1, -1)}</code>);
    }
    key += 1;
    last = match.index + token.length;
    match = pattern.exec(text);
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

export function MarkdownView({ source }: { source: string }) {
  const blocks = parseMarkdown(source);
  if (blocks.length === 0) {
    return <p className="md-empty">还没有审查意见。</p>;
  }
  return (
    <div className="md-report">
      {blocks.map((block, index) => {
        if (block.type === "heading") {
          const Tag = block.level === 1 ? "h3" : block.level === 2 ? "h4" : "h5";
          return <Tag key={index}>{inlineNodes(block.text)}</Tag>;
        }
        if (block.type === "paragraph") {
          return <p key={index}>{inlineNodes(block.text)}</p>;
        }
        if (block.type === "quote") {
          return <blockquote key={index}>{inlineNodes(block.text)}</blockquote>;
        }
        if (block.type === "code") {
          return <pre key={index}><code>{block.text}</code></pre>;
        }
        if (block.type === "list") {
          const Tag = block.ordered ? "ol" : "ul";
          return (
            <Tag key={index}>
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{inlineNodes(item)}</li>
              ))}
            </Tag>
          );
        }
        return (
          <div className="md-table-wrap" key={index}>
            <table>
              <thead>
                <tr>
                  {block.headers.map((cell, cellIndex) => (
                    <th key={cellIndex}>{inlineNodes(cell)}</th>
                  ))}
                </tr>
              </thead>
              {block.rows.length > 0 ? (
                <tbody>
                  {block.rows.map((row, rowIndex) => (
                    <tr key={rowIndex}>
                      {block.headers.map((_, cellIndex) => (
                        <td key={cellIndex}>{inlineNodes(row[cellIndex] || "")}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              ) : null}
            </table>
          </div>
        );
      })}
    </div>
  );
}

function escapeHtml(text: string) {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;");
}

function inlineHtml(text: string) {
  const escaped = escapeHtml(text);
  return escaped
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
}

export function markdownToHtml(source: string): string {
  return parseMarkdown(source).map((block) => {
    if (block.type === "heading") {
      const tag = block.level === 1 ? "h1" : block.level === 2 ? "h2" : "h3";
      return `<${tag}>${inlineHtml(block.text)}</${tag}>`;
    }
    if (block.type === "paragraph") return `<p>${inlineHtml(block.text)}</p>`;
    if (block.type === "quote") return `<blockquote>${inlineHtml(block.text)}</blockquote>`;
    if (block.type === "code") return `<pre><code>${escapeHtml(block.text)}</code></pre>`;
    if (block.type === "list") {
      const tag = block.ordered ? "ol" : "ul";
      const items = block.items.map((item) => `<li>${inlineHtml(item)}</li>`).join("");
      return `<${tag}>${items}</${tag}>`;
    }
    const head = block.headers.map((cell) => `<th>${inlineHtml(cell)}</th>`).join("");
    const body = block.rows.map((row) => {
      const cells = block.headers.map((_, cellIndex) => `<td>${inlineHtml(row[cellIndex] || "")}</td>`).join("");
      return `<tr>${cells}</tr>`;
    }).join("");
    return `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
  }).join("\n");
}
