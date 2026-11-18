package it.solis.advisor.context;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

@Schema(description = "Caratteristiche dell'impianto usate per stimare produzione ed energia in batteria")
public record SiteSettings(
        @Schema(description = "Capacità utile totale delle batterie (Wh)", example = "20000")
        @Positive double batteryCapacityWh,
        @Schema(description = "Rendimento complessivo dell'impianto (perdite di inverter, cavi, temperatura, sporco)",
                example = "0.8") @DecimalMin("0.1") @DecimalMax("1.0") double performanceRatio,
        @Schema(description = "Potenza di picco di un pannello (lato anteriore)", example = "615")
        @Positive double panelPeakPowerW,
        @Valid @NotNull InverterLimits inverter,
        @Schema(description = "Per ogni stringa, numero di pannelli su ciascuna falda. I nomi delle falde sono quelli "
                + "del servizio openmeteo", example = "{\"pv1\": {\"falda10\": 7, \"falda17\": 2}, \"pv2\": {\"falda17\": 10}}")
        @NotEmpty Map<String, @NotEmpty Map<String, @NotNull @Positive Integer>> strings) {

    public SiteSettings withInverter(InverterLimits limits) {
        return new SiteSettings(batteryCapacityWh, performanceRatio, panelPeakPowerW, limits, strings);
    }

    /** Potenza di picco di una stringa (W). */
    public double stringPeakPowerW(String string) {
        return strings.get(string).values().stream().mapToInt(Integer::intValue).sum() * panelPeakPowerW;
    }
}
