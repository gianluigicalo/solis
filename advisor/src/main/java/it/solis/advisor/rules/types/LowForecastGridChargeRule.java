package it.solis.advisor.rules.types;

import it.solis.advisor.context.EnergyContext;
import it.solis.advisor.rules.Action;
import it.solis.advisor.rules.ParameterDefinition;
import it.solis.advisor.rules.RuleOutcome;
import it.solis.advisor.rules.RuleParameters;
import it.solis.advisor.rules.RuleType;
import org.springframework.stereotype.Component;

import java.util.List;

import static it.solis.advisor.rules.types.RuleSupport.fmt;

/**
 * Nella fascia a basso costo (es. F3 notturna), se il sole previsto per la giornata non basterà a caricare la
 * batteria, consiglia di caricarla dalla rete fino al SOC obiettivo.
 */
@Component
public class LowForecastGridChargeRule implements RuleType {

    @Override
    public String id() {
        return "LOW_FORECAST_GRID_CHARGE";
    }

    @Override
    public String description() {
        return "Nella fascia oraria indicata, se la produzione FV prevista per la prossima giornata è sotto soglia "
                + "e il SOC è sotto l'obiettivo, carica la batteria dalla rete. Prima di mezzogiorno considera la "
                + "produzione rimanente di oggi, dopo quella di domani.";
    }

    @Override
    public Action action() {
        return Action.CHARGE_FROM_GRID;
    }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.time("windowStart", "23:00", "Inizio fascia di carica (tariffa bassa)"),
                ParameterDefinition.time("windowEnd", "07:00", "Fine fascia di carica"),
                ParameterDefinition.number("maxForecastWh", 6000, 0, 200_000, "Wh",
                        "Si carica dalla rete solo se la produzione prevista è inferiore a questo valore"),
                ParameterDefinition.integer("targetSoc", 80, 10, 100, "%", "SOC da raggiungere"));
    }

    @Override
    public RuleOutcome evaluate(EnergyContext context, RuleParameters p) {
        var missing = RuleSupport.missingData(context, true, true);
        if (missing.isPresent()) {
            return missing.get();
        }
        var start = p.time("windowStart");
        var end = p.time("windowEnd");
        if (!RuleSupport.inWindow(context.time().toLocalTime(), start, end)) {
            return RuleOutcome.noMatch("Fuori dalla fascia " + start + "-" + end);
        }
        boolean morning = context.time().getHour() < 12;
        double expected = morning ? context.forecast().todayRemainingWh() : context.forecast().tomorrowWh();
        String day = morning ? "per il resto di oggi" : "per domani";
        double threshold = p.number("maxForecastWh");
        if (expected >= threshold) {
            return RuleOutcome.noMatch("Produzione prevista " + day + " " + fmt(expected, "Wh")
                    + ", non sotto la soglia di " + fmt(threshold, "Wh"));
        }
        double soc = context.inverter().batterySoc();
        int target = p.integer("targetSoc");
        if (soc >= target) {
            return RuleOutcome.noMatch("SOC " + fmt(soc) + "% già pari o superiore all'obiettivo " + target + "%");
        }
        double wh = RuleSupport.energyWh(context, target - soc);
        return RuleOutcome.match("Produzione prevista " + day + " solo " + fmt(expected, "Wh") + " (soglia "
                + fmt(threshold, "Wh") + "): caricare dal " + fmt(soc) + "% al " + target + "%, circa "
                + fmt(wh, "Wh") + " (almeno " + fmt(RuleSupport.hoursAtMaxPower(context, wh)) + " h a "
                + fmt(context.settings().inverter().maxBatteryPowerW(), "W") + ")");
    }
}
