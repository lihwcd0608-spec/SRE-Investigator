import type { InvestigationInput, InvestigatorRuntime, RuntimeEvent } from "@sre-investigator/runtime-core";

/** OpenAI-compatible fallback adapter. The public runtime contract remains unchanged. */
export class SelfHostedRuntime implements InvestigatorRuntime {
  async *investigate(input: InvestigationInput): AsyncIterable<RuntimeEvent> {
    const baseUrl = process.env.SELF_HOSTED_AI_BASE_URL;
    const apiKey = process.env.SELF_HOSTED_AI_API_KEY;
    const model = process.env.SELF_HOSTED_AI_MODEL;
    if (!baseUrl || !apiKey || !model) {
      throw new Error("SELF_HOSTED_AI_BASE_URL、SELF_HOSTED_AI_API_KEY、SELF_HOSTED_AI_MODEL 均为必填");
    }
    yield { type: "status", message: "正在调用 OpenAI-compatible Runtime" };
    const response = await fetch(`${baseUrl.replace(/\/$/, "")}/chat/completions`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: "你是只读故障排查助手。必须输出事实、证据、假设和下一步。" },
          { role: "user", content: `${input.question}\n\n项目上下文：${JSON.stringify(input.context)}` },
        ],
      }),
    });
    if (!response.ok) throw new Error(`模型请求失败：HTTP ${response.status}`);
    const body = await response.json() as { choices?: Array<{ message?: { content?: string } }> };
    const text = body.choices?.[0]?.message?.content ?? "模型未返回文本结果。";
    yield { type: "text", text };
    yield { type: "complete", report: { summary: text, evidence: [], hypotheses: [], nextSteps: [] } };
  }
}
