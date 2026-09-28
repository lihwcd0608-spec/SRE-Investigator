import { FormEvent, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { api } from "../api";
import {
  getLiveSession,
  openConversation,
  patchLiveSession,
  rememberedConversationId,
  resetLiveSession,
  startLiveInvestigation,
  stopLiveInvestigation,
  subscribeLiveSession,
  activateLiveSurface,
  type BudgetPrompt,
} from "../conversation/liveSession";
import {
  COMPOSER_SLASH_GROUPS,
  SlashCommandMenu,
  applyNamedMcpMention,
  buildSlashCommands,
  consumeSlashToken,
  ensureNamedMcpMention,
  isComposerSlashPicker,
  parseNamedMcpProfile,
  reviewRuleSlug,
  reviewSkillSlug,
  routeForFocus,
  slashTokenAt,
  type InvestigateFocus,
  type McpFocus,
  type RuleFocus,
  type SlashCommand,
  type SlashGroup,
} from "../conversation/SlashCommandMenu";
import {
  CodeMentionMenu,
  mentionAt,
  mentionScope,
  type CodeEntry,
  type CodeHit,
  type CodeWorkspace,
  type MentionChoice,
} from "../conversation/CodeMentionMenu";
import { ComposerAttachRow, UserMessageMedia } from "../conversation/ComposerAttachRow";
import { useComposerAttachments } from "../conversation/composerAttachments";
import { MarkdownView } from "../review/markdown";
import type { StructuredReport } from "../conversation/liveSession";
import { IconChat, IconCpu, IconPaperclip, IconPlus, IconSend, IconSlash, IconSparkle, IconX } from "../icons";
import type { LayoutContext } from "../layout/AppLayout";
import {
  parseEnvironment,
  type Binding,
  type CatalogAsset,
  type ConversationAttachmentPayload,
  type ConversationMessage,
  type ConversationModeDescriptor,
  type ConversationModeId,
  type ConversationSummary,
  type InvestigationRoute,
  type InvestigationRun,
  type ModelSnapshot,
  type RunStatus,
} from "../types";

function statusBadge(status: RunStatus) {
  if (status === "COMPLETED") return "ok";
  if (status === "FAILED") return "danger";
  if (status === "NEEDS_INPUT") return "warn";
  return "accent";
}

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
  if (kind === "evidence") return "证据";
  return "思考";
}

function EvidenceGroup({ title, items }: { title: string; items: string[] }) {
  if (!items || items.length === 0) return null;
  return (
    <div className="evidence-group">
      <b>{title}</b>
      <ul>
        {items.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}
      </ul>
    </div>
  );
}

function EvidencePanel({ report }: { report: StructuredReport }) {
  const empty = !report.confirmedEvidence?.length
    && !report.hypotheses?.length
    && !report.ruledOut?.length
    && !report.nextSteps?.length;
  if (empty) return null;
  return (
    <aside className="evidence-panel" aria-label="结构化证据">
      <EvidenceGroup title="已确认的证据" items={report.confirmedEvidence} />
      <EvidenceGroup title="根因假设" items={report.hypotheses} />
      <EvidenceGroup title="已排除项" items={report.ruledOut} />
      <EvidenceGroup title="建议下一步" items={report.nextSteps} />
    </aside>
  );
}

function resizeComposer(node: HTMLTextAreaElement | null) {
  if (!node) return;
  node.style.height = "auto";
  node.style.height = `${Math.min(Math.max(node.scrollHeight, 140), 320)}px`;
}

function modeLabel(id?: ConversationModeId) {
  if (id === "ASK") return "提问";
  if (id === "CODE") return "写代码";
  if (id === "REVIEW") return "审查";
  return "排查";
}

function mcpFocusFromText(text: string, items: Binding[]): McpFocus | null {
  const profile = parseNamedMcpProfile(text);
  if (!profile) return null;
  const hit = items.find((item) => item.profile.toLowerCase() === profile.toLowerCase());
  return hit ? { id: hit.id, profile: hit.profile, toolKind: hit.toolKind } : null;
}

function mcpFocusStorageKey(projectId: string) {
  return `sre-investigator.mcp-focus.${projectId}`;
}

