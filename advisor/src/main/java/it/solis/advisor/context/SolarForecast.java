package it.solis.advisor.context;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Produzione FV stimata dall'irraggiamento previsto sulle falde (energie in Wh)")
public record SolarForecast(
        @Schema(description = "Produzione stimata per oggi (Wh)", example = "12400") double todayWh,
        @Schema(description = "Produzione stimata da adesso a fine giornata (Wh)", example = "5100")
        double todayRemainingWh,
        @Schema(description = "Produzione stimata per domani (Wh)", example = "18000") double tomorrowWh,
        @Schema(description = "Parte della produzione rimanente di oggi che supera l'uscita AC dell'inverter: "
                + "può andare solo in batteria, altrimenti viene persa (Wh)", example = "0")
        double todayRemainingAboveAcWh,
        @Schema(description = "Parte della produzione di domani che supera l'uscita AC dell'inverter: può andare "
                + "solo in batteria, altrimenti viene persa (Wh)", example = "4200") double tomorrowAboveAcWh,
        @Schema(description = "Dettaglio per stringa (può mancare nelle simulazioni)") Map<String, StringForecast> byString,
        @Schema(description = "Dettaglio orario (può mancare nelle simulazioni)") List<HourlyEstimate> hourly) {
}
