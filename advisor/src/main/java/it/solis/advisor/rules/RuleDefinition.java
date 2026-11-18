package it.solis.advisor.rules;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Regola configurata: un tipo di regola con i suoi parametri")
public record RuleDefinition(
        @Schema(example = "carica-notturna") String id,
        @Schema(example = "Carica da rete di notte se domani c'è poco sole") String name,
        @Schema(description = "Tipo di regola (vedi /api/rules/types)", example = "LOW_FORECAST_GRID_CHARGE") String type,
        boolean enabled,
        @Schema(description = "Le regole sono valutate per priorità crescente: vince la prima soddisfatta",
                example = "10") int priority,
        @Schema(example = "{\"windowStart\": \"23:00\", \"windowEnd\": \"07:00\", \"maxForecastWh\": 6000, \"targetSoc\": 80}")
        Map<String, Object> parameters) {
}
