package it.solis.meteo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@Validated
@ConfigurationProperties("openmeteo")
public record OpenMeteoProperties(
        @DecimalMin("-90") @DecimalMax("90") double latitude,
        @DecimalMin("-180") @DecimalMax("180") double longitude,
        @NotBlank String timezone,
        /* Falde (gruppi di pannelli con lo stesso orientamento) per nome, ordinate alfabeticamente. */
        @NotEmpty Map<String, @Valid @NotNull PanelOrientation> planes,
        @NotNull Duration timeout,
        @NotNull Duration cacheTtl,
        @NotNull URI forecastUrl,
        @NotNull URI archiveUrl,
        @NotNull URI airQualityUrl) {

    public OpenMeteoProperties {
        // L'ordine dei binding (yml + variabili d'ambiente) non è garantito: si ordina per nome.
        planes = planes == null ? null : Collections.unmodifiableSortedMap(new TreeMap<>(planes));
    }

    /** Orientamento di una falda: tilt 0° = orizzontale; azimuth 0° = sud, -90° = est, 90° = ovest. */
    public record PanelOrientation(@Min(0) @Max(90) int tilt, @Min(-180) @Max(180) int azimuth) {
    }
}
