import { FormEvent, useEffect, useState } from "react";
import { useNavigate, useOutletContext } from "react-router-dom";
import { api } from "../api";
import { IconFolder, IconPencil, IconPlugs, IconPlus, IconSquares, IconTrash } from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import type { Binding, Project, Repository } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";

interface ProjectCard extends Project {
  repoCount: number;
  bindingCount: number;
}

const LAST_PROJECT_KEY = "sre-investigator.projectId";

export function ProjectsPage() {
  const { projects, project, refreshProjects, hasMenu, hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.projects.write");
  const canDelete = hasPermission("action.projects.delete");
  const navigate = useNavigate();
  const [cards, setCards] = useState<ProjectCard[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<Project | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Project | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [topology, setTopology] = useState("admin-app-dual");

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      const next = await Promise.all(projects.map(async (item) => {
        const [repos, bindings] = await Promise.all([
          hasMenu("menu.repositories")
            ? api<Repository[]>(`/api/projects/${item.id}/repositories`).catch(() => [])
            : Promise.resolve([]),
          hasMenu("menu.bindings")
            ? api<Binding[]>(`/api/projects/${item.id}/bindings`).catch(() => [])
            : Promise.resolve([]),
        ]);
        return { ...item, repoCount: repos.length, bindingCount: bindings.length };
      }));
      if (!cancelled) setCards(next);
    };
    void load().catch((cause: Error) => {
      if (!cancelled) setError(cause.message);
    });
    return () => { cancelled = true; };
  }, [projects]);

  const resetForm = () => {
    setEditing(null);
    setName("");
    setSlug("");
    setTopology("admin-app-dual");
  };

  const startEdit = (item: Project) => {
    setEditing(item);
    setName(item.name);
    setSlug(item.slug);
    setTopology(item.topology);
    setError("");
  };

  const onSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      const body = JSON.stringify({ name, slug, topology: topology || "admin-app-dual" });
      if (editing) {
        await api<Project>(`/api/projects/${editing.id}`, { method: "PUT", body });
        resetForm();
        await refreshProjects();
      } else {
        const created = await api<Project>("/api/projects", { method: "POST", body });
        resetForm();
        await refreshProjects();
        navigate(`/projects/${created.id}/repositories`);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : (editing ? "保存项目失败" : "创建项目失败"));
    } finally {
      setSaving(false);
    }
  };

  const onDelete = async () => {
    if (!pendingDelete) return;
    setError("");
    setDeleting(true);
    try {
      await api(`/api/projects/${pendingDelete.id}`, { method: "DELETE" });
      if (editing?.id === pendingDelete.id) resetForm();
      if (project?.id === pendingDelete.id) {
        localStorage.removeItem(LAST_PROJECT_KEY);
        navigate("/projects");
      }
      setPendingDelete(null);
      await refreshProjects();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除项目失败");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">控制面</p>
          <h2>项目</h2>
          <p>每个项目隔离代码仓、环境和只读工具。先创建或选择项目，再配置仓库与 Binding。</p>
        </div>
      </div>

      <div className="grid two">
        <section className="card">
          <div className="card-head">
            <h3>已有项目</h3>
            <span className="badge">{cards.length} 个</span>
          </div>
          {cards.length === 0 ? (
            <div className="empty empty-hero">
              <span className="empty-icon"><IconSquares size={22} /></span>
              <h3>还没有项目</h3>
              <p>在右侧填写名称与 slug，创建后即可绑定仓库与 MCP。</p>
            </div>
          ) : (
            <div className="grid cards">
              {cards.map((item) => (
                <article className="project-card" key={item.id}>
                  <button
                    type="button"
                    className="project-card-main"
                    onClick={() => {
                      if (hasMenu("menu.repositories")) navigate(`/projects/${item.id}/repositories`);
                      else if (hasMenu("menu.automations")) {
                        localStorage.setItem(LAST_PROJECT_KEY, item.id);
                        localStorage.setItem("sre-investigator.automationProjectId", item.id);
                        navigate("/automations");
                      }
                      else if (hasMenu("menu.investigate")) navigate(`/projects/${item.id}/investigate`);
                      else if (hasMenu("menu.agents")) navigate(`/projects/${item.id}/agents`);
                      else if (hasMenu("menu.bindings")) navigate(`/projects/${item.id}/bindings`);
                    }}
                  >
                    <div>
                      <h3>{item.name}</h3>
                      <div className="meta">
                        <span className="mono">{item.slug}</span>
                        <span>拓扑 · {item.topology}</span>
                      </div>
                    </div>
                    <div className="counts">
                      <span className="badge"><IconFolder size={12} /> {item.repoCount} 仓库</span>
                      <span className="badge"><IconPlugs size={12} /> {item.bindingCount} 工具</span>
                    </div>
                  </button>
                  <div className="item-actions">
                    {canWrite ? (
                      <button className="btn ghost" type="button" onClick={() => startEdit(item)}>
                        <IconPencil size={13} />
                        修改
                      </button>
                    ) : null}
                    {canDelete ? (
                      <button className="btn ghost danger" type="button" onClick={() => setPendingDelete(item)}>
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

        {canWrite ? (
        <section className="card">
          <h3>{editing ? "修改项目" : "创建项目"}</h3>
          <form className="form" onSubmit={(event) => void onSave(event)}>
            <label htmlFor="project-name">项目名称
              <input id="project-name" value={name} onChange={(event) => setName(event.target.value)} required placeholder="demo-app" />
            </label>
            <label htmlFor="project-slug">Slug
              <input id="project-slug" value={slug} onChange={(event) => setSlug(event.target.value)} required pattern="[a-z0-9-]+" placeholder="demo-app" />
            </label>
            <label htmlFor="project-topology">拓扑
              <input id="project-topology" value={topology} onChange={(event) => setTopology(event.target.value)} placeholder="admin-app-dual" />
            </label>
            <div className="form-actions">
              {editing ? (
                <button className="btn secondary" type="button" onClick={resetForm}>取消</button>
              ) : null}
              <button className="btn" disabled={saving}>
                <IconPlus size={16} />
                {saving ? (editing ? "保存中…" : "创建中…") : (editing ? "保存修改" : "创建项目")}
              </button>
            </div>
          </form>
          {error ? <p className="error">{error}</p> : null}
        </section>
        ) : (
          <section className="card">
            <h3>项目</h3>
            <p className="notice">当前账号无项目创建/修改权限。</p>
            {error ? <p className="error">{error}</p> : null}
          </section>
        )}
      </div>
      {pendingDelete ? (
        <ConfirmDialog
          title="删除项目"
          message={`将删除「${pendingDelete.name}」及其代码仓绑定、MCP 连接和对话记录。本机仓库目录不会删除。`}
          busy={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}
    </>
  );
}
