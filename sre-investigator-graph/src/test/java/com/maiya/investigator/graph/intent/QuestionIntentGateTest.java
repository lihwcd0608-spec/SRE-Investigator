package com.maiya.investigator.graph.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QuestionIntentGateTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsInsultAndGreetingWithoutModel() {
        assertEquals(QuestionIntentHeuristics.Hint.REJECT, QuestionIntentHeuristics.hint("吊毛"));
        assertEquals(QuestionIntentHeuristics.Hint.CHAT, QuestionIntentHeuristics.hint("hi"));
        assertEquals(QuestionIntentHeuristics.Hint.CHAT, QuestionIntentHeuristics.hint("在吗"));
        assertEquals(QuestionIntentHeuristics.Hint.REJECT, QuestionIntentHeuristics.hint("哈哈哈哈"));
    }

    @Test
    void praisesGetWarmReplyWithoutInvestigate() {
        assertEquals(QuestionIntentHeuristics.Hint.CHAT, QuestionIntentHeuristics.hint("不错哦👍"));
        assertEquals(QuestionIntentHeuristics.Hint.CHAT, QuestionIntentHeuristics.hint("谢谢"));
        assertEquals(QuestionIntentHeuristics.Hint.CHAT, QuestionIntentHeuristics.hint("厉害了"));
        String reply = QuestionIntentHeuristics.replyFor("不错哦👍", QuestionIntentHeuristics.Hint.CHAT);
        assertFalse(reply.contains("这不像排查"));
        assertTrue(reply.contains("谢谢") || reply.contains("过奖") || reply.contains("收到"));
    }

    @Test
    void allowsOperationalQuestions() {
        assertEquals(
                QuestionIntentHeuristics.Hint.ALLOW,
                QuestionIntentHeuristics.hint("用户 2081928683760521217 订单没到账")
        );
        assertEquals(QuestionIntentHeuristics.Hint.ALLOW, QuestionIntentHeuristics.hint("网关 5xx 升高"));
        assertEquals(QuestionIntentHeuristics.Hint.ALLOW, QuestionIntentHeuristics.hint("TheaterManagerImpl 空指针"));
    }

    @Test
    void allowsContinuationPhrases() {
        assertEquals(QuestionIntentHeuristics.Hint.ALLOW, QuestionIntentHeuristics.hint("直接说结论"));
        assertEquals(QuestionIntentHeuristics.Hint.ALLOW, QuestionIntentHeuristics.hint("继续"));
        assertEquals(QuestionIntentHeuristics.Hint.ALLOW, QuestionIntentHeuristics.hint("总结一下"));
        assertEquals(QuestionIntentHeuristics.Hint.ALLOW, QuestionIntentHeuristics.hint("然后呢"));
        assertTrue(QuestionIntentHeuristics.isContinuation("直接说结论"));
    }

    @Test
    void shortVagueGoesUnsure() {
        assertEquals(QuestionIntentHeuristics.Hint.UNSURE, QuestionIntentHeuristics.hint("帮我看看这是啥情况"));
    }

    @Test
    void parsesSkipAndGoJson() {
        QuestionIntent skip = QuestionIntentParser.parseModelReply(
                "```json\n{\"action\":\"skip\",\"reply\":\"请描述故障现象\"}\n```",
                mapper
        ).orElseThrow();
        assertFalse(skip.proceed());
        assertEquals("请描述故障现象", skip.reply());

        QuestionIntent go = QuestionIntentParser.parseModelReply("{\"action\":\"go\"}", mapper).orElseThrow();
        assertTrue(go.proceed());
    }
}
