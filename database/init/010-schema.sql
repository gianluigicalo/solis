-- Schema del database "solis". Eseguito una sola volta, alla creazione del volume dati.
-- Questo file usa solo PostgreSQL standard; le funzioni TimescaleDB sono in 020-timescale.sql.

-- =====================================================================================
-- Inverter
-- =====================================================================================

CREATE TABLE inverter_register (
    name        text PRIMARY KEY,
    address     integer     NOT NULL,
    words       integer     NOT NULL,
    type        text        NOT NULL,
    decimals    integer     NOT NULL,
    unit        text,
    category    text        NOT NULL,
    description text,
    source      text        NOT NULL,
    public_name text,
    reference   text,
    updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE inverter_register IS 'Registri Modbus dell''inverter Solis (sincronizzati da /api/inverter/registers)';
COMMENT ON COLUMN inverter_register.source IS 'OFFICIAL, UNOFFICIAL (fonte pubblica non ufficiale) o RESERVED';

CREATE TABLE inverter_measurement (
    time       timestamptz NOT NULL,
    register   text        NOT NULL,
    value      double precision,
    text_value text,
    CONSTRAINT inverter_measurement_uk UNIQUE (register, time)
);
CREATE INDEX inverter_measurement_time_idx ON inverter_measurement (time DESC);
COMMENT ON TABLE inverter_measurement IS 'Storico di tutti i registri dell''inverter: una riga per registro e lettura';
COMMENT ON COLUMN inverter_measurement.value IS 'Valore numerico già scalato (unità in inverter_register.unit)';
COMMENT ON COLUMN inverter_measurement.text_value IS 'Valore testuale (codici esadecimali, numero di serie)';

-- =====================================================================================
-- Open-Meteo
-- =====================================================================================

CREATE TABLE weather_hourly (
    time       timestamptz NOT NULL,
    source     text        NOT NULL,
    series     text        NOT NULL,
    variable   text        NOT NULL,
    value      double precision,
    unit       text,
    fetched_at timestamptz NOT NULL,
    CONSTRAINT weather_hourly_pk PRIMARY KEY (source, series, variable, time)
);
CREATE INDEX weather_hourly_time_idx ON weather_hourly (time DESC);
COMMENT ON TABLE weather_hourly IS 'Valore più recente di ogni grandezza oraria Open-Meteo (per le ore passate converge al dato analizzato)';
COMMENT ON COLUMN weather_hourly.time IS 'Ora a cui si riferisce il valore; per le grandezze medie (radiazione) è la fine dell''ora';
COMMENT ON COLUMN weather_hourly.source IS 'forecast (/api/meteo/forecast), solar (/api/meteo/solar), archive (/api/meteo/archive)';
COMMENT ON COLUMN weather_hourly.series IS 'meteo per forecast e archive, nome della falda per solar';

CREATE TABLE weather_forecast_run (
    fetched_at timestamptz NOT NULL,
    time       timestamptz NOT NULL,
    source     text        NOT NULL,
    series     text        NOT NULL,
    variable   text        NOT NULL,
    value      double precision,
    CONSTRAINT weather_forecast_run_pk PRIMARY KEY (source, series, variable, time, fetched_at)
);
CREATE INDEX weather_forecast_run_fetched_idx ON weather_forecast_run (fetched_at DESC);
COMMENT ON TABLE weather_forecast_run IS 'Archivio di ogni previsione scaricata, per confrontare previsto e reale';

-- =====================================================================================
-- Advisor: regole, impostazioni, consigli
-- =====================================================================================

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TABLE rule (
    id         text PRIMARY KEY CHECK (id ~ '^[a-z0-9][a-z0-9-]{0,63}$'),
    name       text        NOT NULL,
    type       text        NOT NULL,
    enabled    boolean     NOT NULL DEFAULT true,
    priority   integer     NOT NULL DEFAULT 100,
    parameters jsonb       NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(parameters) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER rule_updated_at BEFORE UPDATE ON rule FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE rule IS 'Regole dell''advisor. Le modifiche fatte qui valgono dalla valutazione successiva';
COMMENT ON COLUMN rule.type IS 'Tipo di regola implementato nell''advisor (elenco e parametri: GET /api/rules/types)';
COMMENT ON COLUMN rule.enabled IS 'false = regola disattivata, non viene valutata';
COMMENT ON COLUMN rule.priority IS 'Valutate per priorità crescente: decide la prima soddisfatta';
COMMENT ON COLUMN rule.parameters IS 'Parametri della regola; quelli mancanti prendono il valore di default';

CREATE TABLE site_settings (
    id         smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    settings   jsonb       NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER site_settings_updated_at BEFORE UPDATE ON site_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE site_settings IS 'Impostazioni dell''impianto (batterie, pannelli, stringhe, limiti inverter): una sola riga';

CREATE TABLE advice_log (
    time        timestamptz NOT NULL,
    action      text        NOT NULL,
    decided_by  text,
    reason      text,
    evaluations jsonb,
    context     jsonb,
    CONSTRAINT advice_log_pk PRIMARY KEY (time)
);
COMMENT ON TABLE advice_log IS 'Consigli calcolati dal controllo periodico dell''advisor, con esito di ogni regola e dati usati';
