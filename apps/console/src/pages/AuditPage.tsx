import { useEffect, useState } from "react";
import { api } from "../api";
import { IconShield } from "../icons";
import type { AuditEvent } from "../types";

type Tab = "OP" | "LOGIN";

export function AuditPage() {
  const [tab, setTab] = useState<Tab>("OP");
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const load = async (category: Tab) => {
    setLoading(true);
    setError("");
    try {
      const next = await api<AuditEvent[]>(`/api/audit-events?category=${category}`);
      setEvents(next);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "加载审计日志失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(tab);
  }, [tab]);

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">安全</p>
          <h2>审计日志</h2>
          <p>关键配置变更与登录记录。敏感字段已脱敏。</p>
        </div>
        <div className="page-header-actions">
          <div className="segmented">
            <button className={tab === "OP" ? "active" : ""} type="button" onClick={() => setTab("OP")}>操作日志</button>
            <button className={tab === "LOGIN" ? "active" : ""} type="button" onClick={() => setTab("LOGIN")}>登录日志</button>
          </div>
          <button className="btn secondary" type="button" disabled={loading} onClick={() => void load(tab)}>
            刷新
          </button>
        </div>
      </div>

      {error ? <p className="error">{error}</p> : null}

      <section className="card">
        <div className="card-head">
          <h3>{tab === "LOGIN" ? "登录日志" : "操作日志"}</h3>
          <span className="badge">{events.length} 条</span>
        </div>
        {events.length === 0 ? (
          <div className="empty empty-hero">
            <span className="empty-icon"><IconShield size={22} /></span>
            <h3>暂无记录</h3>
            <p>{loading ? "加载中…" : "还没有符合条件的审计事件。"}</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>操作</th>
                  <th>操作人</th>
                  <th>IP</th>
                  <th>对象</th>
                  {tab === "LOGIN" ? <th>结果</th> : null}
                  <th>详情</th>
                </tr>
              </thead>
              <tbody>
                {events.map((event) => (
                  <tr key={event.id}>
                    <td className="mono">{formatTime(event.occurredAt)}</td>
                    <td className="mono">{event.action}</td>
                    <td>{event.actorUsername || "—"}</td>
                    <td className="mono">{event.clientIp || "—"}</td>
                    <td className="mono truncate">{event.target}</td>
                    {tab === "LOGIN" ? (
                      <td>
                        <span className={`badge ${event.success === false ? "danger" : "ok"}`}>
                          {event.success === false ? "失败" : "成功"}
                        </span>
                      </td>
                    ) : null}
                    <td className="mono faint truncate">{formatMeta(event.metadata)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

function formatTime(value: string): string {
  try {
    return new Date(value).toLocaleString("zh-CN", { hour12: false });
  } catch {
    return value;
  }
}

function formatMeta(metadata: Record<string, unknown> | undefined): string {
  if (!metadata) return "";
  if (typeof metadata.stored === "string") return metadata.stored;
  try {
    return JSON.stringify(metadata);
  } catch {
    return "";
  }
}
