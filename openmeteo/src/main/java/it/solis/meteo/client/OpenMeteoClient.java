package it.solis.meteo.client;

import it.solis.meteo.config.OpenMeteoProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/** Client HTTP verso Open-Meteo. Gli errori 4xx/5xx di Open-Meteo non sono eccezioni: vengono restituiti. */
@Component
public class OpenMeteoClient {

    private final RestClient restClient;

    public OpenMeteoClient(OpenMeteoProperties properties) {
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public UpstreamResponse get(URI uri) {
        try {
            return restClient.get().uri(uri).exchange((request, response) -> new UpstreamResponse(
                    response.getStatusCode().value(),
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw new OpenMeteoUnavailableException("Open-Meteo non raggiungibile: " + rootMessage(e), e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && !(t instanceof IOException)) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
