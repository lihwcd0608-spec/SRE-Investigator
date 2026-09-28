package com.maiya.investigator.boot;

import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.mcp.ReadonlyCodeTool;
import com.maiya.investigator.mcp.ReadonlyCodeTool.CodeWorkspace;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/code")
@RequirePermission(Permissions.INVESTIGATE)
public class ProjectCodeController {
    private final ReadonlyCodeTool code;
    private final AccessGuard access;

    public ProjectCodeController(ReadonlyCodeTool code, AccessGuard access) {
        this.code = code;
        this.access = access;
    }

    @GetMapping("/workspaces")
    public List<CodeWorkspace> workspaces(@PathVariable UUID projectId) {
        access.requireProject(projectId);
        return code.workspaces(projectId);
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@PathVariable UUID projectId, @RequestParam String q) {
        access.requireProject(projectId);
        return code.lookup(code.workspaces(projectId), q);
    }

    @GetMapping("/files")
    public List<Map<String, Object>> files(
            @PathVariable UUID projectId,
            @RequestParam String workspace,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String q
    ) {
        access.requireProject(projectId);
        return code.listEntries(code.workspaces(projectId), workspace, path, q);
    }
}
