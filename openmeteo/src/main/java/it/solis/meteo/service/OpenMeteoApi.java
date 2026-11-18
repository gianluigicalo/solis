package it.solis.meteo.service;

import java.util.Map;

/**
 * API Open-Meteo esposte, con le variabili richieste di default quando il client non specifica
 * nessuno tra {@code current}, {@code hourly}, {@code daily} e {@code minutely_15}.
 */
public enum OpenMeteoApi {

    FORECAST(Map.of(
            "current", "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,"
                    + "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
            "hourly", "temperature_2m,relative_humidity_2m,precipitation_probability,precipitation,weather_code,"
                    + "cloud_cover,wind_speed_10m,shortwave_radiation",
            "daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
                    + "precipitation_probability_max,sunrise,sunset,sunshine_duration,uv_index_max,"
                    + "shortwave_radiation_sum")),

    SOLAR(Map.of(
            "current", "is_day,cloud_cover,shortwave_radiation,direct_radiation,diffuse_radiation,"
                    + "global_tilted_irradiance",
            "hourly", "temperature_2m,cloud_cover,cloud_cover_low,shortwave_radiation,direct_radiation,"
                    + "diffuse_radiation,direct_normal_irradiance,global_tilted_irradiance",
            "daily", "sunrise,sunset,daylight_duration,sunshine_duration,shortwave_radiation_sum,uv_index_max")),

    ARCHIVE(Map.of(
            "daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunshine_duration,"
                    + "shortwave_radiation_sum")),

    AIR_QUALITY(Map.of(
            "current", "european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone,uv_index",
            "hourly", "european_aqi,pm10,pm2_5,ozone,uv_index"));

    private final Map<String, String> defaultVariables;

    OpenMeteoApi(Map<String, String> defaultVariables) {
        this.defaultVariables = defaultVariables;
    }

    public Map<String, String> defaultVariables() {
        return defaultVariables;
    }
}
