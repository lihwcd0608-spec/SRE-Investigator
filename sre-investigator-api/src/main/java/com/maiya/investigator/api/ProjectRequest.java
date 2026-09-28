package com.maiya.investigator.api;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
        @NotBlank String name,
        @NotBlank String slug,
        String topology
) {
}
