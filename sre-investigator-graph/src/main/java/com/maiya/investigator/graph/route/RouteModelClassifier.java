package com.maiya.investigator.graph.route;

import java.util.List;
import java.util.Optional;

public interface RouteModelClassifier {
    Optional<InvestigationRoute> classify(String question, List<InvestigationRoute> routes, String provider, String model);
}
