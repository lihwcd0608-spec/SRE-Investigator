package com.maiya.investigator.boot.auth;

public interface FeishuOAuthClient {
    boolean configured();

    String authorizeUrl(String state);

    default String authorizeUrl(String state, String redirectUri) {
        return authorizeUrl(state);
    }

    FeishuProfile redeem(String code);

    default FeishuProfile redeem(String code, String redirectUri) {
        return redeem(code);
    }

    record FeishuProfile(String openId, String name, String email) {
    }
}
