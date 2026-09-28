package com.maiya.investigator.boot.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SkillBundleParser {
    public static final int MAX_FILES = 20;
    public static final int MAX_FILE_BYTES = 64 * 1024;
    public static final int MAX_TOTAL_BYTES = 256 * 1024;
    public static final String ENTRY_NAME = "SKILL.md";

    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "txt", "yml", "yaml", "json", "csv");
    private static final Set<String> SECRET_NAMES = Set.of(
            ".env",
            ".env.local",
            ".env.production",
            "id_rsa",
            "id_dsa",
            "id_ecdsa",
            "id_ed25519",
            "credentials",
            "credentials.json",
            "secrets.yml",
            "secrets.yaml"
    );

    private SkillBundleParser() {
    }

    public record BundleFile(String path, String content) {
    }

    public record ParsedBundle(String name, String summary, List<BundleFile> files) {
        public String entryContent() {
            for (BundleFile file : files) {
                if (ENTRY_NAME.equalsIgnoreCase(file.path())) {
                    return file.content();
                }
            }
            return "";
        }
    }

    public record UploadedFile(String path, byte[] bytes) {
    }

    public static ParsedBundle parseZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("请上传 Skill 包 zip");
        }
        List<UploadedFile> files = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                files.add(new UploadedFile(entry.getName(), readLimited(zip, entry.getName())));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 zip：" + exception.getMessage());
        }
        return parseFiles(files);
    }

    public static ParsedBundle parseFiles(List<UploadedFile> uploaded) {
        if (uploaded == null || uploaded.isEmpty()) {
            throw new IllegalArgumentException("请选择含 SKILL.md 的文件夹或 zip");
        }
        Map<String, String> texts = new LinkedHashMap<>();
        int total = 0;
        for (UploadedFile file : uploaded) {
            rejectSecret(file.path());
            String path = normalizePath(file.path());
            if (path == null) {
                continue;
            }
            rejectExtension(path);
            byte[] bytes = file.bytes() == null ? new byte[0] : file.bytes();
            rejectBinary(bytes, path);
            if (bytes.length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("文件过大：" + path + "，单文件最多 64KB");
            }
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Skill 包合计超过 256KB");
            }
            if (texts.put(path, new String(bytes, StandardCharsets.UTF_8)) != null) {
                throw new IllegalArgumentException("重复文件：" + path);
            }
        }
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("包里没有可用的文本文件");
        }
        if (texts.size() > MAX_FILES) {
            throw new IllegalArgumentException("Skill 包最多 " + MAX_FILES + " 个文件");
        }
        String folder = commonRoot(texts.keySet());
        Map<String, String> stripped = stripSingleRoot(texts);
        String entry = findEntry(stripped);
        FrontMatter meta = parseFrontMatter(stripped.get(entry));
        List<BundleFile> files = new ArrayList<>();
        for (Map.Entry<String, String> item : stripped.entrySet()) {
            files.add(new BundleFile(item.getKey(), item.getValue()));
        }
        files.sort(Comparator.comparing((BundleFile file) -> ENTRY_NAME.equalsIgnoreCase(file.path()) ? 0 : 1)
                .thenComparing(BundleFile::path));
        String name = blankToNull(meta.name());
        if (name == null && folder != null) {
            name = folder.substring(0, folder.length() - 1);
        }
        if (name == null) {
            name = "导入的 Skill";
        }
        return new ParsedBundle(name, meta.description(), files);
    }

    private static String findEntry(Map<String, String> files) {
        List<String> entries = new ArrayList<>();
        for (String path : files.keySet()) {
            if (ENTRY_NAME.equalsIgnoreCase(fileName(path))) {
                entries.add(path);
            }
        }
        if (entries.size() != 1) {
            throw new IllegalArgumentException(entries.isEmpty()
                    ? "包里必须有一份 SKILL.md"
                    : "包里只能有一份 SKILL.md");
        }
        if (!ENTRY_NAME.equalsIgnoreCase(entries.get(0))) {
            throw new IllegalArgumentException("SKILL.md 必须在包根目录（或只包一层目录）");
        }
        return entries.get(0);
    }

    private static Map<String, String> stripSingleRoot(Map<String, String> files) {
        String prefix = commonRoot(files.keySet());
        if (prefix == null) {
            return files;
        }
        Map<String, String> stripped = new LinkedHashMap<>();
        for (Map.Entry<String, String> item : files.entrySet()) {
            stripped.put(item.getKey().substring(prefix.length()), item.getValue());
        }
        return stripped;
    }

    private static String commonRoot(Iterable<String> paths) {
        String first = null;
        for (String path : paths) {
            int slash = path.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            String root = path.substring(0, slash + 1);
            if (first == null) {
                first = root;
            } else if (!first.equals(root)) {
                return null;
            }
        }
        return first;
    }

    static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String path = raw.replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.startsWith("/") || path.contains(":/") || path.contains("..")) {
            throw new IllegalArgumentException("非法路径：" + raw);
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IllegalArgumentException("非法路径：" + raw);
            }
            parts.add(part);
        }
        if (parts.isEmpty() || parts.stream().anyMatch(part -> part.startsWith("."))) {
            return null;
        }
        return String.join("/", parts);
    }

    private static void rejectSecret(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        String name = fileName(path.replace('\\', '/')).toLowerCase(Locale.ROOT);
        if (SECRET_NAMES.contains(name) || name.endsWith(".pem") || name.endsWith(".key")
                || name.contains("secret") || name.contains("password")) {
            throw new IllegalArgumentException("拒绝密钥类文件：" + path);
        }
    }

    private static void rejectBinary(byte[] bytes, String path) {
        for (byte item : bytes) {
            if (item == 0) {
                throw new IllegalArgumentException("拒绝二进制文件：" + path);
            }
        }
    }

    private static void rejectExtension(String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("只接受文本文件：" + path);
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!TEXT_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型：" + path);
        }
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    static FrontMatter parseFrontMatter(String content) {
        if (content == null) {
            return new FrontMatter(null, null);
        }
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return new FrontMatter(null, null);
        }
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            return new FrontMatter(null, null);
        }
        String name = null;
        String description = null;
        for (String line : normalized.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = stripQuotes(line.substring(colon + 1).trim());
            if ("name".equals(key) && !value.isBlank()) {
                name = value;
            } else if ("description".equals(key) && !value.isBlank()) {
                description = value;
            }
        }
        return new FrontMatter(name, description);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static byte[] readLimited(ZipInputStream zip, String name) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        int total = 0;
        while ((read = zip.read(chunk)) >= 0) {
            total += read;
            if (total > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("文件过大：" + name + "，单文件最多 64KB");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    record FrontMatter(String name, String description) {
    }
}
