package com.maiya.investigator.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MongoEndpointTest {
    @Test
    void prefersUriAndMasksPassword() {
        MongoEndpoint endpoint = new MongoEndpoint(
                "mongodb://test:secret@mongo.example.com:3717/demo_test",
                "127.0.0.1",
                27017,
                "",
                "readonly",
                ""
        );
        assertEquals("mongodb://test:secret@mongo.example.com:3717/demo_test", endpoint.connectionUri());
        assertEquals("demo_test", endpoint.defaultDatabase());
        assertEquals("mongodb://test:***@mongo.example.com:3717/demo_test", endpoint.summary());
    }

    @Test
    void buildsUriFromHost() {
        MongoEndpoint endpoint = new MongoEndpoint("", "127.0.0.1", 27017, "demo", "ro", "p@ss");
        assertTrue(endpoint.connectionUri().startsWith("mongodb://ro:p%40ss@127.0.0.1:27017/demo"));
        assertEquals("demo", endpoint.defaultDatabase());
    }
}
