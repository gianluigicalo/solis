package it.solis.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.modbus.Category;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Tutti i dati letti dall'inverter, raggruppati per categoria")
public record InverterData(
        @Schema(description = "Istante della lettura Modbus") Instant readAt,
        @Schema(description = "Categoria -> (nome registro -> valore)")
        Map<Category, Map<String, Measurement>> categories) {
}
