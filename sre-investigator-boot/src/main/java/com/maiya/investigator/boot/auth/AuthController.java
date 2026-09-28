package com.maiya.investigator.boot.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthProperties properties;

    public AuthController(AuthService auth, AuthProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/login")
    public AuthService.MeResponse login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String clientIp = AuditActorBinder.clientIp(httpRequest);
        try {
            var user = auth.authenticatePassword(request.username(), request.password());
            var principal = auth.establish(user, httpRequest, httpResponse);
            auth.recordLogin(user, "auth.login", clientIp);
            return auth.toMe(user, principal);
        } catch (UnauthorizedException exception) {
            auth.recordLoginFailure("auth.login", request == null ? null : request.username(), clientIp, exception.getMessage());
            throw exception;
        }
    }

    @GetMapping("/me")
    public AuthService.MeResponse me() {
        return auth.me();
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response);
    }

    @GetMapping("/feishu/url")
    public FeishuUrlResponse feishuUrl(HttpServletRequest request) {
        if (!auth.feishuEnabled()) {
            return new FeishuUrlResponse(false, null);
        }
        String state = HexFormat.of().formatHex(random(16));
        String redirect = properties.getFeishu().getRedirectUri();
        request.getSession(true).setAttribute(AuthService.FEISHU_STATE, state);
        request.getSession().setAttribute(AuthService.FEISHU_REDIRECT, redirect);
        return new FeishuUrlResponse(true, auth.feishu().authorizeUrl(state, redirect));
    }

    @GetMapping("/feishu/callback")
    public void feishuCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String clientIp = AuditActorBinder.clientIp(request);
        try {
            Object expected = request.getSession(true).getAttribute(AuthService.FEISHU_STATE);
            Object redirect = request.getSession().getAttribute(AuthService.FEISHU_REDIRECT);
            request.getSession().removeAttribute(AuthService.FEISHU_STATE);
            request.getSession().removeAttribute(AuthService.FEISHU_REDIRECT);
            var user = auth.completeFeishu(
                    code,
                    expected == null ? null : expected.toString(),
                    state,
                    redirect == null ? properties.getFeishu().getRedirectUri() : redirect.toString()
            );
            auth.establish(user, request, response);
            auth.recordLogin(user, "auth.feishu", clientIp);
            response.sendRedirect(properties.getFeishu().getSuccessRedirect());
        } catch (RuntimeException exception) {
            auth.recordLoginFailure("auth.feishu", null, clientIp, exception.getMessage());
            response.sendRedirect("/login?error=feishu");
        }
    }

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public record LoginRequest(String username, String password) {
    }

    public record FeishuUrlResponse(boolean enabled, String url) {
    }
}
