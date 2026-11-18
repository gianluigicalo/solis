package it.solis.advisor.context;

/**
 * Produzione stimata (Wh) nell'ora che termina a {@code time} (convenzione Open-Meteo per la radiazione).
 * Numericamente coincide con la potenza media dell'ora in W.
 */
public record HourlyEstimate(java.time.LocalDateTime time, double wh) {
}
