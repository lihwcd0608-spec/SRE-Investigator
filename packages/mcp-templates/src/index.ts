import { Client as ElasticClient } from "@elastic/elasticsearch";
import { Redis } from "ioredis";
import mysql from "mysql2/promise";

export const MAX_SQL_RESULT_ROWS = 200;
const WRITE_SQL = /\b(insert|update|delete|replace|alter|drop|create|truncate|grant|revoke|call|load|outfile|for\s+update|lock\s+in\s+share\s+mode)\b/i;
const LIMIT = /\blimit\s+(\d+)(?:\s*,\s*(\d+)|\s+offset\s+(\d+))?\b/i;

export function validateReadOnlySql(sql: string): string {
  const normalized = sql.trim().replace(/;+\s*$/, "");
  if (!/^(select|show|describe|explain|with)\b/i.test(normalized) || WRITE_SQL.test(normalized)) {
    throw new Error("仅允许只读 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH 语句");
  }
  if (normalized.includes(";")) {
    throw new Error("不允许多语句查询");
  }
  const limitMatch = LIMIT.exec(normalized);
  if (!limitMatch) return `${normalized} LIMIT ${MAX_SQL_RESULT_ROWS}`;

  const requested = Number(limitMatch[2] ?? limitMatch[1]);
  if (requested <= MAX_SQL_RESULT_ROWS) return normalized;
  const bounded = limitMatch[2]
    ? `LIMIT ${limitMatch[1]}, ${MAX_SQL_RESULT_ROWS}`
    : `LIMIT ${MAX_SQL_RESULT_ROWS}${limitMatch[3] ? ` OFFSET ${limitMatch[3]}` : ""}`;
  return `${normalized.slice(0, limitMatch.index)}${bounded}${normalized.slice(limitMatch.index + limitMatch[0].length)}`;
}

export interface MysqlReadonlyConfig {
  uri: string;
}

export class MysqlReadonlyTool {
  constructor(private readonly config: MysqlReadonlyConfig) {}

  async query(sql: string): Promise<unknown> {
    const connection = await mysql.createConnection(this.config.uri);
    try {
      const [rows] = await connection.query(validateReadOnlySql(sql));
      return rows;
    } finally {
      await connection.end();
    }
  }

  async schema(table: string): Promise<unknown> {
    return this.query(`DESCRIBE \`${table.replace(/`/g, "")}\``);
  }
}

export class RedisReadonlyTool {
  constructor(private readonly redis: Redis) {}

  async get(key: string) {
    return this.redis.get(key);
  }
  async ttl(key: string) {
    return this.redis.ttl(key);
  }
  async type(key: string) {
    return this.redis.type(key);
  }
  async scanPrefix(prefix: string, count = 100, cursor = "0") {
    const [nextCursor, keys] = await this.redis.scan(
      cursor,
      "MATCH",
      `${prefix}*`,
      "COUNT",
      Math.min(Math.max(count, 1), 200),
    );
    return { cursor: nextCursor, keys };
  }
}

export class ElasticsearchReadonlyTool {
  constructor(private readonly client: ElasticClient) {}

  async indices(): Promise<unknown> {
    return this.client.cat.indices({ format: "json" });
  }
  async mapping(index: string): Promise<unknown> {
    return this.client.indices.getMapping({ index });
  }
  async search(index: string, query: Record<string, unknown>, size = 50): Promise<unknown> {
    return this.client.search({ index, query, size: Math.min(size, 100) });
  }
}

export interface LogSearchConfig {
  endpoint: string;
  authorization?: string;
}

/** Adapter for an existing log-query endpoint; it exposes search only. */
export class LogByTraceReadonlyTool {
  constructor(private readonly config: LogSearchConfig) {}

  async search(input: { traceId?: string; keyword?: string; from: string; to: string }): Promise<unknown> {
    if (!input.traceId && !input.keyword) {
      throw new Error("日志检索必须提供 traceId 或关键字");
    }
    const response = await fetch(this.config.endpoint, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(this.config.authorization ? { authorization: this.config.authorization } : {}),
      },
      body: JSON.stringify({ ...input, size: 100 }),
    });
    if (!response.ok) throw new Error(`日志查询失败：HTTP ${response.status}`);
    return response.json();
  }
}
