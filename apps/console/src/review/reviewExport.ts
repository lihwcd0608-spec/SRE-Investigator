import type { GitFileChange } from "../types";
import { markdownToHtml } from "./markdown";
import { stripReviewMarks } from "./reviewMarks";

export function changeStatusLabel(status: string) {
  const code = (status || "").trim().charAt(0).toUpperCase();
  if (code === "A") return "新增";
  if (code === "D") return "删除";
  if (code === "R") return "重命名";
  return "修改";
}

export function isSourceCodeFile(item: GitFileChange) {
  if (typeof item.sourceCode === "boolean") return item.sourceCode;
  const path = item.path.replaceAll("\\", "/").toLowerCase();
  const name = path.slice(path.lastIndexOf("/") + 1);
  if (path.startsWith("db/") || path.includes("/db/") || name.endsWith(".sql") || name.endsWith(".md")) {
    return false;
  }
  return /\.(java|kt|kts|xml|yml|yaml|properties|ts|tsx|js|jsx|vue|gradle|groovy)$/.test(name);
}

function fileKindLabel(item: GitFileChange) {
  return isSourceCodeFile(item) ? "源码" : "其他";
}

function safeFilePart(value: string) {
  return (value || "review")
    .replaceAll(/[\\/:*?"<>|]+/g, "-")
    .replaceAll(/\s+/g, "_")
    .slice(0, 48);
}

export function reviewExportFilename(input: {
  repositoryName?: string;
  baseRef?: string;
  headRef?: string;
  extension: "md" | "pdf";
}) {
  const stamp = new Date().toISOString().slice(0, 10);
  return [
    "代码审查",
    safeFilePart(input.repositoryName || ""),
    safeFilePart(input.baseRef || ""),
    safeFilePart(input.headRef || ""),
    stamp,
  ].filter(Boolean).join("-") + "." + input.extension;
}

export function buildReviewMarkdown(input: {
  projectName?: string;
  repositoryName?: string;
  baseRef?: string;
  headRef?: string;
  stat?: string;
  files?: GitFileChange[];
  opinions: string[];
}) {
  const files = input.files ?? [];
  const rows = files.map((item) => `| ${changeStatusLabel(item.status)} | ${item.path.replaceAll("|", "\\|")} | ${fileKindLabel(item)} |`);
  const fileTable = files.length > 0
    ? ["| 状态 | 路径 | 类型 |", "| --- | --- | --- |", ...rows].join("\n")
    : "当前没有可导出的变更文件。";
  const opinions = input.opinions
    .map((item) => stripReviewMarks(item).trim())
    .filter(Boolean);
  const opinionBody = opinions.length === 0
    ? "还没有审查意见。"
    : opinions.map((item, index) => opinions.length > 1 ? `### 第 ${index + 1} 轮\n\n${item}` : item).join("\n\n");
  const lines = [
    "# 代码审查",
    "",
    `- 项目：${input.projectName || "未命名项目"}`,
    `- 仓库：${input.repositoryName || "未选择"}`,
    `- 基准分支：${input.baseRef || "未选择"}`,
    `- 对比分支：${input.headRef || "未选择"}`,
    `- 导出时间：${new Date().toLocaleString()}`,
  ];
  if (input.stat) {
    lines.push(`- 差异统计：${input.stat.replaceAll("\n", " ")}`);
  }
  lines.push("", "## 变更文件", "", fileTable, "", "## 审查意见", "", opinionBody, "");
  return lines.join("\n");
}

export function downloadTextFile(filename: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function downloadReviewMarkdown(filename: string, markdown: string) {
  downloadTextFile(filename, markdown, "text/markdown;charset=utf-8");
}

export function printReviewPdf(title: string, markdown: string) {
  const body = markdownToHtml(markdown);
  const frame = document.createElement("iframe");
  frame.setAttribute("aria-hidden", "true");
  frame.style.position = "fixed";
  frame.style.right = "0";
  frame.style.bottom = "0";
  frame.style.width = "0";
  frame.style.height = "0";
  frame.style.border = "0";
  document.body.appendChild(frame);
  const doc = frame.contentDocument;
  if (!doc) {
    frame.remove();
    return;
  }
  doc.open();
  doc.write(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>${title.replaceAll("<", "")}</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #111; padding: 28px; line-height: 1.55; }
    h1 { font-size: 22px; margin: 0 0 12px; }
    h2 { font-size: 16px; margin: 24px 0 10px; }
    h3 { font-size: 14px; margin: 18px 0 8px; }
    table { border-collapse: collapse; width: 100%; margin: 8px 0 16px; font-size: 12px; }
    th, td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: left; vertical-align: top; }
    th { background: #f1f5f9; }
    pre { background: #f8fafc; padding: 10px; overflow: auto; }
    code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
    @media print { body { padding: 0; } }
  </style>
</head>
<body>
${body}
</body>
</html>`);
  doc.close();
  window.setTimeout(() => {
    frame.contentWindow?.focus();
    frame.contentWindow?.print();
    window.setTimeout(() => frame.remove(), 1500);
  }, 250);
}
