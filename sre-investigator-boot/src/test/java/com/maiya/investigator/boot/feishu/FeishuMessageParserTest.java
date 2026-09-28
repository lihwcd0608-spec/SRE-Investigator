package com.maiya.investigator.boot.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import org.junit.jupiter.api.Test;

class FeishuMessageParserTest {
    @Test
    void parsesInvestigateQuestion() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse("@bot test 环境 user 123 订单没到账");
        assertEquals(ConversationMode.INVESTIGATE, parsed.mode());
        assertEquals("test 环境 user 123 订单没到账", parsed.question());
        assertNull(parsed.environment());
    }

    @Test
    void stripsCompactEnvironmentPrefix() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse("@bot prod环境 用户123 订单没到账");
        assertEquals(Environment.PROD, parsed.environment());
        assertEquals("用户123 订单没到账", parsed.question());
    }

    @Test
    void stripsLabeledAndChineseEnvironmentPrefix() {
        FeishuMessageParser.ParsedMessage labeled = FeishuMessageParser.parse("环境:prod 用户123 回调失败");
        assertEquals(Environment.PROD, labeled.environment());
        assertEquals("用户123 回调失败", labeled.question());

        FeishuMessageParser.ParsedMessage chinese = FeishuMessageParser.parse("测试环境 订单没到账");
        assertEquals(Environment.TEST, chinese.environment());
        assertEquals("订单没到账", chinese.question());

        FeishuMessageParser.ParsedMessage envCode = FeishuMessageParser.parse("env:pre 网关 5xx");
        assertEquals(Environment.PRE, envCode.environment());
        assertEquals("网关 5xx", envCode.question());
    }

    @Test
    void compactPrefixAppliesToReview() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse("prod环境 审查 main..feature/pay 看回调幂等");
        assertEquals(ConversationMode.REVIEW, parsed.mode());
        assertEquals(Environment.PROD, parsed.environment());
        assertEquals("main", parsed.baseRef());
        assertEquals("feature/pay", parsed.headRef());
        assertEquals("看回调幂等", parsed.question());
    }

    @Test
    void parsesReviewRangeSyntax() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse("审查 main..feature/pay 看回调幂等");
        assertEquals(ConversationMode.REVIEW, parsed.mode());
        assertEquals("main", parsed.baseRef());
        assertEquals("feature/pay", parsed.headRef());
        assertEquals("看回调幂等", parsed.question());
        assertNull(parsed.environment());
    }

    @Test
    void parsesNaturalLanguageCodeReviewWithTwoBranches() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse(
                "@常风的智能助手 拉取 feature/20260828_cf_keepWatching分支和release分支的代码进行代码review，将结果输出成表格。"
        );
        assertEquals(ConversationMode.REVIEW, parsed.mode());
        assertEquals("release", parsed.baseRef());
        assertEquals("feature/20260828_cf_keepWatching", parsed.headRef());
        assertTrue(parsed.question().contains("表格"));
    }

    @Test
    void naturalReviewWithoutBranchesStaysReviewNotInvestigate() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse("帮我做一下代码审查，输出表格");
        assertEquals(ConversationMode.REVIEW, parsed.mode());
        assertNull(parsed.baseRef());
        assertNull(parsed.headRef());
    }

    @Test
    void investigateMentioningReviewWithoutBranchSignal() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse("帮我审查下线上 5xx 超时");
        assertEquals(ConversationMode.INVESTIGATE, parsed.mode());
    }

    @Test
    void resetThreadKeyword() {
        FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse("新对话");
        assertTrue(parsed.resetThread());
    }
}
