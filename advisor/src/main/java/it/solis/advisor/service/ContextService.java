package it.solis.advisor.service;

import it.solis.advisor.client.InverterClient;
import it.solis.advisor.client.OpenMeteoClient;
import it.solis.advisor.context.EnergyContext;
import it.solis.advisor.context.ForecastCalculator;
import it.solis.advisor.context.InverterState;
import it.solis.advisor.context.SolarForecast;
import it.solis.advisor.store.StateStore;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Raccoglie in parallelo (un virtual thread per servizio) i dati su cui valutare le regole. */
@Service
public class ContextService {

    private final InverterClient inverterClient;
    private final OpenMeteoClient openMeteoClient;
    private final StateStore store;
    private final Clock clock;

    public ContextService(InverterClient inverterClient, OpenMeteoClient openMeteoClient, StateStore store, Clock clock) {
        this.inverterClient = inverterClient;
        this.openMeteoClient = openMeteoClient;
        this.store = store;
        this.clock = clock;
    }

    public EnergyContext current() {
        var settings = store.settings();
        var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<String> warnings = new ArrayList<>();
        InverterState inverter;
        SolarForecast forecast = null;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<InverterState> inverterFuture = executor.submit(inverterClient::summary);
            Future<JsonNode> solarFuture = executor.submit(openMeteoClient::solarForecast);
            inverter = await(inverterFuture, warnings);
            JsonNode solar = await(solarFuture, warnings);
            if (solar != null) {
                forecast = ForecastCalculator.compute(solar, settings, now, warnings);
            }
        }
        return new EnergyContext(now, inverter, forecast, settings, List.copyOf(warnings));
    }

    /** Completa un contesto fornito dal client (simulazione) con ora e impostazioni correnti dove mancano. */
    public EnergyContext complete(EnergyContext context) {
        return new EnergyContext(
                context.time() != null ? context.time() : LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS),
                context.inverter(),
                context.forecast(),
                context.settings() != null ? context.settings() : store.settings(),
                context.warnings() != null ? context.warnings() : List.of());
    }

    private static <T> T await(Future<T> future, List<String> warnings) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            warnings.add(e.getCause().getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("Interrotto");
            return null;
        }
    }
}
