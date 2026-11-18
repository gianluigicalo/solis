package it.solis.advisor.accuracy;

import io.swagger.v3.oas.annotations.media.Schema;

/** Quale previsione archiviata confrontare con la produzione reale. */
@Schema(description = "LATEST = ultima disponibile prima dell'ora; PRE_DAWN = scaricata prima dell'alba del giorno; "
        + "EVENING = scaricata la sera prima (entro la mezzanotte)")
public enum ForecastSelection {
    LATEST("l'ultima previsione disponibile prima di ogni ora", "prima dell'ora"),
    PRE_DAWN("la previsione scaricata prima dell'alba", "prima dell'alba del giorno"),
    EVENING("la previsione scaricata la sera prima", "la sera prima");

    private final String description;
    private final String when;

    ForecastSelection(String description, String when) {
        this.description = description;
        this.when = when;
    }

    /** Per i testi: "con {description} la stima è stata...". */
    public String description() {
        return description;
    }

    /** Per i testi: "nessuna previsione scaricata {when}". */
    public String when() {
        return when;
    }
}
