package it.solis.meteo.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.solis.meteo.config.OpenMeteoProperties;
import it.solis.meteo.config.OpenMeteoProperties.PanelOrientation;
import it.solis.meteo.service.OpenMeteoApi;
import it.solis.meteo.service.OpenMeteoService;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Espone le API Open-Meteo sulle coordinate configurate. I parametri di query sono inoltrati senza modifiche
 * (vedi https://open-meteo.com/en/docs); latitude e longitude vengono sempre sostituiti da quelli configurati.
 */
@RestController
@RequestMapping(path = "/api/meteo", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Open-Meteo", description = "API Open-Meteo sulle coordinate configurate")
@ApiResponse(responseCode = "200", description = "Risposta di Open-Meteo, inalterata",
        content = @Content(schema = @Schema(type = "object")))
@ApiResponse(responseCode = "400", description = "Parametri rifiutati da Open-Meteo ({\"error\":true,\"reason\":...})",
        content = @Content(schema = @Schema(type = "object")))
@ApiResponse(responseCode = "502", description = "Open-Meteo non raggiungibile",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
public class OpenMeteoController {

    private static final String VARIABLES = "Elenco di variabili separate da virgola. "
            + "Se non si indica nessuno tra current, hourly, daily e minutely_15 si usa un set predefinito.";
    private static final String CACHE_HEADER = "X-Cache";

    private final OpenMeteoService service;
    private final OpenMeteoProperties properties;
    private final JsonMapper jsonMapper;

    public OpenMeteoController(OpenMeteoService service, OpenMeteoProperties properties, JsonMapper jsonMapper) {
        this.service = service;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping("/location")
    @Operation(summary = "Coordinate e impostazioni usate per tutte le richieste")
    public Location location() {
        return new Location(properties.latitude(), properties.longitude(), properties.timezone(),
                properties.planes());
    }

    @GetMapping("/forecast")
    @Operation(summary = "Previsioni meteo (Open-Meteo /v1/forecast)",
            description = "Previsioni fino a 16 giorni e, con past_days, dati recenti. Senza parametri restituisce "
                    + "meteo attuale, previsione oraria e giornaliera.")
    @Parameter(name = "current", in = ParameterIn.QUERY, description = VARIABLES,
            example = "temperature_2m,weather_code,cloud_cover")
    @Parameter(name = "hourly", in = ParameterIn.QUERY, description = VARIABLES,
            example = "temperature_2m,precipitation_probability")
    @Parameter(name = "daily", in = ParameterIn.QUERY, description = VARIABLES,
            example = "temperature_2m_max,temperature_2m_min,sunrise,sunset")
    @Parameter(name = "minutely_15", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "forecast_days", in = ParameterIn.QUERY, description = "Giorni di previsione (0-16, default 7)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "past_days", in = ParameterIn.QUERY, description = "Giorni passati da includere (0-92)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "start_date", in = ParameterIn.QUERY, description = "Inizio intervallo (yyyy-MM-dd)")
    @Parameter(name = "end_date", in = ParameterIn.QUERY, description = "Fine intervallo (yyyy-MM-dd)")
    @Parameter(name = "models", in = ParameterIn.QUERY, description = "Modelli meteo (es. icon_seamless, ecmwf_ifs025)")
    @Parameter(name = "timezone", in = ParameterIn.QUERY, description = "Fuso orario (default da configurazione)")
    @Parameter(name = "temperature_unit", in = ParameterIn.QUERY, description = "celsius | fahrenheit")
    @Parameter(name = "wind_speed_unit", in = ParameterIn.QUERY, description = "kmh | ms | mph | kn")
    @Parameter(name = "precipitation_unit", in = ParameterIn.QUERY, description = "mm | inch")
    public ResponseEntity<String> forecast(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        return forward(OpenMeteoApi.FORECAST, params);
    }

    @GetMapping("/solar")
    @Operation(summary = "Irraggiamento solare per tutte le falde (Open-Meteo /v1/forecast)",
            description = "Una richiesta Open-Meteo per ogni falda configurata, ciascuna con il proprio tilt e "
                    + "azimut. La risposta è un oggetto con una chiave per falda (es. falda10, falda17) il cui valore è la "
                    + "risposta Open-Meteo inalterata. Il set predefinito contiene radiazione globale, diretta, "
                    + "diffusa, DNI e irraggiamento sul piano dei pannelli (global_tilted_irradiance). "
                    + "Valori in W/m², somme giornaliere in MJ/m².")
    @ApiResponse(responseCode = "200", description = "Risposta Open-Meteo per ogni falda",
            content = @Content(schema = @Schema(type = "object", example = "{\"falda10\": {}, \"falda17\": {}}")))
    @Parameter(name = "current", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "hourly", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "daily", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "minutely_15", in = ParameterIn.QUERY, description = VARIABLES,
            example = "shortwave_radiation,global_tilted_irradiance")
    @Parameter(name = "forecast_days", in = ParameterIn.QUERY, description = "Giorni di previsione (0-16)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "past_days", in = ParameterIn.QUERY, description = "Giorni passati da includere (0-92)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "timezone", in = ParameterIn.QUERY, description = "Fuso orario (default da configurazione)")
    public ResponseEntity<String> solar(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        params.remove("tilt");
        params.remove("azimuth");
        Map<String, OpenMeteoService.Result> results = service.fetchSolarAllPlanes(params);
        for (var result : results.values()) {
            if (!result.response().successful()) {
                return toResponse(result);
            }
        }
        var body = jsonMapper.createObjectNode();
        results.forEach((name, result) -> body.set(name, jsonMapper.readTree(result.response().body())));
        boolean allCached = results.values().stream().allMatch(OpenMeteoService.Result::fromCache);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(CACHE_HEADER, allCached ? "HIT" : "MISS")
                .body(jsonMapper.writeValueAsString(body));
    }

    @GetMapping("/solar/{plane}")
    @Operation(summary = "Irraggiamento solare per una falda (Open-Meteo /v1/forecast)",
            description = "Come /solar ma per una sola falda: restituisce la risposta Open-Meteo inalterata. "
                    + "tilt e azimuth, se passati, sostituiscono quelli configurati per la falda.")
    @ApiResponse(responseCode = "404", description = "Falda non configurata",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @Parameter(name = "plane", in = ParameterIn.PATH, description = "Nome della falda configurata", example = "falda10")
    @Parameter(name = "current", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "hourly", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "daily", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "minutely_15", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "tilt", in = ParameterIn.QUERY, description = "Inclinazione in gradi (0-90)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "azimuth", in = ParameterIn.QUERY, description = "Azimut: 0 = sud, -90 = est, 90 = ovest",
            schema = @Schema(type = "integer"))
    @Parameter(name = "forecast_days", in = ParameterIn.QUERY, description = "Giorni di previsione (0-16)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "past_days", in = ParameterIn.QUERY, description = "Giorni passati da includere (0-92)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "timezone", in = ParameterIn.QUERY, description = "Fuso orario (default da configurazione)")
    public ResponseEntity<String> solarPlane(@PathVariable("plane") String plane,
                                              @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        return toResponse(service.fetchSolar(plane, params));
    }

    @GetMapping("/archive")
    @Operation(summary = "Storico meteo (Open-Meteo /v1/archive, rianalisi ERA5 dal 1940)",
            description = "Richiede start_date ed end_date. I dati degli ultimi giorni (circa 5) possono mancare.")
    @Parameter(name = "start_date", in = ParameterIn.QUERY, required = true, description = "yyyy-MM-dd",
            example = "2026-10-01")
    @Parameter(name = "end_date", in = ParameterIn.QUERY, required = true, description = "yyyy-MM-dd",
            example = "2026-10-31")
    @Parameter(name = "hourly", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "daily", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "models", in = ParameterIn.QUERY, description = "Modello (es. era5, era5_land, best_match)")
    @Parameter(name = "timezone", in = ParameterIn.QUERY, description = "Fuso orario (default da configurazione)")
    public ResponseEntity<String> archive(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        return forward(OpenMeteoApi.ARCHIVE, params);
    }

    @GetMapping("/air-quality")
    @Operation(summary = "Qualità dell'aria (Open-Meteo /v1/air-quality, modello CAMS)")
    @Parameter(name = "current", in = ParameterIn.QUERY, description = VARIABLES)
    @Parameter(name = "hourly", in = ParameterIn.QUERY, description = VARIABLES, example = "pm10,pm2_5,european_aqi")
    @Parameter(name = "forecast_days", in = ParameterIn.QUERY, description = "Giorni di previsione (0-7)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "past_days", in = ParameterIn.QUERY, description = "Giorni passati da includere (0-92)",
            schema = @Schema(type = "integer"))
    @Parameter(name = "domains", in = ParameterIn.QUERY, description = "auto | cams_europe | cams_global")
    @Parameter(name = "timezone", in = ParameterIn.QUERY, description = "Fuso orario (default da configurazione)")
    public ResponseEntity<String> airQuality(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        return forward(OpenMeteoApi.AIR_QUALITY, params);
    }

    private ResponseEntity<String> forward(OpenMeteoApi api, MultiValueMap<String, String> params) {
        return toResponse(service.fetch(api, params));
    }

    private static ResponseEntity<String> toResponse(OpenMeteoService.Result result) {
        return ResponseEntity.status(result.response().status())
                .contentType(MediaType.APPLICATION_JSON)
                .header(CACHE_HEADER, result.fromCache() ? "HIT" : "MISS")
                .body(result.response().body());
    }

    @Schema(description = "Coordinate e impostazioni usate per le richieste a Open-Meteo")
    public record Location(
            @Schema(example = "41.1588") double latitude,
            @Schema(example = "16.7667") double longitude,
            @Schema(example = "Europe/Rome") String timezone,
            @Schema(description = "Orientamento di ogni falda (tilt: inclinazione; azimuth: 0 = sud)")
            Map<String, PanelOrientation> planes) {
    }
}
