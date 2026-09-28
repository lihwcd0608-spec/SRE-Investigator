package com.maiya.investigator.mcp.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public record KafkaEndpoint(
        String bootstrapServers,
        String securityProtocol,
        String saslMechanism,
        String username,
        String password,
        String consumerGroup
) {
    public boolean configured() {
        return notBlank(bootstrapServers);
    }

    public String summary() {
        return bootstrapServers + (notBlank(securityProtocol) ? " " + securityProtocol : "");
    }

    public Properties adminProperties() {
        Properties properties = baseProperties();
        properties.put("request.timeout.ms", "8000");
        properties.put("default.api.timeout.ms", "8000");
        return properties;
    }

    public Properties consumerProperties(String groupId) {
        Properties properties = baseProperties();
        properties.put("group.id", notBlank(groupId) ? groupId : "sre-investigator-peek");
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "latest");
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("max.poll.records", "20");
        return properties;
    }

    private Properties baseProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        String protocol = notBlank(securityProtocol) ? securityProtocol.trim() : "PLAINTEXT";
        properties.put("security.protocol", protocol);
        if (protocol.toUpperCase().contains("SASL")) {
            String mechanism = notBlank(saslMechanism) ? saslMechanism.trim() : "PLAIN";
            properties.put("sasl.mechanism", mechanism);
            if (notBlank(username)) {
                properties.put("sasl.jaas.config", jaas(mechanism, username, password == null ? "" : password));
            }
        }
        return properties;
    }

    private static String jaas(String mechanism, String username, String password) {
        String clazz = mechanism.toUpperCase().startsWith("SCRAM")
                ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                : "org.apache.kafka.common.security.plain.PlainLoginModule";
        return clazz + " required username=\"" + escape(username) + "\" password=\"" + escape(password) + "\";";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public Map<String, Object> safeView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("bootstrapServers", bootstrapServers);
        view.put("securityProtocol", securityProtocol);
        view.put("saslMechanism", saslMechanism);
        view.put("consumerGroup", consumerGroup);
        return view;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
