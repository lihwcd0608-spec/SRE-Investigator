import { FormEvent, useEffect, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { api } from "../api";
import { IconFolder, IconPlus, IconTrash } from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import { ENVIRONMENTS, type Environment, type FeishuChatBinding, type Repository } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";

function bindingEnvironments(item: FeishuChatBinding): Environment[] {
  if (item.environments && item.environments.length > 0) {
    return item.environments;
  }
  return [item.environment];
}

export function FeishuPage() {
  const { project, projectsReady, environment, hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.feishu.write");
  const [items, setItems] = useState<FeishuChatBinding[]>([]);
  const [repos, setRepos] = useState<Repository[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [savingId, setSavingId] = useState("");
  const [chatId, setChatId] = useState("");
  const [chatName, setChatName] = useState("");
  const [env, setEnv] = useState<Environment>(environment);
  const [allowed, setAllowed] = useState<Environment[]>([environment]);
  const [defaultRoute, setDefaultRoute] = useState("");
  const [defaultRepositoryId, setDefaultRepositoryId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<FeishuChatBinding | null>(null);

  const load = async () => {
    if (!project) return;
    const [bindings, repositories] = await Promise.all([
      api<FeishuChatBinding[]>(`/api/projects/${project.id}/feishu/chats`),
      api<Repository[]>(`/api/projects/${project.id}/repositories`),
    ]);
    setItems(bindings);
    setRepos(repositories);
  };

  useEffect(() => {
    void load().catch((cause: Error) => setError(cause.message));
  }, [project?.id]);

  if (!project) {
    return (
      <div className="empty empty-hero">
        <span className="empty-icon"><IconFolder size={22} /></span>
        <h3>{projectsReady ? "还没有选中项目" : "正在加载项目…"}</h3>
        <p>
          {projectsReady
            ? <><Link to="/projects">回到项目列表</Link> 后再配置飞书群。</>
            : "正在读取项目上下文。"}
        </p>
      </div>
    );
  }

  const toggleAllowed = (item: Environment) => {
    setAllowed((current) => {
      const selected = current.includes(item);
      if (selected) {
        if (current.length === 1) {
          return current;
        }
        const next = current.filter((value) => value !== item);
        if (!next.includes(env)) {
          setEnv(next[0]);
        }
        return next;
      }
      return [...current, item];
    });
  };

  const onCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (allowed.length === 0) {
      setError("至少选择一个环境");
      return;
    }
    const environments = allowed.includes(env) ? allowed : [env, ...allowed];
    setSaving(true);
    setError("");
    try {
      await api(`/api/projects/${project.id}/feishu/chats`, {
        method: "POST",
        body: JSON.stringify({
          chatId: chatId.trim(),
          chatName: chatName.trim(),
          environment: env,
          environments,
          defaultRoute: defaultRoute.trim() || null,
          defaultRepositoryId: defaultRepositoryId || null,
          enabled: true,
        }),
      });
      setChatId("");
      setChatName("");
      setDefaultRoute("");
      setDefaultRepositoryId("");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const saveBinding = async (item: FeishuChatBinding, nextEnv: Environment, nextAllowed: Environment[]) => {
    const environments = nextAllowed.length === 0
      ? [nextEnv]
      : nextAllowed.includes(nextEnv) ? nextAllowed : [nextEnv, ...nextAllowed];
    setSavingId(item.id);
    setError("");
    try {
      await api(`/api/projects/${project.id}/feishu/chats/${item.id}`, {
        method: "PUT",
        body: JSON.stringify({
          chatId: item.chatId,
          chatName: item.chatName,
          environment: nextEnv,
          environments,
          defaultRoute: item.defaultRoute || null,
          defaultRepositoryId: item.defaultRepositoryId || null,
          enabled: item.enabled,
        }),
      });
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败");
    } finally {
      setSavingId("");
    }
  };

  const toggleBindingEnv = (item: FeishuChatBinding, target: Environment) => {
    const current = bindingEnvironments(item);
    const selected = current.includes(target);
    if (selected) {
      if (current.length === 1) {
        return;
      }
      const next = current.filter((value) => value !== target);
      const nextEnv = next.includes(item.environment) ? item.environment : next[0];
      void saveBinding(item, nextEnv, next);
      return;
    }
    void saveBinding(item, item.environment, [...current, target]);
  };

  const onDelete = async () => {
    if (!pendingDelete) return;
    setSaving(true);
    setError("");
    try {
      await api(`/api/projects/${project.id}/feishu/chats/${pendingDelete.id}`, { method: "DELETE" });
      setPendingDelete(null);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="page-stack">
      <div className="page-header">
        <div>
          <p className="kicker">{project.slug}</p>
          <h2>飞书群</h2>
          <p>
            把飞书群 chat_id 绑定到本项目，并可勾选多个环境。群成员 @机器人 即可排查；发送「审查 main..feature/xxx」可代码审查。
            问句不写环境时用默认环境；写成「prod环境 用户123」则切到对应环境。需先在飞书开放平台启用 Bot，并把事件订阅 URL 指到 <code>/api/feishu/events</code>。
          </p>
        </div>
      </div>

      {canWrite ? (
      <section className="panel">
        <h3>新增绑定</h3>
        <form className="form-grid" onSubmit={(event) => void onCreate(event)}>
          <label>
            <span>chat_id</span>
            <input value={chatId} onChange={(event) => setChatId(event.target.value)} required placeholder="oc_xxx" />
          </label>
          <label>
            <span>群名称（备注）</span>
            <input value={chatName} onChange={(event) => setChatName(event.target.value)} placeholder="支付告警群" />
          </label>
          <label>
            <span>开放环境</span>
            <div className="chip-row">
              {ENVIRONMENTS.map((item) => (
                <button
                  key={item}
                  type="button"
                  className={`chip-btn${allowed.includes(item) ? " active" : ""}`}
                  aria-pressed={allowed.includes(item)}
                  onClick={() => toggleAllowed(item)}
                >
                  {item}
                </button>
              ))}
            </div>
          </label>
          <label>
            <span>默认环境</span>
            <select value={env} onChange={(event) => setEnv(event.target.value as Environment)}>
              {allowed.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label>
            <span>默认排查方向（可选）</span>
            <input value={defaultRoute} onChange={(event) => setDefaultRoute(event.target.value)} placeholder="order-payment" />
          </label>
          <label>
            <span>默认代码仓（审查用，可选）</span>
            <select value={defaultRepositoryId} onChange={(event) => setDefaultRepositoryId(event.target.value)}>
              <option value="">不指定</option>
              {repos.map((repo) => <option key={repo.id} value={repo.id}>{repo.name}</option>)}
            </select>
          </label>
          <div className="form-actions">
            <button className="btn" type="submit" disabled={saving || allowed.length === 0}>
              <IconPlus size={14} />
              添加绑定
            </button>
          </div>
        </form>
      </section>
      ) : null}

      <section className="panel">
        <h3>已绑定群</h3>
        {items.length === 0 ? (
          <p className="notice">还没有绑定。可从飞书群设置或事件 payload 里拿到 chat_id。</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>群</th>
                  <th>chat_id</th>
                  <th>开放环境</th>
                  <th>默认环境</th>
                  <th>默认方向</th>
                  <th>默认仓</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const current = bindingEnvironments(item);
                  const busy = savingId === item.id;
                  return (
                    <tr key={item.id}>
                      <td>{item.chatName || "—"}</td>
                      <td><code>{item.chatId}</code></td>
                      <td>
                        <div className="chip-row">
                          {ENVIRONMENTS.map((value) => (
                            <button
                              key={value}
                              type="button"
                              className={`chip-btn${current.includes(value) ? " active" : ""}`}
                              disabled={busy}
                              aria-pressed={current.includes(value)}
                              onClick={() => toggleBindingEnv(item, value)}
                            >
                              {value}
                            </button>
                          ))}
                        </div>
                      </td>
                      <td>
                        <select
                          value={item.environment}
                          disabled={busy}
                          onChange={(event) => {
                            const nextEnv = event.target.value as Environment;
                            const nextAllowed = current.includes(nextEnv) ? current : [...current, nextEnv];
                            void saveBinding(item, nextEnv, nextAllowed);
                          }}
                        >
                          {current.map((value) => <option key={value} value={value}>{value}</option>)}
                        </select>
                      </td>
                      <td>{item.defaultRoute || "—"}</td>
                      <td>{repos.find((repo) => repo.id === item.defaultRepositoryId)?.name || "—"}</td>
                      <td>
                        {canWrite ? (
                        <button className="btn ghost danger" type="button" onClick={() => setPendingDelete(item)}>
                          <IconTrash size={14} />
                        </button>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <h3>群里怎么说</h3>
        <ul className="hint-list">
          <li><code>@机器人 用户 123 订单没到账</code> — 排查，用绑定的默认环境</li>
          <li><code>@机器人 prod环境 用户 123 订单没到账</code> — 排查生产（须已勾选 prod）</li>
          <li><code>@机器人 审查 main..feature/pay 看回调幂等</code> — 代码审查</li>
          <li><code>@机器人 新对话</code> — 重置会话上下文</li>
        </ul>
      </section>

      {error ? <p className="error">{error}</p> : null}

      {pendingDelete ? (
        <ConfirmDialog
          title="删除飞书群绑定"
          message={`将删除「${pendingDelete.chatName || pendingDelete.chatId}」的绑定。`}
          confirmLabel="删除"
          busy={saving}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}
    </div>
  );
}
