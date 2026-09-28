import type { GitFileChange } from "../types";

const JAVA_ROOT = /(?:^|\/)(src\/(?:main|test|it)\/(?:java|kotlin|groovy|scala))(?=\/|$)/i;
const RESOURCE_ROOT = /(?:^|\/)(src\/(?:main|test|it)\/resources)(?=\/|$)/i;

export type ChangePathKind = "package" | "resources" | "other";

export interface ParsedChangePath {
  module: string;
  sourceRoot: string;
  pkg: string;
  file: string;
  className: string;
  kind: ChangePathKind;
}

export interface PackageBucket {
  module: string;
  pkg: string;
  kind: ChangePathKind;
  files: GitFileChange[];
}

export interface ModuleBucket {
  module: string;
  packages: PackageBucket[];
}

export function classLabel(file: string) {
  return file.replace(/\.(java|kt|kts|groovy|scala)$/i, "");
}

export function parseChangePath(path: string): ParsedChangePath {
  const norm = path.replaceAll("\\", "/").replace(/^\/+/, "");
  const java = JAVA_ROOT.exec(norm);
  if (java) {
    return splitAtRoot(norm, java, "package");
  }
  const resources = RESOURCE_ROOT.exec(norm);
  if (resources) {
    const parsed = splitAtRoot(norm, resources, "resources");
    return { ...parsed, pkg: parsed.pkg.replaceAll(".", "/") };
  }
  const parts = norm.split("/").filter(Boolean);
  const file = parts.pop() || norm;
  if (parts.length === 0) {
    return { module: "", sourceRoot: "", pkg: "", file, className: file, kind: "other" };
  }
  return {
    module: parts[0],
    sourceRoot: "",
    pkg: parts.slice(1).join("/"),
    file,
    className: file,
    kind: "other",
  };
}

function splitAtRoot(norm: string, match: RegExpExecArray, kind: ChangePathKind): ParsedChangePath {
  const prefix = norm.slice(0, match.index).replace(/\/$/, "");
  const modules = prefix.split("/").filter(Boolean);
  const rest = norm.slice(match.index + match[0].length).replace(/^\//, "");
  const segs = rest.split("/").filter(Boolean);
  const file = segs.pop() || rest;
  return {
    module: modules[modules.length - 1] || "",
    sourceRoot: match[1],
    pkg: segs.join("."),
    file,
    className: classLabel(file),
    kind,
  };
}

export function packageLabel(bucket: PackageBucket) {
  if (bucket.kind === "package") return bucket.pkg || "<默认包>";
  if (bucket.kind === "resources") return bucket.pkg ? `resources/${bucket.pkg}` : "resources";
  return bucket.pkg || "其他文件";
}

export function groupChangesByPackage(files: GitFileChange[]): ModuleBucket[] {
  const modules = new Map<string, Map<string, PackageBucket>>();
  for (const file of files) {
    const parsed = parseChangePath(file.path);
    const moduleKey = parsed.module || "仓库根目录";
    const pkgKey = `${parsed.kind}:${parsed.pkg}`;
    let packs = modules.get(moduleKey);
    if (!packs) {
      packs = new Map();
      modules.set(moduleKey, packs);
    }
    let bucket = packs.get(pkgKey);
    if (!bucket) {
      bucket = { module: moduleKey, pkg: parsed.pkg, kind: parsed.kind, files: [] };
      packs.set(pkgKey, bucket);
    }
    bucket.files.push(file);
  }
  const names = Array.from(modules.keys()).sort((left, right) => left.localeCompare(right));
  const result: ModuleBucket[] = [];
  for (const module of names) {
    const packs = Array.from(modules.get(module)?.values() ?? []);
    packs.sort((left, right) => {
      const kind = left.kind.localeCompare(right.kind);
      return kind !== 0 ? kind : left.pkg.localeCompare(right.pkg);
    });
    for (const pack of packs) {
      pack.files.sort((left, right) => {
        const a = classLabel(left.path.slice(left.path.lastIndexOf("/") + 1));
        const b = classLabel(right.path.slice(right.path.lastIndexOf("/") + 1));
        return a.localeCompare(b);
      });
    }
    result.push({ module, packages: packs });
  }
  return result;
}
