package it.solis.modbus;

/** Errore di comunicazione Modbus con l'inverter. */
public class InverterCommunicationException extends RuntimeException {

    public InverterCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
