package com.maiya.investigator.boot.monitor;

import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.mcp.MetricSnapshot;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定时采集已开启监控的 Binding 指标并落库。
 *
 * <p>仅采集 MySQL 类型；其他类型暂不支持。Java 进程未启动则不执行（同 K8s 巡检约定）。
 */
@Service
public class BindingMetricsScheduler {
    private static final Logger log = LoggerFactory.getLogger(BindingMetricsScheduler.class);
    static final int RETENTION_HOURS = 24;

    private final ControlPlaneStore controlPlane;
    private final BindingMetricsService metricsService;

    public BindingMetricsScheduler(ControlPlaneStore controlPlane, BindingMetricsService metricsService) {
        this.controlPlane = controlPlane;
        this.metricsService = metricsService;
    }

    /** 支持监控采集的绑定类型。 */
    private static final Set<ToolKind> MONITORED_KINDS =
            Set.of(ToolKind.MYSQL, ToolKind.REDIS, ToolKind.ELASTICSEARCH, ToolKind.MONGODB);

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void collect() {
        List<ToolBinding> targets = controlPlane.bindingsWithMonitoringEnabled();
        if (targets.isEmpty()) {
            return;
        }
        for (ToolBinding binding : targets) {
            if (!MONITORED_KINDS.contains(binding.toolKind())) {
                continue;
            }
            try {
                MetricSnapshot snapshot = metricsService.snapshot(binding.projectId(), binding.id());
                metricsService.store(binding.id(), snapshot);
            } catch (RuntimeException exception) {
                log.warn("采集 binding 指标失败 binding={} profile={}：{}",
                        binding.id(), binding.profile(), exception.getMessage());
            }
        }
        try {
            metricsService.cleanupOlderThan(RETENTION_HOURS);
        } catch (RuntimeException exception) {
            log.warn("清理过期指标失败：{}", exception.getMessage());
        }
    }
}
