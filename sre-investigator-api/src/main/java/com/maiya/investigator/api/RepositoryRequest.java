package com.maiya.investigator.api;

public record RepositoryRequest(
        String name,
        String role,
        String rootPath,
        String gitUrl,
        String gitRef,
        String username,
        String token,
        String sshIdentity
) {
}
