package it.solis.advisor.rules;

/** Esito della valutazione di una regola: se è soddisfatta e perché. */
public record RuleOutcome(boolean matched, String reason) {

    public static RuleOutcome match(String reason) {
        return new RuleOutcome(true, reason);
    }

    public static RuleOutcome noMatch(String reason) {
        return new RuleOutcome(false, reason);
    }
}
