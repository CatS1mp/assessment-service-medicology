package com.medicology.assessment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI assessmentOpenApi(AssessmentProperties assessmentProperties) {
        Server localServer = new Server()
                .url(assessmentProperties.getApiLocalServerUrl())
                .description("Local assessment service");

        Server productionServer = new Server()
                .url(assessmentProperties.getApiProductionServerUrl())
                .description("Production assessment service");

        return new OpenAPI()
                .info(new Info()
                        .title(assessmentProperties.getApiTitle())
                        .description(assessmentProperties.getApiDescription()))
                .servers(List.of(productionServer, localServer))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
