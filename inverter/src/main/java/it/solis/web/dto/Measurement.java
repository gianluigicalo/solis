package it.solis.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.modbus.RegisterAccess;
import it.solis.modbus.RegisterSource;
import it.solis.modbus.SolisCodes;
import it.solis.modbus.SolisRegister;

import java.math.BigDecimal;

@Schema(description = "Valore di un registro dell'inverter")
public record Measurement(
        @Schema(description = "Valore fisico già scalato (numero, oppure stringa per codici esadecimali e testi)",
                example = "398.7") Object value,
        @Schema(description = "Unità di misura", example = "V") String unit,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Significato del valore secondo le specifiche (codici, bitmask, sì/no); assente per le "
                + "misure", example = "Monofase") String decoded,
        @Schema(description = "Valore grezzo letto dal registro, prima della scala (intero, esadecimale per i codici "
                + "a 16 bit, testo per ASCII)", example = "3987") Object raw,
        @Schema(description = "Descrizione del dato", example = "Tensione stringa 1") String description,
        @Schema(description = "Indirizzo Modbus (33xxx input register, 43xxx holding register)", example = "33049")
        int register,
        @Schema(description = "READ_ONLY (input register, sola lettura) o READ_WRITE (holding register, modificabile)",
                example = "READ_ONLY") RegisterAccess access,
        @Schema(description = "Provenienza della definizione: OFFICIAL, UNOFFICIAL (fonte pubblica non ufficiale) "
                + "o RESERVED (significato non documentato)", example = "OFFICIAL") RegisterSource source,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Solo UNOFFICIAL: nome usato dalla fonte pubblica", example = "Leakage Current")
        String publicName,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Solo UNOFFICIAL: fonte pubblica della definizione", example = "Pho3niX90/solis_modbus")
        String reference) {

    public static Measurement of(SolisRegister register, Object value) {
        return new Measurement(value, register.unit(), SolisCodes.describe(register, value), raw(register, value),
                register.description(), register.address(), register.access(), register.source(), register.publicName(),
                register.reference());
    }

    /** Il valore fisico è raw / 10^decimals: si torna al grezzo spostando la virgola. */
    private static Object raw(SolisRegister register, Object value) {
        return value instanceof BigDecimal n ? n.movePointRight(register.decimals()).toBigInteger() : value;
    }
}
