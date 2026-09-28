package com.maiya.investigator.boot.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSourceContentFetcherTest {
    @Test
    void convertsGithubBlobToRaw() {
        URI uri = OpenSourceContentFetcher.toRawUri(
                "https://github.com/modelcontextprotocol/servers/blob/main/README.md"
        );
        assertEquals("raw.githubusercontent.com", uri.getHost());
        assertTrue(uri.getPath().contains("/main/README.md"));
    }

    @Test
    void nextGithubSkillUriSwitchesSkillsPath() {
        URI nested = URI.create("https://raw.githubusercontent.com/obra/superpowers/main/skills/using-superpowers/SKILL.md");
        assertEquals(
                "/obra/superpowers/main/using-superpowers/SKILL.md",
                OpenSourceContentFetcher.nextGithubSkillUri(nested).getPath()
        );
        URI flat = URI.create("https://raw.githubusercontent.com/obra/superpowers/main/using-superpowers/SKILL.md");
        assertEquals(
                "/obra/superpowers/main/skills/using-superpowers/SKILL.md",
                OpenSourceContentFetcher.nextGithubSkillUri(flat).getPath()
        );
        assertNull(OpenSourceContentFetcher.nextGithubSkillUri(URI.create("https://example.com/SKILL.md")));
    }

    @Test
    void prefersGithubApiOverRawMirror() {
        List<URI> uris = OpenSourceContentFetcher.candidates(
                "https://github.com/obra/superpowers/blob/main/skills/using-superpowers/SKILL.md"
        );
        assertEquals("api.github.com", uris.get(0).getHost());
        assertTrue(uris.get(0).getPath().contains("/repos/obra/superpowers/contents/"));
        assertTrue(uris.stream().anyMatch(uri -> "cdn.jsdelivr.net".equals(uri.getHost())));
        OpenSourceContentFetcher.GithubRef ref = OpenSourceContentFetcher.parseGithub(
                "https://github.com/obra/superpowers/blob/main/skills/using-superpowers/SKILL.md"
        );
        assertEquals("obra", ref.owner());
        assertEquals("superpowers", ref.repo());
        assertEquals("main", ref.ref());
        assertEquals("skills/using-superpowers/SKILL.md", ref.path());
    }

    @Test
    void pathVariantsAddSkillsFolderForRootSkill() {
        assertTrue(OpenSourceContentFetcher.pathVariants("SKILL.md").contains("skills/SKILL.md"));
    }

    @Test
    void rejectsNonHttpsAndLocalhost() {
        assertThrows(IllegalArgumentException.class, () -> OpenSourceContentFetcher.assertPublicHttps(
                URI.create("http://example.com/SKILL.md")
        ));
        assertThrows(IllegalArgumentException.class, () -> OpenSourceContentFetcher.assertPublicHttps(
                URI.create("https://localhost/SKILL.md")
        ));
        assertThrows(IllegalArgumentException.class, () -> OpenSourceContentFetcher.toRawUri(""));
    }
}
