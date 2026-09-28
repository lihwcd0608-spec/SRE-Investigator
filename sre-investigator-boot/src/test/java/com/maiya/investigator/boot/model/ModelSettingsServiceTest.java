package com.maiya.investigator.boot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.domain.model.ModelProvider;
import com.maiya.investigator.infra.InMemorySettingStore;
import com.maiya.investigator.infra.security.SecretCipher;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ModelSettingsServiceTest {
    @Test
    void yamlDashscopeIsAvailableWithoutStoreOverride() {
        ModelSettingsService service = service("sk-test", "qwen-plus");

        assertTrue(service.current().available());
        assertEquals(ModelProvider.DASHSCOPE, service.current().provider());
        assertEquals("qwen-plus", service.current().model());
        assertTrue(service.snapshot().available());
        assertTrue(service.snapshot().providers().get("dashscope").configured());
        assertFalse(service.snapshot().providers().get("openai").configured());
    }

    @Test
    void pageOverrideSwitchesProviderImmediately() {
        ModelSettingsService service = service("sk-test", "qwen-plus");

        service.update(new ModelSettingsService.ModelUpdateRequest(
                "openai",
                "deepseek-chat",
                "sk-deepseek",
                "https://api.deepseek.com"
        ));

        assertEquals(ModelProvider.OPENAI, service.current().provider());
        assertEquals("deepseek-chat", service.current().model());
        assertEquals("https://api.deepseek.com", service.current().baseUrl());
        assertTrue(service.current().available());
    }

    @Test
    void blankApiKeyDoesNotClearExistingKey() {
        ModelSettingsService service = service("sk-keep", "qwen-plus");
        service.update(new ModelSettingsService.ModelUpdateRequest("dashscope", "qwen-max", "********", null));

        assertEquals("qwen-max", service.current().model());
        assertEquals("sk-keep", service.current().apiKey());
    }

    @Test
    void catalogBecomesCurrentModelWithoutYamlDefault() {
        ModelSettingsService service = service("sk-test", "qwen-plus");
        service.update(new ModelSettingsService.ModelUpdateRequest(
                "openai",
                null,
                "sk-deepseek",
                "https://api.deepseek.com",
                java.util.List.of("deepseek-v4-pro", "deepseek-v4-flash")
        ));

        assertEquals("deepseek-v4-pro", service.current().model());
        assertEquals(
                java.util.List.of("deepseek-v4-pro", "deepseek-v4-flash"),
                service.snapshot().providers().get("openai").models()
        );
        assertFalse(service.snapshot().providers().get("openai").models().contains("gpt-4o-mini"));
    }

    @Test
    void clearProviderRemovesStoredOverride() {
        ModelSettingsService service = service("sk-test", "qwen-plus");
        service.update(new ModelSettingsService.ModelUpdateRequest(
                "openai",
                "deepseek-chat",
                "sk-deepseek",
                "https://api.deepseek.com"
        ));

        service.clearProvider("openai");

        assertFalse(service.snapshot().providers().get("openai").configured());
        assertEquals(ModelProvider.DASHSCOPE, service.current().provider());
    }

    @Test
    void rememberCatalogKeepsChoicesInDropdownWithoutImportingAll() {
        ModelSettingsService service = service("sk-test", "qwen-plus");
        service.rememberCatalog(
                new ModelSettingsService.ModelUpdateRequest("openai", null, "sk-deepseek", "https://api.deepseek.com"),
                java.util.List.of("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-chat"),
                "deepseek-v4-flash"
        );

        var openai = service.snapshot().providers().get("openai");
        assertEquals("deepseek-v4-flash", openai.model());
        assertEquals(java.util.List.of("deepseek-v4-flash"), openai.models());
        assertEquals(
                java.util.List.of("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-chat"),
                openai.available()
        );
    }

    @Test
    void largeLegacyCatalogBecomesDropdownChoices() {
        ModelSettingsService service = service("sk-test", "qwen-plus");
        java.util.List<String> dumped = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> "model-" + index)
                .toList();
        service.update(new ModelSettingsService.ModelUpdateRequest(
                "openai",
                "model-3",
                "sk-deepseek",
                "https://api.deepseek.com",
                dumped
        ));

        var openai = service.snapshot().providers().get("openai");
        assertEquals("model-3", openai.model());
        assertEquals(java.util.List.of("model-3"), openai.models());
        assertEquals(dumped, openai.available());
    }

    @Test
    void removeCatalogModelKeepsRemaining() {
        ModelSettingsService service = service("sk-test", "qwen-plus");
        service.update(new ModelSettingsService.ModelUpdateRequest(
                "openai",
                "deepseek-v4-pro",
                "sk-deepseek",
                "https://api.deepseek.com",
                java.util.List.of("deepseek-v4-pro", "deepseek-v4-flash")
        ));

        service.removeCatalogModel("openai", "deepseek-v4-pro");

        assertEquals("deepseek-v4-flash", service.snapshot().providers().get("openai").model());
        assertEquals(
                java.util.List.of("deepseek-v4-flash"),
                service.snapshot().providers().get("openai").models()
        );
    }

    private static ModelSettingsService service(String apiKey, String model) {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("dashscope");
        properties.getDashscope().setApiKey(apiKey);
        properties.getDashscope().setModel(model);
        String masterKey = Base64.getEncoder().encodeToString(new byte[32]);
        return new ModelSettingsService(
                properties,
                new InMemorySettingStore(),
                new SecretCipher(masterKey),
                new ObjectMapper(),
                "",
                ""
        );
    }
}
