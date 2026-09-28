package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MongoJsonArgTest {
    @Test
    void acceptsJsonObjectFromModel() {
        String json = MongoJsonArg.toJsonString(Map.of("userId", "2009475153714520065"));
        assertEquals("{\"userId\":\"2009475153714520065\"}", json);
    }

    @Test
    void acceptsJsonString() {
        assertEquals("{\"userId\":\"1\"}", MongoJsonArg.toJsonString("{\"userId\":\"1\"}"));
        assertNull(MongoJsonArg.toJsonString(""));
        assertNull(MongoJsonArg.toJsonString(null));
    }
}
