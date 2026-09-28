import { FormEvent, useEffect, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { api } from "../api";
import type { LayoutContext } from "../layout/AppLayout";
import { IconPencil, IconPlus, IconTrash, IconUsers } from "../icons";
import type { AdminRole, AdminUser } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";

export function UsersPage() {
  const { hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.users.write");
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<AdminUser | null>(null);
  const [pendingDelete, setPendingDelete] = useState<AdminUser | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [roleIds, setRoleIds] = useState<string[]>([]);

  const load = async () => {
    const [nextUsers, nextRoles] = await Promise.all([
      api<AdminUser[]>("/api/admin/users"),
      api<AdminRole[]>("/api/admin/roles"),
    ]);
    setUsers(nextUsers);
    setRoles(nextRoles);
  };

  useEffect(() => {
    void load().catch((cause: Error) => setError(cause.message));
  }, []);

  const resetForm = () => {
    setEditing(null);
    setUsername("");
    setDisplayName("");
    setEmail("");
    setPassword("");
    setRoleIds([]);
  };

  const startEdit = (user: AdminUser) => {
    setEditing(user);
    setUsername(user.username);
    setDisplayName(user.displayName);
    setEmail(user.email ?? "");
    setPassword("");
    setRoleIds(user.roleIds);
    setError("");
  };

  const toggle = (list: string[], id: string) => (
    list.includes(id) ? list.filter((item) => item !== id) : [...list, id]
  );

  const onSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      if (editing) {
        await api(`/api/admin/users/${editing.id}`, {
          method: "PUT",
          body: JSON.stringify({ displayName, email, password: password || undefined }),
        });
        await api(`/api/admin/users/${editing.id}/roles`, {
          method: "PUT",
          body: JSON.stringify({ roleIds }),
        });
      } else {
        await api("/api/admin/users", {
          method: "POST",
          body: JSON.stringify({ username, displayName, email, password, roleIds }),
        });
      }
      resetForm();
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存用户失败");
    } finally {
      setSaving(false);
    }
  };

  const onToggleStatus = async (user: AdminUser) => {
    setError("");
    try {
      await api(`/api/admin/users/${user.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: user.status === "DISABLED" ? "ENABLED" : "DISABLED" }),
      });
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "更新状态失败");
    }
  };

  const onDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    setError("");
    try {
      await api(`/api/admin/users/${pendingDelete.id}`, { method: "DELETE" });
      if (editing?.id === pendingDelete.id) resetForm();
      setPendingDelete(null);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除用户失败");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">权限</p>
          <h2>用户</h2>
          <p>创建本地账号、启停并分配角色。有用户菜单的人看到同一份账号列表。飞书用户首次登录后会出现在这里。</p>
        </div>
      </div>
      <div className="grid two">
        <section className="card">
          <div className="card-head">
            <h3>账号</h3>
            <span className="badge">{users.length} 个</span>
          </div>
          {users.length === 0 ? (
            <div className="empty empty-hero">
              <span className="empty-icon"><IconUsers size={22} /></span>
              <h3>还没有用户</h3>
              <p>在右侧创建本地账号，或等待飞书用户首次登录。</p>
            </div>
          ) : (
            <div className="stack-list">
              {users.map((user) => (
                <article className="list-row" key={user.id}>
                  <div>
                    <h3>{user.displayName}</h3>
                    <div className="meta">
                      <span className="mono">{user.username}</span>
                      <span>{user.roleCodes.join(" / ") || "未分配角色"}</span>
                    </div>
                  </div>
                  <div className="item-actions">
                    <span className={`badge ${user.status === "ENABLED" ? "ok" : "danger"}`}>{user.status === "ENABLED" ? "启用" : "停用"}</span>
                    {canWrite ? (
                    <>
                    <button className="btn ghost" type="button" onClick={() => startEdit(user)}>
                      <IconPencil size={13} />
                      修改
                    </button>
                    <button className="btn ghost" type="button" onClick={() => void onToggleStatus(user)}>
                      {user.status === "ENABLED" ? "停用" : "启用"}
                    </button>
                    <button className="btn ghost danger" type="button" onClick={() => setPendingDelete(user)}>
                      <IconTrash size={13} />
                      删除
                    </button>
                    </>
                    ) : null}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
        {canWrite ? (
        <section className="card">
          <h3>{editing ? "修改用户" : "创建本地账号"}</h3>
          <form className="form" onSubmit={(event) => void onSave(event)}>
            <label htmlFor="user-username">用户名
              <input id="user-username" value={username} onChange={(event) => setUsername(event.target.value)} required disabled={Boolean(editing)} />
            </label>
            <label htmlFor="user-display">显示名
              <input id="user-display" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
            </label>
            <label htmlFor="user-email">邮箱
              <input id="user-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
            </label>
            <label htmlFor="user-password">{editing ? "新密码（可空）" : "密码"}
              <input id="user-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required={!editing} />
            </label>
            <fieldset className="check-list">
              <legend>角色</legend>
              {roles.map((role) => (
                <label key={role.id} className="check-item">
                  <input
                    type="checkbox"
                    checked={roleIds.includes(role.id)}
                    onChange={() => setRoleIds(toggle(roleIds, role.id))}
                  />
                  {role.name}
                  <span className="mono">{role.code}</span>
                </label>
              ))}
            </fieldset>
            <div className="form-actions">
              {editing ? <button className="btn secondary" type="button" onClick={resetForm}>取消</button> : null}
              <button className="btn" disabled={saving}>
                <IconPlus size={16} />
                {saving ? "保存中…" : (editing ? "保存修改" : "创建用户")}
              </button>
            </div>
          </form>
          {error ? <p className="error">{error}</p> : null}
        </section>
        ) : (
          <section className="card">
            <h3>用户</h3>
            <p className="notice">当前账号无用户管理写权限。</p>
            {error ? <p className="error">{error}</p> : null}
          </section>
        )}
      </div>
      {pendingDelete ? (
        <ConfirmDialog
          title="删除用户"
          message={`将删除账号「${pendingDelete.displayName}」。`}
          busy={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}
    </>
  );
}
