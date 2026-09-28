import { appendFile } from "node:fs/promises";

const SENSITIVE = /(authorization|password|secret|token|uri|url)/i;

export async function audit(action: string, metadata: Record<string, unknown>): Promise<void> {
  const destination = process.env.SRE_AUDIT_LOG_PATH;
  if (!destination) return;
  const safeMetadata = Object.fromEntries(
    Object.entries(metadata).map(([key, value]) => [
      key,
      SENSITIVE.test(key) ? "***" : String(value).slice(0, 512),
    ]),
  );
  await appendFile(destination, `${JSON.stringify({
    at: new Date().toISOString(),
    action,
    ...safeMetadata,
  })}\n`, { encoding: "utf8", mode: 0o600 });
}
