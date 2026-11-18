package it.solis.advisor.store;

import it.solis.advisor.config.AdvisorProperties;
import it.solis.advisor.context.SiteSettings;
import it.solis.advisor.rules.RuleDefinition;
import it.solis.advisor.rules.RuleTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regole e impostazioni dell'impianto, nelle tabelle {@code rule} e {@code site_settings}.
 * Non c'è cache: le modifiche fatte direttamente nel database valgono dalla valutazione successiva.
 * Al primo avvio (tabella {@code site_settings} vuota) inserisce le impostazioni di {@code advisor.defaults}
 * e alcune regole di esempio.
 */
@Component
public class StateStore {

    private static final Logger log = LoggerFactory.getLogger(StateStore.class);
    private static final TypeReference<Map<String, Object>> PARAMETERS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final SiteSettings defaults;

    public StateStore(JdbcClient jdbc, JsonMapper jsonMapper, AdvisorProperties properties, RuleTypeRegistry registry,
                      TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.defaults = properties.defaults();
        transactions.executeWithoutResult(status -> initialize(registry));
    }

    private void initialize(RuleTypeRegistry registry) {
        migrateToWatts();
        int inserted = jdbc.sql("INSERT INTO site_settings (id, settings) VALUES (1, CAST(:settings AS jsonb)) "
                        + "ON CONFLICT (id) DO NOTHING")
                .param("settings", jsonMapper.writeValueAsString(defaults))
                .update();
        if (inserted == 1) {
            exampleRules(registry).forEach(this::saveRule);
            log.info("Primo avvio: inserite le impostazioni di default e {} regole di esempio", rules().size());
        } else {
            log.info("{} regole configurate nel database", rules().size());
        }
    }

    /**
     * Impostazioni e regole salvate quando energie e potenze erano in kWh e kW: le converte in Wh e W
     * (es. {@code batteryCapacityKwh: 20} → {@code batteryCapacityWh: 20000}, {@code maxForecastKwh: 6} →
     * {@code maxForecastWh: 6000}). Non fa nulla se sono già convertite. Niente operatore jsonb {@code ?}: JDBC lo
     * leggerebbe come parametro.
     */
    private void migrateToWatts() {
        int settings = jdbc.sql("""
                        UPDATE site_settings SET settings = jsonb_strip_nulls(
                            (settings - 'batteryCapacityKwh')
                            || jsonb_build_object('batteryCapacityWh', (settings ->> 'batteryCapacityKwh')::numeric * 1000)
                            || CASE WHEN settings #> '{inverter,maxPvInputKw}' IS NOT NULL THEN jsonb_build_object('inverter',
                                   jsonb_build_object(
                                       'maxPvInputW', (settings #>> '{inverter,maxPvInputKw}')::numeric * 1000,
                                       'ratedOutputW', (settings #>> '{inverter,ratedOutputKw}')::numeric * 1000,
                                       'maxBatteryPowerW', (settings #>> '{inverter,maxBatteryPowerKw}')::numeric * 1000))
                               ELSE '{}'::jsonb END)
                        WHERE settings -> 'batteryCapacityKwh' IS NOT NULL OR settings #> '{inverter,maxPvInputKw}' IS NOT NULL
                        """)
                .update();
        int rules = jdbc.sql("""
                        UPDATE rule r SET parameters = (
                            SELECT jsonb_object_agg(
                                CASE WHEN key LIKE '%Kwh' THEN left(key, -3) || 'Wh' ELSE key END,
                                CASE WHEN key LIKE '%Kwh' AND jsonb_typeof(value) = 'number'
                                     THEN to_jsonb(round((value #>> '{}')::numeric * 1000)) ELSE value END)
                            FROM jsonb_each(r.parameters))
                        WHERE EXISTS (SELECT 1 FROM jsonb_object_keys(r.parameters) k WHERE k LIKE '%Kwh')
                        """)
                .update();
        if (settings + rules > 0) {
            log.info("Convertite in Wh e W le impostazioni ({}) e {} regole salvate in kWh e kW", settings, rules);
        }
    }

    public SiteSettings settings() {
        return jdbc.sql("SELECT settings FROM site_settings WHERE id = 1")
                .query((rs, row) -> jsonMapper.readValue(rs.getString(1), SiteSettings.class))
                .optional()
                .map(s -> s.inverter() == null ? s.withInverter(defaults.inverter()) : s)
                .orElse(defaults);
    }

    public void saveSettings(SiteSettings settings) {
        jdbc.sql("INSERT INTO site_settings (id, settings) VALUES (1, CAST(:settings AS jsonb)) "
                        + "ON CONFLICT (id) DO UPDATE SET settings = EXCLUDED.settings")
                .param("settings", jsonMapper.writeValueAsString(settings))
                .update();
    }

    /** Regole ordinate per priorità. */
    public List<RuleDefinition> rules() {
        return jdbc.sql("SELECT id, name, type, enabled, priority, parameters FROM rule ORDER BY priority, id")
                .query(this::mapRule)
                .list();
    }

    public Optional<RuleDefinition> rule(String id) {
        return jdbc.sql("SELECT id, name, type, enabled, priority, parameters FROM rule WHERE id = :id")
                .param("id", id)
                .query(this::mapRule)
                .optional();
    }

    /** Inserisce o sostituisce la regola con lo stesso id. */
    public void saveRule(RuleDefinition rule) {
        jdbc.sql("""
                        INSERT INTO rule (id, name, type, enabled, priority, parameters)
                        VALUES (:id, :name, :type, :enabled, :priority, CAST(:parameters AS jsonb))
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type,
                            enabled = EXCLUDED.enabled, priority = EXCLUDED.priority, parameters = EXCLUDED.parameters
                        """)
                .param("id", rule.id())
                .param("name", rule.name())
                .param("type", rule.type())
                .param("enabled", rule.enabled())
                .param("priority", rule.priority())
                .param("parameters", jsonMapper.writeValueAsString(rule.parameters()))
                .update();
    }

    public boolean deleteRule(String id) {
        return jdbc.sql("DELETE FROM rule WHERE id = :id").param("id", id).update() > 0;
    }

    private RuleDefinition mapRule(ResultSet rs, int row) throws SQLException {
        return new RuleDefinition(rs.getString("id"), rs.getString("name"), rs.getString("type"),
                rs.getBoolean("enabled"), rs.getInt("priority"),
                jsonMapper.readValue(rs.getString("parameters"), PARAMETERS));
    }

    private static List<RuleDefinition> exampleRules(RuleTypeRegistry registry) {
        return List.of(
                example(registry, "carica-notturna", "Carica da rete di notte se il sole previsto è poco",
                        "LOW_FORECAST_GRID_CHARGE", 10),
                example(registry, "conserva-batteria", "Conserva la batteria se oggi resta poco sole",
                        "LOW_FORECAST_HOLD", 20),
                example(registry, "vendi-picco-serale", "Vendi la sera se domani c'è sole",
                        "EVENING_PEAK_SELL", 30));
    }

    private static RuleDefinition example(RuleTypeRegistry registry, String id, String name, String type, int priority) {
        var parameters = registry.normalize(registry.get(type), Map.of(), Map.of());
        return new RuleDefinition(id, name, type, true, priority, parameters);
    }
}
