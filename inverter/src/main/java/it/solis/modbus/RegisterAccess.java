package it.solis.modbus;

/** Accesso consentito dal protocollo Modbus Solis. */
public enum RegisterAccess {
    /** Input register (funzione 0x04): si può solo leggere. */
    READ_ONLY("sola lettura"),
    /** Holding register (funzione 0x03 in lettura, 0x06/0x10 in scrittura): impostazione modificabile. */
    READ_WRITE("modificabile");

    private final String description;

    RegisterAccess(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
