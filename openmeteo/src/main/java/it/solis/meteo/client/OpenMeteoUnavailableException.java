package it.solis.meteo.client;

public class OpenMeteoUnavailableException extends RuntimeException {

    public OpenMeteoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
