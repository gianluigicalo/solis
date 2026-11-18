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

/** Di giorno, se il sole rimanente non ricaricherà la batteria, consiglia di non venderne l'energia. */
@Component
public class LowForecastHoldRule implements RuleType {

    @Override
    public String id() {
        return "LOW_FORECAST_HOLD";
    }

    @Override
    public String description() {
        return "Nella fascia oraria indicata, se la produzione FV rimanente di oggi è sotto soglia e il SOC è sotto "
                + "il limite, conserva la carica per i consumi serali invece di immetterla in rete.";
    }

    @Override
    public Action action() {
        return Action.HOLD_BATTERY;
    }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.time("windowStart", "08:00", "Inizio fascia di controllo"),
                ParameterDefinition.time("windowEnd", "19:00", "Fine fascia di controllo"),
                ParameterDefinition.number("maxRemainingWh", 3000, 0, 200_000, "Wh",
                        "Si conserva la batteria se la produzione rimanente di oggi è inferiore a questo valore"),
                ParameterDefinition.integer("maxSoc", 60, 0, 100, "%",
                        "Si conserva la batteria solo se il SOC è inferiore a questo valore"));
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
        double remaining = context.forecast().todayRemainingWh();
        double threshold = p.number("maxRemainingWh");
        if (remaining >= threshold) {
            return RuleOutcome.noMatch("Produzione rimanente oggi " + fmt(remaining, "Wh") + ", non sotto la soglia di "
                    + fmt(threshold, "Wh"));
        }
        double soc = context.inverter().batterySoc();
        int maxSoc = p.integer("maxSoc");
        if (soc >= maxSoc) {
            return RuleOutcome.noMatch("SOC " + fmt(soc) + "% non sotto il limite di " + maxSoc + "%");
        }
        return RuleOutcome.match("Restano solo " + fmt(remaining, "Wh") + " di sole oggi e il SOC è al " + fmt(soc)
                + "%: conservare la carica per la sera");
    }
}
