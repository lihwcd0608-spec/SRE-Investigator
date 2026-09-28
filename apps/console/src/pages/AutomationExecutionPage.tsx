import { useEffect, useState } from "react";
import { Link, useOutletContext, useParams } from "react-router-dom";
import { api } from "../api";
import { IconArrowLeft, IconPulse } from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import type { AutomationExecutionDetail } from "../types";

function formatTime(value?: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

function statusLabel(status?: string | null) {
  if (status === "OK") return "正常";
  if (status === "FINDINGS") return "有发现";
  if (status === "FAILED") return "失败";
  if (status === "RUNNING") return "执行中";
  return status || "未完成";
}

function statusTone(status?: string | null) {
  if (status === "FAILED") return "danger";
  if (status === "FINDINGS") return "warn";
  if (status === "OK") return "ok";
  if (status === "RUNNING") return "accent";
  return "";
}

function scheduleLabel(kind?: string, expr?: string) {
  if (kind === "EVERY") return `每 ${expr}`;
  if (kind === "CRON") return `cron ${expr}`;
  return `一次 ${expr}`;
}

export function AutomationExecutionPage() {
  const { projects } = useOutletContext<LayoutContext>();
  const { jobId, executionId } = useParams();
  const [detail, setDetail] = useState<AutomationExecutionDetail | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!jobId || !executionId) return;
    let cancelled = false;
    const load = async () => {
      try {
        const next = await api<AutomationExecutionDetail>(
          `/api/automations/${jobId}/executions/${executionId}`,
        );
        if (!cancelled) {
          setDetail(next);
          setError("");
        }
        return next;
      } catch (cause) {
        if (!cancelled) setError(cause instanceof Error ? cause.message : "加载执行详情失败");
        return null;
      }
    };
    void load();
    const timer = window.setInterval(() => {
      void load().then((next) => {
        if (next && next.execution.status !== "RUNNING") window.clearInterval(timer);
      });
    }, 2500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [jobId, executionId]);

  const back = "/automations";
  const job = detail?.job;
  const execution = detail?.execution;
  const project = job ? projects.find((item) => item.id === job.projectId) : undefined;

  return (
    <div className="auto-exec">
      <div className="page-header">
        <div>
          <p className="kicker">{job?.environment ?? "定时"}{project ? ` · ${project.slug}` : ""}</p>
          <h2>执行详情</h2>
          <p>{job ? `${job.name} · ${scheduleLabel(job.scheduleKind, job.scheduleExpr)}` : "查看这次定时执行的报告，不进入对话。"}</p>
        </div>
        <div className="page-header-actions">
          <Link className="btn secondary" to={back}>
            <IconArrowLeft size={16} />
            返回定时
          </Link>
        </div>
      </div>

      {error ? <p className="error">{error}</p> : null}

      {!detail && !error ? (
        <section className="card">
          <div className="empty">
            <span className="empty-icon"><IconPulse size={22} /></span>
            <h3>正在加载执行记录…</h3>
          </div>
        </section>
      ) : null}

      {detail && execution ? (
        <>
          <section className="card">
            <div className="auto-exec-meta">
              <span className={`badge ${statusTone(execution.status)}`}>{statusLabel(execution.status)}</span>
              <span className="tag">{job?.environment}</span>
              {project ? <span className="tag">{project.name} · {project.slug}</span> : null}
              <span className="tag">{job?.payloadKind === "K8S_INSPECT" ? "K8s 巡检" : "模型轮"}</span>
              <span className="tag">{execution.triggerType === "MANUAL" ? "手动" : "调度"}</span>
            </div>
            <p className="auto-detail-sub">
              开始 {formatTime(execution.startedAt)}
              {execution.finishedAt ? ` · 结束 ${formatTime(execution.finishedAt)}` : ""}
            </p>
            {execution.summary ? <p className="auto-exec-summary">{execution.summary}</p> : null}
            {execution.errorMessage ? <p className="error">{execution.errorMessage}</p> : null}
          </section>

          <section className="card auto-exec-body">
            <div className="card-head">
              <h3>报告</h3>
              <span className="badge">{detail.messages.length} 条</span>
            </div>
            {detail.messages.length === 0 ? (
              <div className="empty">
                <h3>{execution.status === "RUNNING" ? "还在执行" : "还没有写出报告"}</h3>
                <p>{execution.status === "RUNNING" ? "跑完后这里会显示提示词和结果。" : "这次执行没有落库消息。"}</p>
              </div>
            ) : (
              <div className="chat-transcript">
                {detail.messages.map((message) => (
                  message.role === "user" ? (
                    <div className="bubble user" key={message.id}>
                      <small>提示词 · {formatTime(message.createdAt)}</small>
                      <div>{message.content}</div>
                    </div>
                  ) : (
                    <div className="bubble assistant" key={message.id}>
                      <small>结果 · {formatTime(message.createdAt)}</small>
                      <pre className="report">{message.content}</pre>
                    </div>
                  )
                ))}
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
