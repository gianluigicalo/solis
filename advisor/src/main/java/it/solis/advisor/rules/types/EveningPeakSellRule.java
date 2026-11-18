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

/** Nella fascia serale (prezzi alti), se domani il sole ricaricherà la batteria, consiglia di vendere l'eccedenza. */
@Component
public class EveningPeakSellRule implements RuleType {

    @Override
    public String id() {
        return "EVENING_PEAK_SELL";
    }

    @Override
    public String description() {
        return "Nella fascia oraria indicata, se il SOC supera la soglia e domani è prevista abbastanza produzione "
                + "per ricaricare, vende in rete l'energia della batteria fino al SOC di riserva.";
    }

    @Override
    public Action action() {
        return Action.SELL_TO_GRID;
    }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.time("windowStart", "19:00", "Inizio fascia di vendita"),
                ParameterDefinition.time("windowEnd", "22:00", "Fine fascia di vendita"),
                ParameterDefinition.integer("minSoc", 70, 0, 100, "%", "Si vende solo se il SOC supera questo valore"),
                ParameterDefinition.integer("reserveSoc", 40, 0, 100, "%",
                        "SOC da conservare per la notte: si vende solo l'energia sopra questo valore"),
                ParameterDefinition.number("minTomorrowWh", 10_000, 0, 200_000, "Wh",
                        "Produzione minima prevista per domani per poter vendere"));
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
        double soc = context.inverter().batterySoc();
        int minSoc = p.integer("minSoc");
        int reserve = p.integer("reserveSoc");
        if (soc <= minSoc || soc <= reserve) {
            return RuleOutcome.noMatch("SOC " + fmt(soc) + "% non sopra la soglia di vendita " + minSoc + "%");
        }
        double tomorrow = context.forecast().tomorrowWh();
        double minTomorrow = p.number("minTomorrowWh");
        if (tomorrow < minTomorrow) {
            return RuleOutcome.noMatch("Domani previsti solo " + fmt(tomorrow, "Wh") + " (minimo "
                    + fmt(minTomorrow, "Wh") + "): meglio non vendere");
        }
        double wh = RuleSupport.energyWh(context, soc - reserve);
        return RuleOutcome.match("Domani previsti " + fmt(tomorrow, "Wh") + ": vendere dal " + fmt(soc) + "% al "
                + reserve + "%, circa " + fmt(wh, "Wh") + " (almeno " + fmt(RuleSupport.hoursAtMaxPower(context, wh))
                + " h a " + fmt(context.settings().inverter().maxBatteryPowerW(), "W") + ")");
    }
}
