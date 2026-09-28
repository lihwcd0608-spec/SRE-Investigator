package com.maiya.investigator.graph.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvestigationRouteResolverTest {
    @Test
    void userSelectionWins() {
        InvestigationRouteResolver resolver = new InvestigationRouteResolver(catalog(), (question, routes, provider, model) -> {
            throw new AssertionError("不应再走模型分类");
        });
        RouteDecision decision = resolver.resolve("order-payment", "随便问接口超时", null, null);
        assertEquals("order-payment", decision.id());
        assertEquals(RouteSource.USER, decision.source());
    }

    @Test
    void modelClassificationRunsBeforeKeywords() {
        InvestigationRouteResolver resolver = new InvestigationRouteResolver(
                catalog(),
                (question, routes, provider, model) -> Optional.of(routes.get(0))
        );
        RouteDecision decision = resolver.resolve(null, "用户支付失败", null, null);
        assertEquals("http-5xx", decision.id());
        assertEquals(RouteSource.MODEL, decision.source());
    }

    @Test
    void keywordsThenDefaultWhenModelMisses() {
        InvestigationRouteResolver resolver = new InvestigationRouteResolver(
                catalog(),
                (question, routes, provider, model) -> Optional.empty()
        );
        assertEquals(RouteSource.KEYWORD, resolver.resolve("auto", "支付回调没到", null, null).source());
        assertEquals("order-payment", resolver.resolve("", "支付回调没到", null, null).id());
        RouteDecision fallback = resolver.resolve(null, "登录验证码收不到", null, null);
        assertEquals("http-5xx", fallback.id());
        assertEquals(RouteSource.DEFAULT, fallback.source());
    }

    @Test
    void unknownRequestedRouteFallsBackToClassify() {
        InvestigationRouteResolver resolver = new InvestigationRouteResolver(catalog(), null);
        RouteDecision decision = resolver.resolve("not-a-route", "订单状态不一致", null, null);
        assertEquals("order-payment", decision.id());
        assertEquals(RouteSource.KEYWORD, decision.source());
    }

    private static InvestigationRouteCatalog catalog() {
        return new InvestigationRouteCatalog(new InvestigationRouteProperties());
    }
}
