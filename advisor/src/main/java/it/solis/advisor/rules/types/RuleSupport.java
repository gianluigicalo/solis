package it.solis.advisor.rules.types;

import it.solis.advisor.context.EnergyContext;
import it.solis.advisor.rules.RuleOutcome;

import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;

/** Funzioni di utilità comuni ai tipi di regola. */
final class RuleSupport {

    private RuleSupport() {
    }

    /** Fascia oraria [start, end), anche a cavallo della mezzanotte (es. 23:00-07:00). */
    static boolean inWindow(LocalTime time, LocalTime start, LocalTime end) {
        return start.isBefore(end)
                ? !time.isBefore(start) && time.isBefore(end)
                : !time.isBefore(start) || time.isBefore(end);
    }

    /** Motivo di non applicabilità se mancano i dati richiesti. */
    static Optional<RuleOutcome> missingData(EnergyContext context, boolean needsInverter, boolean needsForecast) {
        if (needsInverter && (context.inverter() == null || context.inverter().batterySoc() == null)) {
            return Optional.of(RuleOutcome.noMatch("Dati dell'inverter non disponibili"));
        }
        if (needsForecast && context.forecast() == null) {
            return Optional.of(RuleOutcome.noMatch("Previsione solare non disponibile"));
        }
        return Optional.empty();
    }

    /** Energia (Wh) corrispondente a una differenza di SOC. */
    static double energyWh(EnergyContext context, double socDelta) {
        return socDelta / 100.0 * context.settings().batteryCapacityWh();
    }

    /** Ore necessarie per trasferire l'energia indicata (Wh) alla potenza massima della batteria. */
    static double hoursAtMaxPower(EnergyContext context, double wh) {
        return wh / context.settings().inverter().maxBatteryPowerW();
    }

    /** Numero con un decimale (SOC, ore). */
    static String fmt(double value) {
        return String.format(Locale.ITALY, "%.1f", value);
    }

    /** Energia o potenza intera con il separatore delle migliaia, seguita dall'unità: "6.000 Wh", "6.000 W". */
    static String fmt(double value, String unit) {
        return String.format(Locale.ITALY, "%,.0f %s", value, unit);
    }
}
