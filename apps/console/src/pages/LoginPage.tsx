import { FormEvent, useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { IconEye, IconEyeOff, IconPulse } from "../icons";
import type { Me } from "../types";

function FeishuMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#3370ff"
        d="M12.1 3.2c.3 2.4 1.6 4.5 3.6 5.8 1.3.8 2.8 1.2 4.3 1.1-1.8 1.5-2.9 3.8-2.9 6.2 0 1.5.4 3 1.2 4.3-2.5-.3-4.8-1.6-6.4-3.7-.9-1.2-1.5-2.6-1.7-4.1-.5 1.9-.8 3.8-.8 5.7 0 1.3.2 2.6.7 3.8-2.2-1.3-3.7-3.6-3.9-6.3C6 11.2 8.5 6.2 12.1 3.2Z"
      />
      <path
        fill="#14c9c9"
        d="M14.8 7.1c1.4.6 2.6 1.7 3.3 3.1.8 1.6.9 3.5.3 5.2-.4 1.2-1.2 2.2-2.2 2.9 1.3-1.8 1.5-4.3.4-6.3-.6-1.1-1.6-2-2.8-2.5.3-.8.7-1.6 1-2.4Z"
      />
    </svg>
  );
}

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [feishuUrl, setFeishuUrl] = useState<string | null>(null);
  const [error, setError] = useState(params.get("error") === "feishu" ? "飞书登录失败，请重试" : "");
  const [notice, setNotice] = useState("");
  const [fieldsInvalid, setFieldsInvalid] = useState(false);
  const [saving, setSaving] = useState(false);
  const errorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    void api<Me>("/api/auth/me")
      .then(() => {
        if (!cancelled) navigate("/", { replace: true });
      })
      .catch(() => undefined);
    void api<{ enabled: boolean; url: string | null }>("/api/auth/feishu/url")
      .then((body) => {
        if (!cancelled && body.enabled && body.url) setFeishuUrl(body.url);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  useEffect(() => {
    if (error) {
      errorRef.current?.focus();
    }
  }, [error]);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setNotice("");
    setFieldsInvalid(false);
    setSaving(true);
    try {
      await api<Me>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      navigate("/", { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "登录失败");
      setFieldsInvalid(true);
    } finally {
      setSaving(false);
    }
  };

  const errorId = "login-error";
  const noticeId = "login-notice";

  return (
    <div className="login-shell login-stage">
      <section className="login-hero" aria-label="产品介绍">
        <div className="login-hero-brand">
          <span className="brand-mark login-brand-mark">
            <IconPulse size={18} />
          </span>
          <div className="login-hero-copy">
            <p className="login-product">SRE Investigator</p>
            <p className="login-product-sub">智能云维分析平台</p>
          </div>
        </div>

        <div className="login-hero-main">
          <p className="login-headline">让每一次故障，都有迹可循</p>
          <p className="login-lead">
            面向云上运维的智能根因分析平台 —— 从告警到结论，全程可解释、可追溯。
          </p>

          <article className="login-terminal" aria-label="排查会话示例">
            <header className="login-terminal-bar">
              <span className="login-traffic" aria-hidden="true">
                <i className="login-dot login-dot-red" />
                <i className="login-dot login-dot-yellow" />
                <i className="login-dot login-dot-green" />
              </span>
              <span className="login-terminal-title">sre-investigator — session 8x2F</span>
            </header>
            <div className="login-terminal-body">
              <p>
                <span className="login-prompt">$</span> sre investigate --alert ALB-5xx-spike
              </p>
              <p className="login-terminal-line">指标采集完成 — 47 个序列 · 2.4s</p>
              <p className="login-terminal-line">变更关联 — #4821 deploy/api-gateway</p>
              <p className="login-terminal-line">日志聚类 — 识别出 3 类异常模式</p>
              <p className="login-hypothesis">根因假设：上游网关连接池耗尽 · 置信度 92.4%</p>
              <p className="login-terminal-done">
                <span className="login-done-dot" aria-hidden="true" />
                分析完成 · 全程 12.8s
              </p>
            </div>
          </article>
        </div>

        <dl className="login-stats">
          <div>
            <dt>12.8s</dt>
            <dd>平均根因定位</dd>
          </div>
          <div>
            <dt>300+</dt>
            <dd>已接入服务</dd>
          </div>
          <div>
            <dt>87.6%</dt>
            <dd>告警降噪率</dd>
          </div>
        </dl>
      </section>

      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-panel-inner">
          <header className="login-panel-head">
            <h1 id="login-title">欢迎回来</h1>
            <p>登录 SRE Investigator 工作台</p>
          </header>

          {error ? (
            <div
              ref={errorRef}
              id={errorId}
              className="login-error"
              role="alert"
              tabIndex={-1}
            >
              {error}
            </div>
          ) : null}

          <form className="login-form" onSubmit={(event) => void onSubmit(event)}>
            <div className="login-field">
              <label htmlFor="login-username">用户名</label>
              <input
                id="login-username"
                name="username"
                autoComplete="username"
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                autoFocus={!error}
                placeholder="admin 或飞书账号"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                aria-invalid={fieldsInvalid ? true : undefined}
                aria-describedby={error ? errorId : undefined}
                required
              />
            </div>
            <div className="login-field">
              <div className="login-field-head">
                <label htmlFor="login-password">密码</label>
                <button
                  type="button"
                  className="login-forgot"
                  onClick={() => setNotice("请联系管理员重置密码")}
                >
                  忘记密码?
                </button>
              </div>
              <div className="login-password">
                <input
                  id="login-password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  aria-invalid={fieldsInvalid ? true : undefined}
                  aria-describedby={error ? errorId : notice ? noticeId : undefined}
                  required
                />
                <button
                  type="button"
                  className="login-password-toggle"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? "隐藏密码" : "显示密码"}
                  aria-pressed={showPassword}
                >
                  {showPassword ? <IconEyeOff size={18} /> : <IconEye size={18} />}
                </button>
              </div>
            </div>
            <button className="btn login-submit" type="submit" disabled={saving} aria-busy={saving}>
              {saving ? "登录中…" : "登录"}
            </button>
          </form>

          <div className="login-sso">
            <div className="login-divider" role="separator">
              <span>其他登录方式</span>
            </div>
            {feishuUrl ? (
              <a className="btn secondary login-feishu" href={feishuUrl}>
                <FeishuMark />
                使用飞书登录
              </a>
            ) : (
              <button
                type="button"
                className="btn secondary login-feishu"
                onClick={() => setNotice("飞书登录未配置，请使用账号密码登录")}
              >
                <FeishuMark />
                使用飞书登录
              </button>
            )}
          </div>

          {notice ? (
            <p id={noticeId} className="login-notice" role="status">
              {notice}
            </p>
          ) : null}

          <p className="login-legal">
            登录即代表你同意
            <button type="button" className="login-legal-link" onClick={() => setNotice("内部工作台暂未发布独立协议文本。本系统仅用于只读排障。")}>
              《服务协议》
            </button>
            与
            <button type="button" className="login-legal-link" onClick={() => setNotice("内部工作台暂未发布独立隐私政策。账号仅用于工作台访问控制。")}>
              《隐私政策》
            </button>
          </p>
        </div>
      </section>
    </div>
  );
}
