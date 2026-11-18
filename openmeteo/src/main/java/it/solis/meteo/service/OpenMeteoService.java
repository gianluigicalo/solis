package it.solis.meteo.service;

import it.solis.meteo.client.OpenMeteoClient;
import it.solis.meteo.client.UpstreamResponse;
import it.solis.meteo.config.OpenMeteoProperties;
import it.solis.meteo.config.OpenMeteoProperties.PanelOrientation;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inoltra le richieste a Open-Meteo forzando le coordinate configurate e mettendo in cache
 * le risposte positive per {@code openmeteo.cache-ttl}.
 */
@Service
public class OpenMeteoService {

    static final Set<String> VARIABLE_GROUPS = Set.of("current", "hourly", "daily", "minutely_15");
    private static final Set<String> FIXED_PARAMETERS = Set.of("latitude", "longitude");
    private static final int MAX_CACHE_ENTRIES = 500;

    private final OpenMeteoClient client;
    private final OpenMeteoProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final Map<URI, CachedResponse> cache = new ConcurrentHashMap<>();

    public OpenMeteoService(OpenMeteoClient client, OpenMeteoProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Result fetch(OpenMeteoApi api, MultiValueMap<String, String> requestParams) {
        return fetch(buildUri(api, requestParams, null));
    }

    /** Irraggiamento per una falda configurata, con il suo tilt/azimuth (sovrascrivibili dai parametri). */
    public Result fetchSolar(String planeName, MultiValueMap<String, String> requestParams) {
        PanelOrientation orientation = properties.planes().get(planeName);
        if (orientation == null) {
            throw new UnknownPlaneException(planeName, properties.planes().keySet());
        }
        return fetch(buildUri(OpenMeteoApi.SOLAR, requestParams, orientation));
    }

    /** Irraggiamento per tutte le falde configurate, in ordine di nome. */
    public Map<String, Result> fetchSolarAllPlanes(MultiValueMap<String, String> requestParams) {
        var results = new LinkedHashMap<String, Result>();
        properties.planes().forEach((name, orientation) ->
                results.put(name, fetch(buildUri(OpenMeteoApi.SOLAR, requestParams, orientation))));
        return results;
    }

    private Result fetch(URI uri) {
        Instant now = clock.instant();
        CachedResponse cached = cache.get(uri);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return new Result(cached.response(), true);
        }
        UpstreamResponse response = client.get(uri);
        if (response.successful()) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
                if (cache.size() >= MAX_CACHE_ENTRIES) {
                    cache.clear();
                }
            }
            cache.put(uri, new CachedResponse(response, now.plus(properties.cacheTtl())));
        }
        return new Result(response, false);
    }

    URI buildUri(OpenMeteoApi api, MultiValueMap<String, String> requestParams, PanelOrientation orientation) {
        var params = new LinkedMultiValueMap<String, String>();
        requestParams.forEach((name, values) -> {
            if (!FIXED_PARAMETERS.contains(name)) {
                params.put(name, List.copyOf(values));
            }
        });
        if (params.keySet().stream().noneMatch(VARIABLE_GROUPS::contains)) {
            api.defaultVariables().forEach(params::add);
        }
        params.putIfAbsent("timezone", List.of(properties.timezone()));
        if (orientation != null) {
            params.putIfAbsent("tilt", List.of(String.valueOf(orientation.tilt())));
            params.putIfAbsent("azimuth", List.of(String.valueOf(orientation.azimuth())));
        }
        params.set("latitude", String.valueOf(properties.latitude()));
        params.set("longitude", String.valueOf(properties.longitude()));

        return UriComponentsBuilder.fromUri(baseUrl(api))
                .queryParams(params)
                .encode()
                .build()
                .toUri();
    }

    private URI baseUrl(OpenMeteoApi api) {
        return switch (api) {
            case FORECAST, SOLAR -> properties.forecastUrl();
            case ARCHIVE -> properties.archiveUrl();
            case AIR_QUALITY -> properties.airQualityUrl();
        };
    }

    public record Result(UpstreamResponse response, boolean fromCache) {
    }

    private record CachedResponse(UpstreamResponse response, Instant expiresAt) {
    }
}
