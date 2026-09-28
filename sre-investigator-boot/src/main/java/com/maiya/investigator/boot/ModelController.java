package com.maiya.investigator.boot;

import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.boot.model.ModelCatalogClient;
import com.maiya.investigator.boot.model.ModelSettingsService;
import com.maiya.investigator.boot.model.ModelSettingsService.ModelSnapshot;
import com.maiya.investigator.boot.model.ModelSettingsService.ModelUpdateRequest;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.graph.ChatModelFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class ModelController {
    private final ModelSettingsService settings;
    private final ChatModelFactory factory;
    private final ModelCatalogClient catalog;

    public ModelController(ModelSettingsService settings, ChatModelFactory factory, ModelCatalogClient catalog) {
        this.settings = settings;
        this.factory = factory;
        this.catalog = catalog;
    }

    @GetMapping("/model")
    @RequirePermission(any = {Permissions.INVESTIGATE, Permissions.REVIEW, Permissions.MODEL})
    public ModelSnapshot get() {
        return settings.snapshot();
    }

    @PutMapping("/model")
    @RequirePermission(Permissions.MODEL_WRITE)
    public ModelSnapshot update(@RequestBody(required = false) ModelUpdateRequest request) {
        return settings.update(request == null ? new ModelUpdateRequest(null, null, null, null) : request);
    }

    @DeleteMapping("/model/providers/{provider}")
    @RequirePermission(Permissions.MODEL_WRITE)
    public ModelSnapshot clearProvider(@PathVariable String provider) {
        return settings.clearProvider(provider);
    }

    @DeleteMapping("/model/catalog")
    @RequirePermission(Permissions.MODEL_WRITE)
    public ModelSnapshot removeCatalogModel(@RequestParam String provider, @RequestParam String model) {
        return settings.removeCatalogModel(provider, model);
    }

    @PostMapping("/model/test")
    @RequirePermission(Permissions.MODEL_WRITE)
    public Map<String, Object> test(@RequestBody(required = false) ModelUpdateRequest request) {
        ResolvedModelSettings resolved = request == null
                ? settings.current()
                : settings.preview(request);
        if (!resolved.credentialsReady()) {
            throw new IllegalStateException(resolved.missingReason());
        }
        CatalogFetch fetch = fetchCatalog(resolved);
        String requested = request == null ? "" : request.model();
        String implicit = resolved.provider().defaultModel();
        String probe = ModelCatalogClient.pickProbeModel(requested, fetch.models, implicit);
        if (fetch.models.isEmpty() && fetch.error != null && (probe == null || probe.isBlank() || probe.equals(implicit))) {
            throw fetch.error;
        }
        if (probe == null || probe.isBlank()) {
            throw new IllegalStateException("服务方没有返回可用模型，请手动填写模型名后再测。");
        }
        List<String> stored = ModelCatalogClient.prepend(probe, fetch.models);
        ModelSnapshot snapshot = settings.rememberCatalog(request, stored, probe);
        ResolvedModelSettings toProbe = resolved.withModel(probe);
        try {
            String reply = ChatClient.create(factory.create(toProbe))
                    .prompt()
                    .user("只回复 ok")
                    .call()
                    .content();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("provider", toProbe.provider().id());
            body.put("model", probe);
            body.put("reply", reply == null ? "" : reply);
            body.put("models", stored);
            body.put("snapshot", snapshot);
            return body;
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw new IllegalStateException("模型探测失败：" + message, exception);
        }
    }

    private CatalogFetch fetchCatalog(ResolvedModelSettings resolved) {
        try {
            return new CatalogFetch(catalog.list(resolved), null);
        } catch (RuntimeException exception) {
            return new CatalogFetch(List.of(), exception);
        }
    }

    private record CatalogFetch(List<String> models, RuntimeException error) {
    }
}
