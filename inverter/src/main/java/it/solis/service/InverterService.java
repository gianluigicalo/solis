package it.solis.service;

import it.solis.config.SolisProperties;
import it.solis.modbus.SolisModbusClient;
import it.solis.modbus.SolisRegister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Fornisce le letture dell'inverter con una cache per registro: ogni chiamata legge dall'inverter solo i registri
 * richiesti il cui valore è più vecchio della sua durata ({@code solis.cache-ttl} per gli input register,
 * {@code solis.holding-cache-ttl} per gli holding register, che sono impostazioni e cambiano di rado).
 * Così il riepilogo per l'animazione interroga solo i pochi registri che usa, e più chiamate ravvicinate
 * producono una sola lettura Modbus.
 */
@Service
public class InverterService {

    public static final Set<SolisRegister> ALL_REGISTERS = Collections.unmodifiableSet(EnumSet.allOf(SolisRegister.class));

    private record Cached(Object value, Instant readAt) {
    }

    private final SolisModbusClient client;
    private final SolisProperties properties;
    private final Clock clock;

    /** Ultimo valore letto di ogni registro (accesso solo dentro {@link #snapshot(Collection)}, sincronizzato). */
    private final Map<SolisRegister, Cached> cache = new EnumMap<>(SolisRegister.class);

    @Autowired
    public InverterService(SolisModbusClient client, SolisProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    InverterService(SolisModbusClient client, SolisProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /** Tutti i registri. */
    public InverterSnapshot snapshot() {
        return snapshot(ALL_REGISTERS);
    }

    /**
     * Valori dei registri indicati: quelli scaduti vengono riletti dall'inverter, gli altri arrivano dalla cache.
     * L'istante della lettura è quello del valore di input register più vecchio fra quelli restituiti.
     */
    public synchronized InverterSnapshot snapshot(Collection<SolisRegister> registers) {
        Instant now = clock.instant();
        EnumSet<SolisRegister> stale = EnumSet.noneOf(SolisRegister.class);
        for (SolisRegister r : registers) {
            Cached c = cache.get(r);
            if (c == null || !c.readAt().plus(ttl(r)).isAfter(now)) {
                stale.add(r);
            }
        }
        if (!stale.isEmpty()) {
            Map<SolisRegister, Object> values = client.read(stale);
            Instant readAt = clock.instant();
            for (SolisRegister r : stale) {
                cache.put(r, new Cached(values.get(r), readAt));
            }
        }
        Map<SolisRegister, Object> result = new EnumMap<>(SolisRegister.class);
        Instant oldest = null;
        for (SolisRegister r : registers) {
            Cached c = cache.get(r);
            result.put(r, c.value());
            if (!r.holding() && (oldest == null || c.readAt().isBefore(oldest))) {
                oldest = c.readAt();
            }
        }
        return new InverterSnapshot(oldest != null ? oldest : now, Collections.unmodifiableMap(result));
    }

    /** Lettura diretta, senza cache, di un intervallo di input register. */
    public int[] readRaw(int start, int count) {
        return client.readRaw(start, count);
    }

    private Duration ttl(SolisRegister register) {
        return register.holding() ? properties.holdingCacheTtl() : properties.cacheTtl();
    }
}
