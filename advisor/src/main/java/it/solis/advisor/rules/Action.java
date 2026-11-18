package it.solis.advisor.rules;

/** Azione consigliata. */
public enum Action {
    CHARGE_FROM_GRID("Caricare la batteria dalla rete"),
    SELL_TO_GRID("Vendere: scaricare la batteria verso la rete"),
    HOLD_BATTERY("Conservare la carica: non scaricare la batteria in rete"),
    SELF_CONSUMPTION("Autoconsumo normale: nessun intervento");

    private final String description;

    Action(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
