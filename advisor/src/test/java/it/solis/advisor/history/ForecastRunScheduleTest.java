package it.solis.advisor.history;

import it.solis.advisor.config.AdvisorProperties.ForecastRuns;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastRunScheduleTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    // alba 06:43:19 e tramonto 16:32:39 ora italiana (dati Open-Meteo del 17/11/2026)
    private static final SunTimes SUN = new SunTimes(DAY, Instant.ofEpochSecond(1794894199),
            Instant.ofEpochSecond(1794929559));
    private final ForecastRunSchedule schedule = new ForecastRunSchedule(new ForecastRuns(Duration.ofMinutes(5),
            LocalTime.of(21, 0), Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(3)), ROME);

    @Test
    void sixRunsADayAroundSunriseAndSunset() {
        var slots = schedule.slots(DAY, SUN);
        assertEquals(List.of("05:43:19", "08:43:19", "11:37:59", "13:32:39", "16:32:39", "21:00"),
                slots.stream().map(s -> s.time().atZone(ROME).toLocalTime().toString()).toList());
        assertEquals("prima dell'alba", slots.getFirst().name());
    }

    @Test
    void lastDueIsTheLatestPastSlotIncludingYesterdayEvening() {
        var sun = Map.of(DAY, SUN);
        var early = DAY.atTime(3, 0).atZone(ROME).toInstant();
        assertEquals("sera", schedule.lastDue(early, sun).orElseThrow().name());
        var noon = DAY.atTime(12, 30).atZone(ROME).toInstant();
        assertEquals("mezzogiorno solare", schedule.lastDue(noon, sun).orElseThrow().name());
        assertEquals("prima del tramonto", schedule.next(noon, sun).orElseThrow().name());
    }

    @Test
    void withoutSunTimesOnlyTheEveningRunRemains() {
        var slots = schedule.slots(DAY, null);
        assertEquals(1, slots.size());
        assertTrue(schedule.lastDue(DAY.atTime(22, 0).atZone(ROME).toInstant(), Map.of()).isPresent());
    }

    @Test
    void parsesOpenMeteoDailyUnixTimes() {
        var json = JsonMapper.builder().build().readTree("""
                {"daily":{"time":[1794870000,1794956400],"sunrise":[1794894199,1794980668],
                "sunset":[1794929559,1795015915]}}""");
        var days = SunTimesService.parse(json, ROME);
        assertEquals(List.of(SUN, new SunTimes(DAY.plusDays(1), Instant.ofEpochSecond(1794980668),
                Instant.ofEpochSecond(1795015915))), days);
    }
}
