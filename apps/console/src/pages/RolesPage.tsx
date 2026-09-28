import { FormEvent, useEffect, useMemo, useState } from "react";
import { useOutletContext } from "react-router-dom";
import { api } from "../api";
import type { LayoutContext } from "../layout/AppLayout";
import { IconKey, IconPencil, IconPlus, IconTrash } from "../icons";
import type { AdminRole, MenuPermission } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";

export function RolesPage() {
  const { hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.roles.write");
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [permissions, setPermissions] = useState<MenuPermission[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<AdminRole | null>(null);
  const [creating, setCreating] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<AdminRole | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [permissionCodes, setPermissionCodes] = useState<string[]>([]);
  const menuPermissions = useMemo(() => permissions.filter((item) => item.type === "MENU" || item.code.startsWith("menu.")), [permissions]);
  const actionPermissions = useMemo(() => permissions.filter((item) => item.type === "ACTION" || item.code.startsWith("action.")), [permissions]);

  const load = async () => {
    const [nextRoles, nextPermissions] = await Promise.all([
      api<AdminRole[]>("/api/admin/roles"),
      api<MenuPermission[]>("/api/admin/permissions"),
    ]);
    setRoles(nextRoles);
    setPermissions(nextPermissions);
  };

  useEffect(() => {
    void load().catch((cause: Error) => setError(cause.message));
  }, []);

  const resetForm = () => {
    setEditing(null);
    setCreating(false);
    setCode("");
    setName("");
    setDescription("");
    setPermissionCodes([]);
  };

  const startEdit = (role: AdminRole) => {
    setEditing(role);
    setCreating(false);
    setCode(role.code);
    setName(role.name);
    setDescription(role.description ?? "");
    setPermissionCodes(role.permissionCodes);
    setError("");
  };

  const startCreate = () => {
    setCreating(true);
    setEditing(null);
    setCode("");
    setName("");
    setDescription("");
    setPermissionCodes([]);
    setError("");
  };

  const onSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      if (editing) {
        await api(`/api/admin/roles/${editing.id}`, {
          method: "PUT",
          body: JSON.stringify({ name, description, permissionCodes }),
        });
      } else {
        await api("/api/admin/roles", {
          method: "POST",
          body: JSON.stringify({ code, name, description, permissionCodes }),
        });
      }
      resetForm();
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存角色失败");
    } finally {
      setSaving(false);
    }
  };

  const onDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    setError("");
    try {
      await api(`/api/admin/roles/${pendingDelete.id}`, { method: "DELETE" });
      if (editing?.id === pendingDelete.id) resetForm();
      setPendingDelete(null);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除角色失败");
    } finally {
      setDeleting(false);
    }
  };

  const formOpen = creating || Boolean(editing);

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">权限</p>
          <h2>角色</h2>
          <p>勾选菜单与操作权限。内置角色可以改授权，不能删除。工具配置写删仅建议授予管理员。</p>
        </div>
        {canWrite ? (
        <button className="btn" type="button" onClick={startCreate}>
          <IconPlus size={16} />
          新建角色
        </button>
        ) : null}
      </div>
      <div className="grid two">
        <section className="card">
          <div className="card-head">
            <h3>角色</h3>
            <span className="badge">{roles.length} 个</span>
          </div>
          {roles.length === 0 ? (
            <div className="empty empty-hero">
              <span className="empty-icon"><IconKey size={22} /></span>
              <h3>还没有角色</h3>
            </div>
          ) : (
            <div className="stack-list">
              {roles.map((role) => (
                <article className="list-row" key={role.id}>
                  <div>
                    <h3>{role.name}</h3>
                    <div className="meta">
                      <span className="mono">{role.code}</span>
                      <span>{role.permissionCodes.length} 项权限</span>
                      {role.builtin ? <span>内置</span> : null}
                    </div>
                  </div>
                  <div className="item-actions">
                    <button className="btn ghost" type="button" onClick={() => startEdit(role)}>
                      <IconPencil size={13} />
                      授权
                    </button>
                    {!role.builtin && canWrite ? (
                      <button className="btn ghost danger" type="button" onClick={() => setPendingDelete(role)}>
                        <IconTrash size={13} />
                        删除
                      </button>
                    ) : null}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
        <section className="card">
          <h3>{editing ? `编辑 ${editing.name}` : (creating ? "新建角色" : "选择一个角色")}</h3>
          {formOpen ? (
            <form className="form" onSubmit={(event) => void onSave(event)}>
              {creating ? (
                <label htmlFor="role-code">编码
                  <input id="role-code" value={code} onChange={(event) => setCode(event.target.value)} required placeholder="OPERATOR_PLUS" />
                </label>
              ) : (
                <p className="hint mono">{code}</p>
              )}
              <label htmlFor="role-name">名称
                <input id="role-name" value={name} onChange={(event) => setName(event.target.value)} required />
              </label>
              <label htmlFor="role-desc">说明
                <input id="role-desc" value={description} onChange={(event) => setDescription(event.target.value)} />
              </label>
              <fieldset className="check-list" disabled={!canWrite}>
                <legend>菜单权限</legend>
                {menuPermissions.map((permission) => (
                  <label key={permission.id} className="check-item">
                    <input
                      type="checkbox"
                      checked={permissionCodes.includes(permission.code)}
                      onChange={() => setPermissionCodes((current) => (
                        current.includes(permission.code)
                          ? current.filter((item) => item !== permission.code)
                          : [...current, permission.code]
                      ))}
                    />
                    {permission.name}
                    <span className="mono">{permission.code}</span>
                  </label>
                ))}
              </fieldset>
              <fieldset className="check-list" disabled={!canWrite}>
                <legend>操作权限</legend>
                {actionPermissions.map((permission) => (
                  <label key={permission.id} className="check-item">
                    <input
                      type="checkbox"
                      checked={permissionCodes.includes(permission.code)}
                      onChange={() => setPermissionCodes((current) => (
                        current.includes(permission.code)
                          ? current.filter((item) => item !== permission.code)
                          : [...current, permission.code]
                      ))}
                    />
                    {permission.name}
                    <span className="mono">{permission.code}</span>
                  </label>
                ))}
              </fieldset>
              <div className="form-actions">
                <button className="btn secondary" type="button" onClick={resetForm}>取消</button>
                <button className="btn" disabled={saving || !canWrite}>{saving ? "保存中…" : "保存"}</button>
              </div>
            </form>
          ) : (
            <p className="hint">从左侧选择角色以勾选菜单，或新建自定义角色。</p>
          )}
          {error ? <p className="error">{error}</p> : null}
        </section>
      </div>
      {pendingDelete ? (
        <ConfirmDialog
          title="删除角色"
          message={`将删除角色「${pendingDelete.name}」。`}
          busy={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}
    </>
  );
}
