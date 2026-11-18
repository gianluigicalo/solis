package it.solis.advisor.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

@Schema(description = "Creazione o sostituzione completa di una regola. I parametri omessi prendono il default.")
public record RuleRequest(
        @Schema(description = "Solo in creazione; se omesso viene generato", example = "carica-domenica")
        @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}", message = "solo minuscole, cifre e trattini") String id,
        @Schema(example = "Carica da rete la domenica") @NotBlank String name,
        @Schema(description = "Tipo di regola (vedi /api/rules/types)", example = "LOW_FORECAST_GRID_CHARGE")
        @NotBlank String type,
        @Schema(description = "Default true") Boolean enabled,
        @Schema(description = "Priorità (minore = valutata prima). Default 100", example = "15") Integer priority,
        @Schema(example = "{\"targetSoc\": 90, \"maxForecastWh\": 8000}") Map<String, Object> parameters) {
}
