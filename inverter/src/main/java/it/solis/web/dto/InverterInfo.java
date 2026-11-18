package it.solis.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Identificazione, configurazione e stato dell'inverter")
public record InverterInfo(
        @Schema(description = "Istante della lettura Modbus") Instant readAt,
        @Schema(example = "0x3173") String productModel,
        @Schema(description = "Famiglia inverter (registro 35000)", example = "0x2190") String modelDefinition,
        @Schema(example = "103173024A290497") String serialNumber,
        @Schema(example = "0x001B") String dspSoftwareVersion,
        @Schema(description = "Versione HMI completa (33002 + 33069)", example = "0x3008") String hmiSoftwareVersion,
        @Schema(example = "0x0001") String protocolVersion,
        @Schema(example = "Monofase") String acOutputType,
        @Schema(description = "Numero di ingressi DC (MPPT)", example = "2") int dcInputs,
        @Schema(description = "Codice normativa di rete", example = "0x003E") String gridStandard,
        @Schema(description = "Modello batteria selezionato", example = "Lithium Battery LV") String batteryModel,
        @Schema(example = "Meter Eastron monofase sul lato rete") String meter,
        @Schema(description = "Limite di immissione in rete (W)", example = "6000") BigDecimal exportLimit,
        @Schema(description = "Data e ora dell'orologio interno dell'inverter") LocalDateTime inverterClock,
        @Schema(example = "0x0003") String status,
        @Schema(example = "In produzione") String statusDescription,
        @Schema(description = "Modalità di accumulo attiva", example = "Autoconsumo") String operatingMode,
        @Schema(description = "Opzioni di accumulo applicate (registro 33132)") List<String> storageModes,
        @Schema(description = "Bit di stato operativo attivi (registro 33121)") List<String> operatingStatus,
        @Schema(description = "Guasti attivi (inverter e BMS)") List<String> activeFaults,
        @Schema(description = "true se è presente almeno un guasto") boolean faultActive) {
}
