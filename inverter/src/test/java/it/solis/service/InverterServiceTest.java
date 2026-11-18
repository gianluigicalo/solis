package it.solis.service;

import it.solis.config.SolisProperties;
import it.solis.modbus.SolisModbusClient;
import it.solis.modbus.SolisRegister;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static it.solis.modbus.SolisRegister.*;
import static org.assertj.core.api.Assertions.assertThat;

class InverterServiceTest {

    private static final SolisProperties PROPERTIES = new SolisProperties("localhost", 502, 1,
            Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofMinutes(10));

    /** Client finto: registra quali registri vengono letti a ogni chiamata. */
    static class FakeClient extends SolisModbusClient {
        final List<Set<SolisRegister>> reads = new ArrayList<>();

        FakeClient() {
            super(PROPERTIES);
        }

        @Override
        public synchronized Map<SolisRegister, Object> read(Collection<SolisRegister> registers) {
            reads.add(EnumSet.copyOf(registers));
            Map<SolisRegister, Object> values = new EnumMap<>(SolisRegister.class);
            registers.forEach(r -> values.put(r, BigDecimal.ONE));
            return values;
        }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void leggeSoloIRegistriRichiestiEScaduti() {
        var client = new FakeClient();
        var clock = new MutableClock();
        var service = new InverterService(client, PROPERTIES, clock);

        service.snapshot(Set.of(BATTERY_SOC, PV_TOTAL_POWER));
        assertThat(client.reads).containsExactly(EnumSet.of(BATTERY_SOC, PV_TOTAL_POWER));

        // entro i 5 s: niente lettura per i registri già in cache, si legge solo quello nuovo
        clock.now = clock.now.plusSeconds(3);
        service.snapshot(Set.of(BATTERY_SOC, METER_VOLTAGE));
        assertThat(client.reads).hasSize(2);
        assertThat(client.reads.get(1)).containsExactly(METER_VOLTAGE);

        // scaduti i 5 s di BATTERY_SOC: si rilegge solo lui
        clock.now = clock.now.plusSeconds(3);
        service.snapshot(Set.of(BATTERY_SOC, METER_VOLTAGE));
        assertThat(client.reads.get(2)).containsExactly(BATTERY_SOC);
    }

    @Test
    void gliHoldingRegisterRestanoInCachePerDieciMinuti() {
        var client = new FakeClient();
        var clock = new MutableClock();
        var service = new InverterService(client, PROPERTIES, clock);

        service.snapshot(Set.of(STORAGE_MODE_SETTING, BATTERY_SOC));
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        service.snapshot(Set.of(STORAGE_MODE_SETTING, BATTERY_SOC));
        assertThat(client.reads.get(1)).containsExactly(BATTERY_SOC);

        clock.now = clock.now.plus(Duration.ofMinutes(6));
        service.snapshot(Set.of(STORAGE_MODE_SETTING));
        assertThat(client.reads.get(2)).containsExactly(STORAGE_MODE_SETTING);
    }

    @Test
    void lIstanteDellaLetturaEQuelloDelValorePiuVecchio() {
        var client = new FakeClient();
        var clock = new MutableClock();
        var service = new InverterService(client, PROPERTIES, clock);
        Instant first = clock.now;
        service.snapshot(Set.of(BATTERY_SOC));
        clock.now = clock.now.plusSeconds(2);
        var snapshot = service.snapshot(Set.of(BATTERY_SOC, PV_TOTAL_POWER));
        assertThat(snapshot.readAt()).isEqualTo(first);
        assertThat(snapshot.number(PV_TOTAL_POWER)).isEqualTo(BigDecimal.ONE);
    }
}
