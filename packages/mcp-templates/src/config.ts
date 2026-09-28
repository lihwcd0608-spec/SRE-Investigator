export interface ConnectionProfiles {
  readonly defaultProfile: string;
  readonly values: ReadonlyMap<string, string>;
}

export interface CodeWorkspace {
  readonly name: string;
  readonly root: string;
}

function nonEmpty(value: string | undefined): value is string {
  return Boolean(value?.trim());
}

function parseJsonObject(raw: string, label: string): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("必须是 JSON 对象");
    }
    return value as Record<string, unknown>;
  } catch (cause) {
    const reason = cause instanceof Error ? cause.message : "格式错误";
    throw new Error(`${label} 配置无效：${reason}`);
  }
}

/**
 * JSON profiles isolate connection choices to preconfigured names. Tool callers
 * can choose a profile name but can never provide a URI or other credentials.
 */
export function loadConnectionProfiles(
  profilesJson: string | undefined,
  legacyUri: string | undefined,
  defaultProfile = process.env.SRE_DEFAULT_PROFILE ?? "test",
  label = "连接 profile",
): ConnectionProfiles {
  const values = new Map<string, string>();
  if (nonEmpty(profilesJson)) {
    for (const [name, uri] of Object.entries(parseJsonObject(profilesJson, label))) {
      if (!/^[a-z][a-z0-9_-]{0,63}$/i.test(name) || typeof uri !== "string" || !nonEmpty(uri)) {
        throw new Error(`${label} 中每项必须是合法名称和非空连接字符串`);
      }
      values.set(name, uri);
    }
  } else if (nonEmpty(legacyUri)) {
    values.set(defaultProfile, legacyUri);
  }
  return { defaultProfile, values };
}

export function resolveProfile(profiles: ConnectionProfiles, profile: string | undefined, label: string): [string, string] {
  const selected = profile ?? profiles.defaultProfile;
  const uri = profiles.values.get(selected);
  if (!uri) {
    const configured = [...profiles.values.keys()];
    throw new Error(
      configured.length
        ? `${label} profile "${selected}" 未配置；可用 profile：${configured.join(", ")}`
        : `未配置任何 ${label} profile`,
    );
  }
  return [selected, uri];
}

export function loadCodeWorkspaces(raw = process.env.SRE_CODE_WORKSPACES_JSON): CodeWorkspace[] {
  if (!nonEmpty(raw)) return [];
  const workspaces: CodeWorkspace[] = [];
  for (const [name, root] of Object.entries(parseJsonObject(raw, "SRE_CODE_WORKSPACES_JSON"))) {
    if (!/^[a-z][a-z0-9_-]{0,63}$/i.test(name) || typeof root !== "string" || !nonEmpty(root)) {
      throw new Error("代码工作区必须是合法名称和非空绝对路径");
    }
    if (!root.startsWith("/")) {
      throw new Error(`代码工作区 "${name}" 必须使用绝对路径`);
    }
    workspaces.push({ name, root });
  }
  return workspaces;
}
