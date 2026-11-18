package it.solis.advisor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    @Bean
    Clock clock(AdvisorProperties properties) {
        return Clock.system(ZoneId.of(properties.timezone()));
    }

    @Bean
    OpenAPI advisorOpenApi(AdvisorProperties properties) {
        return new OpenAPI().info(new Info()
                .title("Energy Advisor API")
                .version("1.0.0")
                .description("Valuta regole configurabili sui dati dell'inverter (" + properties.inverterUrl()
                        + ") e sulle previsioni Open-Meteo (" + properties.openmeteoUrl() + ") e consiglia se "
                        + "caricare la batteria dalla rete, conservarla o vendere. Solo consigli: l'inverter non "
                        + "viene comandato."));
    }
}
