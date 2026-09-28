async function parseError(response: Response): Promise<string> {
  const text = await response.text();
  if (!text) return `${response.status} ${response.statusText}`;
  try {
    const body = JSON.parse(text) as { message?: string };
    return body.message || text;
  } catch {
    return text;
  }
}

function shouldRedirectToLogin(path: string): boolean {
  if (window.location.pathname.startsWith("/login")) return false;
  return path !== "/api/auth/login" && !path.startsWith("/api/auth/feishu");
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("content-type")) {
    headers.set("content-type", "application/json");
  }
  const response = await fetch(path, { ...options, headers, credentials: "include" });
  if (response.status === 401 && shouldRedirectToLogin(path)) {
    window.location.assign("/login");
  }
  if (!response.ok) throw new Error(await parseError(response));
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function apiForm<T>(path: string, body: FormData): Promise<T> {
  const response = await fetch(path, { method: "POST", body, credentials: "include" });
  if (response.status === 401 && shouldRedirectToLogin(path)) {
    window.location.assign("/login");
  }
  if (!response.ok) throw new Error(await parseError(response));
  return response.json() as Promise<T>;
}

export interface SseFrame {
  event: string;
  data: unknown;
}

export async function consumeSse(
  response: Response,
  onFrame: (frame: SseFrame) => void,
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) throw new Error("浏览器不支持流式响应");
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";
    for (const frame of frames) {
      const parsed = parseSseFrame(frame);
      if (parsed) onFrame(parsed);
    }
  }
  const trailing = parseSseFrame(buffer);
  if (trailing) onFrame(trailing);
}

function parseSseFrame(frame: string): SseFrame | null {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trimStart());
    }
  }
  const data = dataLines.join("\n");
  if (!data) return null;
  try {
    return { event, data: JSON.parse(data) };
  } catch {
    return { event, data };
  }
}
