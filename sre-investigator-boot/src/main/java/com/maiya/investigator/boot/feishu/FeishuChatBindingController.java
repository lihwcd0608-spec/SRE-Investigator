package com.maiya.investigator.boot.feishu;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.api.FeishuChatBindingRequest;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.domain.feishu.FeishuChatBinding;
import com.maiya.investigator.domain.feishu.FeishuChatBindingStore;
import com.maiya.investigator.infra.audit.AuditTrail;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/feishu/chats")
public class FeishuChatBindingController {
    private final FeishuChatBindingStore bindings;
    private final ControlPlaneStore controlPlane;
    private final AccessGuard access;
    private final AuditTrail auditTrail;

    public FeishuChatBindingController(
            FeishuChatBindingStore bindings,
            ControlPlaneStore controlPlane,
            AccessGuard access,
            AuditTrail auditTrail
    ) {
        this.bindings = bindings;
        this.controlPlane = controlPlane;
        this.access = access;
        this.auditTrail = auditTrail;
    }

    @GetMapping
    @RequirePermission(Permissions.BINDINGS)
    public List<FeishuChatBindingResponse> list(@PathVariable UUID projectId) {
        access.requireProject(projectId);
        List<FeishuChatBindingResponse> found = new ArrayList<>();
        for (FeishuChatBinding item : bindings.listByProject(projectId)) {
            found.add(toResponse(item));
        }
        return found;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.FEISHU_WRITE)
    public FeishuChatBindingResponse create(
            @PathVariable UUID projectId,
            @Valid @RequestBody FeishuChatBindingRequest request
    ) {
        access.requireProject(projectId);
        validateRepository(projectId, request.defaultRepositoryId());
        if (bindings.findByChatId(request.chatId().trim()).isPresent()) {
            throw new IllegalArgumentException("chat_id 已被其他绑定使用：" + request.chatId());
        }
        FeishuChatBinding saved = bindings.save(FeishuChatBinding.create(
                projectId,
                request.chatId(),
                request.chatName(),
                request.environment(),
                request.environments(),
                request.defaultRoute(),
                request.defaultRepositoryId()
        ));
        auditTrail.record("feishu.chat.create", saved.id().toString(), Map.of(
                "projectId", projectId,
                "chatId", saved.chatId()
        ));
        return toResponse(saved);
    }

    @PutMapping("/{bindingId}")
    @RequirePermission(Permissions.FEISHU_WRITE)
    public FeishuChatBindingResponse update(
            @PathVariable UUID projectId,
            @PathVariable UUID bindingId,
            @Valid @RequestBody FeishuChatBindingRequest request
    ) {
        access.requireProject(projectId);
        FeishuChatBinding current = requireBinding(projectId, bindingId);
        validateRepository(projectId, request.defaultRepositoryId());
        bindings.findByChatId(request.chatId().trim()).ifPresent(existing -> {
            if (!existing.id().equals(bindingId)) {
                throw new IllegalArgumentException("chat_id 已被其他绑定使用：" + request.chatId());
            }
        });
        FeishuChatBinding saved = bindings.save(new FeishuChatBinding(
                current.id(),
                projectId,
                request.chatId().trim(),
                request.chatName() == null ? "" : request.chatName().trim(),
                request.environment(),
                request.environments(),
                request.defaultRoute() == null ? "" : request.defaultRoute().trim(),
                request.defaultRepositoryId(),
                current.lastConversationId(),
                request.enabled() == null || request.enabled(),
                current.createdAt(),
                Instant.now()
        ));
        auditTrail.record("feishu.chat.update", saved.id().toString(), Map.of(
                "projectId", projectId,
                "chatId", saved.chatId()
        ));
        return toResponse(saved);
    }

    @DeleteMapping("/{bindingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.FEISHU_WRITE)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID bindingId) {
        access.requireProject(projectId);
        FeishuChatBinding current = requireBinding(projectId, bindingId);
        bindings.delete(bindingId);
        auditTrail.record("feishu.chat.delete", bindingId.toString(), Map.of(
                "projectId", projectId,
                "chatId", current.chatId()
        ));
    }

    private FeishuChatBinding requireBinding(UUID projectId, UUID bindingId) {
        FeishuChatBinding binding = bindings.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("飞书群绑定不存在：" + bindingId));
        if (!binding.projectId().equals(projectId)) {
            throw new IllegalArgumentException("绑定不属于当前项目");
        }
        return binding;
    }

    private void validateRepository(UUID projectId, UUID repositoryId) {
        if (repositoryId == null) {
            return;
        }
        List<RepositoryConfig> repos = controlPlane.repositories(projectId);
        boolean found = false;
        for (RepositoryConfig repo : repos) {
            if (repo.id().equals(repositoryId)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("默认代码仓不存在：" + repositoryId);
        }
    }

    private static FeishuChatBindingResponse toResponse(FeishuChatBinding binding) {
        return new FeishuChatBindingResponse(
                binding.id(),
                binding.projectId(),
                binding.chatId(),
                binding.chatName(),
                binding.environment(),
                new ArrayList<>(binding.allowedEnvironments()),
                binding.defaultRoute(),
                binding.defaultRepositoryId(),
                binding.lastConversationId(),
                binding.enabled(),
                binding.createdAt(),
                binding.updatedAt()
        );
    }

    public record FeishuChatBindingResponse(
            UUID id,
            UUID projectId,
            String chatId,
            String chatName,
            Environment environment,
            List<Environment> environments,
            String defaultRoute,
            UUID defaultRepositoryId,
            UUID lastConversationId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
