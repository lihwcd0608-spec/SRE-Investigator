package com.maiya.investigator.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

final class GitSsh {
    private static final Pattern UNSAFE = Pattern.compile("[\\s;|&$`\\\\'\"\\n\\r]");

    private GitSsh() {
    }

    static void apply(ProcessBuilder builder, String sshIdentity) {
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        if (sshIdentity == null || sshIdentity.isBlank()) {
            builder.environment().putIfAbsent("GIT_SSH_COMMAND", "ssh -o BatchMode=yes");
            return;
        }
        Path key = resolveIdentity(sshIdentity);
        builder.environment().put(
                "GIT_SSH_COMMAND",
                "ssh -i " + key + " -o IdentitiesOnly=yes -o BatchMode=yes"
        );
    }

    static Path resolveIdentity(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("~/")) {
            value = Path.of(System.getProperty("user.home"), value.substring(2)).toString();
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("请填写 SSH 私钥路径");
        }
        if (UNSAFE.matcher(value).find()) {
            throw new IllegalArgumentException("SSH 私钥路径包含非法字符");
        }
        Path key = Path.of(value);
        if (!key.isAbsolute()) {
            throw new IllegalArgumentException("SSH 私钥必须是本机绝对路径");
        }
        key = key.toAbsolutePath().normalize();
        if (!Files.isRegularFile(key) || !Files.isReadable(key)) {
            throw new IllegalArgumentException("SSH 私钥不存在或不可读：" + key);
        }
        return key;
    }
}
