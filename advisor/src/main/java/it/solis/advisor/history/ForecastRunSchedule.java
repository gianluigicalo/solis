package it.solis.advisor.history;

import it.solis.advisor.config.AdvisorProperties.ForecastRuns;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orari in cui archiviare la previsione Open-Meteo. Ogni giorno: prima dell'alba, dopo l'alba, a mezzogiorno solare,
 * prima del tramonto, al tramonto e la sera (previsione per il giorno dopo).
 */
public final class ForecastRunSchedule {

    /** Un orario programmato. */
    public record Slot(Instant time, String name) {
    }

    private final ForecastRuns config;
    private final ZoneId zone;

    public ForecastRunSchedule(ForecastRuns config, ZoneId zone) {
        this.config = config;
        this.zone = zone;
    }

    /** Orari del giorno indicato; senza alba e tramonto resta solo quello della sera. */
    public List<Slot> slots(LocalDate day, SunTimes sun) {
        List<Slot> slots = new ArrayList<>();
        if (sun != null) {
            slots.add(new Slot(sun.sunrise().minus(config.beforeSunrise()), "prima dell'alba"));
            slots.add(new Slot(sun.sunrise().plus(config.afterSunrise()), "dopo l'alba"));
            slots.add(new Slot(sun.solarNoon(), "mezzogiorno solare"));
            slots.add(new Slot(sun.sunset().minus(config.beforeSunset()), "prima del tramonto"));
            slots.add(new Slot(sun.sunset(), "tramonto"));
        }
        slots.add(new Slot(day.atTime(config.evening()).atZone(zone).toInstant(), "sera"));
        slots.sort(Comparator.comparing(Slot::time));
        return slots;
    }

    /** L'orario programmato più recente già passato (oggi o ieri). */
    public Optional<Slot> lastDue(Instant now, Map<LocalDate, SunTimes> sun) {
        LocalDate today = now.atZone(zone).toLocalDate();
        return List.of(today.minusDays(1), today).stream()
                .flatMap(day -> slots(day, sun.get(day)).stream())
                .filter(slot -> !slot.time().isAfter(now))
                .max(Comparator.comparing(Slot::time));
    }

    /** Il prossimo orario programmato (per il log). */
    public Optional<Slot> next(Instant now, Map<LocalDate, SunTimes> sun) {
        LocalDate today = now.atZone(zone).toLocalDate();
        return List.of(today, today.plusDays(1)).stream()
                .flatMap(day -> slots(day, sun.get(day)).stream())
                .filter(slot -> slot.time().isAfter(now))
                .min(Comparator.comparing(Slot::time));
    }
}
