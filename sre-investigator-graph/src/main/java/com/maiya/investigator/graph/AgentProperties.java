package com.maiya.investigator.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.agent")
public class AgentProperties {
    private int maxToolCalls = 16;
    private int maxUserTurns = 10;
    private int maxIterations = 3;
    private int modelMaxAttempts = 2;
    private long modelRetryBackoffMs = 500;
    private boolean enableExternalMcp = false;
    private String investigatePrompt = "";

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = Math.max(1, maxToolCalls);
    }

    /** 循环 Graph 单轮对话内 plan→act→observe 的最大迭代次数。 */
    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = Math.max(1, maxIterations);
    }

    /** 模型调用（含工具循环）最多尝试次数，用于扛住瞬时 I/O 报错。 */
    public int getModelMaxAttempts() {
        return modelMaxAttempts;
    }

    public void setModelMaxAttempts(int modelMaxAttempts) {
        this.modelMaxAttempts = Math.max(1, modelMaxAttempts);
    }

    /** 两次模型调用之间的初始退避毫秒，指数增长。 */
    public long getModelRetryBackoffMs() {
        return modelRetryBackoffMs;
    }

    public void setModelRetryBackoffMs(long modelRetryBackoffMs) {
        this.modelRetryBackoffMs = Math.max(0, modelRetryBackoffMs);
    }

    public int getMaxUserTurns() {
        return maxUserTurns;
    }

    public void setMaxUserTurns(int maxUserTurns) {
        this.maxUserTurns = Math.max(1, maxUserTurns);
    }

    public boolean isEnableExternalMcp() {
        return enableExternalMcp;
    }

    public void setEnableExternalMcp(boolean enableExternalMcp) {
        this.enableExternalMcp = enableExternalMcp;
    }

    public String getInvestigatePrompt() {
        return investigatePrompt;
    }

    public void setInvestigatePrompt(String investigatePrompt) {
        this.investigatePrompt = investigatePrompt == null ? "" : investigatePrompt;
    }
}
