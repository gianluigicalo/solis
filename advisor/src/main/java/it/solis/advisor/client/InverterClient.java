package it.solis.advisor.client;

import it.solis.advisor.config.AdvisorProperties;
import it.solis.advisor.context.InverterState;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Client del servizio inverter (progetto inverter, endpoint /api/inverter/summary). */
@Component
public class InverterClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public InverterClient(AdvisorProperties properties, JsonMapper jsonMapper) {
        this.restClient = RestClients.create(properties.inverterUrl(), properties.timeout());
        this.jsonMapper = jsonMapper;
    }

    public InverterState summary() {
        try {
            String body = restClient.get().uri("/api/inverter/summary").retrieve().body(String.class);
            return jsonMapper.readValue(body, InverterState.class);
        } catch (RestClientException e) {
            throw new UpstreamException("Servizio inverter non disponibile: " + e.getMessage(), e);
        }
    }

    /** Tutti i registri, raggruppati per categoria (/api/inverter/data). */
    public JsonNode data() {
        return getJson("/api/inverter/data");
    }

    /** Definizioni dei registri (/api/inverter/registers). */
    public JsonNode registers() {
        return getJson("/api/inverter/registers");
    }

    private JsonNode getJson(String path) {
        try {
            return jsonMapper.readTree(restClient.get().uri(path).retrieve().body(String.class));
        } catch (RestClientException e) {
            throw new UpstreamException("Servizio inverter non disponibile: " + e.getMessage(), e);
        }
    }
}
