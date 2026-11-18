package it.solis.advisor.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Stato attuale dell'inverter (da /api/inverter/summary)")
public record InverterState(
        @Schema(description = "Stato di carica della batteria", example = "65") Double batterySoc,
        @Schema(description = "Produzione FV (W)", example = "2400") Double pvPower,
        @Schema(description = "Potenza batteria (W): positiva in carica, negativa in scarica", example = "-800")
        Double batteryPower,
        @Schema(description = "Consumo della casa (W)", example = "600") Double houseLoadPower,
        @Schema(description = "Potenza di rete (W): positiva in immissione, negativa in prelievo", example = "1000")
        Double gridPower,
        @Schema(example = "In produzione") String status) {
}
