package it.solis.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Riepilogo dei flussi di potenza istantanei e dell'energia del giorno")
public record PowerFlow(
        @Schema(description = "Istante della lettura Modbus") Instant readAt,
        @Schema(example = "In produzione") String status,
        @Schema(description = "Potenza prodotta dai pannelli (W)", example = "5402") BigDecimal pvPower,
        @Schema(description = "Potenza batteria (W): positiva in carica, negativa in scarica", example = "-850")
        BigDecimal batteryPower,
        @Schema(example = "SCARICA") BatteryState batteryState,
        @Schema(description = "Stato di carica batteria (%)", example = "100") BigDecimal batterySoc,
        @Schema(description = "Scambio con la rete (W): positivo in immissione, negativo in prelievo", example = "2947")
        BigDecimal gridPower,
        @Schema(example = "IMMISSIONE") GridState gridState,
        @Schema(description = "Consumo della casa (W)", example = "2363") BigDecimal houseLoadPower,
        @Schema(description = "Consumo carichi backup (W)", example = "0") BigDecimal backupLoadPower,
        @Schema(description = "Temperatura inverter (°C)", example = "57.6") BigDecimal inverterTemperature,
        EnergyToday today,
        Electrical electrical) {

    public enum BatteryState { CARICA, SCARICA, INATTIVA }

    public enum GridState { IMMISSIONE, PRELIEVO, NEUTRO }

    @Schema(description = "Tensioni e correnti istantanee (null se non disponibili)")
    public record Electrical(
            @Schema(description = "Tensione MPPT 1 (V)", example = "398.7") BigDecimal pv1Voltage,
            @Schema(description = "Corrente MPPT 1 (A)", example = "6.9") BigDecimal pv1Current,
            @Schema(description = "Tensione MPPT 2 (V)", example = "412.3") BigDecimal pv2Voltage,
            @Schema(description = "Corrente MPPT 2 (A)", example = "6.0") BigDecimal pv2Current,
            @Schema(description = "Tensione del bus DC dell'inverter (V)", example = "469.2") BigDecimal dcBusVoltage,
            @Schema(description = "Corrente AC in uscita, fase A (A)", example = "22.4") BigDecimal acCurrent,
            @Schema(description = "Tensione di rete al contatore (V)", example = "239.7") BigDecimal gridVoltage,
            @Schema(description = "Corrente di rete al contatore (A)", example = "7.8") BigDecimal gridCurrent) {
    }

    @Schema(description = "Energia del giorno corrente (Wh). I contatori dell'inverter hanno risoluzione 100 Wh")
    public record EnergyToday(
            @Schema(example = "22200") BigDecimal pvProduction,
            @Schema(example = "9500") BigDecimal houseConsumption,
            @Schema(example = "100") BigDecimal gridImport,
            @Schema(example = "3300") BigDecimal gridExport,
            @Schema(example = "11300") BigDecimal batteryCharge,
            @Schema(example = "3000") BigDecimal batteryDischarge) {
    }
}
