package com.fee.app.schoolfeeapp.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI schoolFeeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("School Fee Management API")
                        .description("RESTful API for school fee management system")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("School Fee App Team")
                                .email("support@schoolfeeapp.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("http://localhost:8081").description("Docker Development")
                ));
    }
}
