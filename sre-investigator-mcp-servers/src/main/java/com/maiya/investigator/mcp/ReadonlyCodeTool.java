package com.maiya.investigator.mcp;

import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.infra.audit.AuditTrail;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyCodeTool {
    static final int MAX_FILE_BYTES = 96 * 1024;
    static final int MAX_LINES = 300;
    static final int MAX_MATCHES = 80;
    private static final int MAX_LIST_SCAN = 400;
    private static final int MAX_LIST_ENTRIES = 80;
    private static final int MAX_PREVIEW_FILES = 6;
    private static final Pattern NEIGHBOR_TYPE = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9_]*(?:Mapper|Dao|Repository|ServiceImpl))\\b"
    );
    private static final Pattern SQL_TABLE = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|UPDATE|INTO|TABLE)\\s+`?([A-Za-z][A-Za-z0-9_]+)`?"
    );
    private static final Pattern TABLE_ANNOTATION = Pattern.compile(
            "@(?:Table|TableName)\\s*\\(\\s*\"([A-Za-z0-9_]+)\""
    );
    private static final Pattern SENSITIVE_FILE = Pattern.compile(
            "(^|/)(\\.env(?:\\..*)?|.*\\.(pem|key|p12|pfx)|id_rsa|credentials(?:\\.json)?)(/|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ABSOLUTE_PATH = Pattern.compile("(?<![\\w./-])(/[^\\s`'\"<>|]+)");
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".idea", ".cursor", "node_modules", "target", "build", "dist", "out", "vendor", ".gradle"
    );

    private final ControlPlaneStore controlPlane;
    private final AuditTrail auditTrail;

    public ReadonlyCodeTool(ControlPlaneStore controlPlane, AuditTrail auditTrail) {
        this.controlPlane = controlPlane;
        this.auditTrail = auditTrail;
    }

    public List<CodeWorkspace> workspaces(UUID projectId) {
        return workspaces(projectId, null, null);
    }

    public List<CodeWorkspace> workspacesForRepository(UUID projectId, UUID repositoryId) {
        return workspaces(projectId, repositoryId, null);
    }

    public List<CodeWorkspace> workspacesForName(UUID projectId, String repositoryName) {
        return workspaces(projectId, null, repositoryName);
    }

    private List<CodeWorkspace> workspaces(UUID projectId, UUID repositoryId, String repositoryName) {
        if (projectId == null) {
            return List.of();
        }
        LinkedHashMap<String, CodeWorkspace> byName = new LinkedHashMap<>();
        for (RepositoryConfig repository : controlPlane.repositories(projectId)) {
            if (repositoryId != null && !repository.id().equals(repositoryId)) {
                continue;
            }
            if (repositoryName != null && !repositoryName.isBlank() && !repositoryNameMatches(repository.name(), repositoryName)) {
                continue;
            }
            Path root = usableRoot(repository.rootPath());
            if (root == null) {
                continue;
            }
            String name = uniqueName(repository, byName.keySet());
            byName.put(name, new CodeWorkspace(name, root.toString()));
        }
        return List.copyOf(byName.values());
    }

    public static boolean repositoryNameMatches(String actual, String requested) {
        if (actual == null || requested == null) {
            return false;
        }
        if (actual.equalsIgnoreCase(requested.trim())) {
            return true;
        }
        return normalizeRepositoryName(actual).equals(normalizeRepositoryName(requested));
    }

    static String normalizeRepositoryName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String summarize(List<CodeWorkspace> workspaces) {
        if (workspaces == null || workspaces.isEmpty()) {
            return "当前项目还没有可读取的代码仓。请到「代码仓」页填写本机绝对路径。";
        }
        StringBuilder text = new StringBuilder("可用代码工作区：\n");
        for (CodeWorkspace workspace : workspaces) {
            text.append("- ").append(workspace.name()).append(" → ").append(workspace.rootPath()).append('\n');
        }
        text.append("读取用 code_read(workspace, path)，搜索用 code_search(workspace, query)。");
        text.append("用户给了绝对路径且落在工作区内时，path 可直接传绝对路径，workspace 可留空。");
        return text.toString();
    }

    public String hintForQuestion(List<CodeWorkspace> workspaces, String question) {
        if (workspaces == null || workspaces.isEmpty() || question == null || question.isBlank()) {
            return "";
        }
        List<String> hints = new ArrayList<>();
        Matcher matcher = ABSOLUTE_PATH.matcher(question);
        while (matcher.find()) {
            String raw = trimTrailingPunctuation(matcher.group(1));
            try {
                ResolvedPath resolved = resolve(workspaces, "", raw);
                hints.add(raw + " → workspace=" + resolved.workspace() + " path=" + resolved.relativePath());
            } catch (RuntimeException ignored) {
                // 不是已配置工作区内的路径
            }
        }
        if (hints.isEmpty()) {
            return "";
        }
        return "已识别到工作区内的路径，请优先用 code_read 读取：\n" + String.join("\n", hints);
    }

    public Map<String, Object> read(List<CodeWorkspace> workspaces, String workspace, String path, Integer startLine, Integer maxLines) {
        ResolvedPath resolved = resolve(workspaces, workspace, path);
        auditTrail.record("code.read", resolved.relativePath(), Map.of(
                "workspace", resolved.workspace(),
                "absolute", resolved.absolute().toString()
        ));
        try {
            if (!Files.isRegularFile(resolved.absolute())) {
                throw new IllegalArgumentException("只能读取普通文件：" + resolved.relativePath());
            }
            int from = startLine == null ? 1 : Math.max(1, startLine);
            int limit = Math.min(maxLines == null ? 200 : Math.max(maxLines, 1), MAX_LINES);
            LineWindow window = readWindow(resolved.absolute(), from, limit);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("workspace", resolved.workspace());
            result.put("path", resolved.relativePath());
            result.put("startLine", from);
            result.put("endLine", from - 1 + window.lines().size());
            result.put("totalLines", window.totalLines());
            result.put("truncated", window.truncated());
            result.put("text", String.join("\n", window.lines()));
            if (window.truncated()) {
                result.put("hint", "只返回了其中一段，请用 startLine 继续分段读，不要一次读完整文件");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("读取代码失败：" + exception.getMessage(), exception);
        }
    }

    public Map<String, Object> search(List<CodeWorkspace> workspaces, String workspace, String query, String path, String glob) {
        if (query == null || query.isBlank() || query.length() > 256) {
            throw new IllegalArgumentException("检索词长度必须在 1 到 256 字符之间");
        }
        CodeWorkspace target = workspaceOf(workspaces, workspace);
        Path scope = scopePath(target, path);
        auditTrail.record("code.search", query, Map.of("workspace", target.name(), "path", String.valueOf(path)));
        List<Map<String, Object>> matches = searchWithRipgrep(target, scope, query, glob)
                .orElseGet(() -> searchByWalk(target, scope, query));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspace", target.name());
        result.put("query", query);
        result.put("matches", matches);
        result.put("limited", matches.size() >= MAX_MATCHES);
        return result;
    }

    public List<Map<String, Object>> lookup(List<CodeWorkspace> workspaces, String query) {
        String token = query == null ? "" : query.trim();
        if (token.isBlank() || workspaces == null || workspaces.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Map<String, Object>> hits = new LinkedHashMap<>();
        addPathHit(hits, workspaces, token);
        String fileName = token.contains("/") ? token.substring(token.lastIndexOf('/') + 1) : token;
        for (CodeWorkspace workspace : workspaces) {
            for (Path file : findFilesByName(workspace, fileName)) {
                addHit(hits, workspace, file, "file");
            }
        }
        if (hits.size() < 8 && fileName.matches("[A-Za-z_][\\w$]*")) {
            for (CodeWorkspace workspace : workspaces) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> matches = (List<Map<String, Object>>) search(
                            workspaces, workspace.name(), fileName, ".", "*.{java,kt,xml,yml}"
                    ).get("matches");
                    for (Map<String, Object> match : matches) {
                        String path = String.valueOf(match.get("path"));
                        hits.putIfAbsent(workspace.name() + ":" + path, Map.of(
                                "workspace", workspace.name(),
                                "path", path,
                                "kind", "symbol",
                                "line", match.get("line"),
                                "text", match.get("text")
                        ));
                        if (hits.size() >= 12) {
                            break;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // 单个仓检索失败不影响其他仓
                }
            }
        }
        return List.copyOf(hits.values()).stream().limit(12).toList();
    }

    public List<Map<String, Object>> listEntries(List<CodeWorkspace> workspaces, String workspace, String path, String query) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (workspaces == null || workspaces.isEmpty()) {
            return items;
        }
        CodeWorkspace target = workspaceOf(workspaces, workspace);
        Path root = Path.of(target.rootPath()).toAbsolutePath().normalize();
        Path dir = resolveListDir(root, path);
        if (!Files.isDirectory(dir)) {
            return items;
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        addDirectoryChildren(items, target, root, dir, needle);
        // 当前层不够时，再按文件名在本目录下搜一层，方便 @app/Foo 直接命中深层文件
        if (!needle.isBlank() && needle.length() >= 2 && items.size() < MAX_LIST_ENTRIES && !unsafeFileQuery(query.trim())) {
            addFilenameMatches(items, target, root, dir, query.trim(), MAX_LIST_ENTRIES - items.size());
        }
        return items;
    }

    public String expandMentions(List<CodeWorkspace> workspaces, String question) {
        List<String> mentions = MentionParser.extract(question);
        if (mentions.isEmpty()) {
            return hintForQuestion(workspaces, question);
        }
        StringBuilder text = new StringBuilder(
                "用户使用了 Cursor 风格的 @ 引用。这只是入口，不是排查范围的全部。"
                        + "请沿入口继续找它调用的 Mapper/DAO/XML/缓存 key，表名必须来自代码，禁止按业务名猜测。\n"
                        + "同一符号不要再 code_search。\n"
        );
        int previewed = 0;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        for (String mention : mentions) {
            List<Map<String, Object>> hits = lookup(workspaces, mention);
            text.append("\n@").append(mention);
            if (hits.isEmpty()) {
                text.append("：已配置代码仓中未找到，可用 code_search。\n");
                continue;
            }
            Map<String, Object> best = hits.get(0);
            String pathKey = best.get("workspace") + ":" + best.get("path");
            text.append(" → workspace=").append(best.get("workspace"))
                    .append(" path=").append(best.get("path")).append('\n');
            if (previewed >= MAX_PREVIEW_FILES || !seen.add(pathKey)) {
                continue;
            }
            try {
                Map<String, Object> file = read(
                        workspaces,
                        String.valueOf(best.get("workspace")),
                        String.valueOf(best.get("path")),
                        1,
                        180
                );
                String source = String.valueOf(file.get("text"));
                text.append("已预读：\n```\n").append(source).append("\n```\n");
                previewed++;
                collectTables(source, tables);
                previewed += followNeighbors(text, workspaces, source, mention, seen, tables, MAX_PREVIEW_FILES - previewed);
            } catch (RuntimeException exception) {
                text.append("预读失败：").append(exception.getMessage()).append('\n');
            }
        }
        if (tables.isEmpty()) {
            text.append("\n入口源码里还没有出现表名，必须先读 Mapper/DAO/XML，禁止按业务名猜测表名后直接查库。\n");
        } else {
            text.append("\n从已预读代码中看到的表名（查库必须与此一致，禁止另猜表名）：")
                    .append(String.join(", ", tables))
                    .append('\n');
        }
        return text.toString();
    }

    private int followNeighbors(
            StringBuilder text,
            List<CodeWorkspace> workspaces,
            String source,
            String entry,
            LinkedHashSet<String> seen,
            LinkedHashSet<String> tables,
            int remaining
    ) {
        if (remaining <= 0 || source == null || source.isBlank()) {
            return 0;
        }
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        Matcher matcher = NEIGHBOR_TYPE.matcher(source);
        while (matcher.find()) {
            String symbol = matcher.group(1);
            if (!symbol.equals(entry) && !symbol.equalsIgnoreCase(entry)) {
                symbols.add(symbol);
            }
        }
        int added = 0;
        for (String symbol : symbols) {
            if (added >= remaining) {
                break;
            }
            List<Map<String, Object>> hits = lookup(workspaces, symbol);
            if (hits.isEmpty()) {
                continue;
            }
            Map<String, Object> best = hits.get(0);
            String pathKey = best.get("workspace") + ":" + best.get("path");
            if (!seen.add(pathKey)) {
                continue;
            }
            try {
                Map<String, Object> file = read(
                        workspaces,
                        String.valueOf(best.get("workspace")),
                        String.valueOf(best.get("path")),
                        1,
                        180
                );
                String neighbor = String.valueOf(file.get("text"));
                text.append("沿入口继续预读 @").append(symbol)
                        .append(" → ").append(best.get("path")).append("：\n```\n")
                        .append(neighbor).append("\n```\n");
                collectTables(neighbor, tables);
                added++;
            } catch (RuntimeException ignored) {
                seen.remove(pathKey);
            }
        }
        return added;
    }

    private static void collectTables(String source, LinkedHashSet<String> tables) {
        if (source == null || source.isBlank()) {
            return;
        }
        Matcher annotation = TABLE_ANNOTATION.matcher(source);
        while (annotation.find()) {
            tables.add(annotation.group(1));
        }
        Matcher sql = SQL_TABLE.matcher(source);
        while (sql.find()) {
            String table = sql.group(1);
            if (!Set.of("SELECT", "FROM", "WHERE", "AND", "OR", "SET", "VALUES", "DUAL").contains(table.toUpperCase(Locale.ROOT))) {
                tables.add(table);
            }
        }
    }

    ResolvedPath resolve(List<CodeWorkspace> workspaces, String workspace, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("请提供要读取的文件路径");
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("/")) {
            Path absolute = Path.of(trimmed).normalize();
            for (CodeWorkspace candidate : workspaces == null ? List.<CodeWorkspace>of() : workspaces) {
                Path root = Path.of(candidate.rootPath()).normalize();
                if (absolute.startsWith(root)) {
                    return checked(candidate, root.relativize(absolute).toString());
                }
            }
            throw new IllegalArgumentException("绝对路径不在已配置代码仓内：" + trimmed);
        }
        return checked(workspaceOf(workspaces, workspace), trimmed);
    }

    private ResolvedPath checked(CodeWorkspace workspace, String relative) {
        Path root = Path.of(workspace.rootPath()).toAbsolutePath().normalize();
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("目标路径不在代码仓 allowlist 内");
        }
        String relativePath = root.relativize(candidate).toString().replace('\\', '/');
        if (SENSITIVE_FILE.matcher(relativePath).find() || SENSITIVE_FILE.matcher(candidate.getFileName().toString()).find()) {
            throw new IllegalArgumentException("拒绝读取敏感文件");
        }
        Path actual;
        try {
            actual = candidate.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件不存在：" + relativePath);
        }
        if (!actual.startsWith(root.toAbsolutePath().normalize()) && !actual.equals(root)) {
            try {
                if (!actual.startsWith(root.toRealPath())) {
                    throw new IllegalArgumentException("符号链接目标不在代码仓 allowlist 内");
                }
            } catch (IOException exception) {
                throw new IllegalArgumentException("无法解析代码仓路径");
            }
        }
        return new ResolvedPath(workspace.name(), relativePath, actual);
    }

    private CodeWorkspace workspaceOf(List<CodeWorkspace> workspaces, String name) {
        List<CodeWorkspace> available = workspaces == null ? List.of() : workspaces;
        if (available.isEmpty()) {
            throw new IllegalArgumentException("当前项目没有可读取的代码仓");
        }
        if (name == null || name.isBlank()) {
            if (available.size() == 1) {
                return available.get(0);
            }
            throw new IllegalArgumentException("请指定 workspace，可选：" + names(available));
        }
        String wanted = name.trim();
        return available.stream()
                .filter(item -> item.name().equals(wanted) || item.name().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知代码工作区 " + wanted + "，可选：" + names(available)));
    }

    private Path scopePath(CodeWorkspace workspace, String path) {
        if (path == null || path.isBlank() || ".".equals(path.trim())) {
            return Path.of(workspace.rootPath());
        }
        return checked(workspace, path.trim()).absolute();
    }

    private Optional<List<Map<String, Object>>> searchWithRipgrep(CodeWorkspace workspace, Path scope, String query, String glob) {
        if (glob != null && glob.startsWith("-")) {
            throw new IllegalArgumentException("glob 不能以 - 开头");
        }
        List<String> command = new ArrayList<>(List.of(
                ripgrepCommand(),
                "--line-number",
                "--no-heading",
                "--color=never",
                "--fixed-strings",
                "--max-count",
                String.valueOf(MAX_MATCHES),
                "--glob", "!**/.env*",
                "--glob", "!**/*.{pem,key,p12,pfx}",
                "--glob", "!**/credentials*.json"
        ));
        if (glob != null && !glob.isBlank()) {
            command.add("--glob");
            command.add(glob);
        }
        command.add("--");
        command.add(query);
        command.add(scope.toString());
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() == 1) {
                return Optional.of(List.of());
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            Path root = Path.of(workspace.rootPath());
            List<Map<String, Object>> matches = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && matches.size() < MAX_MATCHES) {
                    int first = line.indexOf(':');
                    int second = first < 0 ? -1 : line.indexOf(':', first + 1);
                    if (first < 0 || second < 0) {
                        continue;
                    }
                    Path file = Path.of(line.substring(0, first));
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("path", root.relativize(file).toString().replace('\\', '/'));
                    match.put("line", Integer.parseInt(line.substring(first + 1, second)));
                    match.put("text", line.substring(second + 1));
                    matches.add(match);
                }
            }
            return Optional.of(matches);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private void addPathHit(LinkedHashMap<String, Map<String, Object>> hits, List<CodeWorkspace> workspaces, String token) {
        try {
            String workspace = "";
            String path = token;
            CodeWorkspace prefixed = workspacePrefix(workspaces, token);
            if (prefixed != null) {
                // @app/src/Foo.java 这种仓名/相对路径，不能把整个 token 当单仓相对路径
                workspace = prefixed.name();
                path = token.substring(prefixed.name().length() + 1);
                if (path.isBlank()) {
                    return;
                }
            } else {
                int colon = token.indexOf(':');
                if (colon > 0 && !token.startsWith("/") && !token.substring(0, colon).contains("/")) {
                    workspace = token.substring(0, colon);
                    path = token.substring(colon + 1);
                }
            }
            if (path.startsWith("/") || path.contains("/") || !workspace.isBlank()) {
                ResolvedPath resolved = resolve(workspaces, workspace, path);
                hits.putIfAbsent(resolved.workspace() + ":" + resolved.relativePath(), Map.of(
                        "workspace", resolved.workspace(),
                        "path", resolved.relativePath(),
                        "kind", "path"
                ));
            }
        } catch (RuntimeException ignored) {
            // 不是可解析路径
        }
    }

    private static CodeWorkspace workspacePrefix(List<CodeWorkspace> workspaces, String token) {
        if (token == null || workspaces == null || !token.contains("/")) {
            return null;
        }
        CodeWorkspace best = null;
        for (CodeWorkspace item : workspaces) {
            String prefix = item.name() + "/";
            if (token.startsWith(prefix) && (best == null || item.name().length() > best.name().length())) {
                best = item;
            }
        }
        return best;
    }

    private void addHit(LinkedHashMap<String, Map<String, Object>> hits, CodeWorkspace workspace, Path file, String kind) {
        Path root = Path.of(workspace.rootPath());
        String relative = root.relativize(file).toString().replace('\\', '/');
        hits.putIfAbsent(workspace.name() + ":" + relative, Map.of(
                "workspace", workspace.name(),
                "path", relative,
                "kind", kind
        ));
    }

    private List<Path> findFilesByName(CodeWorkspace workspace, String name) {
        return findFilesByName(workspace, Path.of(workspace.rootPath()), name, 8);
    }

    private List<Path> findFilesByName(CodeWorkspace workspace, Path scope, String name, int limit) {
        List<Path> files = new ArrayList<>();
        if (name == null || name.isBlank() || scope == null || limit <= 0 || !Files.isDirectory(scope) || unsafeFileQuery(name)) {
            return files;
        }
        List<Path> fromRg = findFilesByNameWithRipgrep(scope, name, limit);
        if (!fromRg.isEmpty()) {
            return fromRg;
        }
        return findFilesByNameByWalk(workspace, scope, name, limit);
    }

    private List<Path> findFilesByNameWithRipgrep(Path scope, String name, int limit) {
        List<Path> files = new ArrayList<>();
        String glob = name.contains(".") ? name : "*" + name + "*";
        List<String> command = new ArrayList<>();
        command.add(ripgrepCommand());
        command.add("--files");
        command.add("--glob");
        command.add(glob);
        command.add("--glob");
        command.add("!**/.git/**");
        command.add(scope.toString());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(Duration.ofSeconds(4).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return files;
            }
            if (process.exitValue() != 0) {
                return files;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && files.size() < limit) {
                    Path file = Path.of(line);
                    if (Files.isRegularFile(file)) {
                        files.add(file);
                    }
                }
            }
            return files;
        } catch (Exception exception) {
            return files;
        }
    }

    /** IDEA / 精简 PATH 下常没有 rg：按文件名包含关系走目录树，保证 @类名 仍能命中深层文件。 */
    private List<Path> findFilesByNameByWalk(CodeWorkspace workspace, Path scope, String name, int limit) {
        List<Path> files = new ArrayList<>();
        Path root = Path.of(workspace.rootPath()).toAbsolutePath().normalize();
        String needle = name.toLowerCase(Locale.ROOT);
        String needleJava = needle.endsWith(".java") ? needle : needle + ".java";
        try (Stream<Path> stream = Files.walk(scope, 16)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !skipped(root, path))
                    .filter(path -> !SENSITIVE_FILE.matcher(path.getFileName().toString()).find())
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return fileName.contains(needle) || fileName.equals(needleJava);
                    })
                    .limit(limit)
                    .forEach(files::add);
        } catch (IOException ignored) {
            // 与 rg 失败一样：交给调用方空列表
        }
        return files;
    }

    static String ripgrepCommand() {
        String configured = System.getenv("RIPGREP_PATH");
        if (configured != null && !configured.isBlank() && Files.isExecutable(Path.of(configured))) {
            return configured;
        }
        for (String candidate : List.of(
                "/opt/homebrew/bin/rg",
                "/usr/local/bin/rg",
                System.getProperty("user.home", "") + "/.cargo/bin/rg"
        )) {
            if (candidate != null && !candidate.isBlank() && Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return "rg";
    }

    private void addDirectoryChildren(
            List<Map<String, Object>> items,
            CodeWorkspace workspace,
            Path root,
            Path dir,
            String needle
    ) {
        List<Path> children = new ArrayList<>();
        int visited = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                visited++;
                if (visited > 2000 || children.size() >= MAX_LIST_SCAN) {
                    break;
                }
                if (Files.isSymbolicLink(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (name.startsWith(".") || SKIP_DIRS.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                boolean directory = Files.isDirectory(child);
                boolean regular = Files.isRegularFile(child);
                if (!directory && !regular) {
                    continue;
                }
                if (regular && SENSITIVE_FILE.matcher(name).find()) {
                    continue;
                }
                if (!needle.isBlank() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                children.add(child);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("列出代码文件失败：" + exception.getMessage(), exception);
        }
        children.sort(Comparator
                .comparing((Path path) -> !Files.isDirectory(path))
                .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        for (Path child : children) {
            if (items.size() >= MAX_LIST_ENTRIES) {
                break;
            }
            addListEntry(items, workspace, root, child, Files.isDirectory(child) ? "dir" : "file");
        }
    }

    private void addFilenameMatches(
            List<Map<String, Object>> items,
            CodeWorkspace workspace,
            Path root,
            Path scope,
            String query,
            int remaining
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            seen.add(workspace.name() + ":" + item.get("path"));
        }
        for (Path file : findFilesByName(workspace, scope, query, remaining)) {
            if (items.size() >= MAX_LIST_ENTRIES) {
                break;
            }
            Path absolute = file.toAbsolutePath().normalize();
            if (!absolute.startsWith(root) || skipped(root, absolute) || SENSITIVE_FILE.matcher(absolute.getFileName().toString()).find()) {
                continue;
            }
            String key = workspace.name() + ":" + root.relativize(absolute).toString().replace('\\', '/');
            if (!seen.add(key)) {
                continue;
            }
            addListEntry(items, workspace, root, absolute, "file");
        }
    }

    private static void addListEntry(List<Map<String, Object>> items, CodeWorkspace workspace, Path root, Path file, String kind) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("workspace", workspace.name());
        item.put("name", file.getFileName().toString());
        item.put("path", root.relativize(file).toString().replace('\\', '/'));
        item.put("kind", kind);
        items.add(item);
    }

    private static Path resolveListDir(Path root, String path) {
        String relative = path == null ? "" : path.trim().replace('\\', '/');
        while (relative.startsWith("./")) {
            relative = relative.substring(2);
        }
        if (relative.startsWith("/")) {
            throw new IllegalArgumentException("路径超出代码仓范围");
        }
        Path dir = relative.isBlank() ? root : root.resolve(relative).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("路径超出代码仓范围");
        }
        return dir;
    }

    private static boolean unsafeFileQuery(String name) {
        return name.startsWith("-")
                || name.contains("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('*') >= 0
                || name.indexOf('?') >= 0
                || name.indexOf('[') >= 0;
    }

    private List<Map<String, Object>> searchByWalk(CodeWorkspace workspace, Path scope, String query) {
        Path root = Path.of(workspace.rootPath());
        List<Map<String, Object>> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(scope, 12)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !skipped(root, path))
                    .filter(path -> !SENSITIVE_FILE.matcher(path.getFileName().toString()).find())
                    .limit(400)
                    .forEach(path -> collectMatches(root, path, query, matches));
        } catch (IOException exception) {
            throw new IllegalStateException("代码检索失败：" + exception.getMessage(), exception);
        }
        return matches;
    }

    private void collectMatches(Path root, Path file, String query, List<Map<String, Object>> matches) {
        if (matches.size() >= MAX_MATCHES) {
            return;
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size() && matches.size() < MAX_MATCHES; index++) {
                if (lines.get(index).contains(query)) {
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("path", root.relativize(file).toString().replace('\\', '/'));
                    match.put("line", index + 1);
                    match.put("text", lines.get(index).trim());
                    matches.add(match);
                }
            }
        } catch (IOException ignored) {
            // 跳过无法作为文本读取的文件
        }
    }

    private static boolean skipped(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Path usableRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return null;
        }
        Path path = Path.of(rootPath.trim());
        if (!path.isAbsolute() || !Files.isDirectory(path)) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }

    private static String uniqueName(RepositoryConfig repository, Set<String> used) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addName(candidates, repository.role());
        addName(candidates, repository.name());
        addName(candidates, "repo");
        for (String candidate : candidates) {
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return "repo-" + repository.id().toString().substring(0, 8);
    }

    private static void addName(Set<String> names, String value) {
        if (value != null && !value.isBlank()) {
            names.add(value.trim());
        }
    }

    private static String names(List<CodeWorkspace> workspaces) {
        return String.join(", ", workspaces.stream().map(CodeWorkspace::name).toList());
    }

    private static LineWindow readWindow(Path file, int from, int limit) throws IOException {
        List<String> selected = new ArrayList<>();
        int lineNo = 0;
        boolean more = false;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo < from) {
                    continue;
                }
                if (selected.size() < limit) {
                    selected.add(line);
                    continue;
                }
                more = true;
                break;
            }
        }
        return new LineWindow(selected, more ? lineNo : lineNo, more);
    }

    private static String trimTrailingPunctuation(String path) {
        return path.replaceAll("[，。,.;；]+$", "");
    }

    public record CodeWorkspace(String name, String rootPath) {
    }

    record ResolvedPath(String workspace, String relativePath, Path absolute) {
    }

    private record LineWindow(List<String> lines, int totalLines, boolean truncated) {
    }
}
