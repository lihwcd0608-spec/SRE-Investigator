import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { Client as ElasticClient } from "@elastic/elasticsearch";
import { Redis } from "ioredis";
import { z } from "zod";
import { audit } from "./audit.js";
import {
  loadCodeWorkspaces,
  loadConnectionProfiles,
  resolveProfile,
} from "./config.js";
import { WorkspaceCodeTool } from "./code.js";
import {
  ElasticsearchReadonlyTool,
  LogByTraceReadonlyTool,
  MysqlReadonlyTool,
  RedisReadonlyTool,
} from "./index.js";

const server = new McpServer({
  name: "sre-investigator-readonly",
  version: "0.1.0",
});

const readOnly = { readOnlyHint: true, destructiveHint: false, openWorldHint: false };

function result(value: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(value, null, 2) }],
  };
}

function error(message: string) {
  return {
    content: [{ type: "text" as const, text: message }],
    isError: true,
  };
}

const mysqlProfiles = loadConnectionProfiles(
  process.env.SRE_MYSQL_PROFILES_JSON,
  process.env.SRE_MYSQL_URI,
  process.env.SRE_DEFAULT_PROFILE,
  "MySQL",
);
const redisProfiles = loadConnectionProfiles(
  process.env.SRE_REDIS_PROFILES_JSON,
  process.env.SRE_REDIS_URL,
  process.env.SRE_DEFAULT_PROFILE,
  "Redis",
);
const elasticUrl = process.env.SRE_ES_URL;
const logEndpoint = process.env.SRE_LOG_ENDPOINT;
const codeTool = await WorkspaceCodeTool.create(loadCodeWorkspaces());

server.registerTool(
  "investigator_capabilities",
  {
    title: "查看排查工具能力",
    description: "返回当前只读 MCP Server 已根据本机环境变量启用的工具，不返回连接凭据。",
    annotations: readOnly,
  },
  async () => result({
    server: "sre-investigator-readonly",
    enabled: {
      mysql: mysqlProfiles.values.size > 0,
      redis: redisProfiles.values.size > 0,
      elasticsearch: Boolean(elasticUrl),
      logs: Boolean(logEndpoint),
      code: codeTool.names().length > 0,
    },
    profiles: {
      default: process.env.SRE_DEFAULT_PROFILE ?? "test",
      mysql: [...mysqlProfiles.values.keys()],
      redis: [...redisProfiles.values.keys()],
      codeWorkspaces: codeTool.names(),
    },
    safety: "所有工具仅用于读取。未设置环境变量的工具不会注册。",
  }),
);

if (mysqlProfiles.values.size > 0) {
  server.registerTool(
    "mysql_query_readonly",
    {
      title: "执行只读 MySQL 查询",
      description: "只允许 SELECT、SHOW、DESCRIBE、EXPLAIN 或 WITH 查询；自动限制至 200 行；禁止多语句和写操作。",
      inputSchema: {
        profile: z.string().min(1).max(64).optional(),
        sql: z.string().min(1).max(12000),
      },
      annotations: readOnly,
    },
    async ({ profile, sql }) => {
      try {
        const [selected, uri] = resolveProfile(mysqlProfiles, profile, "MySQL");
        await audit("mysql_query_readonly", { profile: selected, sql });
        return result({ profile: selected, rows: await new MysqlReadonlyTool({ uri }).query(sql) });
      } catch (cause) {
        return error(cause instanceof Error ? cause.message : "MySQL 查询失败");
      }
    },
  );
  server.registerTool(
    "mysql_schema_get",
    {
      title: "读取 MySQL 表结构",
      description: "使用 DESCRIBE 读取一个表的结构；不执行任何写操作。",
      inputSchema: {
        profile: z.string().min(1).max(64).optional(),
        table: z.string().regex(/^[A-Za-z0-9_]+$/),
      },
      annotations: readOnly,
    },
    async ({ profile, table }) => {
      try {
        const [selected, uri] = resolveProfile(mysqlProfiles, profile, "MySQL");
        await audit("mysql_schema_get", { profile: selected, table });
        return result({ profile: selected, columns: await new MysqlReadonlyTool({ uri }).schema(table) });
      } catch (cause) {
        return error(cause instanceof Error ? cause.message : "读取表结构失败");
      }
    },
  );
}

