package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GitSshTest {
    @Test
    void resolvesAbsoluteKeyAndAppliesCommand() throws Exception {
        Path key = Path.of("target/git-ssh-it/id_test").toAbsolutePath();
        Files.createDirectories(key.getParent());
        Files.writeString(key, "dummy-key");

        assertEquals(key.normalize(), GitSsh.resolveIdentity(key.toString()));

        ProcessBuilder builder = new ProcessBuilder("true");
        GitSsh.apply(builder, key.toString());
        assertEquals("0", builder.environment().get("GIT_TERMINAL_PROMPT"));
        assertTrue(builder.environment().get("GIT_SSH_COMMAND").contains("-i " + key.normalize()));
        assertTrue(builder.environment().get("GIT_SSH_COMMAND").contains("IdentitiesOnly=yes"));
    }

    @Test
    void defaultCommandUsesBatchMode() {
        ProcessBuilder builder = new ProcessBuilder("true");
        GitSsh.apply(builder, null);
        assertEquals("0", builder.environment().get("GIT_TERMINAL_PROMPT"));
        assertTrue(builder.environment().get("GIT_SSH_COMMAND").contains("BatchMode=yes"));
    }

    @Test
    void rejectsMissingOrUnsafeKey() {
        assertThrows(IllegalArgumentException.class, () -> GitSsh.resolveIdentity("/tmp/does-not-exist-ssh-key"));
        assertThrows(IllegalArgumentException.class, () -> GitSsh.resolveIdentity("id_rsa"));
        assertThrows(IllegalArgumentException.class, () -> GitSsh.resolveIdentity("/tmp/id rsa"));
    }
}
