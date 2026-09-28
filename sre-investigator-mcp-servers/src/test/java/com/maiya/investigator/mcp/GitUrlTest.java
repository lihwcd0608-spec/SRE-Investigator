package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitUrlTest {
    @Test
    void extractsNameAndRejectsUnsafeUrls() {
        assertEquals("demo-app-abroad", GitUrl.repositoryName("https://github.com/org/demo-app-abroad.git"));
        assertEquals("demo-admin-broad", GitUrl.repositoryName("git@github.com:org/demo-admin-broad.git"));
        assertEquals(
                "demo-admin-abroad",
                GitUrl.repositoryName("git@git.example.com:codeup/server/netshort/demo-admin-abroad.git")
        );
        assertEquals(
                "git@git.example.com:codeup/server/netshort/demo-admin-abroad.git",
                GitUrl.normalize("git@git.example.com:codeup/server/netshort/demo-admin-abroad.git")
        );
        assertThrows(IllegalArgumentException.class, () -> GitUrl.normalize("https://evil.com/repo.git; rm -rf /"));
        assertThrows(IllegalArgumentException.class, () -> GitUrl.normalize("-u/--upload-pack=touch"));
        assertThrows(IllegalArgumentException.class, () -> GitUrl.normalize("git@host.example.com"));
    }

    @Test
    void embedsHttpsCredentialsWithoutLoggingPlainUrlInName() {
        String withAuth = GitUrl.withCredentials("https://gitlab.example.com/group/app.git", "bot", "s3cret");
        assertTrue(withAuth.startsWith("https://bot:s3cret@gitlab.example.com/"));
        assertEquals("app", GitUrl.repositoryName("https://gitlab.example.com/group/app.git"));
    }
}
