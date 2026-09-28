package com.maiya.investigator.boot;

import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.infra.audit.AuditEvent;
import com.maiya.investigator.infra.audit.AuditTrail;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-events")
@RequirePermission(Permissions.AUDIT)
public class AuditController {
    private final AuditTrail auditTrail;

    public AuditController(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @GetMapping
    public List<AuditEvent> recent(@RequestParam(required = false) String category) {
        return auditTrail.recent(category);
    }
}
