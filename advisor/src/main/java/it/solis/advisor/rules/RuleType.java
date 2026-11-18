package it.solis.advisor.rules;

import it.solis.advisor.context.EnergyContext;

import java.util.List;

/**
 * Tipo di regola. Per aggiungerne uno nuovo basta creare un {@code @Component} che implementa questa interfaccia:
 * viene registrato automaticamente e si possono creare regole di quel tipo via API.
 */
public interface RuleType {

    /** Identificativo stabile, usato nel campo {@code type} delle regole (es. LOW_FORECAST_GRID_CHARGE). */
    String id();

    String description();

    /** Azione consigliata quando la regola è soddisfatta. */
    Action action();

    /** Parametri modificabili, con default e limiti. */
    List<ParameterDefinition> parameters();

    RuleOutcome evaluate(EnergyContext context, RuleParameters parameters);
}
