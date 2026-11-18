package it.solis.advisor.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.solis.advisor.history.HistoryRepository;
import it.solis.advisor.history.HistoryRepository.AdviceLogEntry;
import it.solis.advisor.history.HistoryRepository.MeasurementPoint;
import it.solis.advisor.history.HistoryRepository.TableStatus;
import it.solis.advisor.history.HistoryRepository.WeatherPoint;
import it.solis.advisor.history.InverterHistoryService;
import it.solis.advisor.history.WeatherHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@Tag(name = "Storico", description = "Storico di inverter, Open-Meteo e consigli salvato nel database")
public class HistoryController {

    private static final String FROM = "Inizio (ISO-8601, es. 2026-11-14T00:00:00Z). Default: 24 ore fa";
    private static final String TO = "Fine esclusa (ISO-8601). Default: adesso";

    private final HistoryRepository repository;
    private final InverterHistoryService inverterHistory;
    private final WeatherHistoryService weatherHistory;

    public HistoryController(HistoryRepository repository, InverterHistoryService inverterHistory,
                             WeatherHistoryService weatherHistory) {
        this.repository = repository;
        this.inverterHistory = inverterHistory;
        this.weatherHistory = weatherHistory;
    }

    @GetMapping("/status")
    @Operation(summary = "Ultimo dato salvato e righe delle ultime 24 ore per ogni tabella")
    public Map<String, TableStatus> status() {
        return repository.status();
    }

    @PostMapping("/collect")
    @Operation(summary = "Raccoglie subito un campione di inverter e meteo, senza aspettare il prossimo giro")
    public Map<String, Object> collect() {
        var result = new LinkedHashMap<String, Object>();
        try {
            result.put("inverterRows", inverterHistory.collect());
        } catch (RuntimeException e) {
            result.put("inverterError", e.getMessage());
        }
        try {
            result.put("weatherRows", weatherHistory.collect());
        } catch (RuntimeException e) {
            result.put("weatherError", e.getMessage());
        }
        return result;
    }

    @PostMapping("/weather/backfill")
    @Operation(summary = "Importa lo storico meteo dall'archivio Open-Meteo (ERA5, dal 1940)",
            description = "Salva in weather_hourly con source = archive. Gli ultimi ~5 giorni possono mancare.")
    public Map<String, Object> backfill(
            @Parameter(example = "2026-01-01") @RequestParam("start") LocalDate start,
            @Parameter(example = "2026-10-31") @RequestParam("end") LocalDate end) {
        return Map.of("start", start, "end", end, "rows", weatherHistory.backfill(start, end));
    }

    @GetMapping("/inverter/{register}")
    @Operation(summary = "Storico di un registro dell'inverter (es. BATTERY_SOC)")
    public List<MeasurementPoint> inverter(
            @PathVariable("register") String register,
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(name = "limit", defaultValue = "5000") int limit) {
        var range = Range.of(from, to);
        return repository.measurements(register, range.from, range.to, limit);
    }

    @GetMapping("/weather")
    @Operation(summary = "Storico orario di una grandezza Open-Meteo")
    public List<WeatherPoint> weather(
            @Parameter(description = "forecast, solar o archive") @RequestParam(name = "source", defaultValue = "forecast")
            String source,
            @Parameter(description = "meteo, oppure il nome della falda per solar")
            @RequestParam(name = "series", defaultValue = "meteo") String series,
            @Parameter(example = "temperature_2m") @RequestParam("variable") String variable,
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(name = "limit", defaultValue = "5000") int limit) {
        var range = Range.of(from, to);
        return repository.weather(source, series, variable, range.from, range.to, limit);
    }

    @GetMapping("/advice")
    @Operation(summary = "Consigli registrati dal controllo periodico, dal più recente")
    public List<AdviceLogEntry> advice(
            @Parameter(description = FROM) @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = TO) @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(name = "limit", defaultValue = "500") int limit) {
        var range = Range.of(from, to);
        return repository.advice(range.from, range.to, limit);
    }

    private record Range(OffsetDateTime from, OffsetDateTime to) {

        static Range of(OffsetDateTime from, OffsetDateTime to) {
            var end = to != null ? to : OffsetDateTime.now();
            return new Range(from != null ? from : end.minusHours(24), end);
        }
    }
}
