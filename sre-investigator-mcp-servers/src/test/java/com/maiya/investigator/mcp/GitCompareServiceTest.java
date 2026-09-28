package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.mcp.GitCompareService.GitCompareResult;
import com.maiya.investigator.mcp.config.RepositoryWorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitCompareServiceTest {
    @Test
    void listsBranchesAndDiffsTwoLocalRefs() throws Exception {
        Path tempDir = Path.of("target/git-compare-it").toAbsolutePath();
        Files.createDirectories(tempDir);
        Path repo = tempDir.resolve("repo");
        if (Files.exists(repo)) {
            Files.walk(repo).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("README.md"), "base\n");
        run(repo, "git", "-c", "init.templateDir=", "init");
        run(repo, "git", "config", "user.email", "test@example.com");
        run(repo, "git", "config", "user.name", "tester");
        run(repo, "git", "add", "README.md");
        run(repo, "git", "commit", "-m", "init");
        run(repo, "git", "branch", "-M", "main");
        run(repo, "git", "checkout", "-b", "feature");
        Files.writeString(repo.resolve("README.md"), "base\nfeature change\n");
        Files.writeString(repo.resolve("App.java"), "class App {}\n");
        Files.createDirectories(repo.resolve("db"));
        Files.writeString(repo.resolve("db/demo_app_base.sql"), "CREATE TABLE t(id int);\n");
        run(repo, "git", "add", "README.md", "App.java", "db/demo_app_base.sql");
        run(repo, "git", "commit", "-m", "feature");

        GitCompareService service = new GitCompareService(new RepositoryWorkspaceProperties());
        List<String> branches = service.listBranches(repo).stream().map(GitCompareService.GitBranch::name).toList();
        assertTrue(branches.contains("main"));
        assertTrue(branches.contains("feature"));

        GitCompareResult diff = service.compare(repo, "main", "feature");
        assertEquals("main", diff.baseRef());
        assertEquals("feature", diff.headRef());
        assertTrue(diff.commonAncestor());
        assertTrue(diff.files().stream().anyMatch(item -> item.path().equals("App.java") && item.sourceCode()));
        assertTrue(diff.files().stream().anyMatch(item -> item.path().equals("db/demo_app_base.sql") && !item.sourceCode()));
        assertTrue(GitCompareService.promptBlock(diff).contains("已忽略"));
        assertTrue(diff.unifiedDiff().contains("feature change"));
        assertTrue(GitCompareService.promptBlock(diff).contains("对比分支：feature"));
        var file = service.fileDiff(repo, "main", "feature", "App.java");
        assertTrue(file.unifiedDiff().contains("class App"));
        assertEquals("App.java", file.path());
    }

    @Test
    void compareFetchesLatestRemoteTips() throws Exception {
        Path tempDir = Path.of("target/git-compare-remote-it").toAbsolutePath();
        if (Files.exists(tempDir)) {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Path origin = tempDir.resolve("origin");
        Path clone = tempDir.resolve("clone");
        Files.createDirectories(origin);
        Files.writeString(origin.resolve("README.md"), "base\n");
        run(origin, "git", "-c", "init.templateDir=", "init");
        run(origin, "git", "config", "user.email", "test@example.com");
        run(origin, "git", "config", "user.name", "tester");
        run(origin, "git", "add", "README.md");
        run(origin, "git", "commit", "-m", "init");
        run(origin, "git", "branch", "-M", "main");
        run(origin, "git", "checkout", "-b", "feature");
        Files.writeString(origin.resolve("README.md"), "base\nold feature\n");
        run(origin, "git", "add", "README.md");
        run(origin, "git", "commit", "-m", "old feature");
        run(origin, "git", "checkout", "main");
        Files.createDirectories(clone.getParent());
        run(tempDir, "git", "clone", origin.toString(), clone.toString());

        run(origin, "git", "checkout", "feature");
        Files.writeString(origin.resolve("README.md"), "base\nlatest remote feature\n");
        run(origin, "git", "add", "README.md");
        run(origin, "git", "commit", "-m", "latest feature");

        GitCompareService service = new GitCompareService(new RepositoryWorkspaceProperties());
        GitCompareResult diff = service.compare(clone, "main", "feature");
        assertTrue(diff.fetchedRemote());
        assertTrue(diff.commonAncestor());
        assertTrue(diff.unifiedDiff().contains("latest remote feature"));
    }

    @Test
    void compareUsesLocalRefsWhenRemoteFetchDenied() throws Exception {
        Path tempDir = Path.of("target/git-compare-denied-it").toAbsolutePath();
        if (Files.exists(tempDir)) {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Path origin = tempDir.resolve("origin");
        Path clone = tempDir.resolve("clone");
        Files.createDirectories(origin);
        Files.writeString(origin.resolve("README.md"), "base\n");
        run(origin, "git", "-c", "init.templateDir=", "init");
        run(origin, "git", "config", "user.email", "test@example.com");
        run(origin, "git", "config", "user.name", "tester");
        run(origin, "git", "add", "README.md");
        run(origin, "git", "commit", "-m", "init");
        run(origin, "git", "branch", "-M", "main");
        run(origin, "git", "checkout", "-b", "feature");
        Files.writeString(origin.resolve("App.java"), "class App {}\n");
        run(origin, "git", "add", "App.java");
        run(origin, "git", "commit", "-m", "feature");
        run(origin, "git", "checkout", "main");
        run(tempDir, "git", "clone", origin.toString(), clone.toString());
        run(clone, "git", "remote", "set-url", "origin", tempDir.resolve("missing-origin.git").toString());

        GitCompareService service = new GitCompareService(new RepositoryWorkspaceProperties());
        GitCompareResult diff = service.compare(clone, "main", "feature");
        assertFalse(diff.fetchedRemote());
        assertTrue(diff.files().stream().anyMatch(item -> item.path().equals("App.java")));
        assertTrue(GitCompareService.fetchFailureMessage("Permission denied (publickey).").contains("SSH 公钥被拒绝"));
    }

    @Test
    void compareDeepensShallowCloneToFindMergeBase() throws Exception {
        Path tempDir = Path.of("target/git-compare-shallow-it").toAbsolutePath();
        if (Files.exists(tempDir)) {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Path origin = tempDir.resolve("origin");
        Path clone = tempDir.resolve("clone");
        Files.createDirectories(origin);
        Files.writeString(origin.resolve("README.md"), "base\n");
        run(origin, "git", "-c", "init.templateDir=", "init");
        run(origin, "git", "config", "user.email", "test@example.com");
        run(origin, "git", "config", "user.name", "tester");
        run(origin, "git", "add", "README.md");
        run(origin, "git", "commit", "-m", "root");
        run(origin, "git", "branch", "-M", "main");
        for (int i = 1; i <= 8; i++) {
            Files.writeString(origin.resolve("README.md"), "base\nmain " + i + "\n");
            run(origin, "git", "add", "README.md");
            run(origin, "git", "commit", "-m", "main " + i);
        }
        run(origin, "git", "checkout", "-b", "feature", "HEAD~8");
        Files.writeString(origin.resolve("App.java"), "class App {}\n");
        run(origin, "git", "add", "App.java");
        run(origin, "git", "commit", "-m", "feature only");
        run(origin, "git", "checkout", "main");
        run(tempDir, "git", "clone", "--depth", "1", origin.toString(), clone.toString());

        GitCompareService service = new GitCompareService(new RepositoryWorkspaceProperties());
        GitCompareResult diff = service.compare(clone, "main", "feature");
        assertTrue(diff.commonAncestor());
        assertTrue(diff.files().stream().anyMatch(item -> item.path().equals("App.java")));
        assertTrue(diff.unifiedDiff().contains("class App"));
        assertTrue(diff.files().stream().noneMatch(item -> item.path().equals("README.md")));
    }

    @Test
    void compareIgnoresBaseOnlyCommitsWhenHeadAlreadyMerged() throws Exception {
        Path tempDir = Path.of("target/git-compare-merged-it").toAbsolutePath();
        if (Files.exists(tempDir)) {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("README.md"), "base\n");
        run(repo, "git", "-c", "init.templateDir=", "init");
        run(repo, "git", "config", "user.email", "test@example.com");
        run(repo, "git", "config", "user.name", "tester");
        run(repo, "git", "add", "README.md");
        run(repo, "git", "commit", "-m", "init");
        run(repo, "git", "branch", "-M", "main");
        run(repo, "git", "checkout", "-b", "feature");
        Files.writeString(repo.resolve("Fix.java"), "class Fix {}\n");
        run(repo, "git", "add", "Fix.java");
        run(repo, "git", "commit", "-m", "feature fix");
        run(repo, "git", "checkout", "main");
        run(repo, "git", "merge", "--no-ff", "-m", "merge feature", "feature");
        for (int i = 1; i <= 5; i++) {
            Files.writeString(repo.resolve("README.md"), "main later " + i + "\n");
            Files.writeString(repo.resolve("Extra" + i + ".java"), "class Extra" + i + " {}\n");
            run(repo, "git", "add", "README.md", "Extra" + i + ".java");
            run(repo, "git", "commit", "-m", "main later " + i);
        }

        GitCompareService service = new GitCompareService(new RepositoryWorkspaceProperties());
        GitCompareResult twoTreeWouldBeHuge = service.compare(repo, "main", "feature");
        assertTrue(twoTreeWouldBeHuge.files().isEmpty(), "已合入基准的对比分支不应列出基准后续提交");
        assertTrue(twoTreeWouldBeHuge.stat().contains("没有相对基准"));
    }

    @Test
    void rejectsAncientMasterAsReviewBase() {
        assertFalse(GitCompareService.isAncientAncestorDiff(15, 0));
        assertFalse(GitCompareService.isAncientAncestorDiff(200, 3));
        assertTrue(GitCompareService.isAncientAncestorDiff(16597, 0));
    }

    @Test
    void compareRejectsFirstCommitMasterAgainstLongHistory() throws Exception {
        Path tempDir = Path.of("target/git-compare-ancient-it").toAbsolutePath();
        if (Files.exists(tempDir)) {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("README.md"), "first\n");
        run(repo, "git", "-c", "init.templateDir=", "init");
        run(repo, "git", "config", "user.email", "test@example.com");
        run(repo, "git", "config", "user.name", "tester");
        run(repo, "git", "add", "README.md");
        run(repo, "git", "commit", "-m", "first commit");
        run(repo, "git", "branch", "-M", "master");
        run(repo, "git", "checkout", "-b", "feature");
        for (int i = 1; i <= 200; i++) {
            run(repo, "git", "commit", "--allow-empty", "-m", "empty " + i);
        }
        GitCompareService service = new GitCompareService(new RepositoryWorkspaceProperties());
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.compare(repo, "master", "feature")
        );
        assertTrue(error.getMessage().contains("早期祖先"));
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
