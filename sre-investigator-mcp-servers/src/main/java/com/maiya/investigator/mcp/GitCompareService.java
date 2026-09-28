package com.maiya.investigator.mcp;

import com.maiya.investigator.mcp.config.RepositoryWorkspaceProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitCompareService {
    private static final Logger log = LoggerFactory.getLogger(GitCompareService.class);
    private static final int MAX_DIFF_CHARS = 80_000;
    private static final int MAX_CAPTURE_BYTES = 2_000_000;
    private static final int[] DEEPEN_STEPS = {80, 300, 1200};
    private static final Pattern REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/@~^+-]*");
    private final RepositoryWorkspaceProperties properties;
    private final ConcurrentHashMap<String, Object> destLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedCompare> cache = new ConcurrentHashMap<>();

    public GitCompareService(RepositoryWorkspaceProperties properties) {
        this.properties = properties;
    }

    public List<GitBranch> listBranches(Path root) {
        Path dest = requireGit(root);
        synchronized (lockFor(dest)) {
            tryRun(dest, List.of("git", "fetch", "origin", "--prune"), "拉取远程分支");
            LinkedHashMap<String, GitBranch> branches = new LinkedHashMap<>();
            for (String line : lines(tryRun(dest, List.of("git", "ls-remote", "--heads", "origin"), "列出远端分支"))) {
                String[] parts = line.split("\\s+");
                if (parts.length < 2 || !parts[1].startsWith("refs/heads/")) {
                    continue;
                }
                String name = parts[1].substring("refs/heads/".length());
                if (!name.isBlank()) {
                    branches.put(name, new GitBranch(name, "origin/" + name, true));
                }
            }
            for (String raw : lines(run(dest, List.of("git", "for-each-ref", "--format=%(refname:short)", "refs/heads", "refs/remotes/origin"), "列出本地分支"))) {
                String name = normalizeRef(raw);
                if (name == null || branches.containsKey(name)) {
                    continue;
                }
                boolean remote = raw.startsWith("origin/");
                branches.put(name, new GitBranch(name, remote ? raw : name, remote));
            }
            return new ArrayList<>(branches.values());
        }
    }

    public GitCompareResult compare(Path root, String baseRef, String headRef) {
        return compare(root, baseRef, headRef, true);
    }

    public GitCompareResult compare(Path root, String baseRef, String headRef, boolean includeUnified) {
        Path dest = requireGit(root);
        String base = requireRef(baseRef, "基准分支");
        String head = requireRef(headRef, "对比分支");
        if (base.equals(head)) {
            throw new IllegalArgumentException("请选择两个不同的分支");
        }
        synchronized (lockFor(dest)) {
            ResolvedCompare resolved = resolveCompare(dest, base, head, true);
            CachedCompare cached = cached(dest, base, head, resolved.baseSha, resolved.headSha);
            if (cached != null && (!includeUnified || hasUnified(cached.result) || cached.result.files().isEmpty())) {
                return cached.result;
            }
            String unified = "";
            boolean truncated = false;
            if (cached == null) {
                List<GitFileChange> files = new ArrayList<>();
                String stat = "对比分支没有相对基准的独有变更";
                if (!resolved.empty()) {
                    files = parseFiles(run(dest, diffCommand("--name-status", resolved.range()), "对比文件列表"));
                    stat = run(dest, diffCommand("--shortstat", resolved.range()), "对比统计").trim();
                    if (stat.isBlank()) {
                        stat = files.isEmpty() ? "对比分支没有相对基准的独有变更" : files.size() + " 个文件有差异";
                    }
                }
                if (includeUnified && !resolved.empty()) {
                    unified = readUnified(dest, resolved.range(), null);
                    truncated = unified.length() > MAX_DIFF_CHARS;
                }
                GitCompareResult result = new GitCompareResult(
                        dest.toString(),
                        base,
                        head,
                        resolved.baseSha,
                        resolved.headSha,
                        stat,
                        files,
                        truncated ? unified.substring(0, MAX_DIFF_CHARS) + "\n…（diff 已截断，请用 code_read 阅读完整文件）" : unified,
                        truncated,
                        resolved.fetchedRemote,
                        resolved.commonAncestor
                );
                cache.put(cacheKey(dest, base, head, resolved.baseSha, resolved.headSha), new CachedCompare(result, resolved.range(), System.currentTimeMillis() + 120_000));
                return result;
            }
            unified = cached.result.files().isEmpty() ? "" : readUnified(dest, cached.range, null);
            truncated = unified.length() > MAX_DIFF_CHARS;
            if (truncated) {
                unified = unified.substring(0, MAX_DIFF_CHARS) + "\n…（diff 已截断，请用 code_read 阅读完整文件）";
            }
            GitCompareResult filled = new GitCompareResult(
                    cached.result.rootPath(),
                    cached.result.baseRef(),
                    cached.result.headRef(),
                    cached.result.baseSha(),
                    cached.result.headSha(),
                    cached.result.stat(),
                    cached.result.files(),
                    unified,
                    truncated,
                    cached.result.fetchedRemote(),
                    cached.result.commonAncestor()
            );
            cache.put(cacheKey(dest, base, head, resolved.baseSha, resolved.headSha), new CachedCompare(filled, cached.range, System.currentTimeMillis() + 120_000));
            return filled;
        }
    }

    public GitFileDiff fileDiff(Path root, String baseRef, String headRef, String path) {
        Path dest = requireGit(root);
        String base = requireRef(baseRef, "基准分支");
        String head = requireRef(headRef, "对比分支");
        String file = requirePath(path);
        if (base.equals(head)) {
            throw new IllegalArgumentException("请选择两个不同的分支");
        }
        synchronized (lockFor(dest)) {
            ResolvedCompare resolved = resolveCompare(dest, base, head, false);
            if (resolved.empty()) {
                return new GitFileDiff(file, "", false);
            }
            CachedCompare cached = cached(dest, base, head, resolved.baseSha, resolved.headSha);
            String range = cached != null ? cached.range : resolved.range();
            String unified = readUnified(dest, range, file);
            boolean truncated = unified.length() > MAX_DIFF_CHARS;
            if (truncated) {
                unified = unified.substring(0, MAX_DIFF_CHARS) + "\n…（该文件 diff 已截断）";
            }
            return new GitFileDiff(file, unified, truncated);
        }
    }

    public static String promptBlock(GitCompareResult diff) {
        StringBuilder builder = new StringBuilder();
        builder.append("仓库目录：").append(diff.rootPath()).append('\n');
        builder.append("基准分支：").append(diff.baseRef()).append(" (").append(diff.baseSha()).append(")\n");
        builder.append("对比分支：").append(diff.headRef()).append(" (").append(diff.headSha()).append(")\n");
        if (diff.fetchedRemote()) {
            builder.append("代码来源：已拉取 origin 上这两个分支的最新提交\n");
        }
        if (diff.commonAncestor()) {
            builder.append("对比方式：按共同祖先到对比分支（与 PR 审查相同）\n");
        } else {
            builder.append("对比方式：未找到共同祖先，按两个分支快照整树对比，变更量可能偏大\n");
        }
        List<GitFileChange> sourceFiles = new ArrayList<>();
        int skipped = 0;
        for (GitFileChange file : diff.files()) {
            if (file.sourceCode()) {
                sourceFiles.add(file);
            } else {
                skipped++;
            }
        }
        builder.append("只审查 Git 管理的源码差异（Java/Kotlin/XML/配置等），不要读 SQL 脚本、文档和资源文件。\n");
        builder.append("源码变更：").append(sourceFiles.size()).append(" 个");
        if (skipped > 0) {
            builder.append("，已忽略 ").append(skipped).append(" 个 SQL/脚本/资源");
        }
        if (diff.truncated()) {
            builder.append("（统一 diff 已截断）");
        }
        builder.append('\n');
        if (!diff.stat().isBlank()) {
            builder.append(diff.stat()).append('\n');
        }
        int shown = 0;
        for (GitFileChange file : sourceFiles) {
            if (shown >= 80) {
                break;
            }
            builder.append(file.status()).append('\t').append(file.path()).append('\n');
            shown++;
        }
        if (sourceFiles.size() > 80) {
            builder.append("…还有 ").append(sourceFiles.size() - 80).append(" 个源码文件，按审查焦点挑选后再分段 code_read。\n");
        }
        if (sourceFiles.size() <= 40 && diff.unifiedDiff() != null && !diff.unifiedDiff().isBlank()) {
            builder.append("统一 diff：\n").append(diff.unifiedDiff());
        } else if (sourceFiles.size() > 40) {
            builder.append("变更量大，不注入整份 unified diff。请按源码文件列表挑选后用 code_read(startLine) 分段阅读。\n");
        } else if (sourceFiles.isEmpty()) {
            builder.append("没有源码差异。\n");
        }
        return builder.toString();
    }

    private ResolvedCompare resolveCompare(Path dest, String base, String head, boolean fetchRemote) {
        boolean needFetch = fetchRemote
                || (!exists(dest, "origin/" + base) && !exists(dest, base))
                || (!exists(dest, "origin/" + head) && !exists(dest, head));
        boolean fetchedRemote = needFetch && fetchLatest(dest, base, head);
        String baseResolved = resolved(dest, base);
        String headResolved = resolved(dest, head);
        ensureHistory(dest, baseResolved, headResolved);
        String mergeBase = mergeBase(dest, baseResolved, headResolved);
        if (mergeBase == null) {
            throw new IllegalStateException("找不到这两个分支的共同祖先，无法只对比有差异的提交。请确认 origin 历史已拉全。");
        }
        boolean empty = sameCommit(dest, baseResolved, headResolved)
                || isAncestor(dest, headResolved, baseResolved);
        if (empty) {
            log.info("Git 对比无独有变更 dest={} base={} head={}", dest, baseResolved, headResolved);
        } else {
            int ahead = commitCount(dest, baseResolved, headResolved);
            int behind = commitCount(dest, headResolved, baseResolved);
            log.info("Git 对比范围 dest={} base={} head={} ahead={} behind={}", dest, baseResolved, headResolved, ahead, behind);
            if (isAncientAncestorDiff(ahead, behind)) {
                String suggest = defaultBranch(dest);
                throw new IllegalArgumentException(
                        "基准分支「" + base + "」是对比分支的早期祖先（其后还有 "
                                + ahead
                                + " 个提交），这样会把整仓列成新增。请把基准改成主干「"
                                + suggest
                                + "」。"
                );
            }
        }
        return new ResolvedCompare(
                baseResolved + "..." + headResolved,
                resolveSha(dest, baseResolved),
                resolveSha(dest, headResolved),
                fetchedRemote,
                true,
                empty
        );
    }

    private void ensureHistory(Path dest, String baseResolved, String headResolved) {
        String shallow = tryRun(dest, List.of("git", "rev-parse", "--is-shallow-repository"), "检查浅克隆");
        if (shallow != null && "true".equalsIgnoreCase(shallow.trim())) {
            log.info("Git 对比前补全浅克隆历史 dest={}", dest);
            tryRun(dest, List.of("git", "fetch", "--unshallow", "--no-tags", "origin"), "补全提交历史");
        }
        if (mergeBase(dest, baseResolved, headResolved) == null) {
            ensureMergeBase(dest, baseResolved, headResolved);
        }
    }

    private boolean isAncestor(Path dest, String ancestor, String descendant) {
        return tryRun(dest, List.of("git", "merge-base", "--is-ancestor", ancestor, descendant), "判断是否已合入基准") != null;
    }

    static boolean isAncientAncestorDiff(int ahead, int behind) {
        return behind == 0 && ahead >= 200;
    }

    private int commitCount(Path dest, String from, String to) {
        String output = tryRun(dest, List.of("git", "rev-list", "--count", from + ".." + to), "统计独有提交");
        if (output == null || output.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(output.trim().lines().findFirst().orElse("0").trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String defaultBranch(Path dest) {
        String head = tryRun(dest, List.of("git", "symbolic-ref", "--short", "refs/remotes/origin/HEAD"), "读取默认主干");
        if (head != null && !head.isBlank()) {
            return stripOrigin(head.trim());
        }
        for (String candidate : List.of("release", "main", "develop")) {
            if (exists(dest, "origin/" + candidate) || exists(dest, candidate)) {
                return candidate;
            }
        }
        return "release";
    }

    private boolean sameCommit(Path dest, String left, String right) {
        String a = tryRun(dest, List.of("git", "rev-parse", left), "解析提交");
        String b = tryRun(dest, List.of("git", "rev-parse", right), "解析提交");
        return a != null && b != null && a.trim().equals(b.trim());
    }

    private String readUnified(Path dest, String range, String path) {
        List<String> command = diffCommand(null, range);
        if (path != null) {
            command.add(command.size() - 1, "-U80"); // 单文件多给上下文，方便左右对照
            command.add("--");
            command.add(path);
        }
        return runCapped(dest, command, path == null ? "读取统一 diff" : "读取文件差异", MAX_DIFF_CHARS * 4);
    }

    private static boolean hasUnified(GitCompareResult result) {
        return result.unifiedDiff() != null && !result.unifiedDiff().isBlank();
    }

    private boolean fetchLatest(Path dest, String base, String head) {
        if (!hasOrigin(dest)) {
            return false;
        }
        String baseSpec = "+refs/heads/" + base + ":refs/remotes/origin/" + base;
        String headSpec = "+refs/heads/" + head + ":refs/remotes/origin/" + head;
        try {
            run(dest, List.of("git", "fetch", "--no-tags", "--update-shallow", "origin", baseSpec, headSpec), "拉取 " + base + " / " + head + " 最新提交");
            return true;
        } catch (RuntimeException exception) {
            boolean haveLocal = hasLocalRef(dest, base) && hasLocalRef(dest, head);
            if (haveLocal) {
                log.warn("Git 拉取远程失败，改用本机已有分支 dest={}：{}", dest, exception.getMessage());
                return false;
            }
            throw new IllegalStateException(fetchFailureMessage(exception.getMessage()), exception);
        }
    }

    private boolean hasLocalRef(Path dest, String ref) {
        return exists(dest, "origin/" + ref) || exists(dest, ref);
    }

    static String fetchFailureMessage(String detail) {
        String text = detail == null ? "" : detail;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("publickey") || text.contains("Permission denied")) {
            return "拉不到 origin 最新代码：SSH 公钥被拒绝。请先在本机对该目录执行 git fetch 确认密钥已加入 Codeup，或用能访问该仓的私钥重新克隆。";
        }
        return "拉不到 origin 最新代码：" + preview(text);
    }

    private String ensureMergeBase(Path dest, String baseResolved, String headResolved) {
        String found = mergeBase(dest, baseResolved, headResolved);
        if (found != null) {
            return found;
        }
        if (!hasOrigin(dest)) {
            return null;
        }
        String base = stripOrigin(baseResolved);
        String head = stripOrigin(headResolved);
        String baseSpec = "+refs/heads/" + base + ":refs/remotes/origin/" + base;
        String headSpec = "+refs/heads/" + head + ":refs/remotes/origin/" + head;
        for (int depth : DEEPEN_STEPS) {
            log.info("Git 浅克隆加深历史 dest={} depth={} base={} head={}", dest, depth, base, head);
            tryRun(dest, List.of("git", "fetch", "--no-tags", "--deepen=" + depth, "origin", baseSpec, headSpec), "加深提交历史 " + depth);
            found = mergeBase(dest, baseResolved, headResolved);
            if (found != null) {
                return found;
            }
        }
        log.info("Git 仍无共同祖先，尝试补全历史 dest={}", dest);
        tryRun(dest, List.of("git", "fetch", "--unshallow", "--no-tags", "origin"), "补全提交历史");
        return mergeBase(dest, baseResolved, headResolved);
    }

    private String mergeBase(Path dest, String baseResolved, String headResolved) {
        String output = tryRun(dest, List.of("git", "merge-base", baseResolved, headResolved), "计算共同祖先");
        if (output == null || output.isBlank()) {
            return null;
        }
        return output.trim().lines().findFirst().orElse("").trim();
    }

    private static String stripOrigin(String ref) {
        return ref.startsWith("origin/") ? ref.substring("origin/".length()) : ref;
    }

    private static List<String> diffCommand(String flag, String range) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        command.add("--no-ext-diff");
        command.add("--ignore-submodules");
        command.add("--no-renames");
        if (flag != null) {
            command.add(flag);
        }
        command.add(range);
        return command;
    }

    private boolean hasOrigin(Path dest) {
        String remotes = tryRun(dest, List.of("git", "remote"), "检查远程");
        return remotes != null && remotes.lines().anyMatch(line -> "origin".equals(line.trim()));
    }

    private String resolveSha(Path dest, String ref) {
        return run(dest, List.of("git", "rev-parse", "--short", ref), "解析提交").trim();
    }

    private String resolved(Path dest, String ref) {
        String remote = "origin/" + ref;
        if (exists(dest, remote)) {
            return remote;
        }
        if (exists(dest, ref)) {
            return ref;
        }
        throw new IllegalArgumentException("找不到分支：" + ref);
    }

    private boolean exists(Path dest, String ref) {
        return tryRun(dest, List.of("git", "rev-parse", "--verify", "--quiet", ref + "^{commit}"), "检查引用") != null;
    }

    private Object lockFor(Path dest) {
        return destLocks.computeIfAbsent(dest.toString(), key -> new Object());
    }

    private CachedCompare cached(Path dest, String base, String head, String baseSha, String headSha) {
        String key = cacheKey(dest, base, head, baseSha, headSha);
        CachedCompare cached = cache.get(key);
        if (cached == null || cached.expiresAt < System.currentTimeMillis()) {
            if (cached != null) {
                cache.remove(key);
            }
            return null;
        }
        return cached;
    }

    private static String cacheKey(Path dest, String base, String head, String baseSha, String headSha) {
        return dest + "\0" + base + "\0" + head + "\0" + baseSha + "\0" + headSha;
    }

    private static Path requireGit(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("仓库目录不能为空");
        }
        Path dest = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(dest.resolve(".git"))) {
            throw new IllegalArgumentException("该仓库不是 Git 工作副本，无法对比分支：" + dest);
        }
        return dest;
    }

    private static String requirePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("请选择要查看的文件");
        }
        String path = value.trim().replace('\\', '/');
        if (path.startsWith("/") || path.startsWith("-") || path.contains("..") || path.contains("\0") || path.length() > 1024) {
            throw new IllegalArgumentException("文件路径不合法：" + value);
        }
        return path;
    }

    private static String requireRef(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("请选择" + label);
        }
        String ref = value.trim();
        if (ref.contains("..") || ref.startsWith("-") || !REF.matcher(ref).matches()) {
            throw new IllegalArgumentException(label + "不合法：" + value);
        }
        return ref;
    }

    private static String normalizeRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim();
        if ("HEAD".equals(name) || "origin/HEAD".equals(name) || name.endsWith("/HEAD")) {
            return null;
        }
        if (name.startsWith("origin/")) {
            name = name.substring("origin/".length());
        }
        return name.isBlank() ? null : name;
    }

    private static List<GitFileChange> parseFiles(String nameStatus) {
        List<GitFileChange> files = new ArrayList<>();
        for (String line : lines(nameStatus)) {
            if (line.startsWith("warning:")) {
                continue;
            }
            String[] parts = line.split("\\t");
            if (parts.length < 2) {
                continue;
            }
            String status = parts[0].trim();
            String path = parts[parts.length - 1].trim();
            if (!path.isBlank()) {
                files.add(new GitFileChange(status, path, isSourceCode(path)));
            }
        }
        return files;
    }

    private static List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> values = new ArrayList<>();
        text.lines().map(String::trim).filter(line -> !line.isBlank()).forEach(values::add);
        return values;
    }

    private String tryRun(Path dest, List<String> command, String failure) {
        try {
            return run(dest, command, failure);
        } catch (RuntimeException exception) {
            log.info("Git {} dest={}：{}", failure, dest, exception.getMessage());
            return null;
        }
    }

    private String run(Path dest, List<String> command, String failure) {
        return runCapped(dest, command, failure, MAX_CAPTURE_BYTES);
    }

    private String runCapped(Path dest, List<String> command, String failure, int maxBytes) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(dest.toFile());
        builder.redirectErrorStream(false);
        builder.environment().put("GIT_PAGER", "cat");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        GitSsh.apply(builder, null);
        try {
            Process process = builder.start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> drain(process.getInputStream(), captured, maxBytes), "git-stdout");
            Thread errReader = new Thread(() -> drain(process.getErrorStream(), errors, 32_000), "git-stderr");
            reader.setDaemon(true);
            errReader.setDaemon(true);
            reader.start();
            errReader.start();
            boolean finished = process.waitFor(Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                joinQuietly(reader);
                joinQuietly(errReader);
                throw new IllegalStateException(failure + "：Git 超时");
            }
            joinQuietly(reader);
            joinQuietly(errReader);
            String output = captured.toString(StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                String detail = errors.size() > 0 ? errors.toString(StandardCharsets.UTF_8) : output;
                throw new IllegalStateException(failure + "：" + preview(detail));
            }
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException(failure + "：本机未安装 git，或无法执行 git 命令", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure + "：被中断", exception);
        }
    }

    private static void drain(InputStream input, ByteArrayOutputStream captured, int maxBytes) {
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = maxBytes - captured.size();
                if (remaining > 0) {
                    captured.write(buffer, 0, Math.min(read, remaining));
                }
            }
        } catch (IOException ignored) {
            // 进程被关掉时输入流会断，这里只负责排空管道。
        }
    }

    private static void joinQuietly(Thread reader) {
        try {
            reader.join(5_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String preview(String output) {
        String compact = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        return compact.length() > 280 ? compact.substring(0, 280) + "…" : compact;
    }

    public record GitBranch(String name, String fullRef, boolean remote) {
    }

    public record GitFileChange(String status, String path, boolean sourceCode) {
    }

    static boolean isSourceCode(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("db/") || normalized.contains("/db/")
                || normalized.startsWith("docs/") || normalized.contains("/docs/")
                || normalized.contains("/target/") || normalized.contains("/node_modules/")
                || normalized.contains("/dist/") || normalized.contains("/build/") || normalized.contains("/out/")) {
            return false;
        }
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.endsWith(".sql") || name.endsWith(".md") || name.endsWith(".lock")
                || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".ico")
                || name.endsWith(".svg") || name.endsWith(".jar") || name.endsWith(".class")
                || name.endsWith(".min.js") || name.equals("package-lock.json")
                || name.equals("pnpm-lock.yaml") || name.equals("yarn.lock")) {
            return false;
        }
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".kts")
                || name.endsWith(".xml") || name.endsWith(".yml") || name.endsWith(".yaml")
                || name.endsWith(".properties") || name.endsWith(".ts") || name.endsWith(".tsx")
                || name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".vue")
                || name.endsWith(".gradle") || name.endsWith(".groovy") || name.equals("pom.xml");
    }

    public record GitFileDiff(String path, String unifiedDiff, boolean truncated) {
    }

    public record GitCompareResult(
            String rootPath,
            String baseRef,
            String headRef,
            String baseSha,
            String headSha,
            String stat,
            List<GitFileChange> files,
            String unifiedDiff,
            boolean truncated,
            boolean fetchedRemote,
            boolean commonAncestor
    ) {
    }

    private record CachedCompare(GitCompareResult result, String range, long expiresAt) {
    }

    private record ResolvedCompare(
            String range,
            String baseSha,
            String headSha,
            boolean fetchedRemote,
            boolean commonAncestor,
            boolean empty
    ) {
    }
}
