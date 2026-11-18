package it.solis.advisor.accuracy;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Letture per il confronto tra produzione reale e stimata. Le ore sono identificate dalla loro <b>fine</b>, come
 * l'irraggiamento di Open-Meteo (media dell'ora che termina a {@code time}).
 */
@Repository
class AccuracyRepository {

    static final String GTI = "global_tilted_irradiance";

    private final JdbcClient jdbc;

    AccuracyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Media oraria di un registro dell'inverter e numero di letture, per le ore che terminano in (from, to]. */
    Map<Instant, HourlyAverage> hourlyAverage(String register, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT date_trunc('hour', time, 'UTC') + INTERVAL '1 hour' AS hour_end,
                               avg(value) AS avg, count(value) AS samples
                        FROM inverter_measurement
                        WHERE register = :register AND time >= :from AND time < :to
                        GROUP BY 1
                        """)
                .param("register", register).param("from", utc(from)).param("to", utc(to))
                .query(AccuracyRepository::readAverages);
    }

    /** Potenza media oraria di una stringa (W), calcolata come tensione × corrente di ogni lettura. */
    Map<Instant, HourlyAverage> hourlyStringPower(int string, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT date_trunc('hour', v.time, 'UTC') + INTERVAL '1 hour' AS hour_end,
                               avg(v.value * c.value) AS avg, count(*) AS samples
                        FROM inverter_measurement v
                        JOIN inverter_measurement c ON c.register = :current AND c.time = v.time
                        WHERE v.register = :voltage AND v.time >= :from AND v.time < :to
                          AND v.value IS NOT NULL AND c.value IS NOT NULL
                        GROUP BY 1
                        """)
                .param("voltage", "PV" + string + "_VOLTAGE").param("current", "PV" + string + "_CURRENT")
                .param("from", utc(from)).param("to", utc(to))
                .query(AccuracyRepository::readAverages);
    }

    /**
     * Irraggiamento per falda come era <b>previsto</b>: per ogni ora (fine dell'ora), la previsione archiviata più
     * recente scaricata entro il limite indicato in {@code cutoffs}.
     */
    Irradiance forecastIrradiance(Map<Instant, Instant> cutoffs) {
        var result = new Irradiance(new HashMap<>(), new TreeMap<>());
        if (cutoffs.isEmpty()) {
            return result;
        }
        var times = new StringJoiner(",", "{", "}");
        var limits = new StringJoiner(",", "{", "}");
        cutoffs.forEach((time, cutoff) -> {
            times.add(time.toString());
            limits.add(cutoff.toString());
        });
        jdbc.sql("""
                        SELECT DISTINCT ON (r.series, r.time) r.series, r.time, r.value, r.fetched_at
                        FROM weather_forecast_run r
                        JOIN unnest(CAST(:times AS timestamptz[]), CAST(:cutoffs AS timestamptz[])) AS c(time, cutoff)
                          ON r.time = c.time
                        WHERE r.source = 'solar' AND r.variable = :variable AND r.value IS NOT NULL
                          AND r.fetched_at <= c.cutoff
                        ORDER BY r.series, r.time, r.fetched_at DESC
                        """)
                .param("variable", GTI).param("times", times.toString()).param("cutoffs", limits.toString())
                .query(rs -> {
                    var time = rs.getObject("time", OffsetDateTime.class).toInstant();
                    var fetchedAt = rs.getObject("fetched_at", OffsetDateTime.class).toInstant();
                    result.byPlane().computeIfAbsent(rs.getString("series"), k -> new TreeMap<>())
                            .put(time, rs.getDouble("value"));
                    // se le falde vengono da scaricamenti diversi, conta il più vecchio
                    result.issuedAt().merge(time, fetchedAt, (a, b) -> a.isBefore(b) ? a : b);
                });
        return result;
    }

    /** Irraggiamento per falda più recente: per le ore passate converge al dato analizzato (a posteriori). */
    Irradiance latestIrradiance(Instant from, Instant to) {
        var result = new Irradiance(new HashMap<>(), new TreeMap<>());
        jdbc.sql("""
                        SELECT series, time, value, fetched_at FROM weather_hourly
                        WHERE source = 'solar' AND variable = :variable AND time > :from AND time <= :to
                          AND value IS NOT NULL
                        """)
                .param("variable", GTI).param("from", utc(from)).param("to", utc(to))
                .query(rs -> {
                    var time = rs.getObject("time", OffsetDateTime.class).toInstant();
                    result.byPlane().computeIfAbsent(rs.getString("series"), k -> new TreeMap<>())
                            .put(time, rs.getDouble("value"));
                    result.issuedAt().merge(time, rs.getObject("fetched_at", OffsetDateTime.class).toInstant(),
                            (a, b) -> a.isBefore(b) ? a : b);
                });
        return result;
    }

    private static Map<Instant, HourlyAverage> readAverages(ResultSet rs) throws SQLException {
        Map<Instant, HourlyAverage> result = new TreeMap<>();
        while (rs.next()) {
            result.put(rs.getObject("hour_end", OffsetDateTime.class).toInstant(),
                    new HourlyAverage(rs.getDouble("avg"), rs.getInt("samples")));
        }
        return result;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    record HourlyAverage(double average, int samples) {
    }

    /** Irraggiamento per falda e ora, con l'istante in cui è stato scaricato. */
    record Irradiance(Map<String, Map<Instant, Double>> byPlane, Map<Instant, Instant> issuedAt) {
    }
}
