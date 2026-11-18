package it.solis.advisor.history;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HourlySeriesTest {

    @Test
    void convertsLocalTimesWithOffsetAndSkipsNulls() {
        var response = JsonMapper.builder().build().readTree("""
                {"utc_offset_seconds": 3600,
                 "hourly_units": {"time": "iso8601", "temperature_2m": "°C", "cloud_cover": "%"},
                 "hourly": {"time": ["2026-11-15T00:00", "2026-11-15T01:00"],
                            "temperature_2m": [12.5, null],
                            "cloud_cover": [80, 90]}}""");

        var rows = HourlySeries.parse(response, "forecast", "meteo");

        assertThat(rows).hasSize(3);
        assertThat(rows.getFirst()).isEqualTo(new WeatherRow(OffsetDateTime.parse("2026-11-15T00:00+01:00"),
                "forecast", "meteo", "temperature_2m", 12.5, "°C"));
        assertThat(rows).filteredOn(r -> r.variable().equals("cloud_cover")).extracting(WeatherRow::value)
                .containsExactly(80.0, 90.0);
    }
}
