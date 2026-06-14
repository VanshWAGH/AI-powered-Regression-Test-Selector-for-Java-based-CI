package com.ai.rts.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(@Value("${ai.rts.api-token:}") String apiToken) {
        OpenAPI doc = new OpenAPI()
                .info(new Info()
                        .title("AI-RTS API")
                        .description("Regression test selection for Java CI. Use CLI/GitHub Actions in production; Swagger is for manual testing.")
                        .version("0.1.0"));

        if (apiToken != null && !apiToken.isBlank()) {
            doc.components(new Components().addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .description("Paste the same value as server env AI_RTS_API_TOKEN (without the word Bearer).")));
            doc.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        }
        return doc;
    }
}
