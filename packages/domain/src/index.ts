import { z } from "zod";

export const environmentKinds = ["local", "test", "gray", "prod"] as const;
export type EnvironmentKind = (typeof environmentKinds)[number];

export const toolKinds = [
  "mysql_readonly",
  "redis_readonly",
  "es_search",
  "log_by_trace",
  "nacos_config_read",
] as const;
export type ToolKind = (typeof toolKinds)[number];

export const projectSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(2).max(120),
  slug: z.string().regex(/^[a-z0-9-]+$/),
  topology: z.string().default("generic"),
  createdAt: z.string().datetime(),
});
export type Project = z.infer<typeof projectSchema>;

export const repoSchema = z.object({
  id: z.string().uuid(),
  projectId: z.string().uuid(),
  name: z.string().min(1),
  localPath: z.string().min(1),
  defaultBranch: z.string().min(1).default("main"),
  role: z.enum(["admin", "app", "other"]).default("other"),
});
export type Repo = z.infer<typeof repoSchema>;

export const investigationRequestSchema = z.object({
  projectId: z.string().uuid(),
  environment: z.enum(environmentKinds),
  question: z.string().min(8).max(8000),
});
export type InvestigationRequest = z.infer<typeof investigationRequestSchema>;

export type InvestigationStatus =
  | "queued"
  | "running"
  | "needs_input"
  | "completed"
  | "failed";

export interface ToolBinding {
  id: string;
  projectId: string;
  environment: EnvironmentKind;
  toolInstanceId: string;
  enabledTools: string[];
}

export interface AuditEvent {
  id: string;
  runId: string;
  kind: "run_created" | "context_loaded" | "tool_call" | "runtime_message" | "run_completed" | "run_failed";
  payload: Record<string, unknown>;
  createdAt: string;
}
