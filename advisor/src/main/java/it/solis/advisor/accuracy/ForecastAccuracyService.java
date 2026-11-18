package it.solis.advisor.accuracy;

import it.solis.advisor.accuracy.AccuracyReport.Day;
import it.solis.advisor.accuracy.AccuracyReport.Hour;
import it.solis.advisor.accuracy.AccuracyReport.StringTotals;
import it.solis.advisor.accuracy.AccuracyReport.Summary;
import it.solis.advisor.accuracy.AccuracyReport.Verdict;
import it.solis.advisor.accuracy.AccuracyRepository.HourlyAverage;
import it.solis.advisor.config.AdvisorProperties;
import it.solis.advisor.context.ForecastCalculator;
import it.solis.advisor.context.SiteSettings;
import it.solis.advisor.history.SunTimes;
import it.solis.advisor.history.SunTimesService;
import it.solis.advisor.store.StateStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Confronta, ora per ora, la produzione FV reale con:
 * <ul>
 *   <li>la <b>previsione</b>: irraggiamento Open-Meteo come era previsto prima dell'ora (l'ultima disponibile, quella
 *       scaricata prima dell'alba o quella della sera prima), convertito in energia con la stessa formula usata dalle
 *       regole;</li>
 *   <li>la <b>stima a posteriori</b>: la stessa formula con l'irraggiamento analizzato dopo i fatti. Se sbaglia anche
 *       questa, l'errore è nel modello dell'impianto (performance ratio, falde, ombre) e non nella previsione.</li>
 * </ul>
 * Sono escluse le ore con letture dell'inverter incomplete e quelle con la batteria carica, in cui l'inverter può
 * aver limitato la produzione.
 */
@Service
public class ForecastAccuracyService {

    static final Duration MAX_RANGE = Duration.ofDays(92);

    /** Sotto questa energia oraria (Wh) l'ora è considerata notte. */
    private static final double DAYLIGHT_WH = 10;
    /** Sotto questa energia (Wh) non si calcolano errori percentuali. */
    private static final double MIN_ENERGY_FOR_PCT_WH = 50;
    /** Energia minima prodotta (Wh) per dare un giudizio. */
    private static final double MIN_ENERGY_FOR_VERDICT_WH = 500;
    /** Quota minima dell'ora coperta da letture dell'inverter. */
    private static final double MIN_COVERAGE = 0.66;
    /** SOC medio oltre il quale la produzione può essere stata limitata. */
    private static final double FULL_BATTERY_SOC = 97;
    private static final Pattern STRING_NAME = Pattern.compile("pv(\\d+)");

    private final AccuracyRepository repository;
    private final StateStore store;
    private final Clock clock;
    private final ZoneId zone;
    private final Duration inverterInterval;
    private final SunTimesService sunTimes;

    public ForecastAccuracyService(AccuracyRepository repository, StateStore store, Clock clock,
                                   AdvisorProperties properties, SunTimesService sunTimes) {
        this.repository = repository;
        this.store = store;
        this.clock = clock;
        this.zone = ZoneId.of(properties.timezone());
        this.inverterInterval = properties.history().inverterInterval();
        this.sunTimes = sunTimes;
    }

