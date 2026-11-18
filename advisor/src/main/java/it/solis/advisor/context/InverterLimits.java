package it.solis.advisor.context;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/** Limiti dell'inverter (scheda tecnica Solis S6-EH1P6K-L-PLUS, Global V2.0 06/2024). */
@Schema(description = "Limiti di potenza dell'inverter")
public record InverterLimits(
        @Schema(description = "Potenza FV massima utilizzabile, lato DC (W): oltre, la produzione viene tagliata",
                example = "9600") @Positive double maxPvInputW,
        @Schema(description = "Potenza nominale di uscita AC, casa + immissione in rete (W)", example = "6000")
        @Positive double ratedOutputW,
        @Schema(description = "Potenza massima di carica/scarica della batteria (W)", example = "6000")
        @Positive double maxBatteryPowerW) {
}
