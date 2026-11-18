package it.solis.meteo.service;

import it.solis.meteo.client.OpenMeteoClient;
import it.solis.meteo.client.UpstreamResponse;
import it.solis.meteo.config.OpenMeteoProperties;
import it.solis.meteo.config.OpenMeteoProperties.PanelOrientation;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMeteoServiceTest {

    private static final OpenMeteoProperties PROPERTIES = new OpenMeteoProperties(41.1588, 16.7667, "Europe/Rome",
            new LinkedHashMap<>(Map.of("falda10", new PanelOrientation(10, 0))), Duration.ofSeconds(5), Duration.ofMinutes(10),
            URI.create("https://api.open-meteo.com/v1/forecast"),
            URI.create("https://archive-api.open-meteo.com/v1/archive"),
            URI.create("https://air-quality-api.open-meteo.com/v1/air-quality"));

    private final AtomicInteger calls = new AtomicInteger();
    private final OpenMeteoService service = new OpenMeteoService(new OpenMeteoClient(PROPERTIES) {
        @Override
        public UpstreamResponse get(URI uri) {
            calls.incrementAndGet();
            return new UpstreamResponse(200, "{}");
        }
    }, PROPERTIES);

    @Test
    void forcesConfiguredCoordinatesAndAddsDefaults() {
        var request = new LinkedMultiValueMap<String, String>();
        request.add("latitude", "0");
        request.add("forecast_days", "3");

        var query = UriComponentsBuilder.fromUri(service.buildUri(OpenMeteoApi.FORECAST, request, null)).build().getQueryParams();

        assertThat(query.get("latitude")).containsExactly("41.1588");
        assertThat(query.get("longitude")).containsExactly("16.7667");
        assertThat(query.getFirst("timezone")).isEqualTo("Europe/Rome");
        assertThat(query.getFirst("forecast_days")).isEqualTo("3");
        assertThat(query.keySet()).contains("current", "hourly", "daily");
    }

    @Test
    void keepsClientVariablesWithoutDefaults() {
        var request = new LinkedMultiValueMap<String, String>();
        request.add("hourly", "temperature_2m");

        var query = UriComponentsBuilder.fromUri(service.buildUri(OpenMeteoApi.FORECAST, request, null)).build().getQueryParams();

        assertThat(query.get("hourly")).containsExactly("temperature_2m");
        assertThat(query).doesNotContainKeys("current", "daily");
    }

    @Test
    void solarUsesPlaneOrientationUnlessOverridden() {
        var uri = service.buildUri(OpenMeteoApi.SOLAR, new LinkedMultiValueMap<>(), new PanelOrientation(17, -15));
        var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        assertThat(query.getFirst("tilt")).isEqualTo("17");
        assertThat(query.getFirst("azimuth")).isEqualTo("-15");
        assertThat(query.getFirst("hourly")).contains("global_tilted_irradiance");

        var override = new LinkedMultiValueMap<String, String>();
        override.add("tilt", "25");
        var overridden = UriComponentsBuilder.fromUri(
                service.buildUri(OpenMeteoApi.SOLAR, override, new PanelOrientation(17, -15))).build().getQueryParams();
        assertThat(overridden.get("tilt")).containsExactly("25");
    }

    @Test
    void rejectsUnknownPlane() {
        assertThatThrownBy(() -> service.fetchSolar("falda99", new LinkedMultiValueMap<>()))
                .isInstanceOf(UnknownPlaneException.class)
                .hasMessageContaining("falda10");
    }

    @Test
    void cachesSuccessfulResponses() {
        var request = new LinkedMultiValueMap<String, String>();
        assertThat(service.fetch(OpenMeteoApi.FORECAST, request).fromCache()).isFalse();
        assertThat(service.fetch(OpenMeteoApi.FORECAST, request).fromCache()).isTrue();
        assertThat(calls).hasValue(1);
    }
}
