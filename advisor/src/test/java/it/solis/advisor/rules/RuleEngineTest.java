package it.solis.advisor.rules;

import it.solis.advisor.context.EnergyContext;
import it.solis.advisor.context.InverterLimits;
import it.solis.advisor.context.InverterState;
import it.solis.advisor.context.SiteSettings;
import it.solis.advisor.context.SolarForecast;
import it.solis.advisor.rules.types.EveningPeakSellRule;
import it.solis.advisor.rules.types.LowForecastGridChargeRule;
import it.solis.advisor.rules.types.LowForecastHoldRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    private static final SiteSettings SETTINGS = new SiteSettings(10_000, 0.8, 615, new InverterLimits(9600, 6000, 5000), Map.of("pv1", Map.of("falda10", 5)));

    private final RuleTypeRegistry registry = new RuleTypeRegistry(List.of(
            new LowForecastGridChargeRule(), new LowForecastHoldRule(), new EveningPeakSellRule()));
    private final RuleEngine engine = new RuleEngine(registry);

    @Test
    void chargesAtNightWhenTomorrowIsCloudy() {
        var advice = engine.evaluate(context("2026-11-15T23:30", 30, 4000, 2000), defaultRules());

        assertThat(advice.action()).isEqualTo(Action.CHARGE_FROM_GRID);
        assertThat(advice.decidedBy()).isEqualTo("charge");
        assertThat(advice.reason()).contains("5.000 Wh").contains("1,0 h").contains("5.000 W)"); // (80 - 30)% di 10.000 Wh a 5.000 W
    }

    @Test
    void sellsInTheEveningWhenTomorrowIsSunny() {
        var advice = engine.evaluate(context("2026-11-15T20:00", 90, 0, 20_000), defaultRules());

        assertThat(advice.action()).isEqualTo(Action.SELL_TO_GRID);
        assertThat(advice.reason()).contains("5.000 Wh"); // (90 - 40)% di 10.000 Wh
    }

    @Test
    void fallsBackToSelfConsumptionAndSkipsDisabledRules() {
        var rules = defaultRules().stream()
                .map(r -> new RuleDefinition(r.id(), r.name(), r.type(), false, r.priority(), r.parameters()))
                .toList();
        var advice = engine.evaluate(context("2026-11-15T23:30", 30, 4000, 2000), rules);

        assertThat(advice.action()).isEqualTo(Action.SELF_CONSUMPTION);
        assertThat(advice.evaluations()).isEmpty();
    }

    @Test
    void doesNotMatchWithoutInverterData() {
        var ctx = new EnergyContext(LocalDateTime.parse("2026-11-15T23:30"), null,
                new SolarForecast(0, 0, 1000, 0, 0, null, null), SETTINGS, List.of());
        var advice = engine.evaluate(ctx, defaultRules());

        assertThat(advice.action()).isEqualTo(Action.SELF_CONSUMPTION);
        assertThat(advice.evaluations().getFirst().reason()).contains("inverter");
    }

    @Test
    void customParametersChangeTheOutcome() {
        var charge = registry.get("LOW_FORECAST_GRID_CHARGE");
        var rule = new RuleDefinition("charge", "c", charge.id(), true, 1,
                registry.normalize(charge, Map.of(), Map.of("maxForecastWh", 1000, "windowStart", "22:00")));
        var advice = engine.evaluate(context("2026-11-15T22:30", 30, 4000, 2000), List.of(rule));

        assertThat(advice.action()).isEqualTo(Action.SELF_CONSUMPTION);
        assertThat(advice.evaluations().getFirst().reason()).contains("non sotto la soglia");
    }

    @Test
    void rejectsInvalidParameters() {
        var charge = registry.get("LOW_FORECAST_GRID_CHARGE");
        assertThatThrownBy(() -> registry.normalize(charge, Map.of(), Map.of("targetSoc", 150)))
                .isInstanceOf(InvalidRuleException.class).hasMessageContaining("targetSoc");
        assertThatThrownBy(() -> registry.normalize(charge, Map.of(), Map.of("foo", 1)))
                .isInstanceOf(InvalidRuleException.class).hasMessageContaining("sconosciuto");
        assertThatThrownBy(() -> registry.normalize(charge, Map.of(), Map.of("windowEnd", "25:00")))
                .isInstanceOf(InvalidRuleException.class).hasMessageContaining("HH:mm");
    }

    private List<RuleDefinition> defaultRules() {
        return List.of(rule("charge", "LOW_FORECAST_GRID_CHARGE", 10), rule("hold", "LOW_FORECAST_HOLD", 20),
                rule("sell", "EVENING_PEAK_SELL", 30));
    }

    private RuleDefinition rule(String id, String type, int priority) {
        return new RuleDefinition(id, id, type, true, priority, registry.normalize(registry.get(type), Map.of(), Map.of()));
    }

    private static EnergyContext context(String time, double soc, double remainingToday, double tomorrow) {
        return new EnergyContext(LocalDateTime.parse(time), new InverterState(soc, 0.0, 0.0, 500.0, 0.0, "ok"),
                new SolarForecast(remainingToday, remainingToday, tomorrow, 0, 0, null, null), SETTINGS, List.of());
    }
}
