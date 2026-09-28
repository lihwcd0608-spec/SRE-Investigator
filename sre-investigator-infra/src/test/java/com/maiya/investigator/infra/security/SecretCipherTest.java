package com.maiya.investigator.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTripsAesGcmCiphertext() {
        SecretCipher cipher = new SecretCipher(KEY);

        String encrypted = cipher.encrypt("{\"uri\":\"jdbc:mysql://readonly:secret@db/app\"}");

        assertNotEquals("{\"uri\":\"jdbc:mysql://readonly:secret@db/app\"}", encrypted);
        assertEquals("{\"uri\":\"jdbc:mysql://readonly:secret@db/app\"}", cipher.decrypt(encrypted));
    }

    @Test
    void refusesToStoreSecretsWithoutMasterKey() {
        SecretCipher cipher = new SecretCipher("");

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));
    }
}
