package com.select.global.config;

import com.select.global.security.SecurityPaths;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI babiOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("BABI API").version("v1")
                        .description("BABI 백엔드 API 문서"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public OpenApiCustomizer publicAuthenticationOperations() {
        return openAPI -> {
            for (String path : List.of(SecurityPaths.LOGIN, SecurityPaths.SIGNUP,
                    SecurityPaths.EMAIL_SEND, SecurityPaths.EMAIL_VERIFY)) {
                var pathItem = openAPI.getPaths().get(path);
                if (pathItem != null) {
                    pathItem.readOperations().forEach(operation -> operation.setSecurity(List.of()));
                }
            }
        };
    }
}
