import { FormEvent, useEffect, useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { api } from "../api";
import type { LayoutContext } from "../layout/AppLayout";
import { configureSessionLimits } from "../conversation/liveSession";
import { IconCheck, IconTrash, IconWarning } from "../icons";
import type { AgentBudget, ModelSnapshot } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";

const PROVIDERS = [
  { id: "dashscope", label: "阿里百炼" },
  { id: "openai", label: "OpenAI 兼容" },
  { id: "anthropic", label: "Anthropic" },
  { id: "ollama", label: "Ollama" },
] as const;

export function ModelPage() {
  const { hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.model.write");
  const [snapshot, setSnapshot] = useState<ModelSnapshot | null>(null);
  const [provider, setProvider] = useState("dashscope");
  const [model, setModel] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [pendingClear, setPendingClear] = useState<{ id: string; label: string } | null>(null);
  const [clearing, setClearing] = useState(false);
  const [budget, setBudget] = useState<AgentBudget | null>(null);
  const [maxUserTurns, setMaxUserTurns] = useState("10");
  const [maxToolCalls, setMaxToolCalls] = useState("16");
  const [budgetSaving, setBudgetSaving] = useState(false);
  const [budgetNotice, setBudgetNotice] = useState("");
  const [budgetError, setBudgetError] = useState("");

  const apply = (next: ModelSnapshot, selected = next.provider) => {
    setSnapshot(next);
    const current = next.providers[selected] ?? next.providers[next.provider];
    setProvider(selected);
    const choices = current?.available?.length ? current.available : (current?.models ?? []);
    const prefill = current?.model || next.model || (choices[0] ?? "");
    setModel(prefill);
    setBaseUrl(current?.baseUrl ?? "");
    setApiKey("");
  };

  const load = async () => {
    const next = await api<ModelSnapshot>("/api/agent/model");
    apply(next);
    return next;
  };

  const applyBudget = (next: AgentBudget) => {
    setBudget(next);
    setMaxUserTurns(String(next.maxUserTurns));
    setMaxToolCalls(String(next.maxToolCalls));
    configureSessionLimits({ maxTurns: next.maxUserTurns, maxToolCalls: next.maxToolCalls });
  };

  const loadBudget = async () => {
    applyBudget(await api<AgentBudget>("/api/agent/budget"));
  };

  useEffect(() => {
    void load().catch((cause: Error) => setError(cause.message));
    void loadBudget().catch((cause: Error) => setBudgetError(cause.message));
  }, []);

  const saveBudget = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBudgetError("");
    setBudgetNotice("");
    const turns = Number(maxUserTurns);
    const tools = Number(maxToolCalls);
    if (!Number.isInteger(turns) || turns < 1 || !Number.isInteger(tools) || tools < 1) {
      setBudgetError("轮次和工具次数都必须是大于 0 的整数。");
      return;
    }
    setBudgetSaving(true);
    try {
      applyBudget(await api<AgentBudget>("/api/agent/budget", {
        method: "PUT",
        body: JSON.stringify({ maxUserTurns: turns, maxToolCalls: tools }),
      }));
      setBudgetNotice("已保存。下一轮对话立刻按新上限执行。");
    } catch (cause) {
      setBudgetError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setBudgetSaving(false);
    }
  };

  const current = snapshot?.providers[provider];
  const choices = current?.available?.length ? current.available : (current?.models ?? []);
  const fields = useMemo(() => ({
    needsApiKey: current?.needsApiKey ?? true,
    needsBaseUrl: current?.needsBaseUrl ?? false,
    hint: current?.hint ?? "",
    apiKeySet: current?.apiKeySet ?? false,
  }), [current]);

  const onProviderChange = (nextProvider: string) => {
    if (!snapshot) {
      setProvider(nextProvider);
      return;
    }
    apply(snapshot, nextProvider);
    setError("");
    setNotice("");
  };

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setNotice("");
    setSaving(true);
    try {
      const next = await api<ModelSnapshot>("/api/agent/model", {
        method: "PUT",
        body: JSON.stringify({
          provider,
          model,
          baseUrl: fields.needsBaseUrl ? baseUrl : undefined,
          apiKey: apiKey.trim() || undefined,
        }),
      });
      apply(next, provider);
      setNotice(`已切换到 ${next.providerLabel}${next.model ? ` / ${next.model}` : ""}，下次对话立即生效。`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const test = async () => {
    setError("");
    setNotice("");
    setTesting(true);
    try {
      const result = await api<{
        ok: boolean;
        reply?: string;
        provider?: string;
        model?: string;
        models?: string[];
        snapshot?: ModelSnapshot;
      }>("/api/agent/model/test", {
        method: "POST",
        body: JSON.stringify({
          provider,
          model: model.trim() || undefined,
          baseUrl: fields.needsBaseUrl ? baseUrl : undefined,
          apiKey: apiKey.trim() || undefined,
        }),
      });
      if (result.snapshot) {
        apply(result.snapshot, provider);
      } else if (result.model) {
        setModel(result.model);
      }
      const count = result.models?.length ?? 0;
      setNotice(`探测成功，${count} 个模型可在下拉框中选择。当前 ${result.provider} / ${result.model}，回复 ${result.reply || "ok"}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "探测失败");
      try {
        apply(await api<ModelSnapshot>("/api/agent/model"), provider);
      } catch {
        /* 列表可能已收录，刷新失败不影响错误提示 */
      }
    } finally {
      setTesting(false);
    }
  };

  const clearProvider = async () => {
    if (!pendingClear) return;
    setError("");
    setNotice("");
    setClearing(true);
    try {
      const next = await api<ModelSnapshot>(`/api/agent/model/providers/${pendingClear.id}`, { method: "DELETE" });
      apply(next, pendingClear.id);
      setPendingClear(null);
      setNotice(`已清除 ${pendingClear.label} 的本机配置。`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除配置失败");
    } finally {
      setClearing(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">全局配置</p>
          <h2>模型</h2>
          {!canWrite ? <p className="notice">当前账号仅可查看模型配置，修改需管理员权限。</p> : null}
          <p>填好 Key / Base URL 后点测试连接，再从下拉框手动选择模型。同一页下方可改对话轮次和单轮工具次数。</p>
        </div>
        {snapshot ? (
          <span className={`badge ${snapshot.available ? "ok" : "warn"}`}>
            {snapshot.available ? <IconCheck size={12} /> : <IconWarning size={12} />}
            {snapshot.available ? `${snapshot.providerLabel} · ${snapshot.model}` : (snapshot.message || "模型未就绪")}
          </span>
        ) : null}
      </div>

      <div className="grid two">
        <section className="card">
          <h3>供应商</h3>
          <div className="provider-grid">
            {PROVIDERS.map((item) => {
              const ready = snapshot?.providers[item.id]?.configured;
              return (
                <button
                  key={item.id}
                  type="button"
                  className={`provider-chip${provider === item.id ? " active" : ""}`}
                  onClick={() => onProviderChange(item.id)}
                >
                  <span>
                    <b>{item.label}</b>
                    <small>
                      {snapshot?.providers[item.id]?.available?.length
                        ? `${snapshot.providers[item.id]?.available?.length} 个可选`
                        : snapshot?.providers[item.id]?.model || "待测试"}
                    </small>
                  </span>
                  <span className={`badge ${ready ? "ok" : "warn"}`}>
                    {ready ? "就绪" : "未就绪"}
                  </span>
                </button>
              );
            })}
          </div>
          <form className="form" onSubmit={(event) => void save(event)}>
            <label htmlFor="model-pick">选择模型
              <select
                id="model-pick"
                value={choices.includes(model) ? model : ""}
                onChange={(event) => setModel(event.target.value)}
              >
                <option value="">{choices.length ? "请选择可用模型" : "先测试连接，再从这里选择"}</option>
                {choices.map((name) => (
                  <option key={name} value={name}>{name}</option>
                ))}
              </select>
            </label>
            <label htmlFor="model-name">当前模型
              <input
                id="model-name"
                value={model}
                onChange={(event) => setModel(event.target.value)}
                placeholder="也可手填模型名"
              />
            </label>
            {fields.needsBaseUrl ? (
              <label htmlFor="model-base-url">Base URL
                <input id="model-base-url" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} placeholder="https://api.openai.com" />
              </label>
            ) : null}
            {fields.needsApiKey ? (
              <label htmlFor="model-api-key">API Key{fields.apiKeySet ? "（已配置，留空则不改）" : ""}
                <input
                  id="model-api-key"
                  type="password"
                  value={apiKey}
                  onChange={(event) => setApiKey(event.target.value)}
                  placeholder={fields.apiKeySet ? "********" : "sk-…"}
                  autoComplete="off"
                />
              </label>
            ) : null}
            <p className="notice">{fields.hint}</p>
            <div className="form-actions">
              <button className="btn" disabled={saving || !canWrite}>{saving ? "保存中…" : "保存并启用"}</button>
              <button className="btn secondary" type="button" disabled={testing || !canWrite} onClick={() => void test()}>
                {testing ? "探测中…" : "测试连接"}
              </button>
              {canWrite ? (
              <button
                className="btn danger"
                type="button"
                disabled={!current?.configured}
                onClick={() => setPendingClear({
                  id: provider,
                  label: PROVIDERS.find((item) => item.id === provider)?.label ?? provider,
                })}
              >
                <IconTrash size={13} />
                删除配置
              </button>
              ) : null}
            </div>
          </form>
          {notice ? <p className="notice ok">{notice}</p> : null}
          {error ? <p className="error">{error}</p> : null}
        </section>

        <section className="card">
          <h3>各厂商状态</h3>
          <div className="list">
            {PROVIDERS.map((item) => {
              const detail = snapshot?.providers[item.id];
              return (
                <div key={item.id} className={`item${provider === item.id ? " active" : ""}`}>
                  <b>{item.label}</b>
                  <span>
                    <span className={`badge ${detail?.configured ? "ok" : "warn"}`}>
                      {detail?.configured ? <IconCheck size={12} /> : <IconWarning size={12} />}
                      {detail?.configured ? "就绪" : "未就绪"}
                    </span>
                    {"  "}{detail?.available?.length ? `${detail.available.length} 个可选 · ${detail.model}` : (detail?.model || "待测试")}
                    {detail?.baseUrl ? ` · ${detail.baseUrl}` : ""}
                  </span>
                  {detail?.configured ? (
                    <div className="item-actions">
                      <button className="btn ghost" type="button" onClick={() => onProviderChange(item.id)}>修改</button>
                      <button
                        className="btn ghost danger"
                        type="button"
                        onClick={() => setPendingClear({ id: item.id, label: item.label })}
                      >
                        <IconTrash size={13} />
                        删除
                      </button>
                    </div>
                  ) : (
                    <div className="item-actions">
                      <button className="btn ghost" type="button" onClick={() => onProviderChange(item.id)}>修改</button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
          <p className="notice">OpenAI 兼容可改 Base URL 接 DeepSeek、月之暗面、智谱、SiliconFlow。Key 只保存在本机控制面库，接口不回显。删除配置只清本机覆盖，不会改 yml 默认值。</p>
        </section>
      </div>

      <section className="card" style={{ marginTop: 20 }}>
        <h3>对话预算</h3>
        <p className="notice">同一会话最多问几轮、单轮排查最多调几次只读工具。保存后立刻生效，重启也会记住。</p>
        <form className="form" onSubmit={(event) => void saveBudget(event)}>
          <div className="grid two">
            <label htmlFor="budget-turns">同一会话最多几轮
              <input
                id="budget-turns"
                type="number"
                min={1}
                step={1}
                value={maxUserTurns}
                onChange={(event) => setMaxUserTurns(event.target.value)}
              />
            </label>
            <label htmlFor="budget-tools">单轮最多几次工具调用
              <input
                id="budget-tools"
                type="number"
                min={1}
                step={1}
                value={maxToolCalls}
                onChange={(event) => setMaxToolCalls(event.target.value)}
              />
            </label>
          </div>
          <p className="notice">
            yaml 默认 {budget?.defaultUserTurns ?? 10} 轮 / {budget?.defaultToolCalls ?? 100} 次。
            这里改的是控制面覆盖，不改 yml。
          </p>
          <div className="form-actions">
            <button className="btn" disabled={budgetSaving || !canWrite}>{budgetSaving ? "保存中…" : "保存预算"}</button>
          </div>
          {budgetNotice ? <p className="notice ok">{budgetNotice}</p> : null}
          {budgetError ? <p className="error">{budgetError}</p> : null}
        </form>
      </section>
      {pendingClear ? (
        <ConfirmDialog
          title="删除模型配置"
          message={`将清除「${pendingClear.label}」保存在本机的 Key、Base URL 和已收录模型。yml 里的默认配置不受影响。`}
          busy={clearing}
          onCancel={() => setPendingClear(null)}
          onConfirm={() => void clearProvider()}
        />
      ) : null}
    </>
  );
}
