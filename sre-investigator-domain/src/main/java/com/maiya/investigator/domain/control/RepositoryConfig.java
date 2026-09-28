package com.maiya.investigator.domain.control;

import java.time.Instant;
import java.util.UUID;

public record RepositoryConfig(
        UUID id,
        UUID projectId,
        String name,
        String role,
        String rootPath,
        Instant createdAt,
        String gitUrl,
        String gitRef
) {
    public static RepositoryConfig create(UUID projectId, String name, String role, String rootPath) {
        return create(projectId, name, role, rootPath, null, null);
    }

    public static RepositoryConfig create(
            UUID projectId,
            String name,
            String role,
            String rootPath,
            String gitUrl,
            String gitRef
    ) {
        return new RepositoryConfig(
                UUID.randomUUID(),
                projectId,
                name,
                role,
                rootPath,
                Instant.now(),
                blankToNull(gitUrl),
                blankToNull(gitRef)
        );
    }

    public boolean fromGit() {
        return gitUrl != null && !gitUrl.isBlank();
    }

    public RepositoryConfig withRootPath(String path) {
        return new RepositoryConfig(id, projectId, name, role, path, createdAt, gitUrl, gitRef);
    }

    public RepositoryConfig withDetails(String name, String role, String rootPath, String gitUrl, String gitRef) {
        return new RepositoryConfig(id, projectId, name, role, rootPath, createdAt, blankToNull(gitUrl), blankToNull(gitRef));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
