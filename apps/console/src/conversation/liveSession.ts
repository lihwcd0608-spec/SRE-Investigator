import { api, consumeSse } from "../api";
import type {
  ConversationAttachmentPayload,
  ConversationDetail,
  ConversationMessage,
  ConversationModeId,
  InvestigationRun,
} from "../types";

export interface LiveThought {
  kind: string;
  message: string;
}

export interface BudgetPrompt {
  runId: string;
  question: string;
  attachments: ConversationAttachmentPayload[];
  used: number;
  max: number;
}

export type LiveSurface = "conversation" | "review";

export interface StructuredReport {
  phenomenon: string;
  confirmedEvidence: string[];
  ruledOut: string[];
  hypotheses: string[];
  nextSteps: string[];
}

export interface LiveSessionState {
  surface: LiveSurface;
  projectId: string | null;
  conversationId: string | null;
  transcript: ConversationMessage[];
  thoughts: LiveThought[];
  report: string;
  structuredReport: StructuredReport | null;
  running: boolean;
  recovering: boolean;
  waitStartedAt: number | null;
  error: string;
  userTurns: number;
  maxTurns: number;
  mode: ConversationModeId;
  selected: InvestigationRun | null;
  budgetPrompt: BudgetPrompt | null;
}

interface StreamOutcome {
  run?: InvestigationRun;
  result?: {
    report?: string;
    state?: {
      budgetExhausted?: boolean;
      usedToolCalls?: number;
      maxToolCalls?: number;
      reportStructured?: StructuredReport;
    };
  };
  conversationId?: string;
  turnIndex?: number;
  maxTurns?: number;
  messages?: ConversationMessage[];
}

interface StartInvestigationInput {
  projectId: string;
  environment: string;
  text: string;
  mode: ConversationModeId;
  provider?: string;
  model?: string;
  route?: string;
  attachments: ConversationAttachmentPayload[];
  continueFromRunId?: string;
  repositoryId?: string;
  baseRef?: string;
  headRef?: string;
  skillSlug?: string;
  mcpProfile?: string;
  agentSlug?: string;
  ruleSlug?: string;
}

const STORAGE_PREFIX = "sre-investigator.conversation.";
const REVIEW_STORAGE_PREFIX = "sre-investigator.review.";
const RECOVER_STALE_MS = 90_000;
const RECOVER_TIMEOUT_MS = 10_000;
const RECOVER_POLL_MS = 1_000;
const listeners = new Set<() => void>();
let inFlight = false;
let streamEpoch = 0;
let activeAbort: AbortController | null = null;
let stoppedByUser = false;
let recoverTimer: number | null = null;
let recoverDeadlineTimer: number | null = null;
let recoverStartedAt: number | null = null;
let recoverGeneration = 0;
const sessionLimits = { maxTurns: 10, maxToolCalls: 16 };
let state: LiveSessionState = emptyState();

export function configureSessionLimits(input: { maxTurns?: number; maxToolCalls?: number }) {
  if (input.maxTurns && input.maxTurns > 0) sessionLimits.maxTurns = input.maxTurns;
  if (input.maxToolCalls && input.maxToolCalls > 0) sessionLimits.maxToolCalls = input.maxToolCalls;
  if (!state.conversationId) {
    setState({ maxTurns: sessionLimits.maxTurns });
  }
}

export function sessionLimitDefaults() {
  return { ...sessionLimits };
}

function emptyState(surface: LiveSurface = "conversation"): LiveSessionState {
  return {
    surface,
    projectId: null,
    conversationId: null,
    transcript: [],
    thoughts: [],
    report: surface === "review" ? "选择仓库和两个分支后开始审查。" : "选一种模式，输入问题后发送。",
    structuredReport: null,
    running: false,
    recovering: false,
    waitStartedAt: null,
    error: "",
    userTurns: 0,
    maxTurns: sessionLimits.maxTurns,
    mode: surface === "review" ? "REVIEW" : "INVESTIGATE",
    selected: null,
    budgetPrompt: null,
  };
}

function storageKey(projectId: string, surface: LiveSurface) {
  return (surface === "review" ? REVIEW_STORAGE_PREFIX : STORAGE_PREFIX) + projectId;
}

function emit() {
  for (const listener of listeners) listener();
}

function persist(next: LiveSessionState) {
  if (!next.projectId || !next.conversationId) return;
  const key = storageKey(next.projectId, next.surface);
  sessionStorage.setItem(key, next.conversationId);
  if (next.surface === "review") {
    localStorage.setItem(key, next.conversationId);
  }
}

