import { FormEvent, useEffect, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { api } from "../api";
import { IconPencil, IconPlus, IconPulse, IconTrash, IconX } from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import {
  type AutomationExecution,
  type AutomationJob,
  parseEnvironment,
  type PayloadKind,
  type ScheduleKind,
  type SessionTarget,
} from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";

const DEFAULT_PROMPT = "定时只读巡检：NotReady 节点、CrashLoop/OOM、重启次数、Warning 事件。";
const LAST_AUTO_PROJECT_KEY = "sre-investigator.automationProjectId";

type FormMode = "closed" | "create" | "edit";

function formatTime(value?: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

function statusLabel(status?: string | null) {
  if (status === "OK") return "正常";
  if (status === "FINDINGS") return "有发现";
  if (status === "FAILED") return "失败";
  if (status === "RUNNING") return "执行中";
  return status || "未跑过";
}

function statusTone(status?: string | null) {
  if (status === "FAILED") return "danger";
  if (status === "FINDINGS") return "warn";
  if (status === "OK") return "ok";
  if (status === "RUNNING") return "accent";
  return "";
}

function scheduleLabel(job: Pick<AutomationJob, "scheduleKind" | "scheduleExpr">) {
  if (job.scheduleKind === "EVERY") return `每 ${job.scheduleExpr}`;
  if (job.scheduleKind === "CRON") return `cron ${job.scheduleExpr}`;
  return `一次 ${job.scheduleExpr}`;
}

export function AutomationsPage() {
  const { projects, environment, hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.automations.write");
  const canDelete = hasPermission("action.automations.delete");
  const [jobs, setJobs] = useState<AutomationJob[]>([]);
  const [formProjectId, setFormProjectId] = useState("");
  const [executions, setExecutions] = useState<AutomationExecution[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [formMode, setFormMode] = useState<FormMode>("closed");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [runningId, setRunningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<AutomationJob | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [name, setName] = useState("K8s 只读巡检");
  const [enabled, setEnabled] = useState(true);
  const [scheduleKind, setScheduleKind] = useState<ScheduleKind>("EVERY");
  const [scheduleExpr, setScheduleExpr] = useState("15m");
  const [payloadKind, setPayloadKind] = useState<PayloadKind>("K8S_INSPECT");
  const [sessionTarget, setSessionTarget] = useState<SessionTarget>("ISOLATED");
  const [prompt, setPrompt] = useState(DEFAULT_PROMPT);

  const selected = jobs.find((job) => job.id === selectedId);
  const editing = formMode === "edit" ? selected : undefined;

  const projectLabel = (projectId: string) => {
    const item = projects.find((project) => project.id === projectId);
    return item ? `${item.name} · ${item.slug}` : "未找到项目";
  };

  const load = async () => {
    const next = await api<AutomationJob[]>(`/api/automations?environment=${encodeURIComponent(environment)}`);
    setJobs(next);
    setSelectedId((current) => {
      if (current && next.some((job) => job.id === current)) return current;
      return next[0]?.id ?? "";
    });
    return next;
  };

  const loadExecutions = async (jobId: string, projectId?: string) => {
    const resolvedProjectId = projectId ?? jobs.find((item) => item.id === jobId)?.projectId;
    if (!resolvedProjectId) {
      setExecutions([]);
      return;
    }
    setExecutions(await api<AutomationExecution[]>(`/api/projects/${resolvedProjectId}/automations/${jobId}/executions`));
  };

  useEffect(() => {
    setError("");
    if (formMode !== "closed") closeForm();
    void load().catch((cause: Error) => setError(cause.message));
  }, [environment]);

  useEffect(() => {
    if (!selectedId) {
      setExecutions([]);
      return;
    }
    void loadExecutions(selectedId).catch((cause: Error) => setError(cause.message));
  }, [selectedId, jobs]);

  useEffect(() => {
    if (formMode === "closed") return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !saving) closeForm();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [formMode, saving]);

  const fillForm = (job?: AutomationJob) => {
    setName(job?.name ?? "K8s 只读巡检");
    setEnabled(job?.enabled ?? true);
    setScheduleKind(job?.scheduleKind ?? "EVERY");
    setScheduleExpr(job?.scheduleExpr ?? "15m");
    setPayloadKind(job?.payloadKind ?? "K8S_INSPECT");
    setSessionTarget(job?.sessionTarget ?? "ISOLATED");
    setPrompt(job?.prompt || ((job?.payloadKind ?? "K8S_INSPECT") === "K8S_INSPECT" ? DEFAULT_PROMPT : ""));
  };

  const closeForm = () => {
    setFormMode("closed");
    setError("");
  };

  const startCreate = () => {
    const remembered = localStorage.getItem(LAST_AUTO_PROJECT_KEY);
    setFormProjectId(remembered && projects.some((item) => item.id === remembered) ? remembered : (projects[0]?.id ?? ""));
    fillForm();
    setFormMode("create");
    setError("");
  };

  const startEdit = (job: AutomationJob) => {
    setSelectedId(job.id);
    fillForm(job);
    setFormMode("edit");
    setError("");
  };

  const selectJob = (job: AutomationJob) => {
    setSelectedId(job.id);
    if (formMode === "edit") closeForm();
  };

  const body = () => ({
    name,
    environment,
    enabled,
    scheduleKind,
    scheduleExpr,
    timezone: "Asia/Shanghai",
    payloadKind,
    sessionTarget,
    prompt,
    deleteAfterRun: scheduleKind === "AT",
    timeoutSeconds: 600,
  });

  const onSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      const projectId = formMode === "edit" && editing ? editing.projectId : formProjectId;
      if (!projectId) {
        throw new Error("请选择任务所属项目");
      }
      if (formMode === "create") localStorage.setItem(LAST_AUTO_PROJECT_KEY, projectId);
      const saved = await api<AutomationJob>(
        formMode === "edit" && editing
          ? `/api/projects/${projectId}/automations/${editing.id}`
          : `/api/projects/${projectId}/automations`,
        { method: formMode === "edit" && editing ? "PUT" : "POST", body: JSON.stringify(body()) },
      );
      closeForm();
      setSelectedId(saved.id);
      await load();
      await loadExecutions(saved.id, saved.projectId);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存定时任务失败");
    } finally {
      setSaving(false);
    }
  };

  const onRun = async (job: AutomationJob) => {
    setError("");
    setRunningId(job.id);
    setSelectedId(job.id);
    try {
      await api(`/api/projects/${job.projectId}/automations/${job.id}/run`, { method: "POST" });
      await load();
      await loadExecutions(job.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "立即执行失败");
    } finally {
      setRunningId("");
    }
  };

  const onToggle = async (job: AutomationJob) => {
    setError("");
    try {
      await api(`/api/projects/${job.projectId}/automations/${job.id}`, {
        method: "PATCH",
        body: JSON.stringify({ enabled: !job.enabled }),
      });
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "更新启用状态失败");
    }
  };

  const onDelete = async () => {
    if (!pendingDelete) return;
    setError("");
    setDeleting(true);
    try {
      const removedId = pendingDelete.id;
      await api(`/api/projects/${pendingDelete.projectId}/automations/${removedId}`, { method: "DELETE" });
      setPendingDelete(null);
      if (formMode === "edit" && selectedId === removedId) closeForm();
      const remaining = jobs.filter((job) => job.id !== removedId);
      setSelectedId(remaining[0]?.id ?? "");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">{environment}</p>
          <h2>定时</h2>
          <p>任务按顶栏环境筛选。左侧选任务，右侧看执行记录。点记录进详情，和对话页分开。</p>
        </div>
        <div className="page-header-actions">
          {canWrite ? (
            <button className="btn" type="button" onClick={startCreate}>
              <IconPlus size={16} />
              新建任务
            </button>
          ) : null}
        </div>
      </div>

      {jobs.length === 0 ? (
        <section className="card">
          {error && formMode === "closed" ? <p className="error">{error}</p> : null}
          <div className="empty empty-hero">
            <span className="empty-icon"><IconPulse size={22} /></span>
            <h3>{environment} 还没有定时任务</h3>
            <p>先建一条当前环境的 K8s 只读巡检。保存后不会立刻跑，可再点「立即执行」。切到其他环境会看到对应任务。</p>
            {canWrite ? (
            <button className="btn" type="button" onClick={startCreate}>
              <IconPlus size={16} />
              新建任务
            </button>
            ) : null}
          </div>
        </section>
      ) : (
        <div className="auto-page">
          <section className="card auto-pane">
            <div className="card-head">
              <h3>任务</h3>
              <span className="badge">{jobs.length} 个</span>
            </div>
            {error && formMode === "closed" ? <p className="error">{error}</p> : null}
            <div className="list">
              {jobs.map((job) => (
                <div className={`item auto-job${selectedId === job.id ? " active" : ""}`} key={job.id}>
                  <button
                    type="button"
                    className="auto-job-select"
                    aria-pressed={selectedId === job.id}
                    onClick={() => selectJob(job)}
                  >
                    <span className="auto-job-title">
                      <b>{job.name}</b>
                      <span className={`badge ${job.enabled ? "ok" : ""}`}>{job.enabled ? "启用" : "停用"}</span>
                    </span>
                    <span className="repo-meta">
                      <span className="tag">{parseEnvironment(job.environment)}</span>
                      <span className="tag">{projectLabel(job.projectId)}</span>
                      <span className="tag">{job.payloadKind === "K8S_INSPECT" ? "K8s 巡检" : "模型轮"}</span>
                      {job.running ? <span className="badge accent">进行中</span> : null}
                      {job.lastStatus ? (
                        <span className={`badge ${statusTone(job.lastStatus)}`}>{statusLabel(job.lastStatus)}</span>
                      ) : (
                        <span className="badge">未跑过</span>
                      )}
                    </span>
                    <small>{scheduleLabel(job)} · 下次 {formatTime(job.nextRunAt)}</small>
                  </button>
                  <div className="item-actions">
                    {canWrite ? (
                      <button
                        className="btn ghost"
                        type="button"
                        disabled={runningId === job.id || job.running}
                        onClick={() => void onRun(job)}
                      >
                        {runningId === job.id || job.running ? "执行中…" : "立即执行"}
                      </button>
                    ) : null}
                    {canWrite ? (
                      <button className="btn ghost" type="button" onClick={() => void onToggle(job)}>
                        {job.enabled ? "停用" : "启用"}
                      </button>
                    ) : null}
                    {canWrite ? (
                      <button className="btn ghost" type="button" onClick={() => startEdit(job)}>
                        <IconPencil size={13} />
                        修改
                      </button>
                    ) : null}
                    {canDelete ? (
                      <button className="btn ghost danger" type="button" onClick={() => setPendingDelete(job)}>
                        <IconTrash size={13} />
                        删除
                      </button>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section className="card auto-pane">
            {selected ? (
              <>
                <div className="card-head">
                  <div>
                    <h3>执行记录</h3>
                    <p className="auto-detail-sub">{selected.name} · {scheduleLabel(selected)}</p>
                  </div>
                  <span className="badge">{executions.length} 次</span>
                </div>
                {executions.length === 0 ? (
                  <p className="notice">这条任务还没有执行记录。点「立即执行」或等下一周期。</p>
                ) : (
                  <div className="auto-runs">
                    {executions.map((item) => (
                      <div className="auto-run" key={item.id}>
                        <span className={`badge ${statusTone(item.status)}`}>{statusLabel(item.status)}</span>
                        <div className="auto-run-meta">
                          <b>{item.summary || statusLabel(item.status)}</b>
                          <small>{item.triggerType === "MANUAL" ? "手动" : "调度"} · {formatTime(item.startedAt)}</small>
                          {item.errorMessage ? <p className="error">{item.errorMessage}</p> : null}
                        </div>
                        <Link
                          className="btn ghost"
                          to={`/automations/${selected.id}/executions/${item.id}`}
                        >
                          详情
                        </Link>
                      </div>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div className="empty">
                <h3>还没有选中任务</h3>
                <p>从左侧点一条，这里会显示它的执行记录。</p>
              </div>
            )}
          </section>
        </div>
      )}

      {formMode !== "closed" ? (
        <div className="modal-scrim" role="presentation">
          <div
            className="modal auto-sheet"
            role="dialog"
            aria-modal="true"
            aria-labelledby="auto-sheet-title"
          >
            <div className="card-head">
              <h3 id="auto-sheet-title">{formMode === "create" ? "新建任务" : `修改 · ${editing?.name ?? ""}`}</h3>
              <button className="btn ghost" type="button" onClick={closeForm} disabled={saving}>
                <IconX size={14} />
                关闭
              </button>
            </div>
            <form className="form" onSubmit={(event) => void onSave(event)}>
              <label htmlFor="auto-name">名称
                <input id="auto-name" value={name} onChange={(event) => setName(event.target.value)} required autoFocus />
              </label>
              <p className="notice">任务环境跟随顶栏，当前是 {environment}。</p>
              <div className="row">
                {formMode === "create" ? (
                  <label htmlFor="auto-project">所属项目
                    <select
                      id="auto-project"
                      value={formProjectId}
                      onChange={(event) => setFormProjectId(event.target.value)}
                      required
                    >
                      {projects.length === 0 ? <option value="">没有可用项目</option> : null}
                      {projects.map((item) => (
                        <option key={item.id} value={item.id}>{item.name} · {item.slug}</option>
                      ))}
                    </select>
                  </label>
                ) : (
                  <label>所属项目
                    <input value={editing ? projectLabel(editing.projectId) : ""} readOnly />
                  </label>
                )}
                <label htmlFor="auto-payload">做什么
                  <select
                    id="auto-payload"
                    value={payloadKind}
                    onChange={(event) => {
                      const next = event.target.value as PayloadKind;
                      setPayloadKind(next);
                      if (next === "K8S_INSPECT" && !prompt) setPrompt(DEFAULT_PROMPT);
                    }}
                  >
                    <option value="K8S_INSPECT">K8s 只读巡检</option>
                    <option value="AGENT_TURN">自动开一轮排查</option>
                  </select>
                </label>
              </div>
              <div className="row">
                <label htmlFor="auto-kind">调度
                  <select
                    id="auto-kind"
                    value={scheduleKind}
                    onChange={(event) => {
                      const next = event.target.value as ScheduleKind;
                      setScheduleKind(next);
                      if (next === "EVERY" && !/^\d+[smhd]?$/.test(scheduleExpr)) setScheduleExpr("15m");
                      if (next === "CRON" && scheduleExpr.split(/\s+/).length < 5) setScheduleExpr("0 9 * * *");
                    }}
                  >
                    <option value="EVERY">间隔 EVERY</option>
                    <option value="CRON">cron</option>
                    <option value="AT">一次性 AT</option>
                  </select>
                </label>
                <label htmlFor="auto-expr">
                  {scheduleKind === "EVERY" ? "间隔" : scheduleKind === "CRON" ? "cron" : "时间"}
                  <input
                    id="auto-expr"
                    value={scheduleExpr}
                    onChange={(event) => setScheduleExpr(event.target.value)}
                    required
                    placeholder={scheduleKind === "EVERY" ? "15m" : scheduleKind === "CRON" ? "0 9 * * *" : "2026-09-01T10:00:00+08:00"}
                  />
                </label>
              </div>
              <label htmlFor="auto-session">会话
                <select id="auto-session" value={sessionTarget} onChange={(event) => setSessionTarget(event.target.value as SessionTarget)}>
                  <option value="ISOLATED">每次新对话</option>
                  <option value="THREAD">复用同一对话</option>
                </select>
              </label>
              <label htmlFor="auto-prompt">{payloadKind === "K8S_INSPECT" ? "巡检说明" : "提示词"}
                <textarea
                  id="auto-prompt"
                  rows={3}
                  value={prompt}
                  onChange={(event) => setPrompt(event.target.value)}
                  required={payloadKind === "AGENT_TURN"}
                />
              </label>
              <label className="check-item">
                <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
                保存后启用（间隔任务从下一周期开始）
              </label>
              {error ? <p className="error">{error}</p> : null}
              <div className="form-actions">
                <button className="btn secondary" type="button" onClick={closeForm} disabled={saving}>取消</button>
                <button className="btn" disabled={saving}>
                  {saving ? "保存中…" : formMode === "create" ? "保存任务" : "保存配置"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {pendingDelete ? (
        <ConfirmDialog
          title="删除定时任务"
          message={`将删除「${pendingDelete.name}」及其执行历史。已写入的对话不会删。`}
          busy={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}
    </>
  );
}
