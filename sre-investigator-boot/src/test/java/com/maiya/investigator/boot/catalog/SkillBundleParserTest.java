package com.maiya.investigator.boot.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class SkillBundleParserTest {
    @Test
    void parsesWrappedFolderAndFrontMatter() {
        byte[] zip = zip(
                "http-5xx/SKILL.md", """
                        ---
                        name: HTTP 5xx
                        description: 查网关和超时
                        ---
                        # 入口
                        """,
                "http-5xx/references/playbook.md", "先看网关"
        );
        SkillBundleParser.ParsedBundle parsed = SkillBundleParser.parseZip(zip);
        assertEquals("HTTP 5xx", parsed.name());
        assertEquals("查网关和超时", parsed.summary());
        assertEquals(2, parsed.files().size());
        assertEquals("SKILL.md", parsed.files().get(0).path());
        assertEquals("references/playbook.md", parsed.files().get(1).path());
    }

    @Test
    void rejectsNestedEntry() {
        byte[] zip = zip("docs/guide/SKILL.md", "# 入口", "docs/guide/notes.md", "备注");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> SkillBundleParser.parseZip(zip));
        assertTrue(error.getMessage().contains("根目录"));
    }

    @Test
    void rejectsMissingEntry() {
        byte[] zip = zip("notes.md", "没有入口");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> SkillBundleParser.parseZip(zip));
        assertTrue(error.getMessage().contains("SKILL.md"));
    }

    @Test
    void rejectsZipSlip() {
        byte[] zip = zip("../secret.md", "x", "SKILL.md", "# ok");
        assertThrows(IllegalArgumentException.class, () -> SkillBundleParser.parseZip(zip));
    }

    @Test
    void rejectsSecretFile() {
        List<SkillBundleParser.UploadedFile> files = List.of(
                new SkillBundleParser.UploadedFile("SKILL.md", "# ok".getBytes(StandardCharsets.UTF_8)),
                new SkillBundleParser.UploadedFile(".env", "SECRET=1".getBytes(StandardCharsets.UTF_8))
        );
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> SkillBundleParser.parseFiles(files));
        assertTrue(error.getMessage().contains("密钥"));
    }

    @Test
    void rejectsTooManyFiles() {
        SkillBundleParser.UploadedFile[] files = new SkillBundleParser.UploadedFile[21];
        files[0] = new SkillBundleParser.UploadedFile("SKILL.md", "# ok".getBytes(StandardCharsets.UTF_8));
        for (int i = 1; i < 21; i++) {
            files[i] = new SkillBundleParser.UploadedFile("notes-" + i + ".md", "x".getBytes(StandardCharsets.UTF_8));
        }
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SkillBundleParser.parseFiles(List.of(files))
        );
        assertTrue(error.getMessage().contains("最多"));
    }

    private static byte[] zip(String... pathAndContent) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (int i = 0; i < pathAndContent.length; i += 2) {
                    zip.putNextEntry(new ZipEntry(pathAndContent[i]));
                    zip.write(pathAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
