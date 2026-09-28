export interface ProjectContext {
  projectName: string;
  environment: string;
  repositories: Array<{ name: string; localPath: string; role: string }>;
  skills: Array<{ name: string; path: string; description: string }>;
  tools: Array<{ name: string; description: string }>;
}

export interface InvestigationInput {
  runId: string;
  question: string;
  context: ProjectContext;
}

export type RuntimeEvent =
  | { type: "text"; text: string }
  | { type: "status"; message: string }
  | { type: "complete"; report: InvestigationReport };

export interface InvestigationReport {
  summary: string;
  evidence: string[];
  hypotheses: string[];
  nextSteps: string[];
}

export interface InvestigatorRuntime {
  investigate(input: InvestigationInput): AsyncIterable<RuntimeEvent>;
}
