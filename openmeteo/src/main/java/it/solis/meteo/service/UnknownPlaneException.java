package it.solis.meteo.service;

import java.util.Collection;

public class UnknownPlaneException extends RuntimeException {

    public UnknownPlaneException(String name, Collection<String> available) {
        super("Falda '" + name + "' non configurata. Disponibili: " + String.join(", ", available));
    }
}
