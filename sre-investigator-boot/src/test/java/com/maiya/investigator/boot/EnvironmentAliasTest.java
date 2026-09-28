package com.maiya.investigator.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.Environment;
import org.junit.jupiter.api.Test;

class EnvironmentAliasTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsLegacyAliasesAndWritesLowercase() throws Exception {
        assertEquals(Environment.TEST, Environment.from("TEST"));
        assertEquals(Environment.TEST, Environment.from("local"));
        assertEquals(Environment.PRE, Environment.from("GRAY"));
        assertEquals(Environment.PRE, Environment.from("pre"));
        assertEquals(Environment.PROD, Environment.from("prod"));
        assertEquals("\"test\"", mapper.writeValueAsString(Environment.TEST));
        assertEquals(Environment.PRE, mapper.readValue("\"pre\"", Environment.class));
        assertEquals(Environment.PRE, mapper.readValue("\"GRAY\"", Environment.class));
        assertThrows(IllegalArgumentException.class, () -> Environment.from("dev"));
    }
}
