package it.solis.meteo.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openMeteoOpenApi(OpenMeteoProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Open-Meteo API")
                        .version("1.0.0")
                        .description("Espone le API di Open-Meteo sulle coordinate fisse "
                                + properties.latitude() + " N, " + properties.longitude() + " E. "
                                + "I parametri di query vengono inoltrati così come sono; latitude e longitude "
                                + "sono ignorati. Dati: Open-Meteo.com (CC BY 4.0)."))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentazione dei parametri Open-Meteo")
                        .url("https://open-meteo.com/en/docs"));
    }
}
