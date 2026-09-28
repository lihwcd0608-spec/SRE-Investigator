package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReadOnlySqlGuardTest {
    @Test
    void addsBoundedLimitToReadQuery() {
        assertEquals(
                "SELECT id FROM orders LIMIT 200",
                ReadOnlySqlGuard.normalize("SELECT id FROM orders")
        );
    }

    @Test
    void preservesExplicitLimit() {
        assertEquals(
                "SELECT id FROM orders LIMIT 25",
                ReadOnlySqlGuard.normalize("SELECT id FROM orders LIMIT 25;")
        );
    }

    @Test
    void doesNotAppendLimitToShowOrDescribe() {
        assertEquals("SHOW TABLES LIKE '%slot%'", ReadOnlySqlGuard.normalize("SHOW TABLES LIKE '%slot%'"));
        assertEquals("DESCRIBE reserve_drama", ReadOnlySqlGuard.normalize("DESCRIBE reserve_drama;"));
        assertEquals("EXPLAIN SELECT 1", ReadOnlySqlGuard.normalize("EXPLAIN SELECT 1"));
    }

    @Test
    void rejectsWriteAndMultipleStatements() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReadOnlySqlGuard.normalize("DELETE FROM orders")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ReadOnlySqlGuard.normalize("SELECT 1; SELECT 2")
        );
    }
}
