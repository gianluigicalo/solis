package it.solis.modbus;

/** Raggruppamento logico dei registri esposti dall'inverter. */
public enum Category {
    INFO("Identificazione, firmware e configurazione hardware"),
    CLOCK("Orologio interno"),
    STATUS("Stato, modalità e allarmi"),
    PV("Stringhe fotovoltaiche (lato DC) e inverter FV in parallelo"),
    INVERTER("Uscita AC dell'inverter"),
    BACKUP("Uscita backup (EPS)"),
    BATTERY("Batteria e BMS"),
    METER("Contatore di scambio (meter) e limitazione immissione (EPM)"),
    LOAD("Consumi"),
    ENERGY("Contatori di energia"),
    SETTINGS("Impostazioni correnti (copia in sola lettura)"),
    GRID_PROTECTION("Autotest protezioni di interfaccia CEI 0-21"),
    SETTINGS_RW("Impostazioni modificabili (holding register: lettura e scrittura)"),
    TIME_OF_USE("Carica e scarica a fasce orarie (holding register: lettura e scrittura)"),
    GRID_SETTINGS("Parametri e protezioni di rete (holding register: lettura e scrittura)"),
    SMART_PORT("Porta intelligente, generatore e carichi intelligenti (holding register: lettura e scrittura)"),
    HEAT_PUMP("Controllo della pompa di calore (holding register: lettura e scrittura)"),
    RESERVED("Registri riservati, senza documentazione (valori grezzi a 16 bit)");

    private final String description;

    Category(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
