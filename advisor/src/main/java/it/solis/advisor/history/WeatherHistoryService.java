package it.solis.advisor.history;

import it.solis.advisor.client.OpenMeteoClient;
import it.solis.advisor.config.AdvisorProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Salva le previsioni Open-Meteo (corrente e archiviate agli orari programmati) e importa lo storico. */
@Service
public class WeatherHistoryService {

    private static final int MAX_BACKFILL_DAYS_PER_REQUEST = 366;

    private final OpenMeteoClient client;
    private final HistoryRepository repository;
    private final AdvisorProperties.History config;
    private final Clock clock;
    private final SunTimesService sunTimes;
    private final ForecastRunSchedule schedule;

    public WeatherHistoryService(OpenMeteoClient client, HistoryRepository repository, AdvisorProperties properties,
                                 Clock clock, SunTimesService sunTimes) {
        this.client = client;
        this.repository = repository;
        this.config = properties.history();
        this.clock = clock;
        this.sunTimes = sunTimes;
        this.schedule = new ForecastRunSchedule(config.forecastRuns(), sunTimes.zone());
    }

    /**
     * Aggiorna la previsione corrente (weather_hourly) e alba e tramonto dei prossimi giorni. Non archivia la
     * previsione: lo fa {@link #archiveIfDue()} agli orari programmati.
     * @return valori salvati
     */
    public int collect() {
        var rows = download();
        int saved = repository.upsertWeather(rows, clock.instant());
        LocalDate today = LocalDate.now(clock.withZone(sunTimes.zone()));
        sunTimes.get(today.minusDays(1), today.plusDays(2)); // scarica solo i giorni mancanti
        return saved;
    }

    /**
     * Se è passato un orario programmato (alba, tramonto, sera...) e la previsione non è ancora stata archiviata,
     * la scarica e la salva sia in weather_hourly sia nell'archivio weather_forecast_run. Recupera anche gli orari
     * saltati mentre il servizio era fermo (archiviando subito la previsione più recente).
     * @return l'orario archiviato, vuoto se non c'era niente da fare
     */
    public Optional<ForecastRunSchedule.Slot> archiveIfDue() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(sunTimes.zone()));
        var sun = sunTimes.get(today.minusDays(1), today.plusDays(1));
        var due = schedule.lastDue(now, sun);
        if (due.isEmpty()) {
            return Optional.empty();
        }
        Instant last = repository.lastForecastRun();
        if (last != null && !last.isBefore(due.get().time())) {
            return Optional.empty();
        }
        var rows = download();
        repository.upsertWeather(rows, now);
        repository.insertForecastRun(rows, now);
        return due;
    }

    /** Il prossimo orario di archiviazione (per il log). */
    public Optional<ForecastRunSchedule.Slot> nextRun() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(sunTimes.zone()));
        return schedule.next(now, sunTimes.get(today, today.plusDays(1)));
    }

    /** Previsione meteo e irraggiamento per falda. */
    private List<WeatherRow> download() {
        List<WeatherRow> rows = new ArrayList<>();
        JsonNode forecast = client.get("/api/meteo/forecast", params(config.forecastVariables()));
        rows.addAll(HourlySeries.parse(forecast, "forecast", "meteo"));
        JsonNode solar = client.get("/api/meteo/solar", params(config.solarVariables()));
        for (var plane : solar.properties()) {
            rows.addAll(HourlySeries.parse(plane.getValue(), "solar", plane.getKey()));
        }
        return rows;
    }

    /** Importa dall'archivio ERA5 le ore tra {@code start} ed {@code end} inclusi; @return valori salvati */
    public int backfill(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end deve essere uguale o successiva a start");
        }
        int saved = 0;
        for (LocalDate from = start; !from.isAfter(end); from = from.plusDays(MAX_BACKFILL_DAYS_PER_REQUEST)) {
            LocalDate to = from.plusDays(MAX_BACKFILL_DAYS_PER_REQUEST - 1);
            if (to.isAfter(end)) {
                to = end;
            }
            JsonNode archive = client.get("/api/meteo/archive", Map.of(
                    "hourly", String.join(",", config.archiveVariables()),
                    "start_date", from.toString(),
                    "end_date", to.toString(),
                    "timezone", "GMT"));
            var rows = HourlySeries.parse(archive, "archive", "meteo");
            saved += repository.upsertWeather(rows, clock.instant());
        }
        return saved;
    }

    private Map<String, Object> params(List<String> variables) {
        return Map.of(
                "hourly", String.join(",", variables),
                "past_days", config.pastDays(),
                "forecast_days", config.forecastDays(),
                "timezone", "GMT");
    }
}
