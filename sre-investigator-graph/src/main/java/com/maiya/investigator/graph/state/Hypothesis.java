package com.maiya.investigator.graph.state;

/**
 * 根因假设。likelihood 为 0..1 的粗略可能性，仅用于排序，不做硬判定。
 */
public record Hypothesis(String statement, double likelihood, Status status) {
    public enum Status {
        OPEN,
        CONFIRMED,
        REJECTED
    }

    public Hypothesis {
        statement = statement == null ? "" : statement.trim();
        likelihood = Math.max(0.0, Math.min(1.0, likelihood));
        status = status == null ? Status.OPEN : status;
    }

    public static Hypothesis open(String statement, double likelihood) {
        return new Hypothesis(statement, likelihood, Status.OPEN);
    }
}
