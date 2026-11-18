package it.solis.modbus;

/** Provenienza della definizione di un registro. */
public enum RegisterSource {
    /** Definito nel protocollo ufficiale Solis ESINV-33000ID. */
    OFFICIAL("Protocollo ufficiale Solis RS485_MODBUS(ESINV-33000ID) Hybrid Inverter, 2020-09-15"),
    /** "Reserved" nel protocollo ufficiale, ma interpretato da progetti open source: NON ufficiale. */
    UNOFFICIAL("Definizione NON ufficiale ricavata da progetti open source"),
    /** Ricavato dalle impostazioni remote di SolisCloud (indirizzo, scala e menu della piattaforma Solis). */
    SOLISCLOUD("Definizione ricavata dalle impostazioni remote di SolisCloud, piattaforma ufficiale Solis"),
    /** "Reserved" nel protocollo ufficiale e senza alcuna interpretazione pubblica nota. */
    RESERVED("Registro riservato: significato non documentato");

    private final String description;

    RegisterSource(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
