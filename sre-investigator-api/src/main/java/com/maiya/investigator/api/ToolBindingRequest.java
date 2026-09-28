package com.maiya.investigator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ToolBindingRequest(
        @NotNull Environment environment,
        @NotBlank String toolKind,
        @NotBlank String profile,
        String configuration,
        String summary,
        List<String> tags,
        Boolean monitoringEnabled,
        List<String> monitoringMetrics
) {
}
