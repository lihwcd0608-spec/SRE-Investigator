package com.maiya.investigator.boot.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.domain.model.ModelProvider;
import com.maiya.investigator.domain.model.ModelSettingsResolver;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.domain.model.SettingStore;
import com.maiya.investigator.infra.security.SecretCipher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ModelSettingsService implements ModelSettingsResolver {
    static final String OVERRIDE_KEY = "model.override";
    static final String MASKED_KEY = "********";
    static final int AUTO_IMPORT_LIMIT = 8;

    private final ModelProperties defaults;
    private final SettingStore store;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;
    private final String legacyDashscopeKey;
    private final String legacyDashscopeModel;

    public ModelSettingsService(
            ModelProperties defaults,
            SettingStore store,
            SecretCipher cipher,
            ObjectMapper mapper,
            @Value("${spring.ai.dashscope.api-key:}") String legacyDashscopeKey,
            @Value("${spring.ai.dashscope.chat.options.model:}") String legacyDashscopeModel
    ) {
        this.defaults = defaults;
        this.store = store;
        this.cipher = cipher;
        this.mapper = mapper;
        this.legacyDashscopeKey = legacyDashscopeKey;
        this.legacyDashscopeModel = legacyDashscopeModel;
    }

    @Override
    public ResolvedModelSettings current() {
        if (!defaults.isEnabled()) {
            return ResolvedModelSettings.disabled();
        }
        ModelOverride override = loadOverride();
        ModelProvider provider = resolveProvider(override.provider, defaults.getProvider());
        return merge(provider, null, null, null, override);
    }

    @Override
    public ResolvedModelSettings resolve(ModelProvider provider, String model, String apiKey, String baseUrl) {
        if (!defaults.isEnabled()) {
            return ResolvedModelSettings.disabled();
        }
        return merge(provider == null ? current().provider() : provider, model, apiKey, baseUrl, loadOverride());
    }

    public ModelSnapshot snapshot() {
        ResolvedModelSettings current = current();
        Map<String, ProviderSnapshot> providers = new LinkedHashMap<>();
        ModelOverride override = loadOverride();
        for (ModelProvider type : ModelProvider.values()) {
            ResolvedModelSettings resolved = merge(type, null, null, null, override);
            CatalogViews views = catalogViews(override, type, resolved);
            providers.put(type.id(), new ProviderSnapshot(
                    type.label(),
                    resolved.available(),
                    type.needsApiKey() && notBlank(resolved.apiKey()),
                    type.needsApiKey(),
                    type.needsBaseUrl(),
                    resolved.model(),
                    blankToNull(resolved.baseUrl()),
                    views.imported(),
                    views.available(),
                    hint(type)
            ));
        }
        return new ModelSnapshot(
                defaults.isEnabled(),
                current.provider().id(),
                current.provider().label(),
                current.model(),
                current.available(),
                current.available() ? null : current.missingReason(),
                providers
        );
    }

    public ModelSnapshot update(ModelUpdateRequest request) {
        ModelOverride override = loadOverride();
        if (notBlank(request.provider())) {
            override.provider = ModelProvider.from(request.provider()).id();
        }
        ModelProvider target = resolveProvider(firstNonBlank(request.provider(), override.provider), defaults.getProvider());
        ProviderOverride slot = override.providers.computeIfAbsent(target.id(), ignored -> new ProviderOverride());
        List<String> catalog = sanitizeModels(request.models());
        if (!catalog.isEmpty()) {
            slot.models = catalog;
        }
        if (notBlank(request.model())) {
            slot.model = request.model().trim();
            if (catalog.isEmpty()) {
                slot.models = ModelCatalogClient.prepend(slot.model, sanitizeModels(slot.models));
            }
        } else if (!notBlank(slot.model) && slot.models != null && !slot.models.isEmpty()) {
            slot.model = slot.models.get(0);
        }
        if (notBlank(request.apiKey()) && !MASKED_KEY.equals(request.apiKey().trim())) {
            slot.apiKey = request.apiKey().trim();
        }
        if (notBlank(request.baseUrl())) {
            slot.baseUrl = request.baseUrl().trim();
        }
        saveOverride(override);
        return snapshot();
    }

    public ModelSnapshot rememberCatalog(ModelUpdateRequest request, List<String> models, String selectedModel) {
        ModelOverride override = loadOverride();
        if (request != null && notBlank(request.provider())) {
            override.provider = ModelProvider.from(request.provider()).id();
        }
        ModelProvider target = resolveProvider(
                firstNonBlank(request == null ? null : request.provider(), override.provider),
                defaults.getProvider()
        );
        ProviderOverride slot = override.providers.computeIfAbsent(target.id(), ignored -> new ProviderOverride());
        if (request != null && notBlank(request.apiKey()) && !MASKED_KEY.equals(request.apiKey().trim())) {
            slot.apiKey = request.apiKey().trim();
        }
        if (request != null && notBlank(request.baseUrl())) {
            slot.baseUrl = request.baseUrl().trim();
        }
        slot.available = sanitizeModels(models);
        if (notBlank(selectedModel)) {
            slot.model = selectedModel.trim();
        } else if (!notBlank(slot.model) && slot.available != null && !slot.available.isEmpty()) {
            slot.model = slot.available.get(0);
        }
        slot.models = notBlank(slot.model) ? List.of(slot.model) : List.of();
        saveOverride(override);
        return snapshot();
    }

    public ModelSnapshot clearProvider(String providerId) {
        ModelProvider type = ModelProvider.from(providerId);
        ModelOverride override = loadOverride();
        override.providers.remove(type.id());
        if (type.id().equals(override.provider)) {
            override.provider = defaults.getProvider();
        }
        saveOverride(override);
        return snapshot();
    }

    public ModelSnapshot removeCatalogModel(String providerId, String model) {
        if (!notBlank(model)) {
            throw new IllegalArgumentException("请指定要删除的模型");
        }
        ModelProvider type = ModelProvider.from(providerId);
        ModelOverride override = loadOverride();
        ProviderOverride slot = override.providers.get(type.id());
        if (slot == null) {
            return snapshot();
        }
        String wanted = model.trim();
        List<String> next = sanitizeModels(slot.models).stream()
                .filter(item -> !item.equals(wanted))
                .toList();
        slot.models = next;
        if (wanted.equals(slot.model)) {
            slot.model = next.isEmpty() ? null : next.get(0);
        }
        saveOverride(override);
        return snapshot();
    }

    public ResolvedModelSettings preview(ModelUpdateRequest request) {
        ModelProvider provider = notBlank(request.provider())
                ? ModelProvider.from(request.provider())
                : current().provider();
        return resolve(provider, request.model(), request.apiKey(), request.baseUrl());
    }

    private ResolvedModelSettings merge(
            ModelProvider provider,
            String modelOverride,
            String apiKeyOverride,
            String baseUrlOverride,
            ModelOverride stored
    ) {
        ModelProperties.Provider yaml = defaults.provider(provider);
        ProviderOverride slot = stored.providers.getOrDefault(provider.id(), new ProviderOverride());
        String apiKey = firstNonBlank(usable(apiKeyOverride), slot.apiKey, yaml.getApiKey(), fallbackApiKey(provider));
        String baseUrl = firstNonBlank(usable(baseUrlOverride), slot.baseUrl, yaml.getBaseUrl(), provider.defaultBaseUrl());
        String model = firstNonBlank(usable(modelOverride), slot.model, firstModel(slot.models));
        if (!notBlank(model) && credentialsPresent(provider, apiKey, baseUrl)) {
            model = firstNonBlank(yaml.getModel(), fallbackModel(provider));
        }
        return new ResolvedModelSettings(provider, model, apiKey, baseUrl, defaults.isEnabled());
    }

    private static boolean credentialsPresent(ModelProvider provider, String apiKey, String baseUrl) {
        if (provider.needsApiKey() && !notBlank(apiKey)) {
            return false;
        }
        return !provider.needsBaseUrl() || notBlank(baseUrl);
    }

    private String fallbackApiKey(ModelProvider provider) {
        return provider == ModelProvider.DASHSCOPE ? legacyDashscopeKey : "";
    }

    private String fallbackModel(ModelProvider provider) {
        if (provider == ModelProvider.DASHSCOPE && notBlank(legacyDashscopeModel)) {
            return legacyDashscopeModel;
        }
        return provider.defaultModel();
    }

    private ModelOverride loadOverride() {
        return store.get(OVERRIDE_KEY)
                .filter(ModelSettingsService::notBlank)
                .map(this::decode)
                .orElseGet(ModelOverride::new);
    }

    private void saveOverride(ModelOverride override) {
        try {
            String json = mapper.writeValueAsString(override);
            store.put(OVERRIDE_KEY, cipher.configured() ? cipher.encrypt(json) : json);
        } catch (Exception exception) {
            throw new IllegalStateException("保存模型配置失败", exception);
        }
    }

    private ModelOverride decode(String raw) {
        try {
            String json = looksEncrypted(raw) && cipher.configured() ? cipher.decrypt(raw) : raw;
            ModelOverride override = mapper.readValue(json, new TypeReference<>() {
            });
            if (override == null) {
                return new ModelOverride();
            }
            if (override.providers == null) {
                override.providers = new LinkedHashMap<>();
            }
            return override;
        } catch (Exception exception) {
            throw new IllegalStateException("读取模型配置失败", exception);
        }
    }

    private static boolean looksEncrypted(String raw) {
        return raw != null && !raw.isBlank() && !raw.trim().startsWith("{");
    }

    private static ModelProvider resolveProvider(String override, String fallback) {
        return ModelProvider.from(firstNonBlank(override, fallback, ModelProvider.DASHSCOPE.id()));
    }

    private static String usable(String value) {
        if (!notBlank(value) || MASKED_KEY.equals(value.trim())) {
            return "";
        }
        return value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return notBlank(value) ? value : null;
    }

    private static CatalogViews catalogViews(ModelOverride override, ModelProvider type, ResolvedModelSettings resolved) {
        ProviderOverride slot = override.providers.getOrDefault(type.id(), new ProviderOverride());
        List<String> imported = sanitizeModels(slot.models);
        List<String> available = sanitizeModels(slot.available);
        if (available.isEmpty() && imported.size() > AUTO_IMPORT_LIMIT) {
            available = imported;
            imported = notBlank(resolved.model()) ? List.of(resolved.model()) : imported.subList(0, 1);
        } else if (imported.isEmpty() && resolved.available() && notBlank(resolved.model())) {
            imported = List.of(resolved.model());
        }
        return new CatalogViews(imported, available);
    }

    private static String firstModel(List<String> models) {
        List<String> sanitized = sanitizeModels(models);
        return sanitized.isEmpty() ? "" : sanitized.get(0);
    }

    private static List<String> sanitizeModels(List<String> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String model : models) {
            if (notBlank(model) && !ids.contains(model.trim())) {
                ids.add(model.trim());
            }
        }
        return List.copyOf(ids);
    }

    private static String hint(ModelProvider provider) {
        return switch (provider) {
            case DASHSCOPE -> "填好 Key 后点测试连接，可用模型会出现在下拉框里，请手动选择，不会全部导入。";
            case OPENAI -> "DeepSeek：https://api.deepseek.com，月之暗面：https://api.moonshot.cn，智谱：https://open.bigmodel.cn/api/paas/v4。测试后从下拉框选择模型。";
            case ANTHROPIC -> "填写 Anthropic API Key，测试后从下拉框选择 Claude 模型。";
            case OLLAMA -> "本机默认 http://localhost:11434，无需 Key。测试后从下拉框选择本地模型。";
        };
    }

    public record ModelUpdateRequest(String provider, String model, String apiKey, String baseUrl, List<String> models) {
        public ModelUpdateRequest {
            if (models == null) {
                models = List.of();
            }
        }

        public ModelUpdateRequest(String provider, String model, String apiKey, String baseUrl) {
            this(provider, model, apiKey, baseUrl, List.of());
        }
    }

    public record ModelSnapshot(
            boolean enabled,
            String provider,
            String providerLabel,
            String model,
            boolean available,
            String message,
            Map<String, ProviderSnapshot> providers
    ) {
    }

    public record ProviderSnapshot(
            String label,
            boolean configured,
            boolean apiKeySet,
            boolean needsApiKey,
            boolean needsBaseUrl,
            String model,
            String baseUrl,
            List<String> models,
            List<String> available,
            String hint
    ) {
        public ProviderSnapshot {
            if (models == null) {
                models = List.of();
            }
            if (available == null) {
                available = List.of();
            }
        }
    }

    private record CatalogViews(List<String> imported, List<String> available) {
    }

    static class ModelOverride {
        public String provider;
        public Map<String, ProviderOverride> providers = new LinkedHashMap<>();
    }

    static class ProviderOverride {
        public String apiKey;
        public String baseUrl;
        public String model;
        public List<String> models;
        public List<String> available;
    }
}
