package com.maiya.investigator.mcp;

import com.maiya.investigator.mcp.config.RepositoryWorkspaceProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitRepositoryCloner {
    private static final Logger log = LoggerFactory.getLogger(GitRepositoryCloner.class);
    private final RepositoryWorkspaceProperties properties;

    public GitRepositoryCloner(RepositoryWorkspaceProperties properties) {
        this.properties = properties;
    }

    public Path checkout(String destPath, String gitUrl, String gitRef, String username, String token) {
        return checkout(destPath, gitUrl, gitRef, username, token, null);
    }

    public Path checkout(String destPath, String gitUrl, String gitRef, String username, String token, String sshIdentity) {
        String url = GitUrl.normalize(gitUrl);
        Path dest = requireDestination(destPath);
        String cloneUrl = GitUrl.withCredentials(url, username, token);
        if (Files.isDirectory(dest.resolve(".git"))) {
            log.info("Git 仓已存在，改为拉取更新 dest={}", dest);
            pull(dest, url, gitRef, username, token, sshIdentity);
            return dest;
        }
        if (Files.exists(dest) && !isEmptyDirectory(dest)) {
            throw new IllegalStateException("目标目录已存在且不是空目录或 Git 仓库：" + dest);
        }
        List<String> command = new ArrayList<>(List.of("git", "clone", "--depth", "1"));
        if (gitRef != null && !gitRef.isBlank()) {
            command.add("--branch");
            command.add(gitRef.trim());
            command.add("--single-branch");
        }
        command.add("--");
        command.add(cloneUrl);
        command.add(dest.toString());
        log.info("Git clone dest={} ref={} sshKey={}", dest, gitRef == null || gitRef.isBlank() ? "default" : gitRef.trim(), sshIdentity == null || sshIdentity.isBlank() ? "default" : "set");
        run(command, dest.getParent(), "克隆失败", sshIdentity);
        return dest;
    }

    public Path pull(Path root, String gitUrl, String gitRef, String username, String token) {
        return pull(root, gitUrl, gitRef, username, token, null);
    }

    public Path pull(Path root, String gitUrl, String gitRef, String username, String token, String sshIdentity) {
        Path dest = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(dest.resolve(".git"))) {
            throw new IllegalArgumentException("该目录不是 Git 仓库，无法拉取更新：" + dest);
        }
        String url = gitUrl == null || gitUrl.isBlank() ? null : GitUrl.normalize(gitUrl);
        if (url != null) {
            run(List.of("git", "-C", dest.toString(), "remote", "set-url", "origin", GitUrl.withCredentials(url, username, token)), dest, "更新远程地址失败", sshIdentity);
        }
        List<String> fetch = new ArrayList<>(List.of("git", "-C", dest.toString(), "fetch", "--depth", "1", "origin"));
        if (gitRef != null && !gitRef.isBlank()) {
            fetch.add(gitRef.trim());
        }
        run(fetch, dest, "拉取失败", sshIdentity);
        run(List.of("git", "-C", dest.toString(), "reset", "--hard", "FETCH_HEAD"), dest, "对齐远端最新提交失败", sshIdentity);
        if (url != null) {
            run(List.of("git", "-C", dest.toString(), "remote", "set-url", "origin", url), dest, "还原远程地址失败", sshIdentity);
        }
        return dest;
    }

    public static Path requireDestination(String destPath) {
        if (destPath == null || destPath.isBlank()) {
            throw new IllegalArgumentException("请填写克隆到的本机目录");
        }
        Path dest = Path.of(destPath.trim());
        if (!dest.isAbsolute()) {
            throw new IllegalArgumentException("克隆目录必须是本机绝对路径");
        }
        dest = dest.toAbsolutePath().normalize();
        Path parent = dest.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("克隆目录的上级目录不存在：" + parent);
        }
        return dest;
    }

    private static boolean isEmptyDirectory(Path dest) {
        if (!Files.isDirectory(dest)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dest)) {
            return !stream.iterator().hasNext();
        } catch (IOException exception) {
            return false;
        }
    }

    private void run(List<String> command, Path workDir, String failure, String sshIdentity) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        GitSsh.apply(builder, sshIdentity);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                log.error("Git {} dest={}：超时（{}s） output={}", failure, workDir, properties.getTimeoutSeconds(), maskSecrets(output));
                throw new IllegalStateException(failure + "：Git 超时");
            }
            if (process.exitValue() != 0) {
                log.error("Git {} dest={} exit={} output={}", failure, workDir, process.exitValue(), maskSecrets(output));
                throw new IllegalStateException(failure + "：" + preview(output));
            }
        } catch (IOException exception) {
            log.error("Git {} dest={}：无法执行 git 命令", failure, workDir, exception);
            throw new IllegalStateException(failure + "：本机未安装 git，或无法执行 git 命令", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Git {} dest={}：被中断", failure, workDir);
            throw new IllegalStateException(failure + "：被中断", exception);
        }
    }

    private static String preview(String output) {
        String compact = maskSecrets(output).replaceAll("\\s+", " ").trim();
        return compact.length() > 280 ? compact.substring(0, 280) + "…" : compact;
    }

    private static String maskSecrets(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return output.replaceAll("(?i)(://)[^\\s/@]+:[^\\s/@]+@", "$1***:***@").trim();
    }
}
