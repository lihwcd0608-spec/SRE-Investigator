import type {
  InvestigationInput,
  InvestigatorRuntime,
  RuntimeEvent,
} from "@sre-investigator/runtime-core";

/**
 * Cursor local-agent adapter. It deliberately refuses to start without an API key;
 * the harness can use LocalRuntime for demo and configuration validation.
 */
export class CursorRuntime implements InvestigatorRuntime {
  async *investigate(input: InvestigationInput): AsyncIterable<RuntimeEvent> {
    const apiKey = process.env.CURSOR_API_KEY;
    if (!apiKey) {
      throw new Error("CURSOR_API_KEY is required when RUNTIME_PROVIDER=cursor");
    }

    yield { type: "status", message: "正在初始化 Cursor 本地 Agent" };
    const module = await import("@cursor/sdk");
    const cwd = input.context.repositories[0]?.localPath ?? process.cwd();
    const prompt = buildPrompt(input);
    const result = await module.Agent.prompt(prompt, {
      apiKey,
      local: { cwd },
    });
    const resultText = result.result ?? "Cursor 未返回文本结果。";

    yield { type: "text", text: resultText };
    yield {
      type: "complete",
      report: {
        summary: resultText,
        evidence: [],
        hypotheses: [],
        nextSteps: ["请根据 Cursor 输出核实线上证据。"],
      },
    };
  }
}

function buildPrompt(input: InvestigationInput): string {
  const context = JSON.stringify(input.context, null, 2);
  return `你是只读故障排查 Agent。不得修改代码、配置、数据库或缓存。
当前问题：${input.question}
项目上下文：
${context}
按“现象、证据、根因假设、下一步”输出；先读代码，再明确指出需要的运行时证据。`;
}
