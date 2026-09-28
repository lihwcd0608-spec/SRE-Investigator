import { FormEvent, useEffect, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { api } from "../api";
import { IconArrowRight, IconFolder, IconGitBranch, IconPencil, IconPlus, IconTrash } from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import { REPO_PORTS, repoPortLabel, type Repository } from "../types";
import { ConfirmDialog } from "../ui/ConfirmDialog";

type Source = "git" | "local";

export function RepositoriesPage() {
  const { project, projectsReady, hasPermission } = useOutletContext<LayoutContext>();
  const canWrite = hasPermission("action.repositories.write");
  const canDelete = hasPermission("action.repositories.delete");
  const [repos, setRepos] = useState<Repository[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [pullingId, setPullingId] = useState("");
  const [source, setSource] = useState<Source>("git");
  const [name, setName] = useState("");
  const [role, setRole] = useState("admin");
  const [rootPath, setRootPath] = useState("");
  const [gitUrl, setGitUrl] = useState("");
  const [gitRef, setGitRef] = useState("");
  const [username, setUsername] = useState("");
  const [token, setToken] = useState("");
  const [sshIdentity, setSshIdentity] = useState("");
  const [editing, setEditing] = useState<Repository | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Repository | null>(null);
  const [deleting, setDeleting] = useState(false);

  const load = async () => {
    if (!project) return;
    setRepos(await api<Repository[]>(`/api/projects/${project.id}/repositories`));
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
            ? <><Link to="/projects">回到项目列表</Link> 创建或选择一个项目。</>
            : "正在读取项目上下文。"}
        </p>
      </div>
    );
  }

  const resetForm = () => {
    setEditing(null);
    setName("");
    setRole("admin");
    setRootPath("");
    setGitUrl("");
    setGitRef("");
    setUsername("");
    setToken("");
    setSshIdentity("");
    setSource("git");
  };

  const startEdit = (repo: Repository) => {
    setEditing(repo);
    setName(repo.name);
    setRole(repo.role || "admin");
    setRootPath(repo.rootPath);
    setGitUrl(repo.gitUrl ?? "");
    setGitRef(repo.gitRef ?? "");
    setUsername("");
    setToken("");
    setSshIdentity("");
    setSource(repo.gitUrl ? "git" : "local");
    setError("");
  };

  const onSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      await api(
        editing
          ? `/api/projects/${project.id}/repositories/${editing.id}`
          : `/api/projects/${project.id}/repositories`,
        {
          method: editing ? "PUT" : "POST",
          body: JSON.stringify(source === "git"
            ? { name, role, gitUrl, gitRef, rootPath, username, token, sshIdentity }
            : { name, role, rootPath, gitUrl: "", gitRef: "" }),
        },
      );
      resetForm();
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : (editing ? "保存仓库失败" : "保存仓库失败"));
    } finally {
      setSaving(false);
    }
  };

  const onPull = async (repo: Repository) => {
    setError("");
    setPullingId(repo.id);
    try {
      await api(`/api/projects/${project.id}/repositories/${repo.id}/pull`, { method: "POST" });
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "拉取更新失败");
    } finally {
      setPullingId("");
    }
  };

  const onDelete = async () => {
    if (!pendingDelete) return;
    setError("");
    setDeleting(true);
    try {
      await api(`/api/projects/${project.id}/repositories/${pendingDelete.id}`, { method: "DELETE" });
      if (editing?.id === pendingDelete.id) resetForm();
      setPendingDelete(null);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "删除仓库失败");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="kicker">{project.slug}</p>
          <h2>代码仓</h2>
          <p>为 {project.name} 绑定本机目录，或把 Git 仓克隆到你指定的目录。Agent 只会读这些路径，不会改文件。</p>
        </div>
        <Link className="btn secondary" to={`/projects/${project.id}/bindings`}>
          下一步：工具
          <IconArrowRight size={16} />
        </Link>
      </div>

      <div className="grid two">
        <section className="card">
          <div className="card-head">
            <h3>已绑定仓库</h3>
            <span className="badge">{repos.length} 个</span>
          </div>
          {repos.length === 0 ? (
            <div className="empty empty-hero">
              <span className="empty-icon"><IconGitBranch size={22} /></span>
              <h3>还没有仓库</h3>
              <p>用 Git 地址拉取，或填写本机绝对路径。端口用管理端 / 客户端 / 其他区分工作区。</p>
            </div>
          ) : (
            <div className="list">
              {repos.map((repo) => (
                <div className="item" key={repo.id}>
                  <b>{repo.name}</b>
                  <span className="repo-meta">
                    <span className="tag">{repoPortLabel(repo.role)}</span>
                    <span className={`badge ${repo.gitUrl ? "accent" : ""}`}>{repo.gitUrl ? "git" : "本地"}</span>
                  </span>
                  {repo.gitUrl ? (
                    <small className="mono">{repo.gitUrl}{repo.gitRef ? ` @ ${repo.gitRef}` : ""}</small>
                  ) : null}
                  <small className="mono">{repo.rootPath}</small>
                  <div className="item-actions">
                    {repo.gitUrl && canWrite ? (
                      <button
                        className="btn ghost"
                        type="button"
                        disabled={pullingId === repo.id}
                        onClick={() => void onPull(repo)}
                      >
                        {pullingId === repo.id ? "拉取中…" : "拉取更新"}
                      </button>
                    ) : null}
                    {canWrite ? (
                      <button className="btn ghost" type="button" onClick={() => startEdit(repo)}>
                        <IconPencil size={13} />
                        修改
                      </button>
                    ) : null}
                    {canDelete ? (
                      <button className="btn ghost danger" type="button" onClick={() => setPendingDelete(repo)}>
                        <IconTrash size={13} />
                        删除
                      </button>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {canWrite ? (
        <section className="card">
          <h3>{editing ? "修改仓库" : "添加仓库"}</h3>
          <div className="source-toggle" role="tablist" aria-label="添加方式">
            <button
              type="button"
              className={source === "git" ? "active" : ""}
              onClick={() => setSource("git")}
            >
              Git 拉取
            </button>
            <button
              type="button"
              className={source === "local" ? "active" : ""}
              onClick={() => setSource("local")}
            >
              本地路径
            </button>
          </div>
          <form className="form" onSubmit={(event) => void onSave(event)}>
            <label htmlFor="repo-name">名称
              <input
                id="repo-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder={source === "git" ? "可留空，默认用仓库名" : "demo-admin-broad"}
                required={source === "local"}
              />
            </label>
            <label htmlFor="repo-port">端口
              <select id="repo-port" value={role} onChange={(event) => setRole(event.target.value)}>
                {REPO_PORTS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
              </select>
            </label>
            {source === "git" ? (
              <>
                <label htmlFor="repo-git-url">Git 地址
                  <input
                    id="repo-git-url"
                    value={gitUrl}
                    onChange={(event) => setGitUrl(event.target.value)}
                    required
                    placeholder="https://github.com/org/demo-app-abroad.git"
                  />
                </label>
                <label htmlFor="repo-git-ref">分支 / Tag
                  <input
                    id="repo-git-ref"
                    value={gitRef}
                    onChange={(event) => setGitRef(event.target.value)}
                    placeholder="默认分支，可填 main / release-1.0"
                  />
                </label>
                <label htmlFor="repo-git-dest">克隆到
                  <input
                    id="repo-git-dest"
                    value={rootPath}
                    onChange={(event) => setRootPath(event.target.value)}
                    required
                    placeholder="/Users/you/IdeaProjects/demo-app-abroad"
                  />
                </label>
                <label htmlFor="repo-git-ssh">SSH 私钥（SSH 地址用）
                  <input
                    id="repo-git-ssh"
                    value={sshIdentity}
                    onChange={(event) => setSshIdentity(event.target.value)}
                    placeholder="/Users/you/.ssh/id_rsa"
                    autoComplete="off"
                  />
                </label>
                <div className="row">
                  <label htmlFor="repo-git-user">HTTPS 用户名
                    <input
                      id="repo-git-user"
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      placeholder="仅 HTTPS 需要"
                      autoComplete="username"
                    />
                  </label>
                  <label htmlFor="repo-git-token">HTTPS Token
                    <input
                      id="repo-git-token"
                      type="password"
                      value={token}
                      onChange={(event) => setToken(event.target.value)}
                      placeholder="仅 HTTPS 需要"
                      autoComplete="current-password"
                    />
                  </label>
                </div>
                <p className="notice">SSH 地址请填本机私钥绝对路径，不要填 Token。Token 只用于 HTTPS。私钥路径只用于本次克隆，不会保存。</p>
              </>
            ) : (
              <>
                <label htmlFor="repo-path">根路径
                  <input
                    id="repo-path"
                    value={rootPath}
                    onChange={(event) => setRootPath(event.target.value)}
                    required
                    placeholder="/Users/.../demo-admin-broad"
                  />
                </label>
                <p className="notice">填写本机已有目录的绝对路径。该目录如果是 Git 仓，也可以点「拉取更新」。</p>
              </>
            )}
            <div className="form-actions">
              {editing ? (
                <button className="btn secondary" type="button" onClick={resetForm}>取消</button>
              ) : null}
              <button className="btn" disabled={saving}>
                <IconPlus size={16} />
                {saving
                  ? (source === "git" && !editing ? "正在克隆…" : "保存中…")
                  : editing
                    ? "保存修改"
                    : source === "git" ? "拉取并保存" : "保存仓库"}
              </button>
            </div>
          </form>
          {error ? <p className="error">{error}</p> : null}
        </section>
        ) : null}
      </div>
      {pendingDelete ? (
        <ConfirmDialog
          title="删除代码仓"
          message={`将解除「${pendingDelete.name}」的绑定。本机目录不会删除。`}
          busy={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void onDelete()}
        />
      ) : null}
    </>
  );
}
