package it.solis.advisor.rules;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Tutti i tipi di regola disponibili (i bean {@link RuleType}). */
@Component
public class RuleTypeRegistry {

    private final Map<String, RuleType> types = new TreeMap<>();

    public RuleTypeRegistry(List<RuleType> types) {
        types.forEach(type -> this.types.put(type.id(), type));
    }

    public Collection<RuleType> all() {
        return types.values();
    }

    public Optional<RuleType> find(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public RuleType get(String id) {
        return find(id).orElseThrow(() -> new InvalidRuleException(
                "Tipo di regola '" + id + "' sconosciuto. Disponibili: " + String.join(", ", types.keySet())));
    }

    /**
     * Calcola i parametri completi: per ogni parametro del tipo usa {@code overrides}, poi {@code base}, poi il
     * default. Parametri sconosciuti in {@code overrides} sono un errore; quelli in {@code base} vengono scartati
     * (es. parametri rimossi da una nuova versione della regola).
     */
    public Map<String, Object> normalize(RuleType type, Map<String, Object> base, Map<String, Object> overrides) {
        var known = type.parameters().stream().map(ParameterDefinition::name).toList();
        for (String name : overrides.keySet()) {
            if (!known.contains(name)) {
                throw new InvalidRuleException("Parametro '" + name + "' sconosciuto per " + type.id()
                        + ". Ammessi: " + String.join(", ", known));
            }
        }
        var result = new LinkedHashMap<String, Object>();
        for (ParameterDefinition definition : type.parameters()) {
            Object value = overrides.containsKey(definition.name()) ? overrides.get(definition.name())
                    : base.getOrDefault(definition.name(), definition.defaultValue());
            result.put(definition.name(), definition.normalize(value));
        }
        return result;
    }
}
