package com.maiya.investigator.boot;

import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.graph.InvestigationAgent;
import com.maiya.investigator.graph.conversation.ConversationStrategyRegistry;
import com.maiya.investigator.graph.conversation.ConversationStrategyRegistry.ModeDescriptor;
import com.maiya.investigator.graph.route.InvestigationRouteCatalog;
import com.maiya.investigator.graph.route.RouteDescriptor;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentCapabilitiesController {
    private final InvestigationAgent agent;
    private final ConversationStrategyRegistry modes;
    private final InvestigationRouteCatalog routes;
    private final AgentBudgetService budget;

    public AgentCapabilitiesController(
            InvestigationAgent agent,
            ConversationStrategyRegistry modes,
            InvestigationRouteCatalog routes,
            AgentBudgetService budget
    ) {
        this.agent = agent;
        this.modes = modes;
        this.routes = routes;
        this.budget = budget;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return agent.capabilities();
    }

    @GetMapping("/budget")
    @RequirePermission(any = {Permissions.INVESTIGATE, Permissions.REVIEW, Permissions.MODEL})
    public AgentBudgetService.Snapshot budget() {
        return budget.snapshot();
    }

    @PutMapping("/budget")
    @RequirePermission(Permissions.MODEL_WRITE)
    public AgentBudgetService.Snapshot updateBudget(@RequestBody(required = false) BudgetUpdateRequest request) {
        BudgetUpdateRequest body = request == null ? new BudgetUpdateRequest(null, null) : request;
        return budget.update(body.maxUserTurns(), body.maxToolCalls());
    }

    @GetMapping("/modes")
    public List<ModeDescriptor> modes() {
        return modes.descriptors();
    }

    @GetMapping("/routes")
    public List<RouteDescriptor> routes() {
        return routes.descriptors();
    }

    public record BudgetUpdateRequest(Integer maxUserTurns, Integer maxToolCalls) {
    }
}
