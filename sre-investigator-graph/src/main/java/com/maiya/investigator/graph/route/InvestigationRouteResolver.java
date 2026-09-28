package com.maiya.investigator.graph.route;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InvestigationRouteResolver {
    private static final Logger log = LoggerFactory.getLogger(InvestigationRouteResolver.class);
    private final InvestigationRouteCatalog catalog;
    private final RouteModelClassifier classifier;

    public InvestigationRouteResolver(InvestigationRouteCatalog catalog, RouteModelClassifier classifier) {
        this.catalog = catalog;
        this.classifier = classifier;
    }

    public RouteDecision resolve(String requestedRoute, String question, String provider, String model) {
        Optional<InvestigationRoute> chosen = catalog.find(requestedRoute);
        if (chosen.isPresent()) {
            RouteDecision decision = new RouteDecision(chosen.get(), RouteSource.USER);
            log.info("排查方向 source=USER route={} question={}", decision.id(), preview(question));
            return decision;
        }
        if (requestedRoute != null && !requestedRoute.isBlank() && !InvestigationRouteCatalog.isAuto(requestedRoute)) {
            log.warn("未知排查方向 {}，改为自动分类", requestedRoute);
        }
        if (classifier != null) {
            Optional<InvestigationRoute> classified = classifier.classify(question, catalog.all(), provider, model);
            if (classified.isPresent()) {
                RouteDecision decision = new RouteDecision(classified.get(), RouteSource.MODEL);
                log.info("排查方向 source=MODEL route={} question={}", decision.id(), preview(question));
                return decision;
            }
        }
        Optional<InvestigationRoute> keyword = InvestigationRouteMatcher.matchKeywords(question, catalog.all());
        if (keyword.isPresent()) {
            RouteDecision decision = new RouteDecision(keyword.get(), RouteSource.KEYWORD);
            log.info("排查方向 source=KEYWORD route={} question={}", decision.id(), preview(question));
            return decision;
        }
        RouteDecision decision = new RouteDecision(catalog.fallback(), RouteSource.DEFAULT);
        log.info("排查方向 source=DEFAULT route={} question={}", decision.id(), preview(question));
        return decision;
    }

    public String fallbackId() {
        return catalog.fallbackId();
    }

    public InvestigationRouteCatalog catalog() {
        return catalog;
    }

    private static String preview(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String compact = question.replaceAll("\\s+", " ").trim();
        return compact.length() > 120 ? compact.substring(0, 120) + "…" : compact;
    }
}
