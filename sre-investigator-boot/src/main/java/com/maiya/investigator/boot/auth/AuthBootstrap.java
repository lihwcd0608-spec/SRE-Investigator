package com.maiya.investigator.boot.auth;

import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.auth.UserIdentity;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);
    private final AuthStore store;
    private final AuthProperties properties;
    private final PasswordEncoder passwords;

    public AuthBootstrap(AuthStore store, AuthProperties properties, PasswordEncoder passwords) {
        this.store = store;
        this.properties = properties;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (store.userCount() > 0) {
            return;
        }
        String username = properties.getBootstrapUser() == null || properties.getBootstrapUser().isBlank()
                ? "admin"
                : properties.getBootstrapUser().trim();
        String raw = properties.getBootstrapPassword();
        boolean generated = raw == null || raw.isBlank();
        if (generated) {
            byte[] bytes = new byte[12];
            new SecureRandom().nextBytes(bytes);
            raw = HexFormat.of().formatHex(bytes);
        }
        Instant now = Instant.now();
        AuthUser user = store.saveUser(new AuthUser(
                UUID.randomUUID(),
                username,
                username,
                null,
                passwords.encode(raw),
                AuthUser.ENABLED,
                now,
                now
        ));
        store.saveIdentity(new UserIdentity(UUID.randomUUID(), user.id(), UserIdentity.PASSWORD, username));
        store.roleByCode(Permissions.SUPER_ADMIN)
                .ifPresent(role -> store.setUserRoles(user.id(), List.of(role.id())));
        log.warn("已创建引导超管 {}，请立刻修改密码", username);
        if (generated) {
            log.warn("引导超管一次性口令：{}", raw);
        }
    }
}
