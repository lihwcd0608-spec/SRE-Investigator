import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "../api";
import { IconRefresh, IconWarning, IconX } from "../icons";
import type { Binding, MetricHistoryPoint, MetricPoint, MetricSnapshot, SlowQueryPage } from "../types";

interface BindingMetricsPanelProps {
  projectId: string;
  binding: Binding;
  onClose: () => void;
}

type Health = "ok" | "warn" | "danger" | "none";

const SPARK_KEYS = new Set(["qps", "threads_running", "ops_per_sec"]);
const SLOW_PAGE_SIZE = 10;
const SLOW_MIN_MILLIS = 100;

const KIND_TAG: Record<string, string> = {
  MYSQL: "MySQL 实时监控",
  REDIS: "Redis 实时监控",
  ELASTICSEARCH: "Elasticsearch 实时监控",
  MONGODB: "MongoDB 实时监控",
};

const CLUSTER_STATUS_LABEL: Record<string, string> = {
  green: "健康",
  yellow: "亚健康",
  red: "故障",
};

export function BindingMetricsPanel({ projectId, binding, onClose }: BindingMetricsPanelProps) {
  const [snapshot, setSnapshot] = useState<MetricSnapshot | null>(null);
  const [history, setHistory] = useState<Record<string, MetricHistoryPoint[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [collectedAt, setCollectedAt] = useState("");
  const [slow, setSlow] = useState<SlowQueryPage | null>(null);
  const [slowLoading, setSlowLoading] = useState(true);
  const [slowPage, setSlowPage] = useState(1);

  const loadSlow = useCallback(
    async (page: number) => {
      setSlowLoading(true);
      try {
        const data = await api<SlowQueryPage>(
          `/api/projects/${projectId}/bindings/${binding.id}/metrics/slow-queries?page=${page}&size=${SLOW_PAGE_SIZE}&minMillis=${SLOW_MIN_MILLIS}`,
        );
        setSlow(data);
      } catch {
        setSlow({ rows: [], total: 0, note: "慢查询明细获取失败" });
      } finally {
        setSlowLoading(false);
      }
    },
    [projectId, binding.id],
  );

  const load = useCallback(
    async (isRefresh = false) => {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setError("");
      try {
        const data = await api<MetricSnapshot>(`/api/projects/${projectId}/bindings/${binding.id}/metrics`);
        setSnapshot(data);
        setCollectedAt(new Date().toLocaleTimeString("zh-CN", { hour12: false }));
        const keys = data.points.filter((point) => SPARK_KEYS.has(point.key)).map((point) => point.key);
        const entries = await Promise.all(
          keys.map(async (key) => {
            try {
              const rows = await api<MetricHistoryPoint[]>(
                `/api/projects/${projectId}/bindings/${binding.id}/metrics/history?metricKey=${key}&limit=60`,
              );
              return [key, rows] as const;
            } catch {
              return [key, [] as MetricHistoryPoint[]] as const;
            }
          }),
        );
        setHistory(Object.fromEntries(entries));
        if (binding.toolKind === "MYSQL") {
          setSlowPage(1);
          void loadSlow(1);
        }
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "采集失败");
        setSlowLoading(false);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [projectId, binding.id, loadSlow],
  );

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const totalPages = slow ? Math.max(1, Math.ceil(slow.total / SLOW_PAGE_SIZE)) : 1;

  const changePage = (page: number) => {
    if (page < 1 || page > totalPages) return;
    setSlowPage(page);
    void loadSlow(page);
  };

  return (
    <div className="modal-scrim" role="presentation" onClick={onClose}>
      <div
        className="metrics-modal"
        role="dialog"
        aria-modal="true"
        aria-label="MySQL 监控指标"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="metrics-head">
          <div className="metrics-head-copy">
            <div className="metrics-head-title">
              <span className="live-dot" aria-hidden="true" />
              <h3>{binding.profile}</h3>
              <span className="metrics-head-tag">{KIND_TAG[binding.toolKind] ?? "实例监控"}</span>
            </div>
            <p className="metrics-head-sub">
              只读采集 · 每 60 秒自动更新{collectedAt ? ` · 最近采集 ${collectedAt}` : ""}
            </p>
          </div>
          <div className="metrics-head-actions">
            <button className="btn ghost" type="button" onClick={() => void load(true)} disabled={refreshing}>
              <IconRefresh size={14} className={refreshing ? "spin" : undefined} />
              {refreshing ? "采集中" : "刷新"}
            </button>
            <button className="icon-btn" type="button" onClick={onClose} aria-label="关闭">
              <IconX size={15} />
            </button>
          </div>
        </header>

        <div className="metrics-body">
          {loading ? (
            <div className="metrics-grid">
              {Array.from({ length: 9 }).map((_, index) => (
                <div key={index} className="metric-card skeleton" />
              ))}
            </div>
          ) : error ? (
            <div className="metrics-error">
              <IconWarning size={18} />
              <p>{error}</p>
            </div>
          ) : snapshot ? (
            <>
              <section className="metrics-grid">
                {snapshot.points.map((point) => (
                  <MetricCard key={point.key} point={point} series={history[point.key]} />
                ))}
              </section>

              {binding.toolKind === "MYSQL" ? (
              <section className="slow-section">
                <div className="slow-head">
                  <h4>慢查询明细</h4>
                  <span className="slow-head-sub">按耗时倒排 · 最近语句事件（&gt;{SLOW_MIN_MILLIS}ms）</span>
                </div>
                {slowLoading ? (
                  <p className="slow-hint">加载慢查询明细…</p>
                ) : !slow || slow.rows.length === 0 ? (
                  <p className="slow-hint">{slow?.note ?? "暂无慢查询记录。"}</p>
                ) : (
                  <>
                    <table className="slow-table">
                      <thead>
                        <tr>
                          <th>SQL</th>
                          <th>执行用户</th>
                          <th>时间点</th>
                          <th className="num">耗时</th>
                        </tr>
                      </thead>
                      <tbody>
                        {slow.rows.map((row, index) => (
                          <tr key={index}>
                            <td className="sql-cell">
                              <span className="sql-badge">{sqlKind(row.sql)}</span>
                              <span className="sql-text" title={row.sql}>{row.sql}</span>
                            </td>
                            <td className="user-cell">{row.user ?? "—"}</td>
                            <td className="time-cell">{row.executedAt ?? "—"}</td>
                            <td className="num dur-cell">{formatDuration(row.seconds)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <div className="slow-pager">
                      <span className="pager-info">
                        共 {slow.total} 条 · 第 {slowPage} / {totalPages} 页
                      </span>
                      <div className="pager-actions">
                        <button
                          className="btn ghost"
                          type="button"
                          disabled={slowPage <= 1 || slowLoading}
                          onClick={() => changePage(slowPage - 1)}
                        >
                          上一页
                        </button>
                        <button
                          className="btn ghost"
                          type="button"
                          disabled={slowPage >= totalPages || slowLoading}
                          onClick={() => changePage(slowPage + 1)}
                        >
                          下一页
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </section>
              ) : null}
            </>
          ) : null}
        </div>

        <footer className="metrics-foot">监控为旁路采集，不进入 Agent 排查链路，全部为只读命令或接口。</footer>
      </div>
    </div>
  );
}

function MetricCard({ point, series }: { point: MetricPoint; series?: MetricHistoryPoint[] }) {
  const unavailable = point.value.startsWith("unavailable");
  const health: Health = unavailable ? "none" : healthFor(point);
  const usage = unavailable ? null : usagePct(point);
  const displayValue = unavailable
    ? point.value.includes("首次")
      ? "待采集"
      : "不可用"
    : point.key === "cluster_status"
      ? CLUSTER_STATUS_LABEL[point.value] ?? point.value
      : compactValue(point.value);
  const limit = point.key === "connections" && !unavailable ? point.value.split("/")[1] : null;

  const values = useMemo(() => {
    if (!series) return [];
    return series
      .map((row) => ({ v: parseFloat(row.value), t: Date.parse(row.collectedAt) }))
      .filter((row) => Number.isFinite(row.v) && Number.isFinite(row.t))
      .sort((a, b) => a.t - b.t)
      .map((row) => row.v);
  }, [series]);

  return (
    <article className={`metric-card health-${health}${unavailable ? " unavailable" : ""}`}>
      <div className="metric-label">{point.label}</div>
      <div className="metric-value">
        {displayValue}
        {!unavailable ? <span className="metric-unit">{limit ? `/ ${limit} 上限` : point.unit}</span> : null}
      </div>
      {usage !== null ? (
        <div className="metric-bar">
          <span className="metric-bar-fill" style={{ width: `${Math.max(2, usage)}%` }} />
        </div>
      ) : SPARK_KEYS.has(point.key) ? (
        <Sparkline values={values} />
      ) : (
        <div className="metric-bar metric-bar-idle" />
      )}
    </article>
  );
}

function Sparkline({ values }: { values: number[] }) {
  if (values.length < 2) {
    return <div className="spark spark-empty">积累采集中…</div>;
  }
  const w = 120;
  const h = 30;
  const pad = 2;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const stepX = (w - pad * 2) / (values.length - 1);
  const points = values.map(
    (v, i) => `${(pad + i * stepX).toFixed(1)},${(h - pad - ((v - min) / range) * (h - pad * 2)).toFixed(1)}`,
  );
  return (
    <svg className="spark" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" aria-hidden="true">
      <polygon className="spark-area" points={`${pad},${h - pad} ${points.join(" ")} ${w - pad},${h - pad}`} />
      <polyline className="spark-line" points={points.join(" ")} />
    </svg>
  );
}

function healthFor(point: MetricPoint): Health {
  if (point.key === "cluster_status") {
    if (point.value === "red") return "danger";
    if (point.value === "yellow") return "warn";
    if (point.value === "green") return "ok";
    return "none";
  }
  const num = parseFloat(point.value);
  if (!Number.isFinite(num)) return "none";
  if (point.key === "cpu_usage" || point.key === "os_memory" || point.key === "memory_usage"
    || point.key === "heap_usage" || point.key === "disk_usage") {
    return num > 90 ? "danger" : num > 75 ? "warn" : "ok";
  }
  if (point.key === "connections") {
    const [current, max] = point.value.split("/").map(Number);
    if (!max) return "none";
    const pct = (current / max) * 100;
    return pct > 85 ? "danger" : pct > 65 ? "warn" : "ok";
  }
  if (point.key === "threads_running") {
    return num > 30 ? "danger" : num > 10 ? "warn" : "ok";
  }
  if (point.key === "unassigned_shards" || point.key === "evicted_keys" || point.key === "rejected_connections") {
    return num > 0 ? "warn" : "ok";
  }
  return "none";
}

function usagePct(point: MetricPoint): number | null {
  if (point.key === "cpu_usage" || point.key === "os_memory" || point.key === "memory_usage"
    || point.key === "heap_usage" || point.key === "disk_usage") {
    const v = parseFloat(point.value);
    return Number.isFinite(v) ? Math.min(v, 100) : null;
  }
  if (point.key === "connections") {
    const [current, max] = point.value.split("/").map(Number);
    return max > 0 && Number.isFinite(current) ? Math.min((current / max) * 100, 100) : null;
  }
  return null;
}

/** 连接数 11/151 只显示主值，比值放入 unit 提示。 */
function compactValue(value: string): string {
  const slashIndex = value.indexOf("/");
  if (slashIndex > 0) return value.slice(0, slashIndex);
  return value;
}

function sqlKind(sql: string): string {
  const match = sql.match(/^([A-Za-z]+)/);
  return match ? match[1].toUpperCase().slice(0, 12) : "SQL";
}

function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return "—";
  if (seconds < 1) return `${Math.max(1, Math.round(seconds * 1000))}ms`;
  return `${seconds.toFixed(2)}s`;
}
