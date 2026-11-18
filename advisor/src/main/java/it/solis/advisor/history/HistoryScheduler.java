package it.solis.advisor.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

/** Raccolta periodica dello storico (disattivabile con advisor.history.enabled=false). */
@Component
@ConditionalOnProperty(prefix = "advisor.history", name = "enabled", havingValue = "true")
class HistoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoryScheduler.class);

    private final InverterHistoryService inverterHistory;
    private final WeatherHistoryService weatherHistory;
    private volatile boolean inverterFailing;
    private volatile boolean weatherFailing;
    private volatile boolean archiveFailing;

    HistoryScheduler(InverterHistoryService inverterHistory, WeatherHistoryService weatherHistory) {
        this.inverterHistory = inverterHistory;
        this.weatherHistory = weatherHistory;
    }

    @Scheduled(initialDelayString = "PT15S", fixedDelayString = "${advisor.history.inverter-interval}")
    void collectInverter() {
        inverterFailing = run("inverter", inverterHistory::collect, inverterFailing);
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${advisor.history.weather-interval}")
    void collectWeather() {
        weatherFailing = run("meteo", weatherHistory::collect, weatherFailing);
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${advisor.history.forecast-runs.check-interval}")
    void archiveForecast() {
        try {
            weatherHistory.archiveIfDue().ifPresent(slot -> log.info("Previsione archiviata (orario: {} {}). "
                    + "Prossima: {}", slot.name(), slot.time(), weatherHistory.nextRun()
                    .map(next -> next.name() + " " + next.time()).orElse("non nota")));
            archiveFailing = false;
        } catch (RuntimeException e) {
            if (!archiveFailing) {
                log.warn("Archivio previsioni: scaricamento fallito ({}). Riprovo al prossimo controllo", e.getMessage());
            }
            archiveFailing = true;
        }
    }

    /** Registra nel log solo i cambi di stato (inizio e fine di un periodo di errori). */
    private static boolean run(String name, IntSupplier task, boolean wasFailing) {
        try {
            int rows = task.getAsInt();
            if (wasFailing) {
                log.info("Storico {}: raccolta ripresa", name);
            }
            log.debug("Storico {}: {} righe", name, rows);
            return false;
        } catch (RuntimeException e) {
            if (!wasFailing) {
                log.warn("Storico {}: raccolta fallita ({}). Riprovo in silenzio fino al ripristino", name, e.getMessage());
            }
            return true;
        }
    }
}
