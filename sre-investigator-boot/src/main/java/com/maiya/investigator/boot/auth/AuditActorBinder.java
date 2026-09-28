package com.maiya.investigator.boot.auth;

import com.maiya.investigator.infra.audit.AuditTrail;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditActorBinder {
    private final AuditTrail auditTrail;

    public AuditActorBinder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @PostConstruct
    void bind() {
        auditTrail.setActorResolver(() -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UUID userId = null;
            String username = null;
            if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
                userId = principal.userId();
                username = principal.username();
            }
            return new AuditTrail.ActorContext(userId, username, clientIp());
        });
    }

    public static String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return clientIp(attributes.getRequest());
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
