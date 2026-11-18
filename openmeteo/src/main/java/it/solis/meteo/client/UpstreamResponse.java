package it.solis.meteo.client;

/** Risposta di Open-Meteo: stato HTTP e corpo JSON, restituiti al client senza modifiche. */
public record UpstreamResponse(int status, String body) {

    public boolean successful() {
        return status >= 200 && status < 300;
    }
}
