import { FormEvent, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api, consumeSse } from "../api";
import { declaredToolKinds, missingToolKinds, type PlazaAgent } from "../catalog/agentSpec";
import type { LiveThought } from "../conversation/liveSession";
import { IconPlus, IconSend, IconSparkle, IconX } from "../icons";
import { mcpKindLabel } from "../mcp/connection";
import type {
  Binding,
  ConversationDetail,
  ConversationMessage,
  Environment,
  InvestigationRun,
  Repository,
} from "../types";

interface AgentPlaygroundProps {
  projectId: string;
  environment: Environment;
  card: PlazaAgent;
  bindings: Binding[];
  repositories: Repository[];
  onClose: () => void;
}

interface StreamOutcome {
  run?: InvestigationRun;
  result?: { report?: string };
  conversationId?: string;
  turnIndex?: number;
  maxTurns?: number;
  messages?: ConversationMessage[];
}

const MAX_TURNS_DEFAULT = 10;

export function AgentPlayground({
  projectId,
  environment,
  card,
  bindings,
  repositories,
  onClose,
}: AgentPlaygroundProps) {
  const [conversationId, setConversationId] = useState<string | null>(() => readPlayId(projectId, card.slug));
  const [transcript, setTranscript] = useState<ConversationMessage[]>([]);
  const [thoughts, setThoughts] = useState<LiveThought[]>([]);
  const [report, setReport] = useState("点下方示例或直接提问。");
  const [question, setQuestion] = useState("");
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");
  const [userTurns, setUserTurns] = useState(0);
  const [maxTurns, setMaxTurns] = useState(MAX_TURNS_DEFAULT);
  const [waitSeconds, setWaitSeconds] = useState(0);
  const [hydrated, setHydrated] = useState(!readPlayId(projectId, card.slug));
  const abortRef = useRef<AbortController | null>(null);
  const waitStartedRef = useRef<number | null>(null);
  const runningRef = useRef(false);
  const stoppedRef = useRef(false);
  const onCloseRef = useRef(onClose);
  const conversationIdRef = useRef(conversationId);
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const missing = missingToolKinds(card.spec, bindings, repositories.length, environment);
  const noDomainTools = declaredToolKinds(card.spec).length === 0 && card.slug !== "sre-investigate";
  const atLimit = userTurns >= maxTurns;
  onCloseRef.current = onClose;
  conversationIdRef.current = conversationId;

  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !runningRef.current) onCloseRef.current();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = previous;
      window.removeEventListener("keydown", onKey);
    };
  }, []);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    const stored = readPlayId(projectId, card.slug);
    if (!stored) {
      setConversationId(null);
      setHydrated(true);
      return;
    }
    let cancelled = false;
    setConversationId(stored);
    void api<ConversationDetail>(`/api/conversations/${stored}`)
      .then((detail) => {
        if (cancelled || runningRef.current) {
          setHydrated(true);
          return;
        }
        setTranscript(detail.messages ?? []);
        setUserTurns(detail.userTurns ?? 0);
        setMaxTurns(detail.maxTurns || MAX_TURNS_DEFAULT);
        setHydrated(true);
      })
      .catch(() => {
        if (cancelled || runningRef.current) {
          setHydrated(true);
          return;
        }
        writePlayId(projectId, card.slug, null);
        setConversationId(null);
        setHydrated(true);
      });
    return () => {
      cancelled = true;
    };
  }, [card.slug, projectId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [transcript, thoughts, running]);

  useEffect(() => {
    if (!running) {
      setWaitSeconds(0);
      waitStartedRef.current = null;
      return;
    }
    waitStartedRef.current = Date.now();
    const timer = window.setInterval(() => {
      const started = waitStartedRef.current ?? Date.now();
      setWaitSeconds(Math.max(0, Math.round((Date.now() - started) / 1000)));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [running]);

  const stop = () => {
    stoppedRef.current = true;
    runningRef.current = false;
    abortRef.current?.abort();
    abortRef.current = null;
    setRunning(false);
    setThoughts((current) => appendThought(current, "phase", "已停止"));
  };

  const resetThread = () => {
    if (running) stop();
    writePlayId(projectId, card.slug, null);
    setConversationId(null);
    setTranscript([]);
    setThoughts([]);
    setReport("点下方示例或直接提问。");
    setUserTurns(0);
    setError("");
  };

  const send = async (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || runningRef.current || atLimit) return;
    setQuestion("");
    setError("");
    stoppedRef.current = false;
    runningRef.current = true;
    setRunning(true);
    setUserTurns((current) => current + 1);
    const now = new Date().toISOString();
    const localUserId = `local-${Date.now()}`;
    const pendingId = `pending-${Date.now()}`;
    const threadId = conversationIdRef.current || "local";
    setTranscript((current) => [
      ...current,
      {
        id: localUserId,
        conversationId: threadId,
        role: "user",
        content: trimmed,
        createdAt: now,
      },
      {
        id: pendingId,
        conversationId: threadId,
        role: "assistant",
        content: "正在处理…",
        createdAt: now,
      },
    ]);
    setThoughts([{ kind: "phase", message: conversationIdRef.current ? "正在继续本轮对话" : "已发送，正在处理" }]);
    setReport("正在按该 Agent 的提示词和工具作答…");
    const abort = new AbortController();
    abortRef.current = abort;
    const finishAssistant = (content: string) => {
      setTranscript((current) => current.map((item) => (
        item.id === pendingId
          ? { ...item, content, createdAt: new Date().toISOString() }
          : item
      )));
    };
    try {
      const response = await fetch("/api/investigations/stream", {
        method: "POST",
        credentials: "include",
        signal: abort.signal,
        headers: { "content-type": "application/json", accept: "text/event-stream" },
        body: JSON.stringify({
          projectId,
          environment,
          question: trimmed,
          mode: "INVESTIGATE",
          conversationId: conversationIdRef.current || undefined,
          agentSlug: card.slug,
        }),
      });
      if (!response.ok) throw new Error(await readError(response));
      let receivedResult = false;
      let streamError = "";
      await consumeSse(response, (frame) => {
        if (frame.event === "heartbeat") return;
        if (frame.event === "started") {
          const payload = frame.data as { conversationId?: string };
          if (payload.conversationId) {
            writePlayId(projectId, card.slug, payload.conversationId);
            conversationIdRef.current = payload.conversationId;
            setConversationId(payload.conversationId);
            setTranscript((current) => current.map((item) => (
              item.id === localUserId || item.id === pendingId
                ? { ...item, conversationId: payload.conversationId as string }
                : item
            )));
          }
          return;
        }
        if (frame.event === "thought") {
          const thought = frame.data as { kind?: string; message?: string };
          const message = thought.message?.trim();
          if (message) setThoughts((current) => appendThought(current, thought.kind || "think", message));
          return;
        }
        if (frame.event === "phase") {
          const phase = frame.data as { name?: string; message?: string };
          if (phase.name === "wait" || phase.message?.includes("请稍候")) return;
          const label = phase.message || phase.name || "";
          if (label) setThoughts((current) => appendThought(current, "phase", label));
          return;
        }
        if (frame.event === "error") {
          const payload = frame.data as { message?: string };
          streamError = payload.message || "执行失败";
          setError(streamError);
          return;
        }
        if (frame.event === "result") {
          receivedResult = true;
          const payload = (typeof frame.data === "object" && frame.data) ? frame.data as StreamOutcome : {};
          const nextReport = payload.result?.report || payload.run?.report || streamError;
          if (payload.conversationId) {
            writePlayId(projectId, card.slug, payload.conversationId);
            conversationIdRef.current = payload.conversationId;
            setConversationId(payload.conversationId);
          }
          if (payload.turnIndex != null) setUserTurns(payload.turnIndex);
          if (payload.maxTurns) setMaxTurns(payload.maxTurns);
          if (nextReport) setReport(nextReport);
          if (payload.messages?.length) {
            setTranscript(payload.messages);
          } else if (nextReport) {
            finishAssistant(nextReport);
          }
        }
      });
      if (stoppedRef.current) {
        finishAssistant("已停止");
        return;
      }
      if (!receivedResult) {
        const message = streamError || "连接已断开，可再问一句续上。";
        setError(message);
        finishAssistant(message);
      } else if (streamError) {
        finishAssistant(streamError);
      }
    } catch (cause) {
      if (stoppedRef.current) {
        finishAssistant("已停止");
        return;
      }
      const message = cause instanceof Error ? cause.message : "发送失败";
      setError(message);
      finishAssistant(message);
    } finally {
      if (abortRef.current === abort) abortRef.current = null;
      runningRef.current = false;
      setRunning(false);
    }
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void send(question);
  };

  return (
    <div className="modal-scrim playground-scrim" role="presentation" onClick={() => { if (!running) onClose(); }}>
      <div
        className="modal playground-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="agent-play-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="playground-head">
          <div>
            <p className="kicker">{card.slug} · {environment}</p>
            <h3 id="agent-play-title">{card.name}</h3>
            <div className="chip-row">
              {declaredToolKinds(card.spec).length > 0 ? (
                declaredToolKinds(card.spec).map((kind) => <span className="tag" key={kind}>{mcpKindLabel(kind)}</span>)
              ) : (
                <span className="tag">无业务查询工具</span>
              )}
              {!card.installed ? <span className="badge faint">未安装 · 用官方定义</span> : null}
            </div>
          </div>
          <div className="playground-head-actions">
            <span className={`badge${atLimit ? " warn" : ""}`}>{userTurns}/{maxTurns} 轮</span>
            <button className="btn secondary" type="button" disabled={running} onClick={resetThread}>
              <IconPlus size={14} /> 新对话
            </button>
            <button className="btn ghost" type="button" aria-label="关闭工作台" onClick={onClose}>
              <IconX size={16} />
            </button>
          </div>
        </header>

        {missing.length > 0 ? (
          <p className="notice playground-notice" role="status">
            当前 {environment} 还没绑上 {missing.map((kind) => mcpKindLabel(kind)).join("、")}。
            去 <Link to={`/projects/${projectId}/bindings`}>工具</Link>
            {missing.includes("CODE") ? <> 或 <Link to={`/projects/${projectId}/repositories`}>代码仓</Link></> : null}
            打开后，再回来问，避免空转。
          </p>
        ) : null}
        {noDomainTools ? (
          <p className="notice playground-notice" role="status">
            当前环境没有用户域查询工具（本期不接业务 Dubbo）。可以打开工作台，但查不到账号状态或封禁记录。
          </p>
        ) : null}
        {error ? <p className="error" role="alert">{error}</p> : null}

        <div className="playground-transcript">
          {!hydrated && transcript.length === 0 && !running ? (
            <p className="hint">正在恢复上次对话…</p>
          ) : transcript.length > 0 || running ? (
            <>
              {transcript.filter((message) => !(running && message.id.startsWith("pending-"))).map((message) => (
                message.role === "user" ? (
                  <div className="bubble user" key={message.id}>
                    <small>你 · {formatTime(message.createdAt)}</small>
                    <div>{message.content}</div>
                  </div>
                ) : (
                  <div className="bubble assistant" key={message.id}>
                    <small>助手 · {formatTime(message.createdAt)}</small>
                    <pre className="report">{message.content}</pre>
                  </div>
                )
              ))}
              {running ? (
                <div className="bubble assistant pending" aria-busy="true">
                  <div className="run-status" role="status" aria-atomic="true">
                    <span className="run-spinner" aria-hidden="true" />
                    <div className="run-status-main">
                      <b>{thoughts[thoughts.length - 1]?.message || report}</b>
                      <small>已等待 {formatWait(waitSeconds)}</small>
                    </div>
                    <button type="button" className="btn danger secondary run-stop" onClick={stop}>
                      <IconX size={14} />
                      停止
                    </button>
                  </div>
                  {thoughts.length > 0 ? (
                    <ol className="thought-log" role="log" aria-live="polite">
                      {thoughts.map((item, index) => (
                        <li key={`${item.kind}-${item.message}-${index}`} className={index === thoughts.length - 1 ? "current" : ""}>
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
            <div className="empty empty-hero playground-empty">
              <span className="empty-icon"><IconSparkle size={22} /></span>
              <h3>按这张卡的流程直接问</h3>
              <p>{card.summary || "提示词和允许的工具已经编排好，不必先安装。"}</p>
            </div>
          )}
        </div>

        {card.spec.starters.length > 0 && !running ? (
          <div className="chip-row playground-starters">
            {card.spec.starters.map((item) => (
              <button key={item} className="chip" type="button" disabled={atLimit} onClick={() => void send(item)}>
                {item}
              </button>
            ))}
          </div>
        ) : null}

        <form className="composer playground-composer" onSubmit={onSubmit}>
          <label className="composer-field" htmlFor="agent-play-question">
            <span className="sr-only">问题</span>
            <textarea
              id="agent-play-question"
              ref={textareaRef}
              value={question}
              rows={3}
              placeholder={atLimit ? "本会话已到轮次上限，请开新对话" : `向「${card.name}」提问`}
              disabled={running || atLimit}
              onChange={(event) => setQuestion(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void send(question);
                }
              }}
            />
          </label>
          <div className="composer-bar">
            <button className="btn" type="submit" disabled={running || atLimit || !question.trim()}>
              <IconSend size={14} />
              发送
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function storageKey(projectId: string, slug: string) {
  return `sre-investigator.agent-playground.${projectId}.${slug}`;
}

function readPlayId(projectId: string, slug: string): string | null {
  try {
    return sessionStorage.getItem(storageKey(projectId, slug));
  } catch {
    return null;
  }
}

async function readError(response: Response) {
  const text = await response.text();
  if (!text) return `${response.status} ${response.statusText}`;
  try {
    const body = JSON.parse(text) as { message?: string };
    return body.message || text;
  } catch {
    return text;
  }
}

function writePlayId(projectId: string, slug: string, id: string | null) {
  try {
    if (id) sessionStorage.setItem(storageKey(projectId, slug), id);
    else sessionStorage.removeItem(storageKey(projectId, slug));
  } catch {
    /* ignore quota */
  }
}

function appendThought(current: LiveThought[], kind: string, message: string) {
  const text = message.trim();
  if (!text || current[current.length - 1]?.message === text) return current;
  const next = [...current, { kind: kind || "think", message: text }];
  return next.length > 60 ? next.slice(next.length - 60) : next;
}

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleString();
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
