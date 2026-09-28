package com.maiya.investigator.boot;

import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequirePermission(Permissions.INVESTIGATE)
public class ConversationController {
    private final ConversationSessionService conversations;
    private final AccessGuard access;

    public ConversationController(ConversationSessionService conversations, AccessGuard access) {
        this.conversations = conversations;
        this.access = access;
    }

    @GetMapping("/projects/{projectId}/conversations")
    public List<ConversationSessionService.ConversationSummary> list(@PathVariable UUID projectId) {
        access.requireProject(projectId);
        return conversations.list(projectId, access.current());
    }

    @GetMapping("/conversations/{conversationId}")
    @RequirePermission(any = {Permissions.INVESTIGATE, Permissions.REVIEW, Permissions.BINDINGS, Permissions.AGENTS})
    public ConversationSessionService.ConversationDetail get(@PathVariable UUID conversationId) {
        ConversationSessionService.ConversationDetail detail = conversations.get(conversationId, access.current());
        access.requireProject(detail.projectId());
        return detail;
    }
}
