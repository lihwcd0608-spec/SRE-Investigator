package com.maiya.investigator.graph.conversation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.memory.MemoryFactExtractor;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryFactExtractorTest {
    @Test
    void extractsReusableTypesAndTables() {
        List<MemoryFactExtractor.ExtractedFact> facts = MemoryFactExtractor.extract(
                "看 ReserveDramaInsertProcessor",
                "入口在 ReserveDramaInsertProcessor，表 t_reserve_drama。不要按业务名猜表。"
        );
        assertTrue(facts.stream().anyMatch(fact -> fact.content().contains("ReserveDramaInsertProcessor")));
        assertTrue(facts.stream().anyMatch(fact -> fact.content().contains("t_reserve_drama")));
        assertTrue(facts.size() <= 3);
    }

    @Test
    void skipsOneOffIds() {
        List<MemoryFactExtractor.ExtractedFact> facts = MemoryFactExtractor.extract(
                "userId=123 的订单",
                "不要把 userId=123 当成长期记忆。"
        );
        assertFalse(facts.stream().anyMatch(fact -> fact.content().contains("userId=123")));
    }
}
