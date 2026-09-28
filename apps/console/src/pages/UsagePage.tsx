import { FormEvent, useEffect, useState } from "react";
import { api } from "../api";
import { IconScale } from "../icons";
import type { UsageReport, UsageRow, UsageTotals } from "../types";

function todayIso() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function daysAgoIso(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function formatCount(value: number) {
  return value.toLocaleString("zh-CN");
}

function TokenCell({ row }: { row: Pick<UsageRow, "promptTokens" | "completionTokens" | "totalTokens" | "tokenIncomplete"> }) {
  return (
    <td>
      <span className="mono">{formatCount(row.totalTokens)}</span>
      <span className="usage-token-parts">
        {formatCount(row.promptTokens)} / {formatCount(row.completionTokens)}
      </span>
      {row.tokenIncomplete ? <span className="badge warn">有未知 token</span> : null}
    </td>
  );
}

function UsageTable({
  rows,
  kind,
}: {
  rows: UsageRow[];
  kind: "user" | "role" | "model";
}) {
  if (rows.length === 0) {
    return (
      <div className="empty">
        <p>这段时间没有用量。</p>
      </div>
    );
  }
  return (
    <div className="usage-table-wrap">
      <table className="usage-table">
        <thead>
          <tr>
            {kind === "user" ? <th>用户</th> : null}
            {kind === "role" ? <th>角色</th> : null}
            <th>供应商</th>
            <th>模型</th>
            <th>次数</th>
            <th>Token（合计 / 输入 / 输出）</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={`${row.userId ?? ""}-${row.roleCode ?? ""}-${row.provider}-${row.model}`}>
              {kind === "user" ? (
                <td>
                  <strong>{row.username || "—"}</strong>
                  {row.userId ? <div className="mono faint">{row.userId}</div> : null}
                </td>
              ) : null}
              {kind === "role" ? <td className="mono">{row.roleCode}</td> : null}
              <td className="mono">{row.provider}</td>
              <td className="mono">{row.model}</td>
              <td className="mono">{formatCount(row.callCount)}</td>
              <TokenCell row={row} />
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Totals({ totals }: { totals: UsageTotals }) {
  return (
    <div className="usage-totals">
      <div>
        <span className="kicker">合计次数</span>
        <strong>{formatCount(totals.callCount)}</strong>
      </div>
      <div>
        <span className="kicker">合计 Token</span>
        <strong>{formatCount(totals.totalTokens)}</strong>
        <span className="usage-token-parts">
          输入 {formatCount(totals.promptTokens)} · 输出 {formatCount(totals.completionTokens)}
        </span>
      </div>
      {totals.tokenIncomplete ? <span className="badge warn">有未知 token</span> : null}
    </div>
  );
}

export function UsagePage() {
  const [from, setFrom] = useState(() => daysAgoIso(29));
  const [to, setTo] = useState(() => todayIso());
  const [report, setReport] = useState<UsageReport | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const load = async (start = from, end = to) => {
    setError("");
    setLoading(true);
    try {
      const query = new URLSearchParams({ from: start, to: end });
      setReport(await api<UsageReport>(`/api/admin/usage?${query}`));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "加载用量失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const onFilter = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void load(from, to);
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">统计</p>
          <h2>用量</h2>
          <p>
            按登录用户和写入时的角色快照汇总真实模型调用。次数含工具循环里的每一次补全；读不到 token 时次数仍计，并标「有未知 token」。
            一人多角色时，角色表里每个角色各计一次，人次可能大于调用次数。模型页探测连通性和工具 MCP 调用不计入。历史对话无法回填。
          </p>
        </div>
      </div>
      <section className="card">
        <form className="form usage-filter" onSubmit={onFilter}>
          <label htmlFor="usage-from">开始
            <input id="usage-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} required />
          </label>
          <label htmlFor="usage-to">结束
            <input id="usage-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} required />
          </label>
          <button className="btn" disabled={loading}>{loading ? "查询中…" : "查询"}</button>
        </form>
        {error ? <p className="error">{error}</p> : null}
        {report ? <Totals totals={report.totals} /> : null}
      </section>
      <div className="grid">
        <section className="card">
          <div className="card-head">
            <h3>按人</h3>
            <span className="badge">{report?.byUser.length ?? 0} 行</span>
          </div>
          {report ? <UsageTable rows={report.byUser} kind="user" /> : (
            <div className="empty empty-hero">
              <span className="empty-icon"><IconScale size={22} /></span>
              <h3>还没有用量</h3>
            </div>
          )}
        </section>
        <section className="card">
          <div className="card-head">
            <h3>按角色</h3>
            <span className="badge">{report?.byRole.length ?? 0} 行</span>
          </div>
          {report ? <UsageTable rows={report.byRole} kind="role" /> : null}
        </section>
        <section className="card">
          <div className="card-head">
            <h3>按模型</h3>
            <span className="badge">{report?.byModel.length ?? 0} 行</span>
          </div>
          {report ? <UsageTable rows={report.byModel} kind="model" /> : null}
        </section>
      </div>
    </>
  );
}
