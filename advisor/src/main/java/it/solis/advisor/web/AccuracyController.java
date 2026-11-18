package it.solis.advisor.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.solis.advisor.accuracy.AccuracyReport;
import it.solis.advisor.accuracy.ForecastAccuracyService;
import it.solis.advisor.accuracy.ForecastSelection;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/history")
@Tag(name = "Affidabilità previsioni", description = "Produzione FV reale confrontata con la stima da Open-Meteo")
public class AccuracyController {

    private final ForecastAccuracyService service;

    public AccuracyController(ForecastAccuracyService service) {
        this.service = service;
    }

    @GetMapping("/forecast-accuracy")
    @Operation(summary = "Confronta la produzione reale con quella stimata da Open-Meteo",
            description = """
                    Per ogni ora del periodo (default: le ultime 24 ore complete) confronta la produzione FV reale \
                    con la stima calcolata con la stessa formula delle regole:
                    - dalla previsione Open-Meteo archiviata scelta con run: l'ultima disponibile prima dell'ora \
                    (LATEST), quella scaricata prima dell'alba del giorno (PRE_DAWN) o la sera prima (EVENING);
                    - dall'irraggiamento a posteriori, per separare l'errore della previsione da quello del modello \
                    dell'impianto.
                    Esclude le ore con letture dell'inverter incomplete e quelle con la batteria carica (produzione \
                    forse limitata). Errori positivi = stima più alta del reale.""")
    public AccuracyReport accuracy(
            @Parameter(description = "Inizio (ISO-8601). Default: 24 ore prima della fine")
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Fine (ISO-8601), arrotondata all'ora completa. Default: adesso")
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Previsione da confrontare: LATEST (ultima disponibile prima dell'ora), "
                    + "PRE_DAWN (scaricata prima dell'alba), EVENING (la sera prima, come la regola di carica notturna)")
            @RequestParam(name = "run", defaultValue = "LATEST") ForecastSelection run) {
        return service.report(from == null ? null : from.toInstant(), to == null ? null : to.toInstant(), run);
    }
}
