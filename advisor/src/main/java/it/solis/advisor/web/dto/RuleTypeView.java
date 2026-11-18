package it.solis.advisor.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.advisor.rules.Action;
import it.solis.advisor.rules.ParameterDefinition;
import it.solis.advisor.rules.RuleType;

import java.util.List;

@Schema(description = "Tipo di regola disponibile")
public record RuleTypeView(String id, String description, Action action, List<ParameterDefinition> parameters) {

    public static RuleTypeView of(RuleType type) {
        return new RuleTypeView(type.id(), type.description(), type.action(), type.parameters());
    }
}
