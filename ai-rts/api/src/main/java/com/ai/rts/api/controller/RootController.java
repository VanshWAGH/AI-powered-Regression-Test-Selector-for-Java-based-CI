package com.ai.rts.api.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root landing response so deployed URLs do not show a Whitelabel 404.
 * Interactive API docs: {@code /swagger-ui/index.html}
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "service", "AI-powered Regression Test Selector (AI-RTS)",
                "type", "REST API — no browser UI; use CLI or GitHub Actions",
                "health", "/actuator/health",
                "openapi", "/v3/api-docs",
                "swaggerUi", "/swagger-ui/index.html",
                "endpoints", List.of(
                        "POST /api/v1/{repoId}/{prId}/recommend",
                        "POST /api/v1/{repoId}/{correlationId}/history/ingest"));
    }
}
