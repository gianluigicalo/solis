package it.solis.modbus;

/** Codifica di un registro Modbus. I tipi a 32 bit sono big-endian con la word alta per prima. */
public enum RegisterType {
    U16(1),
    S16(1),
    U32(2),
    S32(2),
    /** Valore a 16 bit mostrato in esadecimale (codici modello, versioni firmware, bitmask). */
    HEX16(1),
    /** Stringa ASCII, 2 caratteri per registro (16 registri, come il numero di serie 33004-33019). */
    ASCII(16);

    private final int defaultWords;

    RegisterType(int defaultWords) {
        this.defaultWords = defaultWords;
    }

    public int defaultWords() {
        return defaultWords;
    }
}
