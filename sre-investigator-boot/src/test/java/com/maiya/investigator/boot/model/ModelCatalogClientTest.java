package com.maiya.investigator.boot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelCatalogClientTest {
    @Test
    void parsesOpenAiCompatibleCatalog() {
        List<String> models = ModelCatalogClient.parseOpenAi("""
                {"data":[
                  {"id":"deepseek-v4-pro"},
                  {"id":"deepseek-v4-flash"},
                  {"id":"deepseek-v4-flash-vision-exp"},
                  {"id":"text-embedding-3-small"}
                ]}
                """);

        assertEquals(List.of("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp"), models);
        assertFalse(models.contains("text-embedding-3-small"));
    }

    @Test
    void ignoresYamlDefaultWhenCatalogExists() {
        String probe = ModelCatalogClient.pickProbeModel(
                "gpt-4o-mini",
                List.of("deepseek-v4-pro", "deepseek-v4-flash"),
                "gpt-4o-mini"
        );
        assertEquals("deepseek-v4-pro", probe);
    }

    @Test
    void keepsUserTypedModel() {
        assertEquals(
                "deepseek-v4-flash",
                ModelCatalogClient.pickProbeModel(
                        "deepseek-v4-flash",
                        List.of("deepseek-v4-pro", "deepseek-v4-flash"),
                        "gpt-4o-mini"
                )
        );
        assertTrue(ModelCatalogClient.likelyChat("deepseek-v4-flash-vision-exp"));
    }
}
