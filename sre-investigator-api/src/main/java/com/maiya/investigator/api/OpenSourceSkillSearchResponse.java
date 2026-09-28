package com.maiya.investigator.api;

import java.util.ArrayList;
import java.util.List;

public record OpenSourceSkillSearchResponse(
        String query,
        List<OpenSourceSkillSearchHit> hits,
        List<String> warnings
) {
    public OpenSourceSkillSearchResponse {
        hits = hits == null ? new ArrayList<>() : hits;
        warnings = warnings == null ? new ArrayList<>() : warnings;
    }
}