if (redisProfiles.values.size > 0) {
  const redisClients = new Map<string, Redis>();
  const useRedis = async <T>(profile: string | undefined, operation: (tool: RedisReadonlyTool, selected: string) => Promise<T>) => {
    try {
      const [selected, uri] = resolveProfile(redisProfiles, profile, "Redis");
      let redis = redisClients.get(selected);
      if (!redis) {
        redis = new Redis(uri, { lazyConnect: true, maxRetriesPerRequest: 1, connectTimeout: 5_000 });
        redisClients.set(selected, redis);
      }
      if (redis.status === "wait") await redis.connect();
      return result(await operation(new RedisReadonlyTool(redis), selected));
    } catch (cause) {
      return error(cause instanceof Error ? cause.message : "Redis 查询失败");
    }
  };
  server.registerTool("redis_get", {
    title: "读取 Redis 字符串值",
    description: "按 key 读取 Redis 字符串值；不支持写入或删除。",
    inputSchema: { profile: z.string().min(1).max(64).optional(), key: z.string().min(1).max(512) },
    annotations: readOnly,
  }, async ({ profile, key }) => useRedis(profile, async (tool, selected) => {
    await audit("redis_get", { profile: selected, key });
    return { profile: selected, value: await tool.get(key) };
  }));
  server.registerTool("redis_ttl", {
    title: "读取 Redis 键剩余时间",
    description: "读取 key 的 TTL；不支持修改过期时间。",
    inputSchema: { profile: z.string().min(1).max(64).optional(), key: z.string().min(1).max(512) },
    annotations: readOnly,
  }, async ({ profile, key }) => useRedis(profile, async (tool, selected) => {
    await audit("redis_ttl", { profile: selected, key });
    return { profile: selected, ttl: await tool.ttl(key) };
  }));
  server.registerTool("redis_type", {
    title: "读取 Redis 键类型",
    description: "读取 key 的数据类型；不支持修改。",
    inputSchema: { profile: z.string().min(1).max(64).optional(), key: z.string().min(1).max(512) },
    annotations: readOnly,
  }, async ({ profile, key }) => useRedis(profile, async (tool, selected) => {
    await audit("redis_type", { profile: selected, key });
    return { profile: selected, type: await tool.type(key) };
  }));
  server.registerTool("redis_scan_prefix", {
    title: "按前缀扫描 Redis 键",
    description: "从 cursor=0 按前缀有限扫描，最多返回 200 个 key；不支持 KEYS 或删除。",
    inputSchema: {
      profile: z.string().min(1).max(64).optional(),
      prefix: z.string().min(1).max(256),
      cursor: z.string().regex(/^\d+$/).optional(),
      count: z.number().int().min(1).max(200).optional(),
    },
    annotations: readOnly,
  }, async ({ profile, prefix, cursor, count }) => useRedis(profile, async (tool, selected) => {
    await audit("redis_scan_prefix", { profile: selected, prefix, cursor: cursor ?? "0", count: count ?? 100 });
    return { profile: selected, ...(await tool.scanPrefix(prefix, count, cursor)) };
  }));
}

if (elasticUrl) {
  const client = new ElasticClient({ node: elasticUrl, auth: process.env.SRE_ES_API_KEY ? { apiKey: process.env.SRE_ES_API_KEY } : undefined });
  const elastic = new ElasticsearchReadonlyTool(client);
  server.registerTool("es_search", {
    title: "检索 Elasticsearch",
    description: "在指定索引执行只读查询，返回最多 100 条结果；不支持写入、删除或更新。",
    inputSchema: {
      index: z.string().min(1).max(255),
      query: z.record(z.unknown()),
      size: z.number().int().min(1).max(100).optional(),
    },
    annotations: readOnly,
  }, async ({ index, query, size }) => {
    try {
      await audit("es_search", { index, size: size ?? 50 });
      return result(await elastic.search(index, query, size));
    } catch (cause) {
      return error(cause instanceof Error ? cause.message : "ES 查询失败");
    }
  });
}

if (logEndpoint) {
  const logs = new LogByTraceReadonlyTool({
    endpoint: logEndpoint,
    authorization: process.env.SRE_LOG_AUTHORIZATION,
  });
  server.registerTool("log_search_by_trace", {
    title: "按 Trace 查询日志",
    description: "按 traceId 或关键字和时间范围检索日志，最多请求 100 条；不提供日志删除或修改能力。",
    inputSchema: {
      traceId: z.string().min(1).max(256).optional(),
      keyword: z.string().min(1).max(512).optional(),
      from: z.string().datetime(),
      to: z.string().datetime(),
    },
    annotations: readOnly,
  }, async (input) => {
    try {
      await audit("log_search_by_trace", { traceId: input.traceId ?? "", keyword: input.keyword ?? "" });
      return result(await logs.search(input));
    } catch (cause) {
      return error(cause instanceof Error ? cause.message : "日志查询失败");
    }
  });
}

if (codeTool.names().length > 0) {
  server.registerTool("code_search", {
    title: "在授权代码工作区检索",
    description: "仅在预配置的 workspace allowlist 中，以字面量检索源码；自动排除 .env、密钥和凭据文件。",
    inputSchema: {
      workspace: z.string().min(1).max(64),
      query: z.string().min(1).max(256),
      path: z.string().min(1).max(512).optional(),
      glob: z.string().min(1).max(128).optional(),
    },
    annotations: readOnly,
  }, async ({ workspace, query, path, glob }) => {
    try {
      await audit("code_search", { workspace, query, path: path ?? ".", glob: glob ?? "" });
      return result(await codeTool.search(workspace, query, path, glob));
    } catch (cause) {
      return error(cause instanceof Error ? cause.message : "代码检索失败");
    }
  });
  server.registerTool("code_read", {
    title: "读取授权代码片段",
    description: "仅读取 workspace allowlist 内的普通源码文件，拒绝 .env、密钥、凭据文件和路径越界。",
    inputSchema: {
      workspace: z.string().min(1).max(64),
      path: z.string().min(1).max(512),
      startLine: z.number().int().min(1).optional(),
      maxLines: z.number().int().min(1).max(300).optional(),
    },
    annotations: readOnly,
  }, async ({ workspace, path, startLine, maxLines }) => {
    try {
      await audit("code_read", { workspace, path, startLine: startLine ?? 1, maxLines: maxLines ?? 120 });
      return result(await codeTool.read(workspace, path, startLine, maxLines));
    } catch (cause) {
      return error(cause instanceof Error ? cause.message : "代码读取失败");
    }
  });
}

await server.connect(new StdioServerTransport());
