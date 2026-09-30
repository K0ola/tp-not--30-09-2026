package com.fitconnect.bookingservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Booking Service API")
                .description("Reservations FitConnect : saga reservation -> paiement -> confirmation")
                .version("v1"));
    }
}
