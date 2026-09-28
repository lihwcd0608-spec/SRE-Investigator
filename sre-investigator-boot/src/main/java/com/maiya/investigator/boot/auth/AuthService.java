package com.maiya.investigator.boot.auth;

import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.auth.UserIdentity;
import com.maiya.investigator.infra.audit.AuditTrail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    public static final String FEISHU_STATE = "FEISHU_OAUTH_STATE";
    public static final String FEISHU_REDIRECT = "FEISHU_OAUTH_REDIRECT";

    private final AuthStore store;
    private final PasswordEncoder passwords;
    private final FeishuOAuthClient feishu;
    private final SecurityContextRepository securityContextRepository;
    private final AuditTrail auditTrail;

    public AuthService(
            AuthStore store,
            PasswordEncoder passwords,
            FeishuOAuthClient feishu,
            SecurityContextRepository securityContextRepository,
            AuditTrail auditTrail
    ) {
        this.store = store;
        this.passwords = passwords;
        this.feishu = feishu;
        this.securityContextRepository = securityContextRepository;
        this.auditTrail = auditTrail;
    }

    public AuthUser authenticatePassword(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        AuthUser user = store.userByUsername(username.trim())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));
        if (!user.enabled() || user.passwordHash() == null || !passwords.matches(password, user.passwordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        return user;
    }

    public AuthUser completeFeishu(String code, String expectedState, String actualState) {
        return completeFeishu(code, expectedState, actualState, null);
    }

    public AuthUser completeFeishu(String code, String expectedState, String actualState, String redirectUri) {
        if (!feishu.configured()) {
            throw new IllegalStateException("未配置飞书登录");
        }
        if (expectedState == null || !Objects.equals(expectedState, actualState)) {
            throw new UnauthorizedException("飞书登录状态无效");
        }
        FeishuOAuthClient.FeishuProfile profile = feishu.redeem(code, redirectUri);
        return store.identity(UserIdentity.FEISHU, profile.openId())
                .flatMap(identity -> store.user(identity.userId()))
                .map(this::requireEnabled)
                .orElseGet(() -> createFeishuUser(profile));
    }

    public AuthPrincipal establish(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        AuthPrincipal principal = AuthPrincipal.from(user, store);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        securityContextRepository.saveContext(context, request, response);
        return principal;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        AuthPrincipal principal = null;
        try {
            AuthenticationHolder holder = AuthenticationHolder.current();
            principal = holder == null ? null : holder.principal();
        } catch (RuntimeException ignored) {
            // already anonymous
        }
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        if (principal != null) {
            auditTrail.record("auth.logout", principal.userId().toString(), Map.of("username", principal.username()));
        }
    }

    public MeResponse me() {
        AuthPrincipal principal = currentPrincipal();
        AuthUser user = store.user(principal.userId()).orElseThrow(() -> new UnauthorizedException("未登录"));
        return toMe(user, principal);
    }

    public MeResponse toMe(AuthUser user) {
        return toMe(user, AuthPrincipal.from(user, store));
    }

    public MeResponse toMe(AuthUser user, AuthPrincipal principal) {
        List<String> all = principal.permissions();
        List<String> menus = all.stream().filter(code -> code.startsWith("menu.")).toList();
        return new MeResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                principal.roles(),
                menus,
                all,
                store.projectIdsForUser(user.id()),
                true
        );
    }

    public boolean feishuEnabled() {
        return feishu.configured();
    }

    public FeishuOAuthClient feishu() {
        return feishu;
    }

    public void recordLogin(AuthUser user, String action, String clientIp) {
        auditTrail.recordLogin(true, action, user.username(), user.id(), clientIp, null);
    }

    public void recordLoginFailure(String action, String username, String clientIp, String message) {
        auditTrail.recordLogin(false, action, username, null, clientIp, message);
    }

    private AuthUser createFeishuUser(FeishuOAuthClient.FeishuProfile profile) {
        Instant now = Instant.now();
        String username = uniqueUsername(profile);
        AuthUser user = store.saveUser(new AuthUser(
                UUID.randomUUID(),
                username,
                profile.name() == null || profile.name().isBlank() ? username : profile.name(),
                blankToNull(profile.email()),
                null,
                AuthUser.ENABLED,
                now,
                now
        ));
        store.saveIdentity(new UserIdentity(UUID.randomUUID(), user.id(), UserIdentity.FEISHU, profile.openId()));
        store.roleByCode("VIEWER").ifPresent(role -> store.setUserRoles(user.id(), List.of(role.id())));
        return user;
    }

    private String uniqueUsername(FeishuOAuthClient.FeishuProfile profile) {
        String base = profile.openId().replaceAll("[^A-Za-z0-9_]", "");
        if (base.isBlank()) {
            base = "feishu";
        }
        String candidate = "fs_" + (base.length() > 60 ? base.substring(0, 60) : base);
        int suffix = 1;
        while (store.userByUsername(candidate).isPresent()) {
            candidate = "fs_" + suffix + "_" + (base.length() > 50 ? base.substring(0, 50) : base);
            suffix++;
        }
        return candidate;
    }

    private AuthUser requireEnabled(AuthUser user) {
        if (!user.enabled()) {
            throw new UnauthorizedException("账号已停用");
        }
        return user;
    }

    private AuthPrincipal currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new UnauthorizedException("未登录");
        }
        return principal;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AuthenticationHolder(AuthPrincipal principal) {
        static AuthenticationHolder current() {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
                return new AuthenticationHolder(principal);
            }
            return null;
        }
    }

    public record MeResponse(
            UUID id,
            String username,
            String displayName,
            String email,
            List<String> roles,
            List<String> menus,
            List<String> permissions,
            List<UUID> projectIds,
            boolean allProjects
    ) {
    }
}
