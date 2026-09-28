package com.maiya.investigator.graph.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InvestigationRouteCatalogTest {
    @Test
    void emptyYamlUsesBuiltinTable() {
        InvestigationRouteCatalog catalog = new InvestigationRouteCatalog(new InvestigationRouteProperties());
        assertEquals(2, catalog.all().size());
        assertEquals("http-5xx", catalog.fallbackId());
        assertTrue(catalog.find("http-5xx").orElseThrow().toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.K8S));
        assertTrue(catalog.find("http-5xx").orElseThrow().toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.ARMS));
        assertTrue(catalog.find("http-5xx").orElseThrow().toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.LOAD_BALANCER));
        assertTrue(!catalog.find("http-5xx").orElseThrow().toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.CDN));
        assertTrue(catalog.find("order-payment").orElseThrow().toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.REDIS));
        assertTrue(!catalog.find("order-payment").orElseThrow().toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.K8S));
        assertTrue(catalog.find("auto").isEmpty());
        assertEquals("order-payment", catalog.find("order-payment").orElseThrow().id());
    }

    @Test
    void configuredItemsReplaceBuiltin() {
        InvestigationRouteProperties properties = new InvestigationRouteProperties();
        properties.setFallback("login");
        InvestigationRouteProperties.Item login = new InvestigationRouteProperties.Item();
        login.setId("login");
        login.setLabel("登录/鉴权");
        login.setDescription("登录失败、验证码、token");
        login.setKeywords(List.of("登录", "验证码", "token"));
        InvestigationRouteProperties.Item always = new InvestigationRouteProperties.Item();
        always.setId("always");
        always.setLabel("应被忽略");
        properties.setItems(List.of(login, always));

        InvestigationRouteCatalog catalog = new InvestigationRouteCatalog(properties);
        assertEquals(1, catalog.all().size());
        assertEquals("login", catalog.fallbackId());
        assertTrue(catalog.find("http-5xx").isEmpty());
        assertEquals("登录/鉴权", catalog.descriptors().get(0).label());
        assertTrue(catalog.all().get(0).toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.CODE));
        assertTrue(catalog.all().get(0).toolKinds().contains(com.maiya.investigator.domain.control.ToolKind.MYSQL));
    }
}
