package com.fitconnect.classservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI classServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Class Service API")
                .description("Gestion des cours de sport FitConnect")
                .version("v1"));
    }
}
