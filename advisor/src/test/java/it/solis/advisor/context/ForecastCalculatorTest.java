package it.solis.advisor.context;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastCalculatorTest {

    @Test
    void combinesPlanesPerStringAndSplitsByDay() {
        var solar = JsonMapper.builder().build().readTree("""
                {
                  "falda10": {"hourly": {"time": ["2026-11-15T12:00", "2026-11-15T13:00", "2026-11-16T00:00", "2026-11-16T12:00"],
                                         "global_tilted_irradiance": [500, 1000, 0, 250]}},
                  "falda17": {"hourly": {"time": ["2026-11-15T12:00", "2026-11-15T13:00", "2026-11-16T00:00", "2026-11-16T12:00"],
                                         "global_tilted_irradiance": [1000, 1000, null, 500]}}
                }""");
        // pannelli da 500 W, PR 0,5: ogni pannello produce GTI/1000 × 250 Wh
        var settings = new SiteSettings(20_000, 0.5, 500, new InverterLimits(100_000, 100_000, 10_000),
                Map.of("pv1", Map.of("falda10", 2, "falda17", 2), "pv2", Map.of("falda17", 4), "pv3", Map.of("falda30", 1)));
        var warnings = new ArrayList<String>();

        var forecast = ForecastCalculator.compute(solar, settings, LocalDateTime.parse("2026-11-15T12:30"), warnings);

        var pv1 = forecast.byString().get("pv1");
        assertThat(pv1.peakPowerW()).isEqualTo(2000);
        assertThat(pv1.todayWh()).isEqualTo(1750);           // falda10: 250 + 500   falda17: 500 + 500
        assertThat(pv1.todayRemainingWh()).isEqualTo(1000);  // solo l'ora che termina alle 13:00
        assertThat(pv1.tomorrowWh()).isEqualTo(375);         // 125 + 250
        var pv2 = forecast.byString().get("pv2");
        assertThat(pv2.todayWh()).isEqualTo(2000);
        assertThat(forecast.todayWh()).isEqualTo(3750);
        assertThat(forecast.tomorrowWh()).isEqualTo(875);    // 375 + 500
        assertThat(warnings).singleElement().asString().contains("falda30");
    }

    @Test
    void capsAtInverterPvLimitAndReportsEnergyAboveAcOutput() {
        var solar = JsonMapper.builder().build().readTree("""
                {"falda10": {"hourly": {"time": ["2026-11-16T12:00", "2026-11-16T13:00"],
                                        "global_tilted_irradiance": [1000, 600]}}}""");
        // 20 pannelli da 500 W, PR 1: 10.000 W a 1000 W/m², 6.000 W a 600 W/m²
        var settings = new SiteSettings(20_000, 1.0, 500, new InverterLimits(9000, 5000, 5000),
                Map.of("pv1", Map.of("falda10", 12), "pv2", Map.of("falda10", 8)));

        var forecast = ForecastCalculator.compute(solar, settings, LocalDateTime.parse("2026-11-15T20:00"), new ArrayList<>());

        assertThat(forecast.tomorrowWh()).isEqualTo(15_000);         // 9.000 (tagliato da 10.000) + 6.000
        assertThat(forecast.tomorrowAboveAcWh()).isEqualTo(5000);    // (9.000 - 5.000) + (6.000 - 5.000)
        assertThat(forecast.byString().get("pv1").tomorrowWh()).isEqualTo(9000); // 6.000 × 0,9 + 3.600
        assertThat(forecast.byString().get("pv2").tomorrowWh()).isEqualTo(6000); // 4.000 × 0,9 + 2.400
    }
}
