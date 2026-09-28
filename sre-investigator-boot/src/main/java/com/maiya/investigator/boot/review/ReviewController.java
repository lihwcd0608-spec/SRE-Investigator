package com.maiya.investigator.boot.review;

import com.maiya.investigator.boot.ConversationSessionService;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.mcp.GitCompareService;
import com.maiya.investigator.mcp.GitCompareService.GitBranch;
import com.maiya.investigator.mcp.GitCompareService.GitCompareResult;
import com.maiya.investigator.mcp.GitCompareService.GitFileDiff;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/review")
@RequirePermission(Permissions.REVIEW)
public class ReviewController {
    private final ConversationSessionService conversations;
    private final ControlPlaneStore controlPlane;
    private final GitCompareService gitCompare;
    private final AccessGuard access;

    public ReviewController(
            ConversationSessionService conversations,
            ControlPlaneStore controlPlane,
            GitCompareService gitCompare,
            AccessGuard access
    ) {
        this.conversations = conversations;
        this.controlPlane = controlPlane;
        this.gitCompare = gitCompare;
        this.access = access;
    }

    @GetMapping("/sessions")
    public List<ConversationSessionService.ConversationSummary> sessions(@PathVariable UUID projectId) {
        access.requireProject(projectId);
        return conversations.listReviews(projectId, access.current());
    }

    @GetMapping("/sessions/{conversationId}")
    public ConversationSessionService.ConversationDetail session(
            @PathVariable UUID projectId,
            @PathVariable UUID conversationId
    ) {
        access.requireProject(projectId);
        ConversationSessionService.ConversationDetail detail = conversations.getReview(conversationId, access.current());
        if (!detail.projectId().equals(projectId)) {
            throw new IllegalArgumentException("审查记录不属于当前项目");
        }
        return detail;
    }

    @GetMapping("/repositories")
    public List<RepositoryConfig> repositories(@PathVariable UUID projectId) {
        access.requireProject(projectId);
        return controlPlane.repositories(projectId);
    }

    @GetMapping("/repositories/{repositoryId}/branches")
    public List<GitBranch> branches(@PathVariable UUID projectId, @PathVariable UUID repositoryId) {
        access.requireProject(projectId);
        RepositoryConfig repository = controlPlane.repository(projectId, repositoryId);
        return gitCompare.listBranches(Path.of(repository.rootPath()));
    }

    @GetMapping("/repositories/{repositoryId}/compare")
    public GitCompareResult compare(
            @PathVariable UUID projectId,
            @PathVariable UUID repositoryId,
            @RequestParam String base,
            @RequestParam String head
    ) {
        access.requireProject(projectId);
        RepositoryConfig repository = controlPlane.repository(projectId, repositoryId);
        return gitCompare.compare(Path.of(repository.rootPath()), base, head, false);
    }

    @GetMapping("/repositories/{repositoryId}/compare/file")
    public GitFileDiff compareFile(
            @PathVariable UUID projectId,
            @PathVariable UUID repositoryId,
            @RequestParam String base,
            @RequestParam String head,
            @RequestParam String path
    ) {
        access.requireProject(projectId);
        RepositoryConfig repository = controlPlane.repository(projectId, repositoryId);
        return gitCompare.fileDiff(Path.of(repository.rootPath()), base, head, path);
    }
}
