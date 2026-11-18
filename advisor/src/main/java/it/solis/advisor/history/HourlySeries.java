package it.solis.advisor.history;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Converte il blocco {@code hourly} di una risposta Open-Meteo in righe da salvare. */
public final class HourlySeries {

    private HourlySeries() {
    }

    /**
     * @param response risposta Open-Meteo con {@code hourly}, {@code hourly_units} e {@code utc_offset_seconds}
     * @return una riga per ogni variabile e ora con valore numerico (i valori null vengono saltati)
     */
    public static List<WeatherRow> parse(JsonNode response, String source, String series) {
        var offset = ZoneOffset.ofTotalSeconds(response.path("utc_offset_seconds").asInt(0));
        JsonNode hourly = response.path("hourly");
        JsonNode units = response.path("hourly_units");
        JsonNode times = hourly.path("time");
        List<WeatherRow> rows = new ArrayList<>();
        for (var variable : hourly.properties()) {
            if (variable.getKey().equals("time")) {
                continue;
            }
            String unit = units.path(variable.getKey()).asString(null);
            JsonNode values = variable.getValue();
            for (int i = 0; i < times.size() && i < values.size(); i++) {
                JsonNode value = values.get(i);
                if (value.isNumber()) {
                    var time = LocalDateTime.parse(times.get(i).asString()).atOffset(offset);
                    rows.add(new WeatherRow(time, source, series, variable.getKey(), value.asDouble(), unit));
                }
            }
        }
        return rows;
    }
}
