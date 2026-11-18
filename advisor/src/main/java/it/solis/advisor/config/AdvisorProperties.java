package it.solis.advisor.config;

import it.solis.advisor.context.SiteSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("advisor")
public record AdvisorProperties(
        @NotNull URI inverterUrl,
        @NotNull URI openmeteoUrl,
        @NotNull Duration timeout,
        @NotBlank String timezone,
        @NotNull Duration checkInterval,
        @Valid @NotNull History history,
        @Valid @NotNull SiteSettings defaults) {

    /** Raccolta dello storico di inverter e Open-Meteo nel database. */
    public record History(
            boolean enabled,
            @NotNull Duration inverterInterval,
            @NotNull Duration weatherInterval,
            @Min(0) @Max(92) int pastDays,
            @Min(1) @Max(16) int forecastDays,
            @NotEmpty List<String> forecastVariables,
            @NotEmpty List<String> solarVariables,
            @NotEmpty List<String> archiveVariables,
            @Valid @NotNull ForecastRuns forecastRuns) {
    }

    /**
     * Orari in cui archiviare la previsione (weather_forecast_run): la sera per il giorno dopo e, ogni giorno, prima
     * dell'alba e tre volte durante la produzione. Alba e tramonto vengono da Open-Meteo.
     */
    public record ForecastRuns(
            @NotNull Duration checkInterval,
            @NotNull java.time.LocalTime evening,
            @NotNull Duration beforeSunrise,
            @NotNull Duration afterSunrise,
            @NotNull Duration beforeSunset) {
    }
}
