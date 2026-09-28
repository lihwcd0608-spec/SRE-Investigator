package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.infra.persistence.AuditEventJpaRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlyCodeToolTest {
    @TempDir
    Path tempDir;

    @Test
    void readsRelativeAndAbsolutePathsWithinConfiguredRepo() throws Exception {
        Path file = tempDir.resolve("src/Foo.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Foo {\n  void bar() {}\n}\n");
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        List<ReadonlyCodeTool.CodeWorkspace> workspaces = tool.workspaces(projectId);

        Map<String, Object> relative = tool.read(workspaces, "app", "src/Foo.java", 1, 20);
        assertEquals("class Foo {", ((String) relative.get("text")).lines().findFirst().orElseThrow());

        Map<String, Object> absolute = tool.read(workspaces, "", file.toAbsolutePath().toString(), null, null);
        assertEquals("app", absolute.get("workspace"));
        assertEquals("src/Foo.java", absolute.get("path"));
        assertTrue(tool.hintForQuestion(workspaces, "读取 " + file.toAbsolutePath() + " 梳理逻辑").contains("workspace=app"));
    }

    @Test
    void rejectsEscapeAndSensitiveFiles() throws Exception {
        Files.writeString(tempDir.resolve(".env"), "SECRET=1");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Ok.java"), "ok");
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        List<ReadonlyCodeTool.CodeWorkspace> workspaces = tool.workspaces(projectId);

        assertThrows(IllegalArgumentException.class, () -> tool.read(workspaces, "app", "../outside", 1, 10));
        assertThrows(IllegalArgumentException.class, () -> tool.read(workspaces, "app", ".env", 1, 10));
        assertEquals("ok", tool.read(workspaces, "app", "src/Ok.java", 1, 10).get("text"));
    }

    @Test
    void expandsAtMentionToExistingClass() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/ReserveDramaInsertProcessor.java"), "public class ReserveDramaInsertProcessor {}");
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        String expanded = tool.expandMentions(tool.workspaces(projectId), "@ReserveDramaInsertProcessor 没生效");
        assertTrue(expanded.contains("ReserveDramaInsertProcessor"));
        assertTrue(expanded.contains("已预读") || expanded.contains("path="));
    }

    @Test
    void followsMapperNeighborFromEntryClass() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(
                tempDir.resolve("src/ReserveDramaInsertProcessor.java"),
                """
                        public class ReserveDramaInsertProcessor {
                          private final ReserveDramaMapper reserveDramaMapper;
                        }
                        """
        );
        Files.writeString(
                tempDir.resolve("src/ReserveDramaMapper.java"),
                """
                        public interface ReserveDramaMapper {
                          @Select("SELECT * FROM reserve_drama_insert WHERE id = #{id}")
                          Object find(Long id);
                        }
                        """
        );
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        String expanded = tool.expandMentions(tool.workspaces(projectId), "@ReserveDramaInsertProcessor 没生效");
        assertTrue(expanded.contains("ReserveDramaMapper"));
        assertTrue(expanded.contains("reserve_drama_insert"));
        assertTrue(expanded.contains("禁止另猜表名") || expanded.contains("从表名来自"));
    }

    @Test
    void readsLargeFileByWindowInsteadOfRejecting() throws Exception {
        StringBuilder huge = new StringBuilder();
        for (int i = 1; i <= 20_000; i++) {
            huge.append("line-").append(i).append('\n');
        }
        Files.writeString(tempDir.resolve("dump.sql"), huge.toString());
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        List<ReadonlyCodeTool.CodeWorkspace> workspaces = tool.workspaces(projectId);

        Map<String, Object> first = tool.read(workspaces, "app", "dump.sql", null, 20);
        assertEquals(true, first.get("truncated"));
        assertTrue(String.valueOf(first.get("text")).startsWith("line-1"));
        assertTrue(String.valueOf(first.get("hint")).contains("分段"));

        Map<String, Object> later = tool.read(workspaces, "app", "dump.sql", 7900, 10);
        assertTrue(String.valueOf(later.get("text")).contains("line-7900"));
        assertEquals(7909, later.get("endLine"));
    }

    @Test
    void searchesConfiguredWorkspace() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/RelatedHotDramaRecommendServiceImpl.java"), "public class RelatedHotDramaRecommendServiceImpl {}");
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        List<ReadonlyCodeTool.CodeWorkspace> workspaces = tool.workspaces(projectId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) tool.search(
                workspaces, "app", "RelatedHotDramaRecommendServiceImpl", "src", "*.java"
        ).get("matches");
        assertTrue(matches.stream().anyMatch(item -> String.valueOf(item.get("path")).contains("RelatedHotDrama")));
    }

    @Test
    void listsImmediateChildrenAndSkipsBuildDirs() throws Exception {
        Files.createDirectories(tempDir.resolve("src/main"));
        Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(tempDir.resolve("src/Foo.java"), "class Foo {}");
        Files.writeString(tempDir.resolve("src/main/Bar.java"), "class Bar {}");
        Files.writeString(tempDir.resolve("target/Skip.java"), "class Skip {}");
        Files.writeString(tempDir.resolve("README.md"), "readme");
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        List<ReadonlyCodeTool.CodeWorkspace> workspaces = tool.workspaces(projectId);

        List<Map<String, Object>> root = tool.listEntries(workspaces, "app", "", "");
        assertTrue(root.stream().anyMatch(item -> "src".equals(item.get("name")) && "dir".equals(item.get("kind"))));
        assertTrue(root.stream().anyMatch(item -> "README.md".equals(item.get("name")) && "file".equals(item.get("kind"))));
        assertTrue(root.stream().noneMatch(item -> "target".equals(item.get("name"))));

        List<Map<String, Object>> src = tool.listEntries(workspaces, "app", "src", "");
        assertTrue(src.stream().anyMatch(item -> "Foo.java".equals(item.get("name"))));
        assertTrue(src.stream().anyMatch(item -> "main".equals(item.get("name")) && "dir".equals(item.get("kind"))));

        List<Map<String, Object>> filtered = tool.listEntries(workspaces, "app", "", "Foo");
        assertTrue(filtered.stream().anyMatch(item -> "src/Foo.java".equals(item.get("path"))));
    }

    @Test
    void listsDeepClassByNameWithoutRelyingOnShallowDirOnly() throws Exception {
        Path deep = tempDir.resolve("demo-app-video/src/main/java/com/maiyawx/controller");
        Files.createDirectories(deep);
        Files.writeString(deep.resolve("VmsTheaterController.java"), "public class VmsTheaterController {}");
        Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(tempDir.resolve("docs/readme.md"), "no");
        UUID projectId = UUID.randomUUID();
        ReadonlyCodeTool tool = tool(projectId, tempDir);
        List<Map<String, Object>> hits = tool.listEntries(tool.workspaces(projectId), "app", "", "VmsTheaterController");
        assertTrue(hits.stream().anyMatch(item ->
                String.valueOf(item.get("path")).endsWith("VmsTheaterController.java")
        ), "应能按类名命中深层 Java 文件：" + hits);
    }

    @Test
    void lookupResolvesWorkspaceSlashPath(@TempDir Path adminDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Foo.java"), "class Foo {}");
        Files.createDirectories(adminDir.resolve("src"));
        Files.writeString(adminDir.resolve("src/Foo.java"), "class AdminFoo {}");
        UUID projectId = UUID.randomUUID();
        InMemoryControlPlaneStore control = new InMemoryControlPlaneStore();
        control.saveRepository(RepositoryConfig.create(projectId, "demo-app-broad", "app", tempDir.toString()));
        control.saveRepository(RepositoryConfig.create(projectId, "demo-admin-broad", "admin", adminDir.toString()));
        ReadonlyCodeTool tool = new ReadonlyCodeTool(control, audit());
        List<Map<String, Object>> hits = tool.lookup(tool.workspaces(projectId), "app/src/Foo.java");
        assertEquals("path", hits.get(0).get("kind"));
        assertEquals("app", hits.get(0).get("workspace"));
        assertEquals("src/Foo.java", hits.get(0).get("path"));
    }

    private static ReadonlyCodeTool tool(UUID projectId, Path root) {
        InMemoryControlPlaneStore control = new InMemoryControlPlaneStore();
        control.saveRepository(RepositoryConfig.create(projectId, "demo-app-broad", "app", root.toString()));
        return new ReadonlyCodeTool(control, audit());
    }

    private static AuditTrail audit() {
        return new AuditTrail(new ObjectProvider<>() {
            @Override
            public AuditEventJpaRepository getObject() {
                return null;
            }

            @Override
            public AuditEventJpaRepository getObject(Object... args) {
                return null;
            }

            @Override
            public AuditEventJpaRepository getIfAvailable() {
                return null;
            }

            @Override
            public AuditEventJpaRepository getIfUnique() {
                return null;
            }

            @Override
            public Iterator<AuditEventJpaRepository> iterator() {
                return List.<AuditEventJpaRepository>of().iterator();
            }
        });
    }
}
