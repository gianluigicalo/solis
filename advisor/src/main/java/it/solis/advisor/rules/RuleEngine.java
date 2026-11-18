package it.solis.advisor.rules;

import it.solis.advisor.context.EnergyContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Valuta le regole abilitate in ordine di priorità: decide la prima soddisfatta. */
@Component
public class RuleEngine {

    private final RuleTypeRegistry registry;

    public RuleEngine(RuleTypeRegistry registry) {
        this.registry = registry;
    }

    public Advice evaluate(EnergyContext context, List<RuleDefinition> rules) {
        var ordered = rules.stream()
                .filter(RuleDefinition::enabled)
                .sorted(Comparator.comparingInt(RuleDefinition::priority).thenComparing(RuleDefinition::id))
                .toList();

        List<RuleEvaluation> evaluations = new ArrayList<>();
        RuleEvaluation decisive = null;
        for (RuleDefinition rule : ordered) {
            var type = registry.find(rule.type()).orElse(null);
            RuleOutcome outcome;
            if (type == null) {
                outcome = RuleOutcome.noMatch("Tipo di regola '" + rule.type() + "' non più disponibile");
            } else {
                try {
                    var parameters = registry.normalize(type, rule.parameters(), java.util.Map.of());
                    outcome = type.evaluate(context, new RuleParameters(parameters));
                } catch (RuntimeException e) {
                    outcome = RuleOutcome.noMatch("Errore nella valutazione: " + e.getMessage());
                }
            }
            boolean isDecisive = outcome.matched() && decisive == null;
            var evaluation = new RuleEvaluation(rule.id(), rule.name(), rule.type(), rule.priority(),
                    type != null ? type.action() : null, outcome.matched(), isDecisive, outcome.reason());
            if (isDecisive) {
                decisive = evaluation;
            }
            evaluations.add(evaluation);
        }

        Action action = decisive != null ? decisive.action() : Action.SELF_CONSUMPTION;
        String reason = decisive != null ? decisive.reason() : "Nessuna regola soddisfatta";
        return new Advice(context.time(), action, action.description(),
                decisive != null ? decisive.ruleId() : null, reason, evaluations, context);
    }
}