function setState(partial: Partial<LiveSessionState> | ((current: LiveSessionState) => Partial<LiveSessionState>)) {
  const patch = typeof partial === "function" ? partial(state) : partial;
  state = { ...state, ...patch };
  persist(state);
  emit();
}

function appendThought(current: LiveThought[], kind: string, message: string) {
  const text = message.trim();
  if (!text || current[current.length - 1]?.message === text) return current;
  const next = [...current, { kind: kind || "think", message: text }];
  return next.length > 60 ? next.slice(next.length - 60) : next;
}

function lastRole(messages: ConversationMessage[], role: ConversationMessage["role"]) {
  return [...messages].reverse().find((item) => item.role === role);
}

function keepLocalAttachments(server: ConversationMessage[], local: ConversationMessage[]) {
  const leftover = local.filter((item) => item.role === "user" && item.attachments?.length);
  if (leftover.length === 0) return server;
  return server.map((message) => {
    if (message.role !== "user" || message.attachments?.length) return message;
    const match = leftover.find((item) => item.content === message.content);
    return match ? { ...message, attachments: match.attachments } : message;
  });
}

function awaitingAssistant(messages: ConversationMessage[]) {
  const last = messages[messages.length - 1];
  return last?.role === "user";
}

function stopRecover() {
  if (recoverTimer == null) return;
  window.clearTimeout(recoverTimer);
  recoverTimer = null;
}

function beginRecoverClock(restart = false) {
  if (!restart && recoverStartedAt != null) return;
  recoverStartedAt = Date.now();
  if (recoverDeadlineTimer != null) window.clearTimeout(recoverDeadlineTimer);
  recoverDeadlineTimer = window.setTimeout(() => {
    recoverDeadlineTimer = null;
    if (inFlight || (!state.running && !state.recovering)) return;
    abandonRecover("服务已断开超过 10 秒，已停止同步。");
  }, RECOVER_TIMEOUT_MS);
}

function clearRecoverClock() {
  recoverStartedAt = null;
  if (recoverDeadlineTimer != null) {
    window.clearTimeout(recoverDeadlineTimer);
    recoverDeadlineTimer = null;
  }
}

function recoverTimedOut() {
  return recoverStartedAt != null && Date.now() - recoverStartedAt >= RECOVER_TIMEOUT_MS;
}

function isStaleRun(run: InvestigationRun) {
  if (run.status !== "RUNNING") return false;
  const updated = Date.parse(run.updatedAt || run.createdAt);
  return Number.isFinite(updated) && Date.now() - updated > RECOVER_STALE_MS;
}

function settleIdle(extra: Partial<LiveSessionState> = {}) {
  recoverGeneration += 1;
  stopRecover();
  clearRecoverClock();
  setState({
    running: inFlight,
    recovering: false,
    waitStartedAt: inFlight ? state.waitStartedAt : null,
    ...extra,
  });
}

function abandonRecover(message: string) {
  recoverGeneration += 1;
  stopRecover();
  clearRecoverClock();
  const syncing = /同步|恢复|连接/.test(state.report);
  setState({
    running: false,
    recovering: false,
    waitStartedAt: null,
    error: message,
    report: syncing || state.report.startsWith("正在") ? "这轮排查已中断。" : state.report,
    thoughts: appendThought(
      state.thoughts.filter((item) => !item.message.includes("同步已保存") && !item.message.includes("从已保存记录恢复")),
      "phase",
      "排查已中断，服务重启后无法继续这轮任务",
    ),
  });
}

function applyFinishedRun(conversationId: string, detail: ConversationDetail, run: InvestigationRun) {
  const messages = detail.messages ?? [];
  const assistant = run.report
    ? [{
        id: `${run.id}-assistant`,
        conversationId,
        role: "assistant" as const,
        content: run.report,
        runId: run.id,
        createdAt: run.updatedAt,
      }]
    : [];
  settleIdle({
    selected: run,
    report: run.report || (run.status === "FAILED" ? "排查失败，没有保存到结果。" : "这次对话还没有结果。"),
    transcript: awaitingAssistant(messages) && run.report ? [...messages, ...assistant] : messages,
  });
}

export function getLiveSession() {
  return state;
}

