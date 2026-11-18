-- Funzioni TimescaleDB: hypertable, compressione e aggregati continui.

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Le tabelle hanno già i loro indici: niente indici di default
SELECT create_hypertable('inverter_measurement', by_range('time', INTERVAL '1 day'), create_default_indexes => false);
SELECT create_hypertable('weather_hourly', by_range('time', INTERVAL '30 days'), create_default_indexes => false);
SELECT create_hypertable('weather_forecast_run', by_range('fetched_at', INTERVAL '7 days'), create_default_indexes => false);
SELECT create_hypertable('advice_log', by_range('time', INTERVAL '30 days'), create_default_indexes => false);

-- Compressione: storico dell'inverter dopo 7 giorni, archivio previsioni e consigli dopo 30
ALTER TABLE inverter_measurement SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'register',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('inverter_measurement', INTERVAL '7 days');

ALTER TABLE weather_forecast_run SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'source, series, variable',
    timescaledb.compress_orderby = 'fetched_at DESC, time'
);
SELECT add_compression_policy('weather_forecast_run', INTERVAL '30 days');

ALTER TABLE advice_log SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('advice_log', INTERVAL '30 days');

-- Media, minimo, massimo e ultimo valore orario dei registri numerici, aggiornati automaticamente
CREATE MATERIALIZED VIEW inverter_measurement_hourly
WITH (timescaledb.continuous) AS
SELECT time_bucket(INTERVAL '1 hour', time) AS bucket,
       register,
       avg(value)        AS avg_value,
       min(value)        AS min_value,
       max(value)        AS max_value,
       last(value, time) AS last_value,
       count(*)          AS samples
FROM inverter_measurement
WHERE value IS NOT NULL
GROUP BY bucket, register
WITH NO DATA;

SELECT add_continuous_aggregate_policy('inverter_measurement_hourly',
    start_offset      => INTERVAL '3 days',
    end_offset        => INTERVAL '1 hour',
    schedule_interval => INTERVAL '30 minutes');