function readStoredMcpFocus(projectId: string): McpFocus | null {
  try {
    const raw = sessionStorage.getItem(mcpFocusStorageKey(projectId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as McpFocus;
    return parsed?.profile ? parsed : null;
  } catch {
    return null;
  }
}

export function InvestigatePage() {
  const { project, environment, setEnvironment, projectsReady } = useOutletContext<LayoutContext>();
  const live = useSyncExternalStore(subscribeLiveSession, getLiveSession, getLiveSession);
  const {
    conversationId,
    transcript,
    userTurns,
    maxTurns,
    selected,
    thoughts,
    report,
    structuredReport,
    error,
    running,
    recovering,
    waitStartedAt,
    budgetPrompt,
  } = live;
  const [runs, setRuns] = useState<InvestigationRun[]>([]);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [waitSeconds, setWaitSeconds] = useState(0);
  const bottomRef = useRef<HTMLDivElement>(null);
  const thoughtEndRef = useRef<HTMLLIElement>(null);
  const [pageError, setError] = useState("");
  const [model, setModel] = useState<ModelSnapshot | null>(null);
  const [modes, setModes] = useState<ConversationModeDescriptor[]>([]);
  const [mode, setMode] = useState<ConversationModeId>(live.mode);
  const [question, setQuestion] = useState("");
  const { files, attachError, addFiles, onPaste, remove, clear, payloads } = useComposerAttachments();
  const [modelOpen, setModelOpen] = useState(false);
  const [slashOpen, setSlashOpen] = useState(false);
  const [slashQuery, setSlashQuery] = useState("");
  const [slashGroups, setSlashGroups] = useState<SlashGroup[]>(COMPOSER_SLASH_GROUPS);
  const [routes, setRoutes] = useState<InvestigationRoute[]>([]);
  const [skills, setSkills] = useState<CatalogAsset[]>([]);
  const [rules, setRules] = useState<CatalogAsset[]>([]);
  const [bindings, setBindings] = useState<Binding[]>([]);
  const [focus, setFocus] = useState<InvestigateFocus>({ kind: "auto" });
  const [ruleFocus, setRuleFocus] = useState<RuleFocus>(null);
  const [mcpFocus, setMcpFocus] = useState<McpFocus | null>(null);
  const [mentionOpen, setMentionOpen] = useState(false);
  const [mentionQuery, setMentionQuery] = useState("");
  const mentionQueryRef = useRef(mentionQuery);
  mentionQueryRef.current = mentionQuery;
  const [mentionHits, setMentionHits] = useState<CodeHit[]>([]);
  const [mentionEntries, setMentionEntries] = useState<CodeEntry[]>([]);
  const [mentionLoading, setMentionLoading] = useState(false);
  const [workspaces, setWorkspaces] = useState<CodeWorkspace[]>([]);
  const fileInput = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const loadRuns = async () => {
    if (!project) return;
    const next = await api<InvestigationRun[]>(`/api/projects/${project.id}/investigations`);
    setRuns(next);
    return next;
  };

  const loadConversations = async () => {
    if (!project) return;
    const next = await api<ConversationSummary[]>(`/api/projects/${project.id}/conversations`);
    setConversations(next);
    return next;
  };

  const resetThread = () => {
    resetLiveSession(project?.id);
    setMode("INVESTIGATE");
    setFocus({ kind: "auto" });
    setRuleFocus(null);
    setMcpFocus(null);
    if (project) sessionStorage.removeItem(mcpFocusStorageKey(project.id));
  };

  useEffect(() => {
    if (!project) return;
    setMcpFocus(readStoredMcpFocus(project.id));
  }, [project?.id]);

  useEffect(() => {
    if (!project) return;
    const key = mcpFocusStorageKey(project.id);
    if (mcpFocus) sessionStorage.setItem(key, JSON.stringify(mcpFocus));
    else sessionStorage.removeItem(key);
  }, [project?.id, mcpFocus]);

  useEffect(() => {
    if (!project) return;
    activateLiveSurface("conversation", project.id);
    const restore = async () => {
      const list = await loadConversations().catch((cause: Error) => {
        setError(cause.message);
        return [] as ConversationSummary[];
      });
      await loadRuns().catch((cause: Error) => setError(cause.message));
      const current = getLiveSession();
      if (current.projectId === project.id && (current.running || current.transcript.length > 0)) {
        if (current.conversationId) {
          await openConversation(current.conversationId).catch((cause: Error) => setError(cause.message));
        } else if (current.running) {
          patchLiveSession({
            running: false,
            recovering: false,
            waitStartedAt: null,
            error: "服务已中断，这轮排查没有保存到结果。",
            report: current.report.startsWith("正在") ? "这轮排查已中断。" : current.report,
          });
        }
        return;
      }
      const target = rememberedConversationId(project.id) || list?.[0]?.id;
      if (target) {
        const item = list?.find((conversation) => conversation.id === target);
        if (item?.environment) setEnvironment(parseEnvironment(item.environment));
        await openConversation(target).catch((cause: Error) => setError(cause.message));
      } else {
        resetLiveSession(project.id);
      }
    };
    void restore();
  }, [project?.id]);

  useEffect(() => {
    if (live.mode) setMode(live.mode);
  }, [live.mode, live.conversationId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [transcript, running, report, thoughts]);

  useEffect(() => {
    thoughtEndRef.current?.scrollIntoView({ block: "nearest" });
  }, [thoughts]);

  useEffect(() => {
    resizeComposer(textareaRef.current);
  }, [question]);

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
    void api<ConversationModeDescriptor[]>("/api/agent/modes")
      .then((next) => setModes(next.filter((item) => item.id !== "REVIEW")))
      .catch((cause: Error) => setError(cause.message));
    void api<InvestigationRoute[]>("/api/agent/routes").then(setRoutes).catch((cause: Error) => setError(cause.message));
  }, []);

  useEffect(() => {
    if (!project) return;
    void api<CatalogAsset[]>(`/api/projects/${project.id}/assets`)
      .then((items) => {
        setSkills(items.filter((item) => item.kind === "SKILL" && item.enabled));
        setRules(items.filter((item) => item.kind === "RULE" && item.enabled));
      })
      .catch(() => {
        setSkills([]);
        setRules([]);
      });
  }, [project?.id]);

  useEffect(() => {
    if (!project) return;
    void api<Binding[]>(`/api/projects/${project.id}/bindings`)
      .then((items) => {
        const next = items
          .filter((item) => item.enabled && item.environment === environment)
          .sort((left, right) => {
            const kind = left.toolKind.localeCompare(right.toolKind);
            return kind !== 0 ? kind : left.profile.localeCompare(right.profile);
          });
        setBindings(next);
        setMcpFocus((current) => {
          if (current && next.some((item) => item.id === current.id || item.profile === current.profile)) {
            return next.find((item) => item.id === current.id || item.profile === current.profile) ?? current;
          }
          return null;
        });
      })
      .catch(() => {
        setBindings([]);
        setMcpFocus(null);
      });
  }, [project?.id, environment]);

  useEffect(() => {
    if (mcpFocus || bindings.length === 0) return;
    const lastUser = [...transcript].reverse().find((item) => item.role === "user")?.content || "";
    const pinned = mcpFocusFromText(lastUser, bindings);
    if (pinned) setMcpFocus(pinned);
  }, [transcript, bindings, mcpFocus]);

  useEffect(() => {
    if (!project) return;
    void api<CodeWorkspace[]>(`/api/projects/${project.id}/code/workspaces`)
      .then(setWorkspaces)
      .catch(() => setWorkspaces([]));
  }, [project?.id]);

  useEffect(() => {
    if (!mentionOpen || !project) return;
    const scope = mentionScope(mentionQuery, workspaces);
    let cancelled = false;
    const timer = window.setTimeout(() => {
      if (scope.mode === "browse") {
        const params = new URLSearchParams({ workspace: scope.workspace });
        if (scope.dir) params.set("path", scope.dir);
        if (scope.filter) params.set("q", scope.filter);
        setMentionLoading(true);
        const listing = api<CodeEntry[]>(`/api/projects/${project.id}/code/files?${params}`)
          .then((items) => {
            if (!cancelled) setMentionEntries(items);
          })
          .catch(() => {
            if (!cancelled) setMentionEntries([]);
          });
        const searching = scope.filter.trim()
          ? api<CodeHit[]>(`/api/projects/${project.id}/code/search?q=${encodeURIComponent(scope.filter.trim())}`)
              .then((items) => {
                if (!cancelled) setMentionHits(items);
              })
              .catch(() => {
                if (!cancelled) setMentionHits([]);
              })
          : Promise.resolve().then(() => {
              if (!cancelled) setMentionHits([]);
            });
        void Promise.all([listing, searching]).finally(() => {
          if (!cancelled) setMentionLoading(false);
        });
        return;
      }
      setMentionEntries([]);
      const query = scope.filter.trim();
      if (!query) {
        setMentionHits([]);
        setMentionLoading(false);
        return;
      }
      setMentionLoading(true);
      void api<CodeHit[]>(`/api/projects/${project.id}/code/search?q=${encodeURIComponent(query)}`)
        .then((items) => {
          if (!cancelled) setMentionHits(items);
        })
        .catch(() => {
          if (!cancelled) setMentionHits([]);
        })
        .finally(() => {
          if (!cancelled) setMentionLoading(false);
        });
    }, 180);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [mentionOpen, mentionQuery, project?.id, workspaces]);

  const readyProviders = Object.entries(model?.providers ?? {}).filter(([, item]) => item.configured);
  const slashCommands = useMemo(
    () => buildSlashCommands({
      modes,
      mode,
      routes,
      skills,
      rules,
      bindings,
      mcpFocus,
      ruleFocus,
      focus,
      groups: slashGroups,
    }),
    [modes, mode, routes, skills, rules, bindings, mcpFocus, ruleFocus, focus, slashGroups],
  );

  const openComposerSlash = () => {
    setSlashGroups(COMPOSER_SLASH_GROUPS);
    setSlashOpen(true);
    setSlashQuery("");
    setMentionOpen(false);
    setModelOpen(false);
  };

  if (!project) {
    return (
      <div className="empty empty-hero">
        <span className="empty-icon"><IconChat size={22} /></span>
        <h3>{projectsReady ? "还没有选中项目" : "正在加载项目…"}</h3>
        <p>
          {projectsReady
            ? <><Link to="/projects">回到项目列表</Link> 后再开始对话。</>
            : "正在读取项目上下文。"}
        </p>
      </div>
    );
  }

  const showConversation = async (id: string) => {
    setModelOpen(false);
    setSlashOpen(false);
    setError("");
    const item = conversations.find((conversation) => conversation.id === id);
    if (item?.environment) setEnvironment(parseEnvironment(item.environment));
    try {
      await openConversation(id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "读取对话失败");
    }
  };

  const showRun = async (run: InvestigationRun) => {
    setModelOpen(false);
    setSlashOpen(false);
    if (run.environment) setEnvironment(parseEnvironment(run.environment));
    const userMessage: ConversationMessage = {
      id: `${run.id}-user`,
      conversationId: run.id,
      role: "user",
      content: run.question,
      runId: run.id,
      createdAt: run.createdAt,
    };
    const assistantMessage: ConversationMessage | null = run.report
      ? {
          id: `${run.id}-assistant`,
          conversationId: run.id,
          role: "assistant",
          content: run.report,
          runId: run.id,
          createdAt: run.updatedAt,
        }
      : null;
    patchLiveSession({
      conversationId: null,
      transcript: assistantMessage ? [userMessage, assistantMessage] : [userMessage],
      userTurns: 1,
      selected: run,
      thoughts: [],
      budgetPrompt: null,
      mode: run.mode || mode,
      report: run.report || (run.status === "RUNNING" ? "这轮排查已中断。" : "这次对话还没有结果。"),
      running: false,
      recovering: false,
    });
    if (run.mode) setMode(run.mode);
    if (run.report) return;
    try {
      const detail = await api<InvestigationRun>(`/api/investigations/${run.id}`);
      patchLiveSession({
        selected: detail,
        report: detail.report || (detail.status === "RUNNING" ? "这轮排查已中断。" : "这次对话还没有结果。"),
        transcript: detail.report
          ? [userMessage, {
              id: `${detail.id}-assistant`,
              conversationId: detail.id,
              role: "assistant",
              content: detail.report,
              runId: detail.id,
              createdAt: detail.updatedAt,
            }]
          : [userMessage],
        running: false,
        recovering: false,
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "读取记录失败");
    }
  };

  const syncComposerTriggers = (text: string, caret: number) => {
    const mention = mentionAt(text, caret);
    const slash = slashTokenAt(text, caret);
    if (mention !== null) {
      setMentionOpen(true);
      setMentionQuery(mention);
      setSlashOpen(false);
      setModelOpen(false);
      return;
    }
    if (slash !== null) {
      setSlashGroups(COMPOSER_SLASH_GROUPS);
      setSlashOpen(true);
      setSlashQuery(slash);
      setMentionOpen(false);
      setModelOpen(false);
      return;
    }
    setMentionOpen(false);
    setSlashOpen(false);
  };

  // 弹层筛选框改 query 时，同步改写输入框里当前的 @token，避免只改本地 state 丢路径
  const patchMentionQuery = (next: string) => {
    const prev = mentionQueryRef.current;
    setMentionQuery(next);
    setQuestion((current) => {
      const node = textareaRef.current;
      if (node && document.activeElement === node) {
        const caret = node.selectionStart ?? current.length;
        const head = current.slice(0, caret);
        if (/@([^\s@]*)$/.test(head)) {
          return `${head.replace(/@([^\s@]*)$/, `@${next}`)}${current.slice(caret)}`;
        }
      }
      const token = `@${prev}`;
      const at = current.lastIndexOf(token);
      if (at >= 0) {
        return `${current.slice(0, at + 1)}${next}${current.slice(at + token.length)}`;
      }
      return current.replace(/@([^\s@]*)$/, `@${next}`);
    });
  };

  const applySlash = (item: SlashCommand) => {
    if (item.disabled) return;
    const caret = textareaRef.current?.selectionStart ?? question.length;
    const consumed = consumeSlashToken(question, caret);
    let nextText = consumed.text;
    let nextCaret = consumed.caret;
    if (item.id.startsWith("mode:")) {
      setMode(item.id.slice(5) as ConversationModeId);
    } else if (item.id === "tool:auto") {
      setMode("INVESTIGATE");
      setFocus({ kind: "auto" });
    } else if (item.id.startsWith("tool:")) {
      const routeId = item.id.slice(5);
      const route = routes.find((entry) => entry.id === routeId);
      setMode("INVESTIGATE");
      setFocus({ kind: "tool", routeId, label: route?.label || item.label });
    } else if (item.id.startsWith("skill:")) {
      const slug = item.id.slice(6);
      const skill = skills.find((entry) => entry.slug === slug);
      setMode("INVESTIGATE");
      setFocus({
        kind: "skill",
        slug,
        name: skill?.name || item.label,
      });
    } else if (item.id.startsWith("rule:")) {
      const slug = item.id.slice(5);
      const rule = rules.find((entry) => entry.slug === slug);
      setRuleFocus({ slug, name: rule?.name || item.label });
    } else if (item.id.startsWith("mcp:")) {
      const bindingId = item.id.slice(4);
      const binding = bindings.find((entry) => entry.id === bindingId);
      const profile = binding?.profile || item.label;
      setMcpFocus({
        id: binding?.id || bindingId,
        profile,
        toolKind: binding?.toolKind || "",
      });
      const inserted = applyNamedMcpMention(consumed.text, consumed.caret, profile);
      nextText = inserted.text;
      nextCaret = inserted.caret;
    }
    setQuestion(nextText);
    setSlashOpen(false);
    setSlashQuery("");
    requestAnimationFrame(() => {
      const node = textareaRef.current;
      if (!node) return;
      node.focus();
      node.setSelectionRange(nextCaret, nextCaret);
    });
  };

  const applyMention = (token: string, keepOpen = false) => {
    const caret = textareaRef.current?.selectionStart ?? question.length;
    const before = question.slice(0, caret).replace(/@([^\s@]*)$/, "");
    const suffix = keepOpen ? "" : " ";
    const next = `${before}@${token}${suffix}${question.slice(caret)}`;
    setQuestion(next);
    setMentionOpen(keepOpen);
    setMentionQuery(keepOpen ? token : "");
    if (!keepOpen) {
      setMentionHits([]);
      setMentionEntries([]);
    }
    requestAnimationFrame(() => {
      const node = textareaRef.current;
      if (!node) return;
      const pos = before.length + 1 + token.length + suffix.length;
      node.focus();
      node.setSelectionRange(pos, pos);
    });
  };

  const onMentionChoice = (choice: MentionChoice) => {
    if (choice.action === "repos") {
      applyMention("", true);
      return;
    }
    if (choice.action === "up") {
      applyMention(choice.dir ? `${choice.workspace}/${choice.dir}/` : `${choice.workspace}/`, true);
      return;
    }
    if (choice.action === "workspace") {
      applyMention(`${choice.name}/`, true);
      return;
    }
    if (choice.action === "repo") {
      applyMention(choice.name);
      return;
    }
    if (choice.action === "dir") {
      applyMention(`${choice.workspace}/${choice.path}/`, true);
      return;
    }
    applyMention(`${choice.workspace}/${choice.path}`);
  };

  const switchModel = async (provider: string, modelName?: string) => {
    setModelOpen(false);
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

  const runConversation = async (input: {
    text: string;
    attachments: ConversationAttachmentPayload[];
    continueFromRunId?: string;
  }) => {
    const pinned = mcpFocusFromText(input.text, bindings) || mcpFocus;
    if (pinned && pinned.profile !== mcpFocus?.profile) setMcpFocus(pinned);
    if (!input.continueFromRunId) {
      setQuestion("");
      clear();
    }
    setModelOpen(false);
    setSlashOpen(false);
    setError("");
    await startLiveInvestigation({
      projectId: project.id,
      environment,
      text: input.text,
      mode,
      provider: model?.provider,
      model: model?.model,
      route: routeForFocus(mode, focus),
      skillSlug: reviewSkillSlug(focus),
      ruleSlug: reviewRuleSlug(ruleFocus),
      attachments: input.attachments,
      continueFromRunId: input.continueFromRunId,
      mcpProfile: pinned?.profile || mcpFocus?.profile,
    });
    await Promise.all([
      loadConversations().catch((cause: Error) => setError(cause.message)),
      loadRuns().catch((cause: Error) => setError(cause.message)),
    ]);
  };

  const atLimit = userTurns >= maxTurns;

  const onStart = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const text = ensureNamedMcpMention(question.trim(), mcpFocus?.profile);
    if ((!text && files.length === 0) || atLimit || running) return;
    await runConversation({
      text: text || "请看附件截图",
      attachments: payloads(),
    });
  };

  const continueInvestigation = async (prompt: BudgetPrompt) => {
    await runConversation({
      text: ensureNamedMcpMention(prompt.question.trim(), mcpFocus?.profile),
      attachments: prompt.attachments,
      continueFromRunId: prompt.runId,
    });
  };

  return (
    <div className="chat-page">
      <section className="chat-main">
        <div className="page-header">
          <div>
            <p className="kicker">{project.slug} / {environment}</p>
            <h2>对话</h2>
            <p>同一会话最多 {maxTurns} 轮，后续提问会带上本会话上下文。提问和排查都可以阅读「代码仓」里配置的本地仓库。</p>
          </div>
          <div className="chat-actions">
            <span className={`badge${atLimit ? " warn" : ""}`}>{userTurns}/{maxTurns} 轮</span>
            <button className="btn secondary" type="button" disabled={running} onClick={resetThread}>
              <IconPlus size={14} />
              新对话
            </button>
          </div>
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
                    <small>{modeLabel(mode)} · {formatTime(message.createdAt)}</small>
                    {message.content.trim() ? <div>{message.content}</div> : null}
                    <UserMessageMedia attachments={message.attachments} />
                  </div>
                ) : (
                  <div className="bubble assistant" key={message.id}>
                    <small>助手 · {formatTime(message.createdAt)}</small>
                    <div className="report report-md"><MarkdownView source={message.content} /></div>
                    {lastAssistant && structuredReport ? (
                      <EvidencePanel report={structuredReport} />
                    ) : null}
                    {canContinue ? (
                      <button
                        className="btn secondary"
                        type="button"
                        style={{ marginTop: 12 }}
                        onClick={() => void continueInvestigation({
                          runId: message.runId as string,
                          question: [...transcript].reverse().find((item) => item.role === "user")?.content || "",
                          attachments: [...transcript].reverse().find((item) => item.role === "user")?.attachments || [],
                          used: 0,
                          max: 0,
                        })}
                      >
                        继续排查
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
              <h3>从下方开始一次对话</h3>
              <p>输入 / 筛选已启用 Skill 或当前环境 MCP，输入 @ 引用代码仓或具体文件，也可以直接粘贴截图。同一会话最多 {maxTurns} 轮。</p>
              <div className="empty-hints">
                <span className="badge">/ Skill · MCP</span>
                <span className="badge warn">模式选择</span>
                <span className="badge">@类名 / @路径</span>
                <span className="badge">粘贴截图</span>
              </div>
            </div>
          )}
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
              groups={slashGroups}
              onSelect={applySlash}
            />
            <CodeMentionMenu
              open={mentionOpen}
              query={mentionQuery}
              workspaces={workspaces}
              entries={mentionEntries}
              hits={mentionHits}
              loading={mentionLoading}
              onSelect={onMentionChoice}
              onQueryChange={patchMentionQuery}
            />
          <label className="composer-field" htmlFor="chat-question">
            <span className="sr-only">问题</span>
            <textarea
              id="chat-question"
              ref={textareaRef}
              value={question}
              rows={5}
              onChange={(event) => {
                const next = event.target.value;
                setQuestion(next);
                syncComposerTriggers(next, event.target.selectionStart);
              }}
              onCompositionEnd={(event) => {
                const node = event.currentTarget;
                syncComposerTriggers(node.value, node.selectionStart);
              }}
              placeholder={mode === "ASK" ? "问接口、配置或架构。可粘贴截图，输入 / 筛 Skill 或 MCP，@ 引用代码。" : "描述故障现象。可粘贴截图，输入 / 筛 Skill 或 MCP，@ 指定代码入口。"}
              onKeyDown={(event) => {
                if (event.key === "Escape") {
                  setMentionOpen(false);
                  setSlashOpen(false);
                }
                if (slashOpen && (event.key === "ArrowDown" || event.key === "ArrowUp" || (event.key === "Enter" && !event.shiftKey))) {
                  event.preventDefault();
                  return;
                }
                if (mentionOpen && (event.key === "ArrowDown" || event.key === "ArrowUp" || (event.key === "Enter" && !event.shiftKey))) {
                  event.preventDefault();
                  return;
                }
                if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) {
                  event.preventDefault();
                  if (!mentionOpen) {
                    event.currentTarget.form?.requestSubmit();
                  }
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
              className="icon-btn"
              type="button"
              aria-label="引用代码"
              title="@ 引用文件或代码仓"
              onClick={() => {
                const node = textareaRef.current;
                const start = node?.selectionStart ?? question.length;
                const next = `${question.slice(0, start)}@${question.slice(start)}`;
                setQuestion(next);
                setMentionOpen(true);
                setMentionQuery("");
                setSlashOpen(false);
                requestAnimationFrame(() => {
                  node?.focus();
                  node?.setSelectionRange(start + 1, start + 1);
                });
              }}
            >
              @
            </button>
            <button
              className={`icon-btn${slashOpen && isComposerSlashPicker(slashGroups) ? " active" : ""}`}
              type="button"
              aria-label="筛选 Skill 或 MCP"
              title="输入 / 筛选 Skill 或 MCP"
              aria-expanded={slashOpen && isComposerSlashPicker(slashGroups)}
              onClick={() => {
                if (slashOpen && isComposerSlashPicker(slashGroups)) {
                  setSlashOpen(false);
                  return;
                }
                const node = textareaRef.current;
                const start = node?.selectionStart ?? question.length;
                let nextText = question;
                let caret = start;
                if (slashTokenAt(question, start) == null) {
                  nextText = `${question.slice(0, start)}/${question.slice(start)}`;
                  caret = start + 1;
                  setQuestion(nextText);
                  requestAnimationFrame(() => {
                    node?.focus();
                    node?.setSelectionRange(caret, caret);
                  });
                } else {
                  node?.focus();
                }
                setSlashGroups(COMPOSER_SLASH_GROUPS);
                setSlashOpen(true);
                setSlashQuery(slashTokenAt(nextText, caret) ?? "");
                setMentionOpen(false);
                setModelOpen(false);
              }}
            >
              <IconSlash size={16} />
            </button>

            <button
              className={`chip-btn${slashOpen && slashGroups.includes("mode") ? " active" : ""}`}
              type="button"
              aria-label="模式选择"
              aria-expanded={slashOpen && slashGroups.includes("mode")}
              onClick={() => {
                if (slashOpen && slashGroups.includes("mode")) {
                  setSlashOpen(false);
                  return;
                }
                setSlashGroups(["mode"]);
                setSlashOpen(true);
                setSlashQuery("");
                setMentionOpen(false);
                setModelOpen(false);
              }}
            >
              <IconSparkle size={14} />
              模式选择
            </button>
            {mode === "INVESTIGATE" && focus.kind !== "auto" ? (
              <span className="chip-btn chip-btn-focus">
                <button type="button" onClick={openComposerSlash}>
                  {focus.kind === "skill" ? `Skill · ${focus.name}` : focus.label}
                </button>
                <button type="button" className="chip-clear" aria-label="改回自动判断" onClick={() => setFocus({ kind: "auto" })}>
                  <IconX size={11} />
                </button>
              </span>
            ) : null}
            {mcpFocus ? (
              <span className="chip-btn chip-btn-focus">
                <button type="button" onClick={openComposerSlash}>
                  MCP · {mcpFocus.profile}
                </button>
                <button type="button" className="chip-clear" aria-label="取消指定 MCP" onClick={() => setMcpFocus(null)}>
                  <IconX size={11} />
                </button>
              </span>
            ) : null}
            {ruleFocus ? (
              <span className="chip-btn chip-btn-focus">
                <button type="button" onClick={openComposerSlash}>
                  Rule · {ruleFocus.name}
                </button>
                <button type="button" className="chip-clear" aria-label="取消点名 Rule" onClick={() => setRuleFocus(null)}>
                  <IconX size={11} />
                </button>
              </span>
            ) : null}

            <div className="popover-wrap">
              <button className="chip-btn" type="button" onClick={() => { setModelOpen((open) => !open); setSlashOpen(false); }}>
                <IconCpu size={14} />
                {model?.available ? `${model.providerLabel} · ${model.model}` : "选择模型"}
              </button>
              {modelOpen ? (
                <div className="popover model-popover">
                  {readyProviders.length === 0 ? (
                    <p className="notice">还没有就绪的模型。先到模型页测试连接，再从下拉框选择。</p>
                  ) : readyProviders.map(([id, item]) => {
                    const names = item.models?.length ? item.models : (item.model ? [item.model] : []);
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
                  <Link className="popover-item" to="/settings/model" onClick={() => setModelOpen(false)}>添加 / 配置模型</Link>
                </div>
              ) : null}
            </div>

            {running ? (
              <button type="button" className="btn danger secondary" onClick={() => stopLiveInvestigation()}>
                <IconX size={15} />
                停止
              </button>
            ) : (
              <button className="btn" disabled={atLimit || (!question.trim() && files.length === 0)}>
                <IconSend size={15} />
                发送
              </button>
            )}
          </div>
          <p className="notice">
            {atLimit
              ? `本对话已达 ${maxTurns} 轮上限，请点右上角「新对话」再继续。`
              : `同一会话 ${userTurns}/${maxTurns} 轮。Enter 发送，Shift+Enter 换行。可直接粘贴截图。/ 筛 Skill 或 MCP；@ 引用文件。`}
          </p>
          {error || pageError || attachError ? <p className="error">{error || pageError || attachError}</p> : null}
        </form>
        {budgetPrompt ? (
          <div className="modal-scrim" role="presentation" onClick={() => patchLiveSession({ budgetPrompt: null })}>
            <div
              className="modal"
              role="dialog"
              aria-modal="true"
              aria-labelledby="budget-title"
              onClick={(event) => event.stopPropagation()}
            >
              <h3 id="budget-title">只读工具已达上限</h3>
              <p>
                本轮已使用 {budgetPrompt.used}/{budgetPrompt.max || "?"} 次工具，当前结论已生成。
                点「继续排查」会带上上一轮结论再跑一轮，并尽量避免重复已经查过的内容。
              </p>
              <div className="modal-actions">
                <button className="btn secondary" type="button" onClick={() => patchLiveSession({ budgetPrompt: null })}>取消</button>
                <button
                  className="btn"
                  type="button"
                  disabled={running}
                  onClick={() => void continueInvestigation(budgetPrompt)}
                >
                  继续排查
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </section>

      <aside className="card chat-history">
        <div className="card-head">
          <h3>历史</h3>
          <span className="badge">{conversations.length || runs.length}</span>
        </div>
        {conversations.length > 0 ? (
          <div className="list">
            {conversations.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`item buttonish${conversationId === item.id ? " active" : ""}`}
                onClick={() => void showConversation(item.id)}
              >
                <b>{excerpt(item.title || "未命名对话")}</b>
                <span>
                  <span className="badge">{item.userTurns}/{item.maxTurns} 轮</span>
                  {running && conversationId === item.id ? <span className="badge accent">进行中</span> : null}
                  {"  "}{modeLabel(item.mode)} · {formatTime(item.updatedAt)}
                </span>
              </button>
            ))}
          </div>
        ) : runs.length > 0 ? (
          <div className="list">
            {runs.map((run) => (
              <button
                key={run.id}
                type="button"
                className={`item buttonish${selected?.id === run.id ? " active" : ""}`}
                onClick={() => void showRun(run)}
              >
                <b>{excerpt(run.question)}</b>
                <span>
                  <span className={`badge ${statusBadge(run.status)}`}>{modeLabel(run.mode)}</span>
                  {"  "}{run.environment} · {formatTime(run.createdAt)}
                </span>
              </button>
            ))}
          </div>
        ) : (
          <div className="empty">还没有对话记录。</div>
        )}
      </aside>
    </div>
  );
}
