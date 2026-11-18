package it.solis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI solisOpenApi(SolisProperties properties) {
        return new OpenAPI().info(new Info()
                .title("Solis Inverter API")
                .version("1.0.0")
                .description("API in sola lettura dei dati esposti via Modbus TCP dall'inverter Solis "
                        + "(" + properties.host() + ":" + properties.port() + ", unit " + properties.unitId() + ")."));
    }
}
