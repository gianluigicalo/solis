package it.solis.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.modbus.Category;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Dati di una categoria dell'inverter")
public record CategoryData(
        @Schema(description = "Istante della lettura Modbus") Instant readAt,
        Category category,
        @Schema(description = "Descrizione della categoria") String description,
        @Schema(description = "Nome registro -> valore") Map<String, Measurement> values) {
}
