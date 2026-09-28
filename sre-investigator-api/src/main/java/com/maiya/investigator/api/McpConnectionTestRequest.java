package com.maiya.investigator.api;

import jakarta.validation.constraints.NotBlank;

public record McpConnectionTestRequest(
        @NotBlank String toolKind,
        String host,
        Integer port,
        String database,
        String username,
        String password,
        String parameters,
        String uri,
        String rootPath,
        String regionId,
        String project,
        String logstore,
        String accessKeyId,
        String accessKeySecret,
        String endpoint,
        String gatewayUniqueId,
        String nacosInstanceId,
        String baseUrl,
        String cluster,
        String namespace,
        Boolean insecureSkipTls,
        String serviceType,
        String applications,
        String instanceIds,
        String domains
) {
    public McpConnectionTestRequest(
            String toolKind,
            String host,
            Integer port,
            String database,
            String username,
            String password,
            String parameters,
            String uri,
            String rootPath
    ) {
        this(
                toolKind, host, port, database, username, password, parameters, uri, rootPath,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );
    }

    public McpConnectionTestRequest(
            String toolKind,
            String host,
            Integer port,
            String database,
            String username,
            String password,
            String parameters,
            String uri,
            String rootPath,
            String regionId,
            String project,
            String logstore,
            String accessKeyId,
            String accessKeySecret,
            String endpoint,
            String gatewayUniqueId,
            String nacosInstanceId
    ) {
        this(
                toolKind, host, port, database, username, password, parameters, uri, rootPath,
                regionId, project, logstore, accessKeyId, accessKeySecret, endpoint,
                gatewayUniqueId, nacosInstanceId, null, null, null, null, null, null, null, null
        );
    }

    public McpConnectionTestRequest(
            String toolKind,
            String host,
            Integer port,
            String database,
            String username,
            String password,
            String parameters,
            String uri,
            String rootPath,
            String regionId,
            String project,
            String logstore,
            String accessKeyId,
            String accessKeySecret,
            String endpoint,
            String gatewayUniqueId,
            String nacosInstanceId,
            String baseUrl,
            String cluster,
            String namespace,
            Boolean insecureSkipTls
    ) {
        this(
                toolKind, host, port, database, username, password, parameters, uri, rootPath,
                regionId, project, logstore, accessKeyId, accessKeySecret, endpoint,
                gatewayUniqueId, nacosInstanceId, baseUrl, cluster, namespace, insecureSkipTls,
                null, null, null, null
        );
    }
}
