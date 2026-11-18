package it.solis.advisor.rules;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Esito di una regola")
public record RuleEvaluation(
        String ruleId,
        String ruleName,
        String type,
        int priority,
        Action action,
        boolean matched,
        @Schema(description = "true per la regola che ha determinato la decisione") boolean decisive,
        String reason) {
}
