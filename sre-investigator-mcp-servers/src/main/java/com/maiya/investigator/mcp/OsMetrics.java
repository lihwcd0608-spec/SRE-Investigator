package com.maiya.investigator.mcp;

import java.lang.management.ManagementFactory;

/**
 * 主机级 OS 指标（CPU / 内存）。
 *
 * <p>仅当目标实例与本服务同机（回环地址）时可通过 JDK OperatingSystemMXBean 读取真实值；
 * 远程实例无法从 SQL / 命令侧获取主机 OS 指标，标记为 unavailable。
 */
public final class OsMetrics {
    private OsMetrics() {
    }

    public static MetricPoint cpuPoint(String host) {
        if (!isLocalInstance(host)) {
            return new MetricPoint("cpu_usage", "CPU 占用率", "unavailable", "%");
        }
        try {
            double load = com.sun.management.OperatingSystemMXBean.class.cast(
                    ManagementFactory.getOperatingSystemMXBean()).getCpuLoad();
            return load >= 0
                    ? new MetricPoint("cpu_usage", "CPU 占用率", String.format("%.1f", load * 100), "%")
                    : new MetricPoint("cpu_usage", "CPU 占用率", "unavailable", "%");
        } catch (RuntimeException exception) {
            return new MetricPoint("cpu_usage", "CPU 占用率", "unavailable", "%");
        }
    }

    public static MetricPoint memoryPoint(String host) {
        if (!isLocalInstance(host)) {
            return new MetricPoint("os_memory", "内存占用率", "unavailable", "%");
        }
        try {
            com.sun.management.OperatingSystemMXBean os = com.sun.management.OperatingSystemMXBean.class.cast(
                    ManagementFactory.getOperatingSystemMXBean());
            long total = os.getTotalPhysicalMemorySize();
            long free = os.getFreePhysicalMemorySize();
            if (total <= 0 || free < 0) {
                return new MetricPoint("os_memory", "内存占用率", "unavailable", "%");
            }
            double pct = (total - free) * 100.0 / total;
            return new MetricPoint("os_memory", "内存占用率", String.format("%.1f", pct), "%");
        } catch (RuntimeException exception) {
            return new MetricPoint("os_memory", "内存占用率", "unavailable", "%");
        }
    }

    /** 本机实例判断：数据库与本服务部署在同一台机器时才可读主机 OS 指标。 */
    public static boolean isLocalInstance(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase();
        return normalized.equals("127.0.0.1") || normalized.equals("localhost") || normalized.equals("::1");
    }
}
