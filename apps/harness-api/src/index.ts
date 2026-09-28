import { serve } from "@hono/node-server";
import { Hono } from "hono";
import { streamSSE } from "hono/streaming";
import { randomUUID } from "node:crypto";
import {
  investigationRequestSchema,
  type AuditEvent,
  type EnvironmentKind,
  type Project,
  type Repo,
} from "@sre-investigator/domain";
import type {
  InvestigationInput,
  InvestigationReport,
  InvestigatorRuntime,
  RuntimeEvent,
} from "@sre-investigator/runtime-core";
import { CursorRuntime } from "@sre-investigator/runtime-cursor";
import { SelfHostedRuntime } from "@sre-investigator/runtime-selfhosted";
import { loadSkillCatalog } from "./skills.js";

const app = new Hono();
const projects = new Map<string, Project>();
const repos = new Map<string, Repo[]>();
const audits: AuditEvent[] = [];
const agentProfiles = [
  { id: "default-investigator", name: "通用只读排查", enabledSkills: ["http-5xx", "order-payment"] },
  { id: "admin-app-investigator", name: "双端微服务排查", enabledSkills: ["http-5xx", "admin-app-dual"] },
];

class LocalRuntime implements InvestigatorRuntime {
  async *investigate(input: InvestigationInput): AsyncIterable<RuntimeEvent> {
    yield { type: "status", message: "已加载项目、代码仓与排查 Skill" };
    const repoText = input.context.repositories.map((repo) => `${repo.role}: ${repo.name}`).join("、");
    const report: InvestigationReport = {
      summary: `已建立对“${input.question}”的只读排查上下文。`,
      evidence: [`项目 ${input.context.projectName}，环境 ${input.context.environment}`, `可检索仓库：${repoText || "尚未绑定"}`],
      hypotheses: ["请配置 CURSOR_API_KEY 后使用 Cursor Runtime 执行代码级调查。"],
      nextSteps: ["确认问题发生时间、traceId、userId 或 orderId。", "为当前环境绑定 MySQL/Redis/日志只读工具。"],
    };
    yield { type: "complete", report };
  }
}

function runtime(): InvestigatorRuntime {
  if (process.env.RUNTIME_PROVIDER === "cursor") return new CursorRuntime();
  if (process.env.RUNTIME_PROVIDER === "selfhosted") return new SelfHostedRuntime();
  return new LocalRuntime();
}

function audit(runId: string, kind: AuditEvent["kind"], payload: Record<string, unknown>) {
  audits.push({ id: randomUUID(), runId, kind, payload, createdAt: new Date().toISOString() });
}

app.get("/health", (c) => c.json({ status: "ok", runtime: process.env.RUNTIME_PROVIDER ?? "local" }));

app.get("/api/projects", (c) => c.json([...projects.values()]));
app.get("/api/agent-profiles", (c) => c.json(agentProfiles));

app.post("/api/projects", async (c) => {
  const body = await c.req.json<{ name?: string; slug?: string; topology?: string }>();
  if (!body.name || !body.slug) return c.json({ error: "name 和 slug 必填" }, 400);
  const project: Project = {
    id: randomUUID(),
    name: body.name,
    slug: body.slug,
    topology: body.topology ?? "generic",
    createdAt: new Date().toISOString(),
  };
  projects.set(project.id, project);
  repos.set(project.id, []);
  return c.json(project, 201);
});

app.get("/api/projects/:projectId/repos", (c) => c.json(repos.get(c.req.param("projectId")) ?? []));
app.get("/api/skills", async (c) => c.json(await loadSkillCatalog()));

app.post("/api/projects/:projectId/repos", async (c) => {
  const projectId = c.req.param("projectId");
  if (!projects.has(projectId)) return c.json({ error: "项目不存在" }, 404);
  const body = await c.req.json<Partial<Repo>>();
  if (!body.name || !body.localPath) return c.json({ error: "name 和 localPath 必填" }, 400);
  const repo: Repo = {
    id: randomUUID(), projectId, name: body.name, localPath: body.localPath,
    defaultBranch: body.defaultBranch ?? "main", role: body.role ?? "other",
  };
  repos.get(projectId)!.push(repo);
  return c.json(repo, 201);
});

app.post("/api/investigations/stream", async (c) => {
  const parsed = investigationRequestSchema.safeParse(await c.req.json());
  if (!parsed.success) return c.json({ error: parsed.error.flatten() }, 400);
  const request = parsed.data;
  const project = projects.get(request.projectId);
  if (!project) return c.json({ error: "项目不存在" }, 404);
  const runId = randomUUID();
  audit(runId, "run_created", request);
  const skillCatalog = await loadSkillCatalog();
  const context = {
    projectName: project.name,
    environment: request.environment as EnvironmentKind,
    repositories: (repos.get(project.id) ?? []).map(({ name, localPath, role }) => ({ name, localPath, role })),
    skills: skillCatalog,
    tools: [],
  };
  audit(runId, "context_loaded", context);
  return streamSSE(c, async (stream) => {
    try {
      for await (const event of runtime().investigate({ runId, question: request.question, context })) {
        await stream.writeSSE({ event: event.type, data: JSON.stringify(event) });
        audit(runId, event.type === "complete" ? "run_completed" : "runtime_message", event);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "未知运行时错误";
      audit(runId, "run_failed", { message });
      await stream.writeSSE({ event: "error", data: JSON.stringify({ message }) });
    }
  });
});

app.get("/api/investigations/:runId/audit", (c) =>
  c.json(audits.filter((event) => event.runId === c.req.param("runId"))),
);

const port = Number(process.env.PORT ?? 8787);
serve({ fetch: app.fetch, port });
console.log(`Harness API listening on http://localhost:${port}`);
