package com.maiya.investigator.domain.model;

public interface ModelSettingsResolver {
    ResolvedModelSettings current();

    ResolvedModelSettings resolve(ModelProvider provider, String model, String apiKey, String baseUrl);
}
