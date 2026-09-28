package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.Document;
import org.junit.jupiter.api.Test;

class MongoReadGuardTest {
    @Test
    void acceptsEmptyAndSimpleFilter() {
        assertTrue(MongoReadGuard.filter(null).isEmpty());
        Document filter = MongoReadGuard.filter("{\"userId\":\"1\"}");
        assertEquals("1", filter.getString("userId"));
    }

    @Test
    void rewritesUserPortraitFilterToId() {
        Document filter = MongoReadGuard.filter("{\"user_id\":\"2009475153714520065\"}");
        Document normalized = MongoReadGuard.normalizeQuery(filter, "ums_user_tag_attr");
        assertEquals(2009475153714520065L, normalized.get("_id"));
    }

    @Test
    void coercesStringIdForPortraitCollection() {
        Document filter = MongoReadGuard.filter("{\"_id\":\"2009475153714520065\"}");
        Document normalized = MongoReadGuard.normalizeQuery(filter, "ums_user_tag_attr");
        assertEquals(2009475153714520065L, normalized.get("_id"));
    }

    @Test
    void rejectsWhereAndWriteStages() {
        assertThrows(IllegalArgumentException.class, () -> MongoReadGuard.filter("{\"$where\":\"1==1\"}"));
        assertThrows(IllegalArgumentException.class, () -> MongoReadGuard.filter("{\"$out\":\"dump\"}"));
        assertThrows(IllegalArgumentException.class, () -> MongoReadGuard.identifier("users;drop", "集合名"));
    }
}
