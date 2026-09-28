package com.maiya.investigator.graph.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InvestigationRouteMatcherTest {
    private final List<InvestigationRoute> routes = InvestigationRouteCatalog.BUILTIN;

    @Test
    void parsesBareIdAndFencedReply() {
        assertEquals("order-payment", InvestigationRouteMatcher.parseModelReply("order-payment", routes).orElseThrow().id());
        assertEquals("http-5xx", InvestigationRouteMatcher.parseModelReply("```\nhttp-5xx\n```", routes).orElseThrow().id());
        assertEquals("order-payment", InvestigationRouteMatcher.parseModelReply("方向：order-payment", routes).orElseThrow().id());
        assertEquals("http-5xx", InvestigationRouteMatcher.parseModelReply("接口/可用性", routes).orElseThrow().id());
        assertTrue(InvestigationRouteMatcher.parseModelReply("随便写点别的", routes).isEmpty());
    }

    @Test
    void matchesKeywordsAndPrefersNonFallbackOnTie() {
        assertEquals("order-payment", InvestigationRouteMatcher.matchKeywords("用户支付回调丢了", routes).orElseThrow().id());
        assertEquals("http-5xx", InvestigationRouteMatcher.matchKeywords("网关超时大量 5xx", routes).orElseThrow().id());
        assertEquals("order-payment", InvestigationRouteMatcher.matchKeywords("订单接口超时", routes).orElseThrow().id());
        assertTrue(InvestigationRouteMatcher.matchKeywords("登录验证码收不到", routes).isEmpty());
    }
}