export function subscribeLiveSession(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function rememberedConversationId(projectId: string) {
  return sessionStorage.getItem(STORAGE_PREFIX + projectId);
}

export function rememberedReviewId(projectId: string) {
  const key = REVIEW_STORAGE_PREFIX + projectId;
  return localStorage.getItem(key) || sessionStorage.getItem(key);
}

export function activateLiveSurface(surface: LiveSurface, projectId: string | null) {
  if (state.surface === surface && (projectId == null || state.projectId === projectId || state.projectId === null)) {
    if (state.projectId == null && projectId) setState({ projectId });
    return;
  }
  streamEpoch += 1;
  inFlight = false;
  recoverGeneration += 1;
  stopRecover();
  clearRecoverClock();
  state = { ...emptyState(surface), projectId };
  emit();
}

export function resetLiveSession(projectId?: string) {
  streamEpoch += 1;
  inFlight = false;
  recoverGeneration += 1;
  stopRecover();
  clearRecoverClock();
  if (projectId) {
    const key = storageKey(projectId, state.surface);
    sessionStorage.removeItem(key);
    if (state.surface === "review") localStorage.removeItem(key);
  }
  state = { ...emptyState(state.surface), projectId: projectId ?? null };
  emit();
}

export function rememberConversation(projectId: string, conversationId: string) {
  sessionStorage.setItem(STORAGE_PREFIX + projectId, conversationId);
  setState({ projectId, conversationId });
}

export function patchLiveSession(partial: Partial<LiveSessionState>) {
  setState(partial);
}

function isAbortError(cause: unknown) {
  return (cause instanceof DOMException || cause instanceof Error) && cause.name === "AbortError";
}

export function stopLiveInvestigation() {
  if (!inFlight && !state.running && !state.recovering) return;
  stoppedByUser = true;
  streamEpoch += 1;
  recoverGeneration += 1;
  stopRecover();
  clearRecoverClock();
  activeAbort?.abort();
  activeAbort = null;
  inFlight = false;
  const stoppedReport = state.report.startsWith("正在") ? "排查已停止。" : state.report;
  setState({
    running: false,
    recovering: false,
    waitStartedAt: null,
    report: stoppedReport,
    thoughts: appendThought(state.thoughts, "phase", "已手动停止"),
  });
}

export async function openConversation(id: string) {
  if (state.conversationId && state.conversationId !== id) {
    clearRecoverClock();
  }
  let detail: ConversationDetail;
  try {
    detail = await api<ConversationDetail>(`/api/conversations/${id}`);
  } catch (cause) {
    if (inFlight || state.running || state.recovering) {
      beginRecoverClock();
      if (recoverTimedOut()) {
        abandonRecover("服务已断开超过 10 秒，已停止同步。");
        return;
      }
      setState({
        conversationId: id,
        running: true,
        recovering: true,
        waitStartedAt: state.waitStartedAt ?? Date.now(),
        report: "连接已断开，正在同步已保存结果…",
        thoughts: appendThought(state.thoughts, "phase", "正在同步已保存结果"),
      });
      watchUntilSettled(id);
      return;
    }
    throw cause;
  }
  applyDetail(detail, inFlight && state.conversationId === id);
  const messages = detail.messages ?? [];
  const lastAssistant = lastRole(messages, "assistant");
  if (lastAssistant?.runId) {
    try {
      setState({ selected: await api<InvestigationRun>(`/api/investigations/${lastAssistant.runId}`) });
    } catch {
      setState({ selected: null });
    }
  }
  if (!awaitingAssistant(messages)) {
    if (!inFlight) settleIdle();
    return;
  }
  const lastUser = lastRole(messages, "user");
  if (inFlight) {
    watchUntilSettled(detail.id);
    return;
  }
  if (!lastUser?.runId) {
    abandonRecover("这轮没有保存到结果，服务可能已中断。");
    return;
  }
  try {
    const run = await api<InvestigationRun>(`/api/investigations/${lastUser.runId}`);
    if (run.status === "RUNNING" && !isStaleRun(run)) {
      beginRecoverClock();
      if (recoverTimedOut()) {
        abandonRecover("服务已断开超过 10 秒，已停止同步。");
        return;
      }
      setState({
        selected: run,
        running: true,
        recovering: true,
        waitStartedAt: state.waitStartedAt ?? Date.now(),
        report: "连接曾中断，正在同步已保存结果…",
        thoughts: appendThought(state.thoughts, "phase", "正在同步已保存结果"),
      });
      watchUntilSettled(detail.id);
      return;
    }
    if (run.status === "RUNNING") {
      abandonRecover("服务已重启，这轮排查没有继续跑完。");
      return;
    }
    applyFinishedRun(detail.id, detail, run);
  } catch {
    beginRecoverClock();
    if (recoverTimedOut()) {
      abandonRecover("服务已断开超过 10 秒，已停止同步。");
      return;
    }
    setState({
      running: true,
      recovering: true,
      waitStartedAt: state.waitStartedAt ?? Date.now(),
      report: "连接已断开，正在同步已保存结果…",
      thoughts: appendThought(state.thoughts, "phase", "正在同步已保存结果"),
    });
    watchUntilSettled(detail.id);
  }
}

function applyDetail(detail: ConversationDetail, keepLiveProgress: boolean) {
  const lastAssistant = lastRole(detail.messages ?? [], "assistant");
  setState({
    projectId: detail.projectId,
    conversationId: detail.id,
    transcript: detail.messages ?? [],
    userTurns: detail.userTurns ?? 0,
    maxTurns: detail.maxTurns ?? sessionLimits.maxTurns,
    mode: detail.mode || state.mode,
    budgetPrompt: keepLiveProgress ? state.budgetPrompt : null,
    report: lastAssistant?.content || (keepLiveProgress ? state.report : "这次对话还没有结果。"),
    thoughts: keepLiveProgress ? state.thoughts : [],
    error: keepLiveProgress ? state.error : "",
  });
}

async function watchUntilSettled(conversationId: string, restartClock = false) {
  stopRecover();
  beginRecoverClock(restartClock);
  const generation = recoverGeneration;
  const tick = async () => {
    if (generation !== recoverGeneration || state.conversationId !== conversationId) return;
    if (!inFlight && recoverTimedOut()) {
      abandonRecover("服务已断开超过 10 秒，已停止同步。");
      return;
    }
    try {
      const detail = await api<ConversationDetail>(`/api/conversations/${conversationId}`);
      if (generation !== recoverGeneration) return;
      if (!awaitingAssistant(detail.messages ?? [])) {
        applyDetail(detail, inFlight);
        settleIdle();
        return;
      }
      const lastUser = lastRole(detail.messages ?? [], "user");
      if (lastUser?.runId) {
        const run = await api<InvestigationRun>(`/api/investigations/${lastUser.runId}`);
        if (generation !== recoverGeneration) return;
        if (run.status !== "RUNNING") {
          applyFinishedRun(conversationId, detail, run);
          return;
        }
        if (!inFlight && isStaleRun(run)) {
          abandonRecover("服务已重启，这轮排查没有继续跑完。");
          return;
        }
      } else if (!inFlight) {
        abandonRecover("这轮没有保存到结果，服务可能已中断。");
        return;
      }
    } catch {
      if (generation !== recoverGeneration) return;
      if (!inFlight && recoverTimedOut()) {
        abandonRecover("服务已断开超过 10 秒，已停止同步。");
        return;
      }
    }
    if (generation !== recoverGeneration) return;
    recoverTimer = window.setTimeout(() => void tick(), RECOVER_POLL_MS);
  };
  void tick();
}

export async function startLiveInvestigation(input: StartInvestigationInput) {
  const epoch = ++streamEpoch;
  const localUserId = `local-user-${Date.now()}`;
  recoverGeneration += 1;
  stopRecover();
  clearRecoverClock();
  stoppedByUser = false;
  activeAbort?.abort();
  activeAbort = new AbortController();
  const signal = activeAbort.signal;
  inFlight = true;
  setState((current) => ({
    projectId: input.projectId,
    error: "",
    running: true,
    recovering: false,
    waitStartedAt: Date.now(),
    budgetPrompt: null,
    mode: input.mode,
    transcript: input.continueFromRunId
      ? current.transcript
      : [
          ...current.transcript,
          {
            id: localUserId,
            conversationId: current.conversationId || "local",
            role: "user",
            content: input.text,
            createdAt: new Date().toISOString(),
            attachments: input.attachments,
          },
        ],
    thoughts: [{
      kind: "phase",
      message: input.continueFromRunId ? "已确认继续排查" : (current.conversationId ? "正在继续本轮对话" : "已发送，正在处理"),
    }],
    report: input.mode === "ASK" ? "正在回答…" : input.mode === "REVIEW" ? "正在按规则审查分支差异…" : "正在执行只读排查…",
    structuredReport: null,
  }));
  try {
    const response = await fetch("/api/investigations/stream", {
      method: "POST",
      credentials: "include",
      signal,
      headers: { "content-type": "application/json", accept: "text/event-stream" },
      body: JSON.stringify({
        projectId: input.projectId,
        environment: input.environment,
        question: input.text,
        mode: input.mode,
        provider: input.provider,
        model: input.model,
        route: input.route || undefined,
        attachments: input.attachments,
        conversationId: state.conversationId,
        continueFromRunId: input.continueFromRunId,
        repositoryId: input.repositoryId,
        baseRef: input.baseRef,
        headRef: input.headRef,
        skillSlug: input.skillSlug || undefined,
        mcpProfile: input.mcpProfile || undefined,
        agentSlug: input.agentSlug || undefined,
        ruleSlug: input.ruleSlug || undefined,
      }),
    });
    if (!response.ok) throw new Error(await response.text());
    let receivedResult = false;
    let exhausted: BudgetPrompt | null = null;
    await consumeSse(response, (frame) => {
      if (epoch !== streamEpoch) return;
      if (frame.event === "heartbeat") return;
      if (frame.event === "started") {
        const payload = frame.data as { conversationId?: string };
        if (payload.conversationId) {
          setState((current) => ({
            conversationId: payload.conversationId as string,
            transcript: current.transcript.map((item) => item.id === localUserId
              ? { ...item, conversationId: payload.conversationId as string }
              : item),
          }));
        }
        return;
      }
      if (frame.event === "thought") {
        const thought = frame.data as { kind?: string; message?: string };
        const message = thought.message?.trim();
        if (message) setState((current) => ({ thoughts: appendThought(current.thoughts, thought.kind || "think", message) }));
        return;
      }
      if (frame.event === "phase") {
        const phase = frame.data as { name?: string; message?: string };
        if (phase.name === "wait" || phase.message?.includes("请稍候")) return;
        const label = phase.message || phase.name || "";
        if (label) setState((current) => ({ thoughts: appendThought(current.thoughts, "phase", label) }));
        return;
      }
      if (frame.event === "evidence") {
        const structured = frame.data as StructuredReport;
        if (structured) setState({ structuredReport: structured });
        return;
      }
      if (frame.event === "error") {
        const payload = frame.data as { message?: string };
        setState({ error: payload.message || "对话执行失败" });
        return;
      }
      if (frame.event === "result") {
        receivedResult = true;
        const payload = frame.data as StreamOutcome;
        const nextReport = payload.result?.report || payload.run?.report || JSON.stringify(frame.data, null, 2);
        const hitLimit = Boolean(payload.result?.state?.budgetExhausted) || payload.run?.status === "NEEDS_INPUT";
        let prompt: BudgetPrompt | null = null;
        if (hitLimit && payload.run && (payload.result?.state?.budgetExhausted || nextReport.includes("只读工具已达上限"))) {
          const used = Number(payload.result?.state?.usedToolCalls || 0);
          const max = Number(payload.result?.state?.maxToolCalls || sessionLimits.maxToolCalls || used);
          prompt = {
            runId: payload.run.id,
            question: input.text,
            attachments: input.attachments,
            used,
            max: max || used,
          };
        }
        exhausted = prompt;
        setState((current) => ({
          report: nextReport,
          structuredReport: payload.result?.state?.reportStructured ?? current.structuredReport,
          selected: payload.run ?? current.selected,
          conversationId: payload.conversationId || current.conversationId,
          userTurns: payload.turnIndex ?? current.userTurns,
          maxTurns: payload.maxTurns || sessionLimits.maxTurns,
          transcript: payload.messages?.length
            ? keepLocalAttachments(payload.messages, current.transcript)
            : current.transcript,
          thoughts: prompt
            ? appendThought(current.thoughts, "phase", `只读工具已达上限（${prompt.used}/${prompt.max || "?"}）`)
            : current.thoughts,
          budgetPrompt: prompt,
        }));
      }
    });
    if (epoch !== streamEpoch) return;
    if (!receivedResult) {
      setState({
        recovering: true,
        report: "连接已断开，正在从已保存记录恢复…",
        thoughts: appendThought(state.thoughts, "phase", "连接已断开，正在从已保存记录恢复"),
      });
      if (state.conversationId) watchUntilSettled(state.conversationId, true);
    } else {
      stopRecover();
      setState({ running: false, recovering: false, waitStartedAt: null, budgetPrompt: exhausted });
    }
  } catch (cause) {
    if (epoch !== streamEpoch || stoppedByUser || isAbortError(cause)) return;
    setState({
      error: cause instanceof Error ? cause.message : "发送失败",
      recovering: Boolean(state.conversationId),
      report: state.conversationId ? "连接已断开，正在从已保存记录恢复…" : (state.report.startsWith("正在") ? "对话中断。" : state.report),
    });
    if (state.conversationId) watchUntilSettled(state.conversationId, true);
    else setState({ running: false, waitStartedAt: null });
  } finally {
    if (epoch === streamEpoch) {
      inFlight = false;
      if (activeAbort?.signal === signal) activeAbort = null;
    }
  }
}

if (typeof document !== "undefined") {
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState !== "visible" || !state.conversationId) return;
    if (inFlight || state.recovering) {
      if (!inFlight && recoverTimedOut()) return;
      void openConversation(state.conversationId);
    }
  });
}
