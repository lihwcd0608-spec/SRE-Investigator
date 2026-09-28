package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileNamesTest {
    @Test
    void mapsMysqlPrefixedAliasToBoundProfile() {
        assertEquals("test", ProfileNames.resolve("mysql-test", List.of("test"), "MySQL"));
        assertEquals("test", ProfileNames.resolve("MYSQL_TEST", List.of("test"), "MySQL"));
        assertEquals("test", ProfileNames.resolve("", List.of("test"), "MySQL"));
    }

    @Test
    void usesTheOnlyBoundProfileWhenNameIsWrong() {
        assertEquals("test", ProfileNames.resolve("prod-readonly", List.of("test"), "MySQL"));
    }

    @Test
    void rejectsUnknownNameWhenMultipleProfilesBound() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProfileNames.resolve("mysql-prod", List.of("test", "gray"), "MySQL")
        );
        assertEquals("gray", ProfileNames.resolve("mysql-gray", List.of("test", "gray"), "MySQL"));
    }

    @Test
    void resolveExactDoesNotFallBackToTheOnlyYamlProfile() {
        assertEquals("my_test", ProfileNames.resolveExact("my-test", List.of("my_test"), "MySQL"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProfileNames.resolveExact("my_test", List.of("test"), "MySQL")
        );
    }
}
