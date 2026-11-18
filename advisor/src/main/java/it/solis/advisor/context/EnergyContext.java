package it.solis.advisor.context;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Dati su cui vengono valutate le regole")
public record EnergyContext(
        @Schema(description = "Ora locale della valutazione", example = "2026-11-15T23:30:00") LocalDateTime time,
        @Schema(description = "Stato inverter; null se non disponibile") InverterState inverter,
        @Schema(description = "Previsione FV; null se non disponibile") SolarForecast forecast,
        SiteSettings settings,
        @Schema(description = "Problemi incontrati nel raccogliere i dati") List<String> warnings) {
}
