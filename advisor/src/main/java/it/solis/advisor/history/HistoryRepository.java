package it.solis.advisor.history;

import it.solis.advisor.rules.Advice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

/** Accesso alle tabelle di storico (inverter, Open-Meteo, consigli). */
@Repository
public class HistoryRepository {

    static final String SUN_SOURCE = "daily";
    static final String SUN_SERIES = "sun";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public HistoryRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    // ---------------------------------------------------------------- inverter

    /** Inserisce o aggiorna le definizioni dei registri (risposta di /api/inverter/registers). */
    public int upsertRegisters(JsonNode registers) {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode r : registers) {
            rows.add(new Object[]{r.path("name").asString(), r.path("address").asInt(), r.path("words").asInt(),
                    r.path("type").asString(), r.path("decimals").asInt(), text(r, "unit"),
                    r.path("category").asString(), text(r, "description"), r.path("source").asString(),
                    text(r, "publicName"), text(r, "reference")});
        }
        return sum(jdbcTemplate.batchUpdate("""
                INSERT INTO inverter_register (name, address, words, type, decimals, unit, category, description,
                                               source, public_name, reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (name) DO UPDATE SET address = EXCLUDED.address, words = EXCLUDED.words,
                    type = EXCLUDED.type, decimals = EXCLUDED.decimals, unit = EXCLUDED.unit,
                    category = EXCLUDED.category, description = EXCLUDED.description, source = EXCLUDED.source,
                    public_name = EXCLUDED.public_name, reference = EXCLUDED.reference, updated_at = now()
                """, rows));
    }

    /** Righe: {time, register, value, text_value}. Le letture già presenti vengono ignorate. */
    public int insertMeasurements(List<Object[]> rows) {
        return sum(jdbcTemplate.batchUpdate("""
                INSERT INTO inverter_measurement (time, register, value, text_value) VALUES (?, ?, ?, ?)
                ON CONFLICT (register, time) DO NOTHING
                """, rows));
    }

    public List<MeasurementPoint> measurements(String register, OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.sql("""
                        SELECT time, value, text_value FROM inverter_measurement
                        WHERE register = :register AND time >= :from AND time < :to
                        ORDER BY time LIMIT :limit
                        """)
                .param("register", register).param("from", from).param("to", to).param("limit", limit)
                .query((rs, n) -> new MeasurementPoint(time(rs, "time"), nullableDouble(rs, "value"),
                        rs.getString("text_value")))
                .list();
    }

    // ---------------------------------------------------------------- open-meteo

    /** Aggiorna il valore più recente di ogni ora. */
    public int upsertWeather(List<WeatherRow> rows, Instant fetchedAt) {
        var fetched = fetchedAt.atOffset(ZoneOffset.UTC);
        return sum(jdbcTemplate.batchUpdate("""
                INSERT INTO weather_hourly (time, source, series, variable, value, unit, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source, series, variable, time) DO UPDATE
                    SET value = EXCLUDED.value, unit = EXCLUDED.unit, fetched_at = EXCLUDED.fetched_at
                """, rows.stream().map(r -> new Object[]{r.time(), r.source(), r.series(), r.variable(), r.value(),
                r.unit(), fetched}).toList()));
    }

    /** Archivia la previsione scaricata. */
    public int insertForecastRun(List<WeatherRow> rows, Instant fetchedAt) {
        var fetched = fetchedAt.atOffset(ZoneOffset.UTC);
        return sum(jdbcTemplate.batchUpdate("""
                INSERT INTO weather_forecast_run (fetched_at, time, source, series, variable, value)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, rows.stream().map(r -> new Object[]{fetched, r.time(), r.source(), r.series(), r.variable(),
                r.value()}).toList()));
    }

    /** Data e ora dell'ultima previsione archiviata, null se l'archivio è vuoto. */
    public Instant lastForecastRun() {
        return jdbc.sql("SELECT max(fetched_at) AS last FROM weather_forecast_run")
                .query((rs, n) -> {
                    var last = rs.getObject("last", OffsetDateTime.class);
                    return last == null ? null : last.toInstant();
                })
                .optional().orElse(null);
    }

    /** Salva alba e tramonto in weather_hourly (source daily, series sun), all'ora 00:00 locale del giorno. */
    public int upsertSunTimes(List<SunTimes> days, ZoneId zone, Instant fetchedAt) {
        List<WeatherRow> rows = new ArrayList<>();
        for (SunTimes sun : days) {
            var time = sun.day().atStartOfDay(zone).toOffsetDateTime();
            rows.add(new WeatherRow(time, SUN_SOURCE, SUN_SERIES, "sunrise", sun.sunrise().getEpochSecond(), "unixtime"));
            rows.add(new WeatherRow(time, SUN_SOURCE, SUN_SERIES, "sunset", sun.sunset().getEpochSecond(), "unixtime"));
        }
        return upsertWeather(rows, fetchedAt);
    }

    /** Alba e tramonto salvati per i giorni tra {@code from} e {@code to} inclusi. */
    public Map<LocalDate, SunTimes> sunTimes(LocalDate from, LocalDate to, ZoneId zone) {
        Map<LocalDate, Instant[]> found = new TreeMap<>();
        jdbc.sql("""
                        SELECT time, variable, value FROM weather_hourly
                        WHERE source = :source AND series = :series AND time >= :from AND time < :to
                        """)
                .param("source", SUN_SOURCE).param("series", SUN_SERIES)
                .param("from", from.atStartOfDay(zone).toOffsetDateTime())
                .param("to", to.plusDays(1).atStartOfDay(zone).toOffsetDateTime())
                .query(rs -> {
                    var day = rs.getObject("time", OffsetDateTime.class).atZoneSameInstant(zone).toLocalDate();
                    var at = Instant.ofEpochSecond((long) rs.getDouble("value"));
                    var pair = found.computeIfAbsent(day, d -> new Instant[2]);
                    pair["sunrise".equals(rs.getString("variable")) ? 0 : 1] = at;
                });
        Map<LocalDate, SunTimes> result = new TreeMap<>();
        found.forEach((day, pair) -> {
            if (pair[0] != null && pair[1] != null) {
                result.put(day, new SunTimes(day, pair[0], pair[1]));
            }
        });
        return result;
    }

    public List<WeatherPoint> weather(String source, String series, String variable, OffsetDateTime from,
                                      OffsetDateTime to, int limit) {
        return jdbc.sql("""
                        SELECT time, value, unit, fetched_at FROM weather_hourly
                        WHERE source = :source AND series = :series AND variable = :variable
                          AND time >= :from AND time < :to
                        ORDER BY time LIMIT :limit
                        """)
                .param("source", source).param("series", series).param("variable", variable)
                .param("from", from).param("to", to).param("limit", limit)
                .query((rs, n) -> new WeatherPoint(time(rs, "time"), rs.getDouble("value"), rs.getString("unit"),
                        time(rs, "fetched_at")))
                .list();
    }

    // ---------------------------------------------------------------- consigli

    public void insertAdvice(Instant time, Advice advice) {
        jdbc.sql("""
                        INSERT INTO advice_log (time, action, decided_by, reason, evaluations, context)
                        VALUES (:time, :action, :decidedBy, :reason, CAST(:evaluations AS jsonb), CAST(:context AS jsonb))
                        ON CONFLICT (time) DO NOTHING
                        """)
                .param("time", time.atOffset(ZoneOffset.UTC))
                .param("action", advice.action().name())
                .param("decidedBy", advice.decidedBy())
                .param("reason", advice.reason())
                .param("evaluations", jsonMapper.writeValueAsString(advice.evaluations()))
                .param("context", jsonMapper.writeValueAsString(advice.context()))
                .update();
    }

    public List<AdviceLogEntry> advice(OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.sql("""
                        SELECT time, action, decided_by, reason FROM advice_log
                        WHERE time >= :from AND time < :to ORDER BY time DESC LIMIT :limit
                        """)
                .param("from", from).param("to", to).param("limit", limit)
                .query((rs, n) -> new AdviceLogEntry(time(rs, "time"), rs.getString("action"),
                        rs.getString("decided_by"), rs.getString("reason")))
                .list();
    }

    // ---------------------------------------------------------------- stato

    /** Per ogni tabella: ultimo istante salvato e righe delle ultime 24 ore. */
    public Map<String, TableStatus> status() {
        var result = new LinkedHashMap<String, TableStatus>();
        for (var table : List.of(new String[]{"inverter_measurement", "time"}, new String[]{"weather_hourly", "fetched_at"},
                new String[]{"weather_forecast_run", "fetched_at"}, new String[]{"advice_log", "time"})) {
            // nomi di tabella e colonna fissi, non provenienti dall'utente
            String sql = "SELECT max(" + table[1] + ") AS last, count(*) FILTER (WHERE " + table[1]
                    + " > now() - INTERVAL '24 hours') AS recent FROM " + table[0];
            result.put(table[0], jdbc.sql(sql)
                    .query((rs, n) -> new TableStatus(time(rs, "last"), rs.getLong("recent")))
                    .single());
        }
        return result;
    }

    // ---------------------------------------------------------------- utilità

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static int sum(int[] counts) {
        return IntStream.of(counts).map(c -> Math.max(c, 0)).sum();
    }

    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    public record MeasurementPoint(OffsetDateTime time, Double value, String textValue) {
    }

    public record WeatherPoint(OffsetDateTime time, double value, String unit, OffsetDateTime fetchedAt) {
    }

    public record AdviceLogEntry(OffsetDateTime time, String action, String decidedBy, String reason) {
    }

    public record TableStatus(OffsetDateTime last, long rowsLast24h) {
    }
}
