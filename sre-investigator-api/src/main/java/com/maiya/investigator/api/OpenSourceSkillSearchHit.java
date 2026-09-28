package com.maiya.investigator.api;

public record OpenSourceSkillSearchHit(
        String id,
        String name,
        String registry,
        String origin,
        String summary,
        String sourceUrl,
        String gitRef,
        String packageName,
        Integer installs
) {
}
