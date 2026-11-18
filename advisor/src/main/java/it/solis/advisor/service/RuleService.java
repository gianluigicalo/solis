package it.solis.advisor.service;

import it.solis.advisor.rules.InvalidRuleException;
import it.solis.advisor.rules.RuleDefinition;
import it.solis.advisor.rules.RuleTypeRegistry;
import it.solis.advisor.store.StateStore;
import it.solis.advisor.web.dto.RulePatch;
import it.solis.advisor.web.dto.RuleRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RuleService {

    private static final int DEFAULT_PRIORITY = 100;

    private final StateStore store;
    private final RuleTypeRegistry registry;

    public RuleService(StateStore store, RuleTypeRegistry registry) {
        this.store = store;
        this.registry = registry;
    }

    public List<RuleDefinition> list() {
        return store.rules();
    }

    public RuleDefinition get(String id) {
        return store.rule(id).orElseThrow(() -> new RuleNotFoundException(id));
    }

    public synchronized RuleDefinition create(RuleRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString().substring(0, 8);
        if (store.rule(id).isPresent()) {
            throw new InvalidRuleException("Esiste già una regola con id '" + id + "'");
        }
        return save(id, request);
    }

    public synchronized RuleDefinition replace(String id, RuleRequest request) {
        get(id);
        if (request.id() != null && !request.id().equals(id)) {
            throw new InvalidRuleException("L'id nel corpo (" + request.id() + ") è diverso da quello nel percorso");
        }
        return save(id, request);
    }

    public synchronized RuleDefinition patch(String id, RulePatch patch) {
        var current = get(id);
        var type = registry.get(current.type());
        var parameters = registry.normalize(type, current.parameters(),
                patch.parameters() != null ? patch.parameters() : Map.of());
        var updated = new RuleDefinition(id,
                Objects.requireNonNullElse(patch.name(), current.name()),
                current.type(),
                Objects.requireNonNullElse(patch.enabled(), current.enabled()),
                Objects.requireNonNullElse(patch.priority(), current.priority()),
                parameters);
        store.saveRule(updated);
        return updated;
    }

    public synchronized void delete(String id) {
        if (!store.deleteRule(id)) {
            throw new RuleNotFoundException(id);
        }
    }

    private RuleDefinition save(String id, RuleRequest request) {
        var type = registry.get(request.type());
        var parameters = registry.normalize(type, Map.of(),
                request.parameters() != null ? request.parameters() : Map.of());
        var rule = new RuleDefinition(id, request.name(), type.id(),
                Objects.requireNonNullElse(request.enabled(), true),
                Objects.requireNonNullElse(request.priority(), DEFAULT_PRIORITY),
                parameters);
        store.saveRule(rule);
        return rule;
    }
}
