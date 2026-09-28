import { FormEvent, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { api } from "../api";
import {
  activateLiveSurface,
  getLiveSession,
  openConversation,
  patchLiveSession,
  rememberedReviewId,
  resetLiveSession,
  startLiveInvestigation,
  stopLiveInvestigation,
  subscribeLiveSession,
  type BudgetPrompt,
} from "../conversation/liveSession";
import { ComposerAttachRow, UserMessageMedia } from "../conversation/ComposerAttachRow";
import { useComposerAttachments } from "../conversation/composerAttachments";
import {
  REVIEW_SLASH_GROUPS,
  SlashCommandMenu,
  buildSlashCommands,
  consumeSlashToken,
  reviewRuleSlug,
  reviewSkillSlug,
  slashTokenAt,
  type InvestigateFocus,
  type RuleFocus,
  type SlashCommand,
} from "../conversation/SlashCommandMenu";
import { IconCpu, IconDownload, IconGitBranch, IconPaperclip, IconPlus, IconScroll, IconSearch, IconSend, IconSlash, IconSparkle, IconX } from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import { PackageFileTree } from "../review/PackageFileTree";
import { parseChangePath } from "../review/packageTree";
import { MarkdownView } from "../review/markdown";
import { marksForPath, parseReviewMarks, pathMatchesMark, stripReviewMarks } from "../review/reviewMarks";
import { ReviewEmptyState } from "../review/ReviewEmptyState";
import { SplitDiffView } from "../review/SplitDiffView";
import { buildReviewMarkdown, downloadReviewMarkdown, printReviewPdf, reviewExportFilename } from "../review/reviewExport";
import { SearchSelect } from "../ui/SearchSelect";
import { SplitPane, useMediaQuery } from "../ui/SplitPane";
import {
  parseEnvironment,
  type ConversationAttachmentPayload,
  type ConversationSummary,
  type GitBranch,
  type GitCompareResult,
  type GitFileChange,
  type CatalogAsset,
  type GitFileDiff,
  type ModelSnapshot,
  type Repository,
} from "../types";

const DEFAULT_FOCUS = "请按已配置的 Skill / Rule 审查这两个分支的差异，指出风险、回归和测试建议。";

function excerpt(text: string, max = 72) {
  const compact = text.replace(/\s+/g, " ").trim();
  return compact.length > max ? `${compact.slice(0, max)}…` : compact;
}

function formatTime(value: string) {
  return new Date(value).toLocaleString();
}

function formatWait(seconds: number) {
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes} 分 ${String(rest).padStart(2, "0")} 秒`;
}

function thoughtKindLabel(kind?: string) {
  if (kind === "tool") return "工具";
  if (kind === "model") return "模型";
  if (kind === "phase") return "步骤";
  return "思考";
}

function resizeComposer(node: HTMLTextAreaElement | null) {
  if (!node) return;
  node.style.height = "auto";
  node.style.height = `${Math.min(Math.max(node.scrollHeight, 72), 160)}px`;
}

function pickDefaultBase(repo: Repository | undefined, branches: GitBranch[]) {
  const names = branches.map((item) => item.name);
  const preferred = [repo?.gitRef, "release", "main", "develop", "master"].filter((name): name is string => {
    if (!name) return false;
    if (name === "master" && names.includes("release")) return false;
    return names.includes(name);
  });
  return preferred[0] ?? names[0] ?? "";
}

function statusKind(status: string) {
  const code = (status || "").trim().charAt(0).toUpperCase();
  if (code === "A") return "A";
  if (code === "D") return "D";
  if (code === "R") return "R";
  return "M";
}

function statusLabel(status: string) {
  const kind = statusKind(status);
  if (kind === "A") return "新增";
  if (kind === "D") return "删除";
  if (kind === "R") return "重命名";
  return "修改";
}

function isSourceCode(item: GitFileChange) {
  if (typeof item.sourceCode === "boolean") return item.sourceCode;
  const path = item.path.replaceAll("\\", "/").toLowerCase();
  const name = path.slice(path.lastIndexOf("/") + 1);
  if (path.startsWith("db/") || path.includes("/db/") || name.endsWith(".sql") || name.endsWith(".md")) {
    return false;
  }
  return /\.(java|kt|kts|xml|yml|yaml|properties|ts|tsx|js|jsx|vue|gradle|groovy)$/.test(name);
}

const REVIEW_WORKSPACE_PREFIX = "sre-investigator.review.workspace.";

interface ReviewCompare {
  repositoryId: string;
  baseRef: string;
  headRef: string;
  selectedPath: string;
}

interface ReviewWorkspace {
  conversationId: string | null;
  repositoryId: string;
  baseRef: string;
  headRef: string;
  selectedPath: string;
  byConversation: Record<string, ReviewCompare>;
}

function emptyWorkspace(): ReviewWorkspace {
  return {
    conversationId: null,
    repositoryId: "",
    baseRef: "",
    headRef: "",
    selectedPath: "",
    byConversation: {},
  };
}

function readReviewWorkspace(projectId: string): ReviewWorkspace {
  try {
    const raw = localStorage.getItem(REVIEW_WORKSPACE_PREFIX + projectId);
    if (!raw) return emptyWorkspace();
    const parsed = JSON.parse(raw) as Partial<ReviewWorkspace>;
    return {
      conversationId: parsed.conversationId ?? null,
      repositoryId: parsed.repositoryId || "",
      baseRef: parsed.baseRef || "",
      headRef: parsed.headRef || "",
      selectedPath: parsed.selectedPath || "",
      byConversation: parsed.byConversation && typeof parsed.byConversation === "object" ? parsed.byConversation : {},
    };
  } catch {
    return emptyWorkspace();
  }
}

function writeReviewWorkspace(projectId: string, next: ReviewWorkspace) {
  localStorage.setItem(REVIEW_WORKSPACE_PREFIX + projectId, JSON.stringify(next));
}

function compareFrom(workspace: ReviewWorkspace, conversationId?: string | null): ReviewCompare {
  const saved = conversationId ? workspace.byConversation[conversationId] : undefined;
  return {
    repositoryId: saved?.repositoryId || workspace.repositoryId,
    baseRef: saved?.baseRef || workspace.baseRef,
    headRef: saved?.headRef || workspace.headRef,
    selectedPath: saved?.selectedPath || workspace.selectedPath,
  };
}

export function ReviewPage() {
  const { project, environment, setEnvironment, projectsReady } = useOutletContext<LayoutContext>();
  const live = useSyncExternalStore(subscribeLiveSession, getLiveSession, getLiveSession);
  const {
    conversationId,
    transcript,
    userTurns,
    maxTurns,
    thoughts,
    report,
    error,
    running,
    recovering,
    waitStartedAt,
    budgetPrompt,
  } = live;
  const [sessions, setSessions] = useState<ConversationSummary[]>([]);
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [repositoryId, setRepositoryId] = useState("");
  const [branches, setBranches] = useState<GitBranch[]>([]);
  const [baseRef, setBaseRef] = useState("");
  const [headRef, setHeadRef] = useState("");
  const [diff, setDiff] = useState<GitCompareResult | null>(null);
  const [diffLoading, setDiffLoading] = useState(false);
  const [fileQuery, setFileQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | "A" | "M" | "D" | "R">("all");
  const [kindFilter, setKindFilter] = useState<"code" | "all">("code");
  const [selectedPath, setSelectedPath] = useState("");
  const [fileDiff, setFileDiff] = useState<GitFileDiff | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const [focus, setFocus] = useState("");
  const [skillFocus, setSkillFocus] = useState<InvestigateFocus>({ kind: "auto" });
  const [ruleFocus, setRuleFocus] = useState<RuleFocus>(null);
  const [skills, setSkills] = useState<CatalogAsset[]>([]);
  const [rules, setRules] = useState<CatalogAsset[]>([]);
  const [slashOpen, setSlashOpen] = useState(false);
  const [slashQuery, setSlashQuery] = useState("");
  const { files, attachError, addFiles, onPaste, remove, clear, payloads } = useComposerAttachments();
  const [pageError, setError] = useState("");
  const [model, setModel] = useState<ModelSnapshot | null>(null);
  const [modelOpen, setModelOpen] = useState<"header" | "composer" | null>(null);
  const [waitSeconds, setWaitSeconds] = useState(0);
  const [workspaceReady, setWorkspaceReady] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const thoughtEndRef = useRef<HTMLLIElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const repository = useMemo(
    () => repositories.find((item) => item.id === repositoryId),
    [repositories, repositoryId],
  );

  const loadSessions = async () => {
    if (!project) return [];
    const next = await api<ConversationSummary[]>(`/api/projects/${project.id}/review/sessions`);
    setSessions(next);
    return next;
  };

  const loadRepositories = async () => {
    if (!project) return [];
    const next = await api<Repository[]>(`/api/projects/${project.id}/review/repositories`);
    setRepositories(next);
    return next;
  };

  const applyCompare = (compare: ReviewCompare) => {
    if (compare.repositoryId) setRepositoryId(compare.repositoryId);
    if (compare.baseRef) setBaseRef(compare.baseRef);
    if (compare.headRef) setHeadRef(compare.headRef);
    if (compare.selectedPath) setSelectedPath(compare.selectedPath);
  };

  useEffect(() => {
    if (!project) return;
    activateLiveSurface("review", project.id);
    setWorkspaceReady(false);
    const restore = async () => {
      setError("");
      const saved = readReviewWorkspace(project.id);
      const [list, repos] = await Promise.all([
        loadSessions().catch((cause: Error) => {
          setError(cause.message);
          return [] as ConversationSummary[];
        }),
        loadRepositories().catch((cause: Error) => {
          setError(cause.message);
          return [] as Repository[];
        }),
      ]);
      const current = getLiveSession();
      const inPlace = current.surface === "review" && current.projectId === project.id && (current.running || current.transcript.length > 0);
      const target = inPlace
        ? current.conversationId
        : rememberedReviewId(project.id) || saved.conversationId || (saved.repositoryId || saved.baseRef || saved.headRef ? null : list?.[0]?.id);
      applyCompare(compareFrom(saved, target));
      if (!compareFrom(saved, target).repositoryId && repos[0]) {
        setRepositoryId(repos[0].id);
      }
      if (target) {
        const item = list?.find((session) => session.id === target);
        if (item?.environment) setEnvironment(parseEnvironment(item.environment));
        await openConversation(target).catch((cause: Error) => setError(cause.message));
      } else if (!inPlace) {
        resetLiveSession(project.id);
      }
      setWorkspaceReady(true);
    };
    void restore();
  }, [project?.id]);

  useEffect(() => {
    if (!workspaceReady || !project) return;
    const current = readReviewWorkspace(project.id);
    const compare = {
      repositoryId,
      baseRef,
      headRef,
      selectedPath,
    };
    const next: ReviewWorkspace = {
      conversationId,
      ...compare,
      byConversation: { ...current.byConversation },
    };
    if (conversationId) {
      next.byConversation[conversationId] = compare;
    }
    writeReviewWorkspace(project.id, next);
  }, [workspaceReady, project?.id, conversationId, repositoryId, baseRef, headRef, selectedPath]);

  useEffect(() => {
    if (!project || !repositoryId) {
      setBranches([]);
      setBaseRef("");
      setHeadRef("");
      setDiff(null);
      return;
    }
    let cancelled = false;
    void api<GitBranch[]>(`/api/projects/${project.id}/review/repositories/${repositoryId}/branches`)
      .then((next) => {
        if (cancelled) return;
        setBranches(next);
        const names = next.map((item) => item.name);
        setBaseRef((current) => (current && names.includes(current) ? current : pickDefaultBase(repository, next)));
        setHeadRef((current) => (current && names.includes(current) ? current : ""));
      })
      .catch((cause: Error) => {
        if (!cancelled) {
          setBranches([]);
          setError(cause.message);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [project?.id, repositoryId]);

  useEffect(() => {
    if (!project || !repositoryId || !baseRef || !headRef || baseRef === headRef) {
      setDiff(null);
      setFileDiff(null);
      return;
    }
    setFileDiff(null);
    setFileQuery("");
    setStatusFilter("all");
    setKindFilter("code");
    let cancelled = false;
    setDiffLoading(true);
    void api<GitCompareResult>(
      `/api/projects/${project.id}/review/repositories/${repositoryId}/compare?base=${encodeURIComponent(baseRef)}&head=${encodeURIComponent(headRef)}`,
    )
      .then((next) => {
        if (cancelled) return;
        setDiff(next);
        setSelectedPath((current) => (current && next.files.some((item) => item.path === current) ? current : ""));
        setError("");
      })
      .catch((cause: Error) => {
        if (!cancelled) {
          setDiff(null);
          setError(cause.message);
        }
      })
      .finally(() => {
        if (!cancelled) setDiffLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [project?.id, repositoryId, baseRef, headRef]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [transcript, running, report, thoughts]);

  useEffect(() => {
    thoughtEndRef.current?.scrollIntoView({ block: "nearest" });
  }, [thoughts]);

  useEffect(() => {
    resizeComposer(textareaRef.current);
  }, [focus]);

  useEffect(() => {
    if (!running) {
      setWaitSeconds(0);
      return;
    }
    const started = waitStartedAt ?? Date.now();
    const tick = () => setWaitSeconds(Math.floor((Date.now() - started) / 1000));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [running, waitStartedAt]);

  useEffect(() => {
    void api<ModelSnapshot>("/api/agent/model").then(setModel).catch((cause: Error) => setError(cause.message));
  }, []);

  useEffect(() => {
    if (!project) {
      setSkills([]);
      setRules([]);
      return;
    }
    void api<CatalogAsset[]>(`/api/projects/${project.id}/assets`)
      .then((items) => {
        setSkills(items
          .filter((item) => item.kind === "SKILL" && item.enabled)
          .sort((left, right) => {
            const rank = (item: CatalogAsset) => item.slug === "code-review" || item.catalogId === "skill-code-review" ? 0 : 1;
            return rank(left) - rank(right) || left.name.localeCompare(right.name, "zh");
          }));
        setRules(items.filter((item) => item.kind === "RULE" && item.enabled));
      })
      .catch(() => {
        setSkills([]);
        setRules([]);
      });
  }, [project?.id]);

  useEffect(() => {
    if (!modelOpen && !slashOpen) return;
    const onDoc = (event: MouseEvent) => {
      const target = event.target as Element | null;
      if (!target?.closest(".review-model-picker")) setModelOpen(null);
      if (!target?.closest(".mention-wrap") && !target?.closest(".review-skill-picker")) setSlashOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [modelOpen, slashOpen]);

  const stacked = useMediaQuery("(max-width: 900px)");
  const slashCommands = useMemo(
    () => buildSlashCommands({
      modes: [],
      mode: "REVIEW",
      routes: [],
      skills,
      rules,
      focus: skillFocus,
      ruleFocus,
      groups: REVIEW_SLASH_GROUPS,
    }),
    [skills, rules, skillFocus, ruleFocus],
  );

  const syncSlash = (text: string, caret: number) => {
    const slash = slashTokenAt(text, caret);
    if (slash !== null) {
      setSlashOpen(true);
      setSlashQuery(slash);
      setModelOpen(null);
      return;
    }
    setSlashOpen(false);
  };

  const openSkillSlash = () => {
    setSlashOpen(true);
    setSlashQuery("");
    setModelOpen(null);
  };

  const applySlash = (item: SlashCommand) => {
    if (item.disabled) return;
    if (item.id.startsWith("skill:")) {
      const slug = item.id.slice(6);
      const skill = skills.find((entry) => entry.slug === slug);
      setSkillFocus({
        kind: "skill",
        slug,
        name: skill?.name || item.label,
      });
    } else if (item.id.startsWith("rule:")) {
      const slug = item.id.slice(5);
      const rule = rules.find((entry) => entry.slug === slug);
      setRuleFocus({ slug, name: rule?.name || item.label });
    } else {
      return;
    }
    const caret = textareaRef.current?.selectionStart ?? focus.length;
    const consumed = consumeSlashToken(focus, caret);
    setFocus(consumed.text);
    setSlashOpen(false);
    setSlashQuery("");
    requestAnimationFrame(() => {
      const node = textareaRef.current;
      if (!node) return;
      node.focus();
      node.setSelectionRange(consumed.caret, consumed.caret);
    });
  };

  if (!project) {
    return (
      <div className="empty empty-hero">
        <span className="empty-icon"><IconScroll size={22} /></span>
        <h3>{projectsReady ? "还没有选中项目" : "正在加载项目…"}</h3>
        <p>
          {projectsReady
            ? <><Link to="/projects">回到项目列表</Link> 后再开始审查。</>
            : "正在读取项目上下文。"}
        </p>
      </div>
    );
  }

  const showSession = async (id: string) => {
    setError("");
    const item = sessions.find((session) => session.id === id);
    if (item?.environment) setEnvironment(parseEnvironment(item.environment));
    if (project) applyCompare(compareFrom(readReviewWorkspace(project.id), id));
    try {
      await openConversation(id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "读取审查记录失败");
    }
  };

  const atLimit = userTurns >= maxTurns;
  const canCompare = Boolean(repositoryId && baseRef && headRef && baseRef !== headRef);
  const readyProviders = Object.entries(model?.providers ?? {}).filter(([, item]) => item.configured);
  const reviewMarks = useMemo(
    () => transcript.flatMap((item) => item.role === "assistant" ? parseReviewMarks(item.content) : []),
    [transcript],
  );
  const branchOptions = branches.map((item) => ({
    value: item.name,
    label: item.name,
    hint: item.remote ? "remote" : "本地",
  }));
  const repositoryOptions = repositories.map((item) => ({
    value: item.id,
    label: item.name,
    hint: item.rootPath,
  }));
  const filteredFiles = useMemo(() => {
    const query = fileQuery.trim().toLowerCase();
    return (diff?.files ?? []).filter((item) => {
      if (kindFilter === "code" && !isSourceCode(item)) return false;
      if (statusFilter !== "all" && statusKind(item.status) !== statusFilter) return false;
      if (!query) return true;
      const parsed = parseChangePath(item.path);
      const hay = `${item.path} ${parsed.module} ${parsed.pkg} ${parsed.className}`.toLowerCase();
      return hay.includes(query) || hay.includes(query.replaceAll(".", "/")) || hay.includes(query.replaceAll("/", "."));
    });
  }, [diff, fileQuery, statusFilter, kindFilter]);
  const reviewOpinions = useMemo(
    () => transcript
      .filter((item) => item.role === "assistant")
      .map((item) => stripReviewMarks(item.content).trim())
      .filter(Boolean),
    [transcript],
  );
  const canExport = reviewOpinions.length > 0;

  const exportReview = (format: "md" | "pdf") => {
    const markdown = buildReviewMarkdown({
      projectName: project.name,
      repositoryName: repository?.name,
      baseRef: diff?.baseRef || baseRef,
      headRef: diff?.headRef || headRef,
      stat: diff?.stat,
      files: diff?.files,
      opinions: reviewOpinions,
    });
    const filename = reviewExportFilename({
      repositoryName: repository?.name,
      baseRef: diff?.baseRef || baseRef,
      headRef: diff?.headRef || headRef,
      extension: format,
    });
    if (format === "md") {
      downloadReviewMarkdown(filename, markdown);
      return;
    }
    printReviewPdf(filename.replace(/\.pdf$/i, ""), markdown);
  };

  const openFile = async (item: GitFileChange) => {
    if (!project || !repositoryId || !baseRef || !headRef) return;
    setSelectedPath(item.path);
    setFileLoading(true);
    setError("");
    try {
      setFileDiff(await api<GitFileDiff>(
        `/api/projects/${project.id}/review/repositories/${repositoryId}/compare/file?base=${encodeURIComponent(baseRef)}&head=${encodeURIComponent(headRef)}&path=${encodeURIComponent(item.path)}`,
      ));
    } catch (cause) {
      setFileDiff(null);
      setError(cause instanceof Error ? cause.message : "读取文件差异失败");
    } finally {
      setFileLoading(false);
    }
  };

  useEffect(() => {
    if (!diff || !selectedPath || fileDiff || fileLoading) return;
    const item = diff.files.find((file) => file.path === selectedPath);
    if (item) void openFile(item);
  }, [diff]);

  const switchModel = async (provider: string, modelName?: string) => {
    setModelOpen(null);
    if (!model || (provider === model.provider && (!modelName || modelName === model.model))) return;
    setError("");
    try {
      setModel(await api<ModelSnapshot>("/api/agent/model", {
        method: "PUT",
        body: JSON.stringify({ provider, model: modelName }),
      }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "切换模型失败");
    }
  };

  const renderModelPicker = (placement: "header" | "composer") => (
    <div className="popover-wrap review-model-picker">
      <button
        className="chip-btn"
        type="button"
        disabled={running}
        onClick={() => {
          setSlashOpen(false);
          setModelOpen((open) => (open === placement ? null : placement));
        }}
      >
        <IconCpu size={14} />
        {model?.available ? `${model.providerLabel} · ${model.model}` : "选择模型"}
      </button>
      {modelOpen === placement ? (
        <div className={`popover model-popover${placement === "header" ? " down" : ""}`}>
          {readyProviders.length === 0 ? (
            <p className="notice">还没有就绪的模型。先到模型页测试连接，再从下拉框选择。</p>
          ) : readyProviders.map(([id, item]) => {
            const names = item.available?.length
              ? item.available
              : (item.models?.length ? item.models : (item.model ? [item.model] : []));
            return (
              <div key={id} className="popover-group">
                <div className="popover-group-label">{item.label}</div>
                {names.map((name) => (
                  <button
                    key={`${id}:${name}`}
                    type="button"
                    className={`popover-item${id === model?.provider && name === model?.model ? " active" : ""}`}
                    onClick={() => void switchModel(id, name)}
                  >
                    <b>{name}</b>
                  </button>
                ))}
              </div>
            );
          })}
          <Link className="popover-item" to="/settings/model" onClick={() => setModelOpen(null)}>添加 / 配置模型</Link>
        </div>
      ) : null}
    </div>
  );

  const runReview = async (input: { text: string; attachments?: ConversationAttachmentPayload[]; continueFromRunId?: string }) => {
    if (!canCompare) {
      setError("请先选择仓库和两个不同的分支");
      return;
    }
    if (!input.continueFromRunId) {
      setFocus("");
      clear();
    }
    setModelOpen(null);
    setError("");
    await startLiveInvestigation({
      projectId: project.id,
      environment,
      text: input.text,
      mode: "REVIEW",
      provider: model?.provider,
      model: model?.model,
      route: "code-review",
      skillSlug: reviewSkillSlug(skillFocus),
      ruleSlug: reviewRuleSlug(ruleFocus),
      attachments: input.attachments ?? [],
      continueFromRunId: input.continueFromRunId,
      repositoryId,
      baseRef,
      headRef,
    });
    await loadSessions().catch((cause: Error) => setError(cause.message));
  };

  const onStart = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const text = focus.trim() || (files.length ? "请结合附件截图审查这两个分支的差异。" : DEFAULT_FOCUS);
    if (atLimit || running) return;
    await runReview({ text, attachments: payloads() });
  };

  const continueReview = async (prompt: BudgetPrompt) => {
    await runReview({
      text: prompt.question,
      continueFromRunId: prompt.runId,
    });
  };

  return (
    <div className="chat-page review-page">
      <SplitPane
        axis={stacked ? "vertical" : "horizontal"}
        sized="end"
        defaultSize={stacked ? 340 : 380}
        minStart={stacked ? 260 : 460}
        minEnd={stacked ? 220 : 300}
        storageKey={stacked ? "sre-investigator.review.dockH" : "sre-investigator.review.dockW"}
        className="review-shell"
      >
      <section className="chat-main review-main">
        <div className="page-header">
          <div>
            <p className="kicker">{project.slug} / {environment}</p>
            <h2>代码审查</h2>
            <p>左侧看分支差异，右侧写审查意见。基准请选主干（release / main）。</p>
          </div>
          <div className="chat-actions">
            <span className={`badge${atLimit ? " warn" : ""}`}>{userTurns}/{maxTurns} 轮</span>
            <div className="page-header-actions">
              {renderModelPicker("header")}
              <button className="btn secondary" type="button" disabled={running} onClick={() => {
                resetLiveSession(project.id);
                setSkillFocus({ kind: "auto" });
                setSlashOpen(false);
              }}>
                <IconPlus size={14} />
                新审查
              </button>
            </div>
          </div>
        </div>

        <div className="review-setup">
        <div className="review-compare">
          <label>
            <span>仓库</span>
            <SearchSelect
              value={repositoryId}
              options={repositoryOptions}
              onChange={setRepositoryId}
              disabled={running}
              placeholder={repositories.length === 0 ? "还没有代码仓" : "选择代码仓库"}
              searchPlaceholder="搜索仓库名或路径"
              emptyLabel="没有匹配的仓库"
            />
          </label>
          <label>
            <span>基准分支</span>
            <SearchSelect
              value={baseRef}
              options={branchOptions}
              onChange={setBaseRef}
              disabled={running || branches.length === 0}
              placeholder={branches.length === 0 ? "没有可对比的分支" : "选择基准分支"}
              searchPlaceholder="搜索分支"
              emptyLabel="没有匹配的分支"
            />
          </label>
          <label>
            <span>对比分支</span>
            <SearchSelect
              value={headRef}
              options={branchOptions}
              onChange={setHeadRef}
              disabled={running || branches.length === 0}
              placeholder={branches.length === 0 ? "没有可对比的分支" : "请选择对比分支"}
              searchPlaceholder="搜索分支"
              emptyLabel="没有匹配的分支"
              align="end"
            />
          </label>
        </div>

        {diffLoading ? <p className="notice">正在拉取所选分支最新代码并对比…</p> : null}
        {!diff && !diffLoading ? (
          <ReviewEmptyState
            hasRepo={Boolean(repositoryId)}
            hasBase={Boolean(baseRef)}
            hasHead={Boolean(headRef)}
            sameBranch={Boolean(baseRef && headRef && baseRef === headRef)}
          />
        ) : null}
        {diff ? (
          <div className="review-diff">
            <div className="review-diff-head">
              <IconGitBranch size={14} />
              <b>{diff.baseRef}...{diff.headRef}</b>
              <span className="badge">{diff.files.filter((item) => isSourceCode(item)).length} 个源码</span>
              {diff.files.some((item) => !isSourceCode(item)) ? (
                <span className="badge">{diff.files.length} 个变更（含脚本/资源）</span>
              ) : (
                <span className="badge">{diff.files.length} 个文件</span>
              )}
              {diff.fetchedRemote ? <span className="badge ok">已拉最新</span> : <span className="badge">本地分支</span>}
              {diff.commonAncestor === false ? <span className="badge warn">无共同祖先</span> : null}
            </div>
            {diff.stat ? <p className="review-stat">{diff.stat}</p> : null}
            {diff.files.length > 0 ? (
              <SplitPane
                axis={stacked ? "vertical" : "horizontal"}
                sized="start"
                defaultSize={stacked ? 220 : 280}
                minStart={160}
                minEnd={220}
                storageKey={stacked ? "sre-investigator.review.filesH" : "sre-investigator.review.filesW"}
                className="review-file-browser"
              >
                <div className="review-file-list">
                  <label className="review-file-search">
                    <IconSearch size={14} />
                    <input
                      value={fileQuery}
                      placeholder="搜索类名或包名"
                      onChange={(event) => setFileQuery(event.target.value)}
                    />
                  </label>
                  <div className="review-file-filters">
                    <button
                      type="button"
                      className={`chip-btn${kindFilter === "code" ? " active" : ""}`}
                      onClick={() => setKindFilter("code")}
                    >
                      源码
                    </button>
                    <button
                      type="button"
                      className={`chip-btn${kindFilter === "all" ? " active" : ""}`}
                      onClick={() => setKindFilter("all")}
                    >
                      全部变更
                    </button>
                    {([
                      ["all", "全部状态"],
                      ["M", "修改"],
                      ["A", "新增"],
                      ["D", "删除"],
                      ["R", "重命名"],
                    ] as const).map(([value, label]) => (
                      <button
                        key={value}
                        type="button"
                        className={`chip-btn${statusFilter === value ? " active" : ""}`}
                        onClick={() => setStatusFilter(value)}
                      >
                        {label}
                      </button>
                    ))}
                    <span className="badge">{filteredFiles.length}/{kindFilter === "code" ? diff.files.filter((item) => isSourceCode(item)).length : diff.files.length}</span>
                  </div>
                  <div className="review-file-items" role="listbox" aria-label="按包分组的变更类">
                    {filteredFiles.length === 0 ? (
                      <p className="notice">
                        {kindFilter === "code" && diff.files.length > 0
                          ? "没有源码差异。SQL / 脚本 / 资源已隐藏，可点「全部变更」查看。"
                          : "没有匹配的文件"}
                      </p>
                    ) : (
                      <PackageFileTree
                        files={filteredFiles.slice(0, 400)}
                        selectedPath={selectedPath}
                        flagged={(path) => reviewMarks.some((item) => pathMatchesMark(item.path, path))}
                        onSelect={(item) => void openFile(item)}
                      />
                    )}
                    {filteredFiles.length > 400 ? (
                      <p className="notice">仅显示前 400 个匹配项，请继续输入类名或包名缩小范围</p>
                    ) : null}
                  </div>
                </div>
                <div className="review-file-view">
                  {fileLoading ? <p className="notice">正在读取文件差异…</p> : null}
                  {!fileLoading && !fileDiff ? (
                    <div className="empty">
                      <h3>点击左侧文件查看差异</h3>
                      <p>先浏览变更列表，再点开具体文件。审查仍按 Skill / Rule 对整次对比给出意见。</p>
                    </div>
                  ) : null}
                  {!fileLoading && fileDiff ? (
                    <SplitDiffView
                      path={fileDiff.path}
                      unifiedDiff={fileDiff.unifiedDiff}
                      truncated={fileDiff.truncated}
                      baseRef={diff.baseRef}
                      headRef={diff.headRef}
                      marks={marksForPath(reviewMarks, fileDiff.path)}
                    />
                  ) : null}
                </div>
              </SplitPane>
            ) : <p className="notice">这两个分支没有差异。</p>}
          </div>
        ) : null}
        {branches.length === 1 ? (
          <p className="notice">当前仓可能只有一个本地分支。选好两个分支后会先拉取 origin 最新再对比。</p>
        ) : null}
        </div>

      </section>

      <aside className="review-dock" aria-label="审查对话">
        <SplitPane
          axis="vertical"
          sized="end"
          defaultSize={180}
          minStart={140}
          minEnd={132}
          storageKey="sre-investigator.review.composerH"
          className="review-dock-split"
        >
        <div className="review-result">
        <div className="card chat-history">
          <div className="card-head">
            <div className="review-export-head">
              <h3>审查记录</h3>
              <span className="badge">{sessions.length}</span>
            </div>
            <div className="review-export">
              <button
                className="btn ghost"
                type="button"
                disabled={!canExport}
                onClick={() => exportReview("md")}
              >
                <IconDownload size={13} />
                Markdown
              </button>
              <button
                className="btn ghost"
                type="button"
                disabled={!canExport}
                onClick={() => exportReview("pdf")}
              >
                <IconDownload size={13} />
                PDF
              </button>
            </div>
          </div>
          {sessions.length > 0 ? (
            <div className="list">
              {sessions.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`item buttonish${conversationId === item.id ? " active" : ""}`}
                  onClick={() => void showSession(item.id)}
                >
                  <b>{excerpt(item.title || "未命名审查")}</b>
                  <span>
                    <span className="badge">{item.userTurns}/{item.maxTurns} 轮</span>
                    {running && conversationId === item.id ? <span className="badge accent">进行中</span> : null}
                    {"  "}{formatTime(item.updatedAt)}
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <div className="empty">还没有审查记录。</div>
          )}
        </div>

        <div className="chat-transcript">
          {transcript.length > 0 || running ? (
            <>
              {transcript.map((message, index) => {
                const lastAssistant = message.role === "assistant" && index === transcript.length - 1;
                const canContinue = lastAssistant
                  && !running
                  && Boolean(message.runId)
                  && message.content.includes("只读工具已达上限");
                return message.role === "user" ? (
                  <div className="bubble user" key={message.id}>
                    <small>审查 · {formatTime(message.createdAt)}</small>
                    {message.content.trim() ? <div>{message.content}</div> : null}
                    <UserMessageMedia attachments={message.attachments} />
                  </div>
                ) : (
                  <div className="bubble assistant" key={message.id}>
                    <small>助手 · {formatTime(message.createdAt)}</small>
                    <MarkdownView source={stripReviewMarks(message.content)} />
                    {canContinue ? (
                      <button
                        className="btn secondary"
                        type="button"
                        style={{ marginTop: 12 }}
                        onClick={() => void continueReview({
                          runId: message.runId as string,
                          question: [...transcript].reverse().find((item) => item.role === "user")?.content || DEFAULT_FOCUS,
                          attachments: [...transcript].reverse().find((item) => item.role === "user")?.attachments || [],
                          used: 0,
                          max: 0,
                        })}
                      >
                        继续审查
                      </button>
                    ) : null}
                  </div>
                );
              })}
              {running ? (
                <div className="bubble assistant pending" aria-busy="true">
                  <div className="run-status" role="status" aria-atomic="true">
                    <span className="run-spinner" aria-hidden="true" />
                    <div className="run-status-main">
                      <b>{thoughts[thoughts.length - 1]?.message || report}</b>
                      <small>
                        {recovering
                          ? "已离开过页面，正在同步已保存结果"
                          : `已等待 ${formatWait(waitSeconds)}，切到其他页也不会丢掉这次结果`}
                      </small>
                    </div>
                    <button
                      type="button"
                      className="btn danger secondary run-stop"
                      onClick={() => stopLiveInvestigation()}
                    >
                      <IconX size={14} />
                      停止
                    </button>
                  </div>
                  {thoughts.length > 0 ? (
                    <ol className="thought-log" role="log" aria-live="polite" aria-relevant="additions">
                      {thoughts.map((item, index) => (
                        <li
                          key={`${item.kind}-${item.message}-${index}`}
                          ref={index === thoughts.length - 1 ? thoughtEndRef : undefined}
                          className={index === thoughts.length - 1 ? "current" : ""}
                        >
                          <span className={`thought-kind ${item.kind}`}>{thoughtKindLabel(item.kind)}</span>
                          <span>{item.message}</span>
                        </li>
                      ))}
                    </ol>
                  ) : null}
                </div>
              ) : null}
              <div ref={bottomRef} />
            </>
          ) : (
            <div className="empty empty-hero">
              <span className="empty-icon"><IconSparkle size={22} /></span>
              <h3>在这里开始审查</h3>
              <p>选好左侧分支后即可发送。输入 / 选择已启用 Skill，可粘贴报错或 diff 截图。</p>
              <div className="empty-hints">
                <span className="badge">只读</span>
                <span className="badge">/ Skill</span>
                <span className="badge">粘贴截图</span>
              </div>
            </div>
          )}
        </div>
        </div>

        <form
          className="composer"
          onSubmit={(event) => void onStart(event)}
          onPaste={onPaste}
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => {
            event.preventDefault();
            if (event.dataTransfer.files.length) void addFiles(event.dataTransfer.files);
          }}
        >
          <ComposerAttachRow files={files} onRemove={remove} />
          <div className="mention-wrap">
            <SlashCommandMenu
              open={slashOpen}
              query={slashQuery}
              commands={slashCommands}
              groups={REVIEW_SLASH_GROUPS}
              onSelect={applySlash}
            />
          <label className="composer-field" htmlFor="review-focus">
            <span className="sr-only">审查焦点</span>
            <textarea
              id="review-focus"
              ref={textareaRef}
              value={focus}
              rows={3}
              onChange={(event) => {
                const next = event.target.value;
                setFocus(next);
                syncSlash(next, event.target.selectionStart);
              }}
              onCompositionEnd={(event) => {
                const node = event.currentTarget;
                syncSlash(node.value, node.selectionStart);
              }}
              placeholder="可选：写审查焦点，或输入 / 选择 Skill。留空则按默认提纲审查。"
              onKeyDown={(event) => {
                if (event.key === "Escape") setSlashOpen(false);
                if (slashOpen && (event.key === "ArrowDown" || event.key === "ArrowUp" || (event.key === "Enter" && !event.metaKey && !event.ctrlKey))) {
                  event.preventDefault();
                  return;
                }
                if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
                  event.currentTarget.form?.requestSubmit();
                }
              }}
            />
          </label>
          </div>
          <div className="composer-bar">
            <input
              ref={fileInput}
              type="file"
              hidden
              multiple
              accept="image/*,.txt,.md,.log,.json,.yml,.yaml,.xml,.csv,.sql"
              onChange={(event) => {
                if (event.target.files) void addFiles(event.target.files);
                event.target.value = "";
              }}
            />
            <button className="icon-btn" type="button" aria-label="添加图片或文本" title="粘贴截图，或选择图片 / 文本" onClick={() => fileInput.current?.click()}>
              <IconPaperclip size={16} />
            </button>
            <button
              className={`icon-btn review-skill-picker${slashOpen ? " active" : ""}`}
              type="button"
              aria-label="选择 Skill"
              title="输入 / 选择已启用 Skill"
              aria-expanded={slashOpen}
              disabled={running}
              onClick={() => {
                if (slashOpen) {
                  setSlashOpen(false);
                  return;
                }
                const node = textareaRef.current;
                const start = node?.selectionStart ?? focus.length;
                let nextText = focus;
                let caret = start;
                if (slashTokenAt(focus, start) == null) {
                  nextText = `${focus.slice(0, start)}/${focus.slice(start)}`;
                  caret = start + 1;
                  setFocus(nextText);
                  requestAnimationFrame(() => {
                    node?.focus();
                    node?.setSelectionRange(caret, caret);
                  });
                } else {
                  node?.focus();
                }
                setSlashOpen(true);
                setSlashQuery(slashTokenAt(nextText, caret) ?? "");
                setModelOpen(null);
              }}
            >
              <IconSlash size={16} />
            </button>
            {skillFocus.kind === "skill" ? (
              <span className="chip-btn chip-btn-focus review-skill-picker">
                <button type="button" onClick={openSkillSlash}>
                  Skill · {skillFocus.name}
                </button>
                <button type="button" className="chip-clear" aria-label="改回默认 Skill" onClick={() => setSkillFocus({ kind: "auto" })}>
                  <IconX size={11} />
                </button>
              </span>
            ) : null}
            {ruleFocus ? (
              <span className="chip-btn chip-btn-focus review-skill-picker">
                <button type="button" onClick={openSkillSlash}>
                  Rule · {ruleFocus.name}
                </button>
                <button type="button" className="chip-clear" aria-label="取消点名 Rule" onClick={() => setRuleFocus(null)}>
                  <IconX size={11} />
                </button>
              </span>
            ) : null}
            {renderModelPicker("composer")}
            {running ? (
              <button type="button" className="btn danger secondary" onClick={() => stopLiveInvestigation()}>
                <IconX size={14} />
                停止
              </button>
            ) : (
              <button className="btn" type="submit" disabled={atLimit || !canCompare}>
                <IconSend size={14} />
                开始审查
              </button>
            )}
          </div>
          <p className="notice">
            {atLimit
              ? `本审查已达 ${maxTurns} 轮上限，请点右上角「新审查」再继续。`
              : `同一审查 ${userTurns}/${maxTurns} 轮。⌘/Ctrl + Enter 发送。/ 选择 Skill。可直接粘贴截图。`}
          </p>
          {error || pageError || attachError ? <p className="error">{error || pageError || attachError}</p> : null}
        </form>
        </SplitPane>
      </aside>
      </SplitPane>

      {budgetPrompt ? (
        <div className="modal-scrim" role="presentation" onClick={() => patchLiveSession({ budgetPrompt: null })}>
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="review-budget-title" onClick={(event) => event.stopPropagation()}>
            <h3 id="review-budget-title">只读工具已达上限</h3>
            <p>本轮已使用 {budgetPrompt.used}/{budgetPrompt.max || "?"} 次工具。点「继续审查」会带上上一轮结论再跑一轮。</p>
            <div className="modal-actions">
              <button className="btn secondary" type="button" onClick={() => patchLiveSession({ budgetPrompt: null })}>取消</button>
              <button className="btn" type="button" disabled={running} onClick={() => void continueReview(budgetPrompt)}>继续审查</button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
