package it.solis.advisor.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Modifica parziale: i campi omessi restano invariati, i parametri indicati sostituiscono "
        + "solo quelli con lo stesso nome. Il tipo non si può cambiare.")
public record RulePatch(
        String name,
        Boolean enabled,
        Integer priority,
        @Schema(example = "{\"targetSoc\": 90}") Map<String, Object> parameters) {
}
