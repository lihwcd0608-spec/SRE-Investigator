package com.maiya.investigator.boot.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.OpenSourceSkillSearchHit;
import java.net.http.HttpTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSourceSkillSearchServiceTest {
    private final OpenSourceSkillSearchService service = new OpenSourceSkillSearchService(new ObjectMapper());

    @Test
    void rejectsShortQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.search("a"));
        assertThrows(IllegalArgumentException.class, () -> service.search(" "));
    }

    @Test
    void parsesSkillsShAndDropsNonGithubSource() throws Exception {
        List<OpenSourceSkillSearchHit> hits = service.parseSkillsSh("""
                {
                  "skills": [
                    {
                      "id": "obra/superpowers/using-superpowers",
                      "skillId": "using-superpowers",
                      "name": "using-superpowers",
                      "source": "obra/superpowers",
                      "installs": 42
                    },
                    {
                      "skillId": "ignored",
                      "name": "ignored",
                      "source": "smithery.ai"
                    }
                  ]
                }
                """);
        assertEquals(1, hits.size());
        OpenSourceSkillSearchHit hit = hits.get(0);
        assertEquals("skills.sh", hit.registry());
        assertEquals("obra/superpowers", hit.packageName());
        assertEquals(
                "https://github.com/obra/superpowers/blob/main/skills/using-superpowers/SKILL.md",
                hit.sourceUrl()
        );
        assertEquals(42, hit.installs());
    }

    @Test
    void parsesClawhubFileUrl() throws Exception {
        List<OpenSourceSkillSearchHit> hits = service.parseClawhub("""
                {
                  "results": [
                    {
                      "slug": "code-review",
                      "displayName": "Code Review",
                      "summary": "Review pull requests",
                      "ownerHandle": "alice",
                      "downloads": 9
                    }
                  ]
                }
                """);
        assertEquals(1, hits.size());
        assertEquals("clawhub", hits.get(0).registry());
        assertEquals("alice/code-review", hits.get(0).packageName());
        assertEquals("https://clawhub.ai/api/v1/skills/code-review/file?path=SKILL.md", hits.get(0).sourceUrl());
        assertEquals(9, hits.get(0).installs());
    }

    @Test
    void parsesGithubRepoWithDefaultBranch() throws Exception {
        List<OpenSourceSkillSearchHit> hits = service.parseGithub("""
                {
                  "items": [
                    {
                      "full_name": "obra/superpowers",
                      "name": "superpowers",
                      "description": "agent skills",
                      "default_branch": "develop",
                      "stargazers_count": 10
                    }
                  ]
                }
                """);
        assertEquals(1, hits.size());
        assertEquals("github", hits.get(0).registry());
        assertEquals("https://github.com/obra/superpowers/blob/develop/SKILL.md", hits.get(0).sourceUrl());
        assertEquals("develop", hits.get(0).gitRef());
        assertTrue(hits.get(0).summary().contains("agent"));
    }

    @Test
    void mapsTimeoutToChineseWarning() {
        assertEquals("skills.sh 超时，已跳过", OpenSourceSkillSearchService.warningFor(
                "skills.sh",
                new HttpTimeoutException("request timed out")
        ));
        assertEquals("clawhub 暂时连不上，已跳过", OpenSourceSkillSearchService.warningFor(
                "clawhub",
                new IllegalStateException("HTTP 503")
        ));
    }
}
