package it.solis.advisor.history;

import it.solis.advisor.client.OpenMeteoClient;
import it.solis.advisor.config.AdvisorProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Alba e tramonto per giorno: dal database, altrimenti da Open-Meteo (che li calcola anche per i giorni passati). */
@Service
public class SunTimesService {

    /** Limiti di Open-Meteo forecast: past_days al massimo 92, forecast_days al massimo 16. */
    private static final int MAX_PAST_DAYS = 92;
    private static final int MAX_FORECAST_DAYS = 16;

    private final OpenMeteoClient client;
    private final HistoryRepository repository;
    private final Clock clock;
    private final ZoneId zone;

    public SunTimesService(OpenMeteoClient client, HistoryRepository repository, Clock clock,
                           AdvisorProperties properties) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
        this.zone = ZoneId.of(properties.timezone());
    }

    public ZoneId zone() {
        return zone;
    }

    /**
     * Alba e tramonto tra {@code from} e {@code to} inclusi. I giorni mancanti vengono scaricati da Open-Meteo se
     * rientrano nei suoi limiti; se Open-Meteo non risponde restano assenti.
     */
    public Map<LocalDate, SunTimes> get(LocalDate from, LocalDate to) {
        var known = repository.sunTimes(from, to, zone);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = maxDate(from, today.minusDays(MAX_PAST_DAYS));
        LocalDate last = minDate(to, today.plusDays(MAX_FORECAST_DAYS - 1));
        if (first.isAfter(last)) {
            return known;
        }
        boolean missing = first.datesUntil(last.plusDays(1)).anyMatch(day -> !known.containsKey(day));
        if (!missing) {
            return known;
        }
        try {
            fetch(first, last, today);
        } catch (RuntimeException e) {
            return known;
        }
        return repository.sunTimes(from, to, zone);
    }

    /** Scarica alba e tramonto da Open-Meteo per il periodo indicato e li salva. */
    public void fetch(LocalDate from, LocalDate to, LocalDate today) {
        long pastDays = Math.max(0, ChronoUnit.DAYS.between(from, today));
        long forecastDays = Math.max(1, ChronoUnit.DAYS.between(today, to) + 1);
        JsonNode response = client.get("/api/meteo/forecast", Map.of(
                "daily", "sunrise,sunset",
                "past_days", Math.min(pastDays, MAX_PAST_DAYS),
                "forecast_days", Math.min(forecastDays, MAX_FORECAST_DAYS),
                "timezone", zone.getId(),
                "timeformat", "unixtime"));
        repository.upsertSunTimes(parse(response, zone), zone, clock.instant());
    }

    /** Converte il blocco {@code daily} (timeformat=unixtime) in alba e tramonto per giorno. */
    static List<SunTimes> parse(JsonNode response, ZoneId zone) {
        JsonNode daily = response.path("daily");
        JsonNode days = daily.path("time");
        JsonNode sunrise = daily.path("sunrise");
        JsonNode sunset = daily.path("sunset");
        List<SunTimes> result = new ArrayList<>();
        for (int i = 0; i < days.size() && i < sunrise.size() && i < sunset.size(); i++) {
            if (days.get(i).isNumber() && sunrise.get(i).isNumber() && sunset.get(i).isNumber()) {
                // daily.time è la mezzanotte locale del giorno
                LocalDate day = Instant.ofEpochSecond(days.get(i).asLong()).atZone(zone).toLocalDate();
                result.add(new SunTimes(day, Instant.ofEpochSecond(sunrise.get(i).asLong()),
                        Instant.ofEpochSecond(sunset.get(i).asLong())));
            }
        }
        return result;
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
