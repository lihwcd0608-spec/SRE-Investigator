import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

function key(): Buffer {
  const encoded = process.env.SECRETS_MASTER_KEY;
  if (!encoded) throw new Error("SECRETS_MASTER_KEY 未配置，拒绝保存连接凭据");
  const value = Buffer.from(encoded, "base64");
  if (value.length !== 32) throw new Error("SECRETS_MASTER_KEY 必须是 32 字节的 base64 值");
  return value;
}

export function encryptSecret(value: Record<string, unknown>): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const data = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [iv, tag, data].map((part) => part.toString("base64")).join(".");
}

export function decryptSecret(payload: string): Record<string, unknown> {
  const [iv64, tag64, data64] = payload.split(".");
  if (!iv64 || !tag64 || !data64) throw new Error("密钥载荷格式不正确");
  const decipher = createDecipheriv("aes-256-gcm", key(), Buffer.from(iv64, "base64"));
  decipher.setAuthTag(Buffer.from(tag64, "base64"));
  return JSON.parse(Buffer.concat([decipher.update(Buffer.from(data64, "base64")), decipher.final()]).toString("utf8"));
}
