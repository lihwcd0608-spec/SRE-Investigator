package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.mcp.config.RepositoryWorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitRepositoryClonerTest {
    @Test
    void clonesIntoUserChosenDirectory() throws Exception {
        Path tempDir = Path.of("target/git-cloner-it").toAbsolutePath();
        Files.createDirectories(tempDir);
        Path source = tempDir.resolve("source");
        if (Files.exists(source)) {
            Files.walk(source).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Files.createDirectories(source);
        Files.writeString(source.resolve("README.md"), "hello investigator");
        run(source, "git", "-c", "init.templateDir=", "init");
        run(source, "git", "config", "user.email", "test@example.com");
        run(source, "git", "config", "user.name", "tester");
        run(source, "git", "add", "README.md");
        run(source, "git", "commit", "-m", "init");

        Path dest = tempDir.resolve("chosen/demo-app-abroad");
        if (Files.exists(dest.getParent())) {
            Files.walk(dest.getParent()).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Files.createDirectories(dest.getParent());
        GitRepositoryCloner cloner = new GitRepositoryCloner(new RepositoryWorkspaceProperties());
        Path cloned = cloner.checkout(dest.toString(), source.toUri().toString(), null, null, null);

        assertEquals(dest, cloned);
        assertTrue(Files.isRegularFile(cloned.resolve("README.md")));
        assertEquals("hello investigator", Files.readString(cloned.resolve("README.md")).trim());
    }

    private static void run(Path cwd, String... command) throws Exception {
        Process process = new ProcessBuilder(List.of(command))
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git 准备失败：" + new String(process.getInputStream().readAllBytes()));
        }
    }
}
