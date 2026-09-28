package com.maiya.investigator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record FeishuChatBindingRequest(
        @NotBlank String chatId,
        String chatName,
        @NotNull Environment environment,
        List<Environment> environments,
        String defaultRoute,
        UUID defaultRepositoryId,
        Boolean enabled
) {
}
