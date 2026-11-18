package it.solis.advisor.rules;

import java.time.LocalTime;
import java.util.Map;

/** Parametri già validati di una regola. */
public record RuleParameters(Map<String, Object> values) {

    public double number(String name) {
        return ((Number) values.get(name)).doubleValue();
    }

    public int integer(String name) {
        return ((Number) values.get(name)).intValue();
    }

    public LocalTime time(String name) {
        return LocalTime.parse(values.get(name).toString());
    }

    public boolean bool(String name) {
        return (Boolean) values.get(name);
    }
}