    /**
     * @param from      inizio (arrotondato all'ora), null = 24 ore prima della fine
     * @param to        fine (arrotondata all'ora completa precedente), null = adesso
     * @param selection quale previsione archiviata usare
     */
    public AccuracyReport report(Instant from, Instant to, ForecastSelection selection) {
        Instant end = (to != null ? to : clock.instant()).truncatedTo(ChronoUnit.HOURS);
        Instant start = from != null ? from.truncatedTo(ChronoUnit.HOURS) : end.minus(Duration.ofHours(24));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Il periodo deve contenere almeno un'ora completa");
        }
        if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("Il periodo può essere al massimo di " + MAX_RANGE.toDays() + " giorni");
        }

        SiteSettings settings = store.settings();
        var warnings = new LinkedHashSet<String>();

        var actual = repository.hourlyAverage("PV_TOTAL_POWER", start, end);
        var soc = repository.hourlyAverage("BATTERY_SOC", start, end);
        Map<String, Map<Instant, HourlyAverage>> actualByString = new TreeMap<>();
        for (String name : new TreeMap<>(settings.strings()).keySet()) {
            var m = STRING_NAME.matcher(name);
            if (m.matches()) {
                actualByString.put(name, repository.hourlyStringPower(Integer.parseInt(m.group(1)), start, end));
            }
        }

        Map<LocalDate, SunTimes> sun = selection == ForecastSelection.PRE_DAWN
                ? sunTimes.get(start.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate())
                : Map.of();
        Map<Instant, Instant> cutoffs = new TreeMap<>();
        int daysWithoutSun = 0;
        for (Instant t = start.plus(Duration.ofHours(1)); !t.isAfter(end); t = t.plus(Duration.ofHours(1))) {
            cutoffs.put(t, cutoff(t, selection, sun));
        }
        if (selection == ForecastSelection.PRE_DAWN) {
            daysWithoutSun = (int) start.atZone(zone).toLocalDate().datesUntil(end.atZone(zone).toLocalDate().plusDays(1))
                    .filter(day -> !sun.containsKey(day)).count();
            if (daysWithoutSun > 0) {
                warnings.add(daysWithoutSun + " giorni senza orario dell'alba da Open-Meteo: per questi è usata la "
                        + "previsione scaricata entro la mezzanotte.");
            }
        }
        var forecast = repository.forecastIrradiance(cutoffs);
        var model = repository.latestIrradiance(start, end);
        var forecastList = new ArrayList<String>();
        var forecastByString = ForecastCalculator.energyByString(forecast.byPlane(), settings, forecastList);
        var modelByString = ForecastCalculator.energyByString(model.byPlane(), settings, forecastList);
        warnings.addAll(forecastList);

        int expectedSamples = (int) Math.max(1, Duration.ofHours(1).dividedBy(inverterInterval));

        // ---------------------------------------------------------------- ore
        List<Hour> hours = new ArrayList<>();
        List<Instant> compared = new ArrayList<>();
        int limited = 0;
        int incomplete = 0;
        int withoutForecast = 0;
        for (Instant t = start.plus(Duration.ofHours(1)); !t.isAfter(end); t = t.plus(Duration.ofHours(1))) {
            HourlyAverage a = actual.get(t);
            double coverage = a == null ? 0 : Math.min(1, (double) a.samples() / expectedSamples);
            Double actualWh = a == null ? null : a.average(); // W medi nell'ora = Wh
            Double forecastWh = sumAt(forecastByString, t);
            Double modelWh = sumAt(modelByString, t);
            Double socAvg = soc.containsKey(t) ? soc.get(t).average() : null;
            boolean daylight = above(actualWh) || above(forecastWh) || above(modelWh);

            String status;
            if (!daylight && (actualWh == null || coverage < MIN_COVERAGE || forecastWh == null)) {
                status = "NIGHT"; // di notte i dati mancanti non contano
            } else if (actualWh == null || coverage < MIN_COVERAGE) {
                status = "INCOMPLETE";
                incomplete++;
            } else if (daylight && socAvg != null && socAvg >= FULL_BATTERY_SOC) {
                status = "LIMITED";
                limited++;
            } else if (forecastWh == null) {
                status = "NO_FORECAST";
                withoutForecast++;
            } else {
                status = daylight ? "OK" : "NIGHT";
                compared.add(t);
            }

            Map<String, Double> actualStrings = new LinkedHashMap<>();
            final Instant hour = t;
            actualByString.forEach((name, series) -> {
                var v = series.get(hour);
                if (v != null) {
                    actualStrings.put(name, round(v.average()));
                }
            });
            Map<String, Double> forecastStrings = new LinkedHashMap<>();
            forecastByString.forEach((name, series) -> {
                var v = series.get(hour);
                if (v != null) {
                    forecastStrings.put(name, round(v));
                }
            });
            hours.add(new Hour(t, round(actualWh), round(forecastWh), round(modelWh), forecast.issuedAt().get(t),
                    Math.round(coverage * 100) / 100.0, socAvg == null ? null : Math.round(socAvg * 10) / 10.0, status, actualStrings,
                    forecastStrings));
        }

        // ---------------------------------------------------------------- totali (solo ore confrontabili)
        var byHour = new LinkedHashMap<Instant, Hour>();
        hours.forEach(h -> byHour.put(h.time(), h));
        Totals forecastTotals = new Totals();
        Totals modelTotals = new Totals();
        int daylightCompared = 0;
        Map<LocalDate, DayTotals> days = new TreeMap<>();
        for (Instant t : compared) {
            double a = actual.get(t).average();
            double f = sumAt(forecastByString, t);
            Double m = sumAt(modelByString, t);
            if (!above(a) && !above(f) && !above(m)) {
                continue; // notte: zero ovunque, non cambia i totali
            }
            daylightCompared++;
            forecastTotals.add(a, f);
            if (m != null) {
                modelTotals.add(a, m);
            }
            // l'ora che termina alle 00:00 appartiene al giorno prima
            days.computeIfAbsent(t.minusSeconds(60).atZone(zone).toLocalDate(), d -> new DayTotals()).add(a, f, m);
        }

        Map<String, StringTotals> strings = new LinkedHashMap<>();
        for (String name : forecastByString.keySet()) {
            double f = 0;
            double m = 0;
            double a = 0;
            boolean hasActual = actualByString.containsKey(name);
            boolean hasModel = true;
            for (Instant t : compared) {
                f += forecastByString.get(name).getOrDefault(t, 0.0);
                Double mv = modelByString.getOrDefault(name, Map.of()).get(t);
                if (mv == null) {
                    hasModel = false;
                } else {
                    m += mv;
                }
                if (hasActual) {
                    var av = actualByString.get(name).get(t);
                    if (av == null) {
                        hasActual = false;
                    } else {
                        a += av.average();
                    }
                }
            }
            strings.put(name, new StringTotals(round(settings.stringPeakPowerW(name)), hasActual ? round(a) : null,
                    round(f), hasModel ? round(m) : null, hasActual ? errorPct(f, a) : null));
        }

        Double modelError = modelTotals.count > 0 ? errorPct(modelTotals.estimated, modelTotals.actual) : null;
        Double suggestedPr = modelTotals.actual > MIN_ENERGY_FOR_VERDICT_WH && modelTotals.estimated > MIN_ENERGY_FOR_VERDICT_WH
                ? Math.round(Math.clamp(settings.performanceRatio() * modelTotals.actual / modelTotals.estimated, 0.1, 1.0)
                * 100) / 100.0
                : null;
        Double forecastError = errorPct(forecastTotals.estimated, forecastTotals.actual);
        Double forecastHourly = forecastTotals.hourlyErrorPct();
        Verdict verdict = verdict(daylightCompared, forecastTotals.actual, forecastError, forecastHourly);

        var summary = new Summary(daylightCompared, limited, incomplete, withoutForecast,
                round(forecastTotals.actual), round(forecastTotals.estimated),
                modelTotals.count > 0 ? round(modelTotals.estimated) : null,
                forecastError, forecastHourly, modelError, modelTotals.count > 0 ? modelTotals.hourlyErrorPct() : null,
                suggestedPr, verdict, verdictText(verdict, forecastTotals, forecastError, forecastHourly, selection));

        List<Day> dayRows = new ArrayList<>();
        days.forEach((day, d) -> dayRows.add(new Day(day, d.hours, round(d.actual), round(d.forecast),
                d.modelComplete ? round(d.model) : null, errorPct(d.forecast, d.actual))));

        var notes = notes(summary, settings, dayRows, strings, selection);
        notes.addAll(warnings);
        return new AccuracyReport(start, end, selection, settings.performanceRatio(), summary, dayRows, strings, hours,
                notes);
    }

    /**
     * Limite di scaricamento della previsione per l'ora che termina a {@code hourEnd}: mai dopo l'inizio dell'ora.
     */
    private Instant cutoff(Instant hourEnd, ForecastSelection selection, Map<LocalDate, SunTimes> sun) {
        Instant hourStart = hourEnd.minus(Duration.ofHours(1));
        LocalDate day = hourStart.atZone(zone).toLocalDate();
        Instant midnight = day.atStartOfDay(zone).toInstant();
        Instant limit = switch (selection) {
            case LATEST -> hourStart;
            case EVENING -> midnight;
            case PRE_DAWN -> sun.containsKey(day) ? sun.get(day).sunrise() : midnight;
        };
        return limit.isBefore(hourStart) ? limit : hourStart;
    }

    // ---------------------------------------------------------------- giudizio

    private static Verdict verdict(int hours, double actualWh, Double totalError, Double hourlyError) {
        if (hours < 4 || actualWh < MIN_ENERGY_FOR_VERDICT_WH || totalError == null || hourlyError == null) {
            return Verdict.DATI_INSUFFICIENTI;
        }
        double total = Math.abs(totalError);
        if (total <= 15 && hourlyError <= 35) {
            return Verdict.AFFIDABILE;
        }
        if (total <= 30 && hourlyError <= 60) {
            return Verdict.DISCRETA;
        }
        return Verdict.INAFFIDABILE;
    }

    private static String verdictText(Verdict verdict, Totals totals, Double totalError, Double hourlyError,
                                      ForecastSelection selection) {
        String anticipo = selection.description();
        if (verdict == Verdict.DATI_INSUFFICIENTI) {
            return "Dati insufficienti per un giudizio con " + anticipo + ": servono almeno 4 ore di sole "
                    + "confrontabili e 500 Wh prodotti.";
        }
        String confronto = String.format(java.util.Locale.ITALY,
                "Con %s la stima è stata %,.0f Wh contro %,.0f Wh reali (%+.0f%% sul totale, errore orario medio %.0f%%). ",
                anticipo, totals.estimated, totals.actual, totalError, hourlyError);
        return confronto + switch (verdict) {
            case AFFIDABILE -> "La previsione è affidabile: si può usare nelle regole, con un margine sulle soglie "
                    + "pari all'errore osservato.";
            case DISCRETA -> "La previsione è discreta: usala con soglie prudenti (margine di almeno il 30%) e "
                    + "verifica su più giorni prima di affidarti.";
            case INAFFIDABILE -> "La previsione non è affidabile in questo periodo: evita regole che dipendono da "
                    + "soglie strette sulla produzione prevista.";
            case DATI_INSUFFICIENTI -> "";
        };
    }

    private static List<String> notes(Summary s, SiteSettings settings, List<Day> days, Map<String, StringTotals> strings,
                                      ForecastSelection selection) {
        List<String> notes = new ArrayList<>();
        if (days.size() < 3) {
            notes.add("Il periodo copre meno di 3 giorni: l'errore dipende molto dal meteo di quei giorni. Prima di "
                    + "decidere sulle regole confronta almeno una settimana.");
        }
        if (s.modelErrorPct() != null && s.verdict() != Verdict.DATI_INSUFFICIENTI) {
            if (Math.abs(s.modelErrorPct()) > 10) {
                notes.add(String.format(java.util.Locale.ITALY, "Anche con l'irraggiamento a posteriori la stima "
                                + "sbaglia del %+.0f%%: parte dell'errore è nel modello dell'impianto (performance ratio, "
                                + "ombre, guadagno bifacciale), non nella previsione meteo.%s", s.modelErrorPct(),
                        s.suggestedPerformanceRatio() == null ? "" : String.format(java.util.Locale.ITALY,
                                " Con performanceRatio %.2f (ora %.2f) la stima a posteriori coinciderebbe con il reale.",
                                s.suggestedPerformanceRatio(), settings.performanceRatio())));
            } else {
                notes.add(String.format(java.util.Locale.ITALY, "Con l'irraggiamento a posteriori la stima sbaglia "
                        + "solo del %+.0f%%: il modello dell'impianto è corretto, l'errore restante dipende dalla "
                        + "previsione meteo.", s.modelErrorPct()));
            }
        }
        var errors = strings.entrySet().stream().filter(e -> e.getValue().forecastErrorPct() != null).toList();
        if (errors.size() > 1) {
            double min = errors.stream().mapToDouble(e -> e.getValue().forecastErrorPct()).min().orElse(0);
            double max = errors.stream().mapToDouble(e -> e.getValue().forecastErrorPct()).max().orElse(0);
            if (max - min > 15) {
                notes.add(String.format(java.util.Locale.ITALY, "Le stringhe si discostano in modo diverso dalla "
                        + "stima (da %+.0f%% a %+.0f%%): controlla pannelli e falde configurati per ciascuna, o "
                        + "eventuali ombre.", min, max));
            }
        }
        if (s.hoursLimited() > 0) {
            notes.add(s.hoursLimited() + " ore di sole escluse: batteria carica (SOC medio ≥ " + (int) FULL_BATTERY_SOC
                    + "%), l'inverter può aver limitato la produzione.");
        }
        if (s.hoursIncomplete() > 0) {
            notes.add(s.hoursIncomplete() + " ore di sole escluse per letture dell'inverter mancanti o incomplete.");
        }
        if (s.hoursWithoutForecast() > 0) {
            notes.add(s.hoursWithoutForecast() + " ore di sole senza una previsione scaricata " + selection.when()
                    + ": lo storico delle previsioni parte da quando advisor le raccoglie (o il servizio era fermo).");
        }
        return notes;
    }

    // ---------------------------------------------------------------- utilità

    private static Double sumAt(Map<String, Map<Instant, Double>> byString, Instant t) {
        Double total = null;
        for (var series : byString.values()) {
            Double v = series.get(t);
            if (v != null) {
                total = (total == null ? 0 : total) + v;
            }
        }
        return total;
    }

    private static boolean above(Double wh) {
        return wh != null && wh > DAYLIGHT_WH;
    }

    private static Double errorPct(double estimated, double actual) {
        return actual > MIN_ENERGY_FOR_PCT_WH ? Math.round((estimated - actual) / actual * 1000) / 10.0 : null;
    }

    /** Wh con un decimale. */
    private static Double round(Double value) {
        return value == null ? null : round(value.doubleValue());
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static final class Totals {
        double actual;
        double estimated;
        double absoluteError;
        int count;

        void add(double a, double e) {
            actual += a;
            estimated += e;
            absoluteError += Math.abs(e - a);
            count++;
        }

        Double hourlyErrorPct() {
            return actual > MIN_ENERGY_FOR_PCT_WH ? Math.round(absoluteError / actual * 1000) / 10.0 : null;
        }
    }

    private static final class DayTotals {
        int hours;
        double actual;
        double forecast;
        double model;
        boolean modelComplete = true;

        void add(double a, double f, Double m) {
            hours++;
            actual += a;
            forecast += f;
            if (m == null) {
                modelComplete = false;
            } else {
                model += m;
            }
        }
    }
}
