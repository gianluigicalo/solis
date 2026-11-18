package it.solis.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.modbus.Category;
import it.solis.modbus.SolisRegister;

import java.time.Instant;

@Schema(description = "Valore di un singolo registro")
public record RegisterValue(
        @Schema(description = "Istante della lettura Modbus") Instant readAt,
        SolisRegister name,
        Category category,
        Measurement measurement) {
}
