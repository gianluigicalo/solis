package it.solis.advisor.service;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String id) {
        super("Regola '" + id + "' non trovata");
    }
}
