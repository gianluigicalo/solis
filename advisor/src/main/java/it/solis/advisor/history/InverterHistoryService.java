package it.solis.advisor.history;

import it.solis.advisor.client.InverterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Salva tutti i registri dell'inverter in inverter_measurement. Gli holding register (impostazioni modificabili,
 * "access": "READ_WRITE") l'inverter li rilegge solo ogni 10 minuti: si salvano quando cambiano o al massimo ogni
 * {@link #HOLDING_INTERVAL}, invece di ripetere lo stesso valore a ogni lettura.
 */
@Service
public class InverterHistoryService {

    private static final Logger log = LoggerFactory.getLogger(InverterHistoryService.class);

    private final InverterClient client;
    private final HistoryRepository repository;
    private static final Duration HOLDING_INTERVAL = Duration.ofMinutes(10);

    private record Saved(Double value, String text, Instant time) {
    }

    private volatile boolean registersSynced;
    /** ultimo valore salvato di ogni holding register */
    private final Map<String, Saved> savedHolding = new HashMap<>();

    public InverterHistoryService(InverterClient client, HistoryRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    /** @return righe inserite (0 se la lettura era già stata salvata) */
    public int collect() {
        JsonNode data = client.data();
        if (!registersSynced) {
            int registers = repository.upsertRegisters(client.registers());
            registersSynced = true;
            log.info("Sincronizzate {} definizioni di registri", registers);
        }
        Instant readAt = Instant.parse(data.path("readAt").asString());
        var time = readAt.atOffset(ZoneOffset.UTC);
        List<Object[]> rows = new ArrayList<>();
        for (var category : data.path("categories").properties()) {
            for (var register : category.getValue().properties()) {
                JsonNode value = register.getValue().path("value");
                Double number = value.isNumber() ? value.asDouble() : null;
                String text = value.isString() ? value.asString() : null;
                if ("READ_WRITE".equals(register.getValue().path("access").asString(null))
                        && !holdingDue(register.getKey(), number, text, readAt)) {
                    continue;
                }
                rows.add(new Object[]{time, register.getKey(), number, text});
            }
        }
        return repository.insertMeasurements(rows);
    }

    /** true se l'holding register va salvato: valore cambiato o ultimo salvataggio più vecchio di 10 minuti */
    private synchronized boolean holdingDue(String name, Double value, String text, Instant now) {
        Saved last = savedHolding.get(name);
        boolean due = last == null || !Objects.equals(last.value(), value) || !Objects.equals(last.text(), text)
                || !last.time().plus(HOLDING_INTERVAL).isAfter(now);
        if (due) {
            savedHolding.put(name, new Saved(value, text, now));
        }
        return due;
    }
}
