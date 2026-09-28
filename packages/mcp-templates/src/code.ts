import { execFile as execFileCallback } from "node:child_process";
import { realpath, readFile, stat } from "node:fs/promises";
import { basename, relative, resolve, sep } from "node:path";
import { promisify } from "node:util";
import type { CodeWorkspace } from "./config.js";

const execFile = promisify(execFileCallback);
const MAX_MATCHES = 100;
const MAX_FILE_BYTES = 32 * 1024;
const SENSITIVE_FILE = /(^|\/)(\.env(?:\..*)?|.*\.(pem|key|p12|pfx)|id_rsa|credentials(?:\.json)?)(\/|$)/i;

export interface CodeMatch {
  readonly path: string;
  readonly line: number;
  readonly text: string;
}

export class WorkspaceCodeTool {
  private readonly workspaces: ReadonlyMap<string, string>;

  private constructor(workspaces: ReadonlyMap<string, string>) {
    this.workspaces = workspaces;
  }

  static async create(configured: readonly CodeWorkspace[]): Promise<WorkspaceCodeTool> {
    const workspaces = new Map<string, string>();
    for (const workspace of configured) {
      const root = await realpath(workspace.root);
      const rootStat = await stat(root);
      if (!rootStat.isDirectory()) throw new Error(`代码工作区 "${workspace.name}" 不是目录`);
      workspaces.set(workspace.name, root);
    }
    return new WorkspaceCodeTool(workspaces);
  }

  names(): string[] {
    return [...this.workspaces.keys()];
  }

  async search(workspace: string, query: string, path = ".", glob?: string): Promise<CodeMatch[]> {
    if (!query.trim() || query.length > 256) throw new Error("检索词长度必须在 1 到 256 字符之间");
    if (glob?.startsWith("-")) throw new Error("glob 不能以 - 开头");
    const target = await this.resolvePath(workspace, path);
    const args = [
      "--line-number",
      "--no-heading",
      "--color=never",
      "--fixed-strings",
      "--max-count",
      String(MAX_MATCHES),
      "--glob",
      "!**/.env*",
      "--glob",
      "!**/*.{pem,key,p12,pfx}",
      "--glob",
      "!**/credentials*.json",
      ...(glob ? ["--glob", glob] : []),
      "--",
      query,
      target,
    ];
    try {
      const { stdout } = await execFile("rg", args, { timeout: 5_000, maxBuffer: 512 * 1024 });
      return stdout.split("\n")
        .filter(Boolean)
        .slice(0, MAX_MATCHES)
        .flatMap((line): CodeMatch[] => {
          const match = /^(.*?):(\d+):(.*)$/.exec(line);
          return match ? [{ path: relative(this.root(workspace), match[1]), line: Number(match[2]), text: match[3] }] : [];
        });
    } catch (cause) {
      const error = cause as NodeJS.ErrnoException & { code?: number; stdout?: string };
      if (error.code === 1) return [];
      throw new Error(`代码检索失败：${error.message}`);
    }
  }

  async read(workspace: string, path: string, startLine = 1, maxLines = 120): Promise<{ path: string; startLine: number; text: string; truncated: boolean }> {
    const fullPath = await this.resolvePath(workspace, path);
    const fileStat = await stat(fullPath);
    if (!fileStat.isFile()) throw new Error("只能读取普通文件");
    if (fileStat.size > MAX_FILE_BYTES) throw new Error(`文件过大（最多 ${MAX_FILE_BYTES} 字节）`);
    const lines = (await readFile(fullPath, "utf8")).split("\n");
    const from = Math.max(1, startLine);
    const limit = Math.min(Math.max(maxLines, 1), 300);
    const selected = lines.slice(from - 1, from - 1 + limit);
    return {
      path: relative(this.root(workspace), fullPath),
      startLine: from,
      text: selected.join("\n"),
      truncated: from - 1 + limit < lines.length,
    };
  }

  private root(workspace: string): string {
    const root = this.workspaces.get(workspace);
    if (!root) throw new Error(`代码工作区 "${workspace}" 未配置；可用工作区：${this.names().join(", ")}`);
    return root;
  }

  private async resolvePath(workspace: string, requested: string): Promise<string> {
    const root = this.root(workspace);
    const candidate = resolve(root, requested);
    const rel = relative(root, candidate);
    if (rel.startsWith(`..${sep}`) || rel === ".." || SENSITIVE_FILE.test(rel) || SENSITIVE_FILE.test(basename(candidate))) {
      throw new Error("目标路径不在 allowlist 内或属于敏感文件");
    }
    // realpath prevents a symlink within the workspace from escaping the allowlist.
    const actual = await realpath(candidate);
    if (actual !== root && !actual.startsWith(`${root}${sep}`)) {
      throw new Error("符号链接目标不在代码工作区 allowlist 内");
    }
    return actual;
  }
}
