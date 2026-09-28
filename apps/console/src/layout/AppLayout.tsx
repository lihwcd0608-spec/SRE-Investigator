import { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate, useParams } from "react-router-dom";
import { api } from "../api";
import {
  IconArrowLeft,
  IconArrowRight,
  IconBot,
  IconChat,
  IconCpu,
  IconGitBranch,
  IconKey,
  IconLogout,
  IconMenu,
  IconMoon,
  IconPlugs,
  IconPulse,
  IconScale,
  IconScroll,
  IconShield,
  IconSquares,
  IconSun,
  IconUsers,
} from "../icons";
import { ENVIRONMENTS, hasMenu, hasPermission, parseEnvironment, type AgentCapabilities, type Environment, type Health, type Me, type Project } from "../types";
import { configureSessionLimits } from "../conversation/liveSession";
import { readStoredTheme, toggleTheme, type ThemeMode } from "../theme";

const LAST_PROJECT_KEY = "sre-investigator.projectId";
const LAST_ENV_KEY = "sre-investigator.environment";
const SIDEBAR_KEY = "sre-investigator.sidebarCollapsed";

export interface LayoutContext {
  me: Me | null;
  projects: Project[];
  project: Project | undefined;
  projectId: string | undefined;
  projectsReady: boolean;
  environment: Environment;
  setEnvironment: (value: Environment) => void;
  refreshProjects: () => Promise<Project[]>;
  hasMenu: (code: string) => boolean;
  hasPermission: (code: string) => boolean;
}

