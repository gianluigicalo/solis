package it.solis.advisor.context;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converte l'irraggiamento sul piano di ogni falda (W/m², media dell'ora precedente) in energia prodotta.
 * <ul>
 *   <li>Per ogni stringa e falda: Wh = GTI / 1000 × pannelli × W di picco × performance ratio (l'irraggiamento
 *       di riferimento dei pannelli è 1000 W/m²).</li>
 *   <li>In ogni ora il totale non può superare la potenza FV massima dell'inverter: l'eccesso viene tagliato,
 *       in proporzione su tutte le stringhe.</li>
 *   <li>La parte che supera l'uscita AC dell'inverter è indicata a parte: può andare solo in batteria.</li>
 * </ul>
 */
public final class ForecastCalculator {

    private ForecastCalculator() {
    }

    /**
     * @param solar risposta di {@code /api/meteo/solar}: un oggetto per falda con {@code hourly.time} e
     *              {@code hourly.global_tilted_irradiance}
     */
    public static SolarForecast compute(JsonNode solar, SiteSettings settings, LocalDateTime now, List<String> warnings) {
        var irradianceByPlane = readIrradiance(solar);

        var byStringHourly = energyByString(irradianceByPlane, settings, warnings);

        // energia oraria in Wh = potenza media in W; oltre l'uscita AC può andare solo in batteria
        double ratedAc = settings.inverter().ratedOutputW();
        Map<LocalDateTime, Double> totalByHour = new TreeMap<>();
        byStringHourly.values().forEach(m -> m.forEach((t, wh) -> totalByHour.merge(t, wh, Double::sum)));
        Map<LocalDateTime, Double> aboveAcByHour = new TreeMap<>();
        totalByHour.forEach((time, wh) -> aboveAcByHour.put(time, Math.max(0, wh - ratedAc)));

        LocalDate today = now.toLocalDate();
        Map<String, StringForecast> byString = new LinkedHashMap<>();
        byStringHourly.forEach((name, hourly) -> {
            var t = DayTotals.of(hourly, today, now);
            byString.put(name, new StringForecast(settings.stringPeakPowerW(name),
                    round(t.today), round(t.remaining), round(t.tomorrow)));
        });

        var totals = DayTotals.of(totalByHour, today, now);
        var aboveAc = DayTotals.of(aboveAcByHour, today, now);
        List<HourlyEstimate> hourly = new ArrayList<>();
        totalByHour.forEach((time, wh) -> hourly.add(new HourlyEstimate(time, round(wh))));
        return new SolarForecast(round(totals.today), round(totals.remaining), round(totals.tomorrow),
                round(aboveAc.remaining), round(aboveAc.tomorrow), byString, hourly);
    }

    /**
     * Energia oraria (Wh) di ogni stringa a partire dall'irraggiamento di ogni falda (W/m²), già tagliata alla
     * potenza FV massima dell'inverter (in proporzione su tutte le stringhe).
     *
     * @param irradianceByPlane per falda, irraggiamento orario indicizzato per ora (fine dell'ora); le chiavi devono
     *                          essere ordinabili (Instant, LocalDateTime)
     * @param warnings          riceve un avviso per ogni falda configurata ma assente
     * @return per stringa (in ordine di nome), energia per ora
     */
    public static <K> Map<String, Map<K, Double>> energyByString(
            Map<String, Map<K, Double>> irradianceByPlane, SiteSettings settings, List<String> warnings) {
        Map<String, Map<K, Double>> byStringHourly = new LinkedHashMap<>();
        for (var string : new TreeMap<>(settings.strings()).entrySet()) {
            Map<K, Double> stringByHour = new TreeMap<>();
            for (var plane : string.getValue().entrySet()) {
                var series = irradianceByPlane.get(plane.getKey());
                if (series == null) {
                    warnings.add("Falda '" + plane.getKey() + "' della stringa '" + string.getKey()
                            + "' non presente nei dati di openmeteo: esclusa dalla stima");
                    continue;
                }
                double peakW = plane.getValue() * settings.panelPeakPowerW();
                series.forEach((time, gti) ->
                        stringByHour.merge(time, gti / 1000.0 * peakW * settings.performanceRatio(), Double::sum));
            }
            byStringHourly.put(string.getKey(), stringByHour);
        }

        double maxPv = settings.inverter().maxPvInputW();
        Map<K, Double> totalByHour = new TreeMap<>();
        byStringHourly.values().forEach(m -> m.forEach((t, wh) -> totalByHour.merge(t, wh, Double::sum)));
        totalByHour.forEach((time, wh) -> {
            if (wh > maxPv) {
                double factor = maxPv / wh;
                byStringHourly.values().forEach(m -> m.computeIfPresent(time, (t, v) -> v * factor));
            }
        });
        return byStringHourly;
    }

    private static Map<String, Map<LocalDateTime, Double>> readIrradiance(JsonNode solar) {
        Map<String, Map<LocalDateTime, Double>> irradianceByPlane = new HashMap<>();
        for (var entry : solar.properties()) {
            JsonNode times = entry.getValue().path("hourly").path("time");
            JsonNode irradiance = entry.getValue().path("hourly").path("global_tilted_irradiance");
            Map<LocalDateTime, Double> series = new TreeMap<>();
            for (int i = 0; i < times.size(); i++) {
                JsonNode value = irradiance.get(i);
                if (value != null && value.isNumber()) {
                    series.put(LocalDateTime.parse(times.get(i).asString()), value.asDouble());
                }
            }
            irradianceByPlane.put(entry.getKey(), series);
        }
        return irradianceByPlane;
    }

    private record DayTotals(double today, double remaining, double tomorrow) {

        static DayTotals of(Map<LocalDateTime, Double> energyByHour, LocalDate today, LocalDateTime now) {
            double todayWh = 0;
            double remainingWh = 0;
            double tomorrowWh = 0;
            for (var e : energyByHour.entrySet()) {
                LocalDateTime hourEnd = e.getKey();
                // l'ora che termina alle 00:00 appartiene al giorno prima
                LocalDate day = hourEnd.minusMinutes(1).toLocalDate();
                if (day.equals(today)) {
                    todayWh += e.getValue();
                    if (hourEnd.isAfter(now)) {
                        remainingWh += e.getValue();
                    }
                } else if (day.equals(today.plusDays(1))) {
                    tomorrowWh += e.getValue();
                }
            }
            return new DayTotals(todayWh, remainingWh, tomorrowWh);
        }
    }

    /** Wh interi. */
    private static double round(double value) {
        return Math.round(value);
    }
}
