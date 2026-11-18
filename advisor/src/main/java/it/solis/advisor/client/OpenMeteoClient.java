package it.solis.advisor.client;

import it.solis.advisor.config.AdvisorProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Client del servizio openmeteo (progetto openmeteo, endpoint /api/meteo/solar). */
@Component
public class OpenMeteoClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String timezone;

    public OpenMeteoClient(AdvisorProperties properties, JsonMapper jsonMapper) {
        this.restClient = RestClients.create(properties.openmeteoUrl(), properties.timeout());
        this.jsonMapper = jsonMapper;
        this.timezone = properties.timezone();
    }

    /** Irraggiamento orario sul piano di ogni stringa per oggi e domani. */
    public JsonNode solarForecast() {
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/api/meteo/solar")
                            .queryParam("hourly", "global_tilted_irradiance")
                            .queryParam("forecast_days", 2)
                            .queryParam("timezone", timezone)
                            .build())
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(body);
        } catch (RestClientException e) {
            throw new UpstreamException("Servizio openmeteo non disponibile: " + e.getMessage(), e);
        }
    }

    /** Chiamata generica al servizio openmeteo, es. get("/api/meteo/forecast", Map.of("hourly", "...")). */
    public JsonNode get(String path, Map<String, ?> params) {
        try {
            String body = restClient.get()
                    .uri(uri -> {
                        uri.path(path);
                        params.forEach(uri::queryParam);
                        return uri.build();
                    })
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(body);
        } catch (RestClientException e) {
            throw new UpstreamException("Servizio openmeteo non disponibile: " + e.getMessage(), e);
        }
    }
}
