package it.solis.advisor.history;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Alba e tramonto di un giorno (data locale), da Open-Meteo. */
public record SunTimes(LocalDate day, Instant sunrise, Instant sunset) {

    /** Mezzogiorno solare: a metà tra alba e tramonto. */
    public Instant solarNoon() {
        return sunrise.plus(Duration.between(sunrise, sunset).dividedBy(2));
    }
}