export function AppLayout() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [me, setMe] = useState<Me | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [health, setHealth] = useState<Health | null>(null);
  const [environment, setEnvironmentState] = useState<Environment>(
    () => parseEnvironment(localStorage.getItem(LAST_ENV_KEY)),
  );
  const [menuOpen, setMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === "1");
  const [projectsReady, setProjectsReady] = useState(false);
  const [theme, setTheme] = useState<ThemeMode>(() => readStoredTheme());

  const allowed = (code: string) => hasMenu(me, code);
  const permitted = (code: string) => hasPermission(me, code);

  const refreshProjects = async () => {
    const next = await api<Project[]>("/api/projects");
    setProjects(next);
    return next;
  };

  useEffect(() => {
    let cancelled = false;
    void api<AgentCapabilities>("/api/agent/capabilities")
      .then((next) => {
        if (cancelled) return;
        configureSessionLimits({
          maxTurns: next.maxUserTurns,
          maxToolCalls: next.maxToolCalls,
        });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    void api<Me>("/api/auth/me")
      .then((next) => {
        if (!cancelled) setMe(next);
      })
      .catch(() => undefined);
    void api<Health>("/api/health").then(setHealth).catch(() => setHealth(null));
    return () => {
      cancelled = true;
    };
  }, []);

  const projectScoped = allowed("menu.projects")
    || allowed("menu.repositories")
    || allowed("menu.bindings")
    || allowed("menu.agents")
    || allowed("menu.automations")
    || allowed("menu.investigate")
    || allowed("menu.review");

  useEffect(() => {
    if (!me) return;
    if (!projectScoped) {
      setProjects([]);
      setProjectsReady(true);
      return;
    }
    void refreshProjects()
      .catch(() => setProjects([]))
      .finally(() => setProjectsReady(true));
  }, [me]);

  useEffect(() => {
    if (projectId) localStorage.setItem(LAST_PROJECT_KEY, projectId);
  }, [projectId]);

  useEffect(() => {
    localStorage.setItem(SIDEBAR_KEY, sidebarCollapsed ? "1" : "0");
  }, [sidebarCollapsed]);

  const selectedId = projectId ?? localStorage.getItem(LAST_PROJECT_KEY) ?? "";
  const project = projects.find((item) => item.id === selectedId);
  const onAutomations = location.pathname.includes("/automations");
  const showProject = !onAutomations;
  const showEnvironment = !["/repositories", "/review", "/settings/"].some((path) => location.pathname.includes(path));

  const pageOf = (id: string) => {
    if (location.pathname.startsWith("/settings/")) return location.pathname;
    if (location.pathname.includes("/agents") && allowed("menu.agents")) return `/projects/${id}/agents`;
    if (location.pathname.includes("/bindings") && allowed("menu.bindings")) return `/projects/${id}/bindings`;
    if (location.pathname.includes("/feishu") && allowed("menu.bindings")) return `/projects/${id}/feishu`;
    if (onAutomations && allowed("menu.automations")) return "/automations";
    if (location.pathname.includes("/investigate") && allowed("menu.investigate")) return `/projects/${id}/investigate`;
    if (location.pathname.includes("/review") && allowed("menu.review")) return `/projects/${id}/review`;
    if (allowed("menu.repositories")) return `/projects/${id}/repositories`;
    if (allowed("menu.automations")) return "/automations";
    if (allowed("menu.investigate")) return `/projects/${id}/investigate`;
    if (allowed("menu.review")) return `/projects/${id}/review`;
    if (allowed("menu.agents")) return `/projects/${id}/agents`;
    if (allowed("menu.bindings")) return `/projects/${id}/bindings`;
    return "/projects";
  };

  const links = useMemo(() => {
    const base = selectedId ? `/projects/${selectedId}` : "/projects";
    return [
      { to: "/projects", label: "项目", icon: IconSquares, end: true, menu: "menu.projects", enabled: true },
      { to: selectedId ? `${base}/repositories` : "/projects", label: "代码仓", icon: IconGitBranch, end: false, menu: "menu.repositories", enabled: Boolean(selectedId) },
      { to: selectedId ? `${base}/bindings` : "/projects", label: "工具", icon: IconPlugs, end: false, menu: "menu.bindings", enabled: Boolean(selectedId) },
      { to: selectedId ? `${base}/agents` : "/projects", label: "领域专家编排", icon: IconBot, end: false, menu: "menu.agents", enabled: Boolean(selectedId) },
      { to: selectedId ? `${base}/feishu` : "/projects", label: "飞书群", icon: IconChat, end: false, menu: "menu.bindings", enabled: Boolean(selectedId) },
      { to: "/automations", label: "定时", icon: IconPulse, end: false, menu: "menu.automations", enabled: true },
      { to: selectedId ? `${base}/investigate` : "/projects", label: "对话", icon: IconChat, end: false, menu: "menu.investigate", enabled: Boolean(selectedId) },
      { to: selectedId ? `${base}/review` : "/projects", label: "代码审查", icon: IconScroll, end: false, menu: "menu.review", enabled: Boolean(selectedId) },
      { to: "/settings/model", label: "模型", icon: IconCpu, end: false, menu: "menu.model", enabled: true },
      { to: "/settings/users", label: "用户", icon: IconUsers, end: false, menu: "menu.users", enabled: true },
      { to: "/settings/roles", label: "角色", icon: IconKey, end: false, menu: "menu.roles", enabled: true },
      { to: "/settings/usage", label: "用量", icon: IconScale, end: false, menu: "menu.usage", enabled: true },
      { to: "/settings/audit", label: "审计", icon: IconShield, end: false, menu: "menu.audit", enabled: true },
    ].filter((link) => allowed(link.menu));
  }, [selectedId, me]);

  const setEnvironment = (value: Environment) => {
    setEnvironmentState(value);
    localStorage.setItem(LAST_ENV_KEY, value);
  };

  const logout = async () => {
    try {
      await api("/api/auth/logout", { method: "POST" });
    } catch {
      // still leave
    }
    window.location.assign("/login");
  };

  if (!me) {
    return (
      <div className="login-shell">
        <div className="login-status">
          <span className="brand-mark login-brand-mark">
            <IconPulse size={22} />
          </span>
          <p>正在确认登录状态…</p>
        </div>
      </div>
    );
  }

  return (
    <div className={`app-shell${sidebarCollapsed ? " sidebar-collapsed" : ""}`}>
      {menuOpen ? <button className="sidebar-scrim" type="button" aria-label="关闭菜单" onClick={() => setMenuOpen(false)} /> : null}
      <aside className={`sidebar${menuOpen ? " open" : ""}${sidebarCollapsed ? " collapsed" : ""}`}>
        <div className="brand">
          <span className="brand-mark">
            <IconPulse size={18} />
          </span>
          <div className="brand-copy">
            <h1>SRE Investigator</h1>
            <p>只读排查控制面</p>
          </div>
        </div>
        <nav className="nav" onClick={() => setMenuOpen(false)}>
          {links.map((link) => {
            const Icon = link.icon;
            if (!link.enabled) {
              return (
                <span key={link.label} className="nav-disabled" aria-disabled="true" title={link.label}>
                  <span className="nav-icon">
                    <Icon size={16} />
                  </span>
                  <span className="nav-label">{link.label}</span>
                </span>
              );
            }
            return (
              <NavLink
                key={link.label}
                to={link.to}
                end={link.end}
                title={link.label}
                className={({ isActive }) => (isActive ? "active" : undefined)}
              >
                <span className="nav-icon">
                  <Icon size={16} />
                </span>
                <span className="nav-label">{link.label}</span>
              </NavLink>
            );
          })}
        </nav>
        <div className="sidebar-note">
          <IconShield size={16} />
          <span>提问与只读排查可用。未配置模型或 MCP 时会安全降级。</span>
        </div>
        <button
          type="button"
          className="sidebar-collapse"
          aria-expanded={!sidebarCollapsed}
          aria-label={sidebarCollapsed ? "展开侧边栏" : "收起侧边栏"}
          title={sidebarCollapsed ? "展开侧边栏" : "收起侧边栏"}
          onClick={() => setSidebarCollapsed((open) => !open)}
        >
          {sidebarCollapsed ? <IconArrowRight size={14} /> : <IconArrowLeft size={14} />}
          <span className="nav-label">收起侧边栏</span>
        </button>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="topbar-controls">
            <button className="btn ghost menu-btn" type="button" onClick={() => setMenuOpen((open) => !open)}>
              <IconMenu size={16} />
              菜单
            </button>
            {projectScoped && showProject ? (
              <select
                className="control"
                aria-label="当前项目"
                value={selectedId}
                onChange={(event) => {
                  const id = event.target.value;
                  if (id) navigate(pageOf(id));
                  else navigate("/projects");
                }}
              >
                <option value="">选择项目</option>
                {projects.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name} · {item.slug}
                  </option>
                ))}
              </select>
            ) : null}
            {showEnvironment ? (
              <select
                className={`control env env-${environment}`}
                aria-label="当前环境"
                value={environment}
                onChange={(event) => setEnvironment(event.target.value as Environment)}
              >
                {ENVIRONMENTS.map((item) => (
                  <option key={item} value={item}>{item}</option>
                ))}
              </select>
            ) : null}
          </div>
          <div className="topbar-meta">
            <span className={`badge mono ${health ? "ok" : "danger"}`}>
              <span className={`status-dot${health ? " on" : ""}`} />
              {health ? `${health.status} · ${health.runtime}` : "服务未连接"}
            </span>
            <button
              className="btn ghost theme-toggle"
              type="button"
              aria-label={theme === "dark" ? "切换到浅色主题" : "切换到深色主题"}
              title={theme === "dark" ? "浅色主题" : "深色主题"}
              onClick={() => setTheme((current) => toggleTheme(current))}
            >
              {theme === "dark" ? <IconSun size={14} /> : <IconMoon size={14} />}
              <span className="theme-toggle-label">{theme === "dark" ? "浅色" : "深色"}</span>
            </button>
            <div className="session">
              <span className="session-name">{me.displayName}</span>
              <button className="btn ghost" type="button" onClick={() => void logout()}>
                <IconLogout size={14} />
                退出
              </button>
            </div>
          </div>
        </header>
        <div className="content">
          <Outlet context={{
            me,
            projects,
            project,
            projectId: selectedId || undefined,
            projectsReady,
            environment,
            setEnvironment,
            refreshProjects,
            hasMenu: allowed,
            hasPermission: permitted,
          } satisfies LayoutContext} />
        </div>
      </div>
    </div>
  );
}
