package it.solis.modbus;

import io.netty.buffer.ByteBuf;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Converte il contenuto grezzo di un registro nel suo valore fisico. */
public final class RegisterDecoder {

    private RegisterDecoder() {
    }

    /**
     * @param buf    buffer della risposta Modbus
     * @param offset offset in byte del primo registro
     * @return {@link BigDecimal} per i valori numerici, {@link String} per HEX16 e ASCII,
     *         {@code null} se il registro segnala "dato non disponibile"
     */
    public static Object decode(ByteBuf buf, int offset, SolisRegister reg) {
        if (reg.ffffMeansUnavailable() && buf.getUnsignedShort(offset) == 0xFFFF) {
            return null;
        }
        return switch (reg.type()) {
            case U16 -> scaled(buf.getUnsignedShort(offset), reg);
            case S16 -> scaled(buf.getShort(offset), reg);
            case U32 -> scaled(buf.getUnsignedInt(offset), reg);
            case S32 -> scaled(buf.getInt(offset), reg);
            case HEX16 -> "0x%04X".formatted(buf.getUnsignedShort(offset));
            case ASCII -> ascii(buf, offset, reg.words() * 2);
        };
    }

    private static BigDecimal scaled(long raw, SolisRegister reg) {
        return reg.decimals() >= 0
                ? BigDecimal.valueOf(raw, reg.decimals())
                : BigDecimal.valueOf(raw).movePointRight(-reg.decimals());
    }

    private static String ascii(ByteBuf buf, int offset, int length) {
        var bytes = new byte[length];
        buf.getBytes(offset, bytes);
        return new String(bytes, StandardCharsets.US_ASCII).replace("\0", "").trim();
    }
}
