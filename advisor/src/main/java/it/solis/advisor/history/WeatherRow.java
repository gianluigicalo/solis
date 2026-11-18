package it.solis.advisor.history;

import java.time.OffsetDateTime;

/** Un valore orario Open-Meteo da salvare in weather_hourly / weather_forecast_run. */
public record WeatherRow(OffsetDateTime time, String source, String series, String variable, double value, String unit) {
}
