package it.solis.advisor.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.advisor.context.EnergyContext;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Consiglio: l'azione della prima regola soddisfatta, oppure autoconsumo")
public record Advice(
        LocalDateTime evaluatedAt,
        Action action,
        @Schema(example = "Caricare la batteria dalla rete") String actionDescription,
        @Schema(description = "Id della regola che ha deciso; null se nessuna è soddisfatta") String decidedBy,
        String reason,
        List<RuleEvaluation> evaluations,
        EnergyContext context) {
}
