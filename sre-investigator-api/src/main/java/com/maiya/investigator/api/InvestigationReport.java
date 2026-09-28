package com.maiya.investigator.api;

import java.util.List;

public record InvestigationReport(
    String runId,
    String status,
    List<String> evidence,
    List<String> hypotheses,
    List<String> nextSteps
) {
}
