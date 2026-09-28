import { jsonb, pgTable, text, timestamp, uuid } from "drizzle-orm/pg-core";

export const projects = pgTable("projects", {
  id: uuid("id").primaryKey(),
  name: text("name").notNull(),
  slug: text("slug").notNull().unique(),
  topology: text("topology").notNull().default("generic"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const repositories = pgTable("repositories", {
  id: uuid("id").primaryKey(),
  projectId: uuid("project_id").notNull().references(() => projects.id),
  name: text("name").notNull(),
  localPath: text("local_path").notNull(),
  defaultBranch: text("default_branch").notNull(),
  role: text("role").notNull(),
});

export const toolTemplates = pgTable("tool_templates", {
  id: uuid("id").primaryKey(),
  name: text("name").notNull().unique(),
  kind: text("kind").notNull(),
  policy: jsonb("policy").notNull(),
});

export const toolInstances = pgTable("tool_instances", {
  id: uuid("id").primaryKey(),
  templateId: uuid("template_id").notNull().references(() => toolTemplates.id),
  name: text("name").notNull(),
  encryptedConfig: text("encrypted_config").notNull(),
});

export const toolBindings = pgTable("tool_bindings", {
  id: uuid("id").primaryKey(),
  projectId: uuid("project_id").notNull().references(() => projects.id),
  environment: text("environment").notNull(),
  toolInstanceId: uuid("tool_instance_id").notNull().references(() => toolInstances.id),
  enabledTools: jsonb("enabled_tools").notNull(),
});

export const investigationRuns = pgTable("investigation_runs", {
  id: uuid("id").primaryKey(),
  projectId: uuid("project_id").notNull().references(() => projects.id),
  environment: text("environment").notNull(),
  question: text("question").notNull(),
  status: text("status").notNull(),
  report: jsonb("report"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const auditEvents = pgTable("audit_events", {
  id: uuid("id").primaryKey(),
  runId: uuid("run_id").notNull().references(() => investigationRuns.id),
  kind: text("kind").notNull(),
  payload: jsonb("payload").notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});
