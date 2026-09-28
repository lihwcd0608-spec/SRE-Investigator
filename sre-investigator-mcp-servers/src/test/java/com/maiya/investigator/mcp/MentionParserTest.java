package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MentionParserTest {
    @Test
    void extractsCursorStyleMentions() {
        assertEquals(
                List.of("ReserveDramaInsertProcessor", "demo-app-video/src/Foo.java"),
                MentionParser.extract("@ReserveDramaInsertProcessor 测试反馈，再看 @demo-app-video/src/Foo.java")
        );
    }

    @Test
    void ignoresEmails() {
        assertEquals(List.of(), MentionParser.extract("联系 sre@company.com 看一下"));
        assertEquals(List.of("admin"), MentionParser.extract("在 @admin 仓里查"));
    }
}
