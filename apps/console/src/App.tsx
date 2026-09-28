import { BrowserRouter, Navigate, Route, Routes, useOutletContext, useParams } from "react-router-dom";
import { AppLayout, type LayoutContext } from "./layout/AppLayout";
import { AgentsPage } from "./pages/AgentsPage";
import { AutomationExecutionPage } from "./pages/AutomationExecutionPage";
import { AutomationsPage } from "./pages/AutomationsPage";
import { BindingsPage } from "./pages/BindingsPage";
import { FeishuPage } from "./pages/FeishuPage";
import { InvestigatePage } from "./pages/InvestigatePage";
import { LoginPage } from "./pages/LoginPage";
import { ModelPage } from "./pages/ModelPage";
import { ProjectsPage } from "./pages/ProjectsPage";
import { RepositoriesPage } from "./pages/RepositoriesPage";
import { ReviewPage } from "./pages/ReviewPage";
import { RequireMenu } from "./pages/RequireMenu";
import { RolesPage } from "./pages/RolesPage";
import { UsagePage } from "./pages/UsagePage";
import { UsersPage } from "./pages/UsersPage";
import { AuditPage } from "./pages/AuditPage";

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<AppLayout />}>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/projects" element={<RequireMenu code="menu.projects"><ProjectsPage /></RequireMenu>} />
          <Route path="/projects/:projectId/repositories" element={<RequireMenu code="menu.repositories"><RepositoriesPage /></RequireMenu>} />
          <Route path="/projects/:projectId/bindings" element={<RequireMenu code="menu.bindings"><BindingsPage /></RequireMenu>} />
          <Route path="/projects/:projectId/agents" element={<RequireMenu code="menu.agents"><AgentsPage /></RequireMenu>} />
          <Route path="/projects/:projectId/feishu" element={<RequireMenu code="menu.bindings"><FeishuPage /></RequireMenu>} />
          <Route path="/automations" element={<RequireMenu code="menu.automations"><AutomationsPage /></RequireMenu>} />
          <Route path="/automations/:jobId/executions/:executionId" element={<RequireMenu code="menu.automations"><AutomationExecutionPage /></RequireMenu>} />
          <Route path="/projects/:projectId/automations" element={<Navigate to="/automations" replace />} />
          <Route path="/projects/:projectId/automations/:jobId/executions/:executionId" element={<AutomationExecutionRedirect />} />
          <Route path="/projects/:projectId/investigate" element={<RequireMenu code="menu.investigate"><InvestigatePage /></RequireMenu>} />
          <Route path="/projects/:projectId/review" element={<RequireMenu code="menu.review"><ReviewPage /></RequireMenu>} />
          <Route path="/projects/:projectId" element={<ProjectIndexRedirect />} />
          <Route path="/settings/model" element={<RequireMenu code="menu.model"><ModelPage /></RequireMenu>} />
          <Route path="/settings/users" element={<RequireMenu code="menu.users"><UsersPage /></RequireMenu>} />
          <Route path="/settings/roles" element={<RequireMenu code="menu.roles"><RolesPage /></RequireMenu>} />
          <Route path="/settings/usage" element={<RequireMenu code="menu.usage"><UsagePage /></RequireMenu>} />
          <Route path="/settings/audit" element={<RequireMenu code="menu.audit"><AuditPage /></RequireMenu>} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function HomeRedirect() {
  const { hasMenu } = useOutletContext<LayoutContext>();
  if (hasMenu("menu.projects")) return <Navigate to="/projects" replace />;
  if (hasMenu("menu.automations")) return <Navigate to="/automations" replace />;
  if (hasMenu("menu.model")) return <Navigate to="/settings/model" replace />;
  if (hasMenu("menu.users")) return <Navigate to="/settings/users" replace />;
  if (hasMenu("menu.roles")) return <Navigate to="/settings/roles" replace />;
  if (hasMenu("menu.usage")) return <Navigate to="/settings/usage" replace />;
  if (hasMenu("menu.audit")) return <Navigate to="/settings/audit" replace />;
  return (
    <div className="empty empty-hero">
      <h3>没有权限</h3>
      <p>当前账号没有可访问的菜单，请联系管理员分配角色。</p>
    </div>
  );
}

function AutomationExecutionRedirect() {
  const { jobId, executionId } = useParams();
  return <Navigate to={`/automations/${jobId}/executions/${executionId}`} replace />;
}

function ProjectIndexRedirect() {
  const { hasMenu } = useOutletContext<LayoutContext>();
  const { projectId } = useParams();
  if (hasMenu("menu.repositories")) return <Navigate to={`/projects/${projectId}/repositories`} replace />;
  if (hasMenu("menu.automations")) return <Navigate to="/automations" replace />;
  if (hasMenu("menu.investigate")) return <Navigate to={`/projects/${projectId}/investigate`} replace />;
  if (hasMenu("menu.review")) return <Navigate to={`/projects/${projectId}/review`} replace />;
  if (hasMenu("menu.agents")) return <Navigate to={`/projects/${projectId}/agents`} replace />;
  if (hasMenu("menu.bindings")) return <Navigate to={`/projects/${projectId}/bindings`} replace />;
  return <Navigate to="/projects" replace />;
}
