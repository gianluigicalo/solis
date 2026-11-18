package it.solis.advisor.context;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Produzione stimata di una stringa (Wh), confrontabile con i registri PV1/PV2 dell'inverter")
public record StringForecast(
        @Schema(description = "Potenza di picco (W)", example = "5535") double peakPowerW,
        double todayWh,
        double todayRemainingWh,
        double tomorrowWh) {
}
