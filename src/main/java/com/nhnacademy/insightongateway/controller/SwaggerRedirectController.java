package com.nhnacademy.insightongateway.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SwaggerRedirectController {

    private static final Map<String, String> API_DOCS_PATHS = Map.of(
            "auth", "/auth/v3/api-docs",
            "core", "/core/v3/api-docs",
            "ai", "/ai/v3/api-docs",
            "engine", "/engine/v3/api-docs"
    );

    private static final String SWAGGER_UI_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Swagger UI</title>
                <link rel="stylesheet" href="/webjars/swagger-ui/swagger-ui.css" />
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="/webjars/swagger-ui/swagger-ui-bundle.js"></script>
                <script src="/webjars/swagger-ui/swagger-ui-standalone-preset.js"></script>
                <script>
                    window.onload = function () {
                        SwaggerUIBundle({
                            url: "%s",
                            dom_id: "#swagger-ui",
                            presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
                            layout: "StandaloneLayout",
                            operationsSorter: "method",
                            tagsSorter: "alpha"
                        });
                    };
                </script>
            </body>
            </html>
            """;

    @GetMapping("/swagger/{service}")
    public ResponseEntity<String> swaggerUi(@PathVariable String service) {
        String apiDocsPath = API_DOCS_PATHS.get(service);
        if (apiDocsPath == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(SWAGGER_UI_HTML.formatted(apiDocsPath));
    }
}
