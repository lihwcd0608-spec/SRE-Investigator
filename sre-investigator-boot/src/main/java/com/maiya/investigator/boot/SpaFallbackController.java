package com.maiya.investigator.boot;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaFallbackController {
    @GetMapping({
            "/",
            "/projects",
            "/projects/{id}",
            "/projects/{id}/repositories",
            "/projects/{id}/bindings",
            "/projects/{id}/agents",
            "/projects/{id}/feishu",
            "/projects/{id}/investigate",
            "/projects/{id}/review",
            "/automations",
            "/automations/{jobId}/executions/{executionId}",
            "/projects/{id}/automations",
            "/projects/{id}/automations/{jobId}/executions/{executionId}",
            "/settings/model",
            "/settings/users",
            "/settings/roles",
            "/settings/usage",
            "/login"
    })
    public String spa() {
        return "forward:/index.html";
    }
}
