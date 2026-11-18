package it.solis.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.modbus.Category;
import it.solis.modbus.RegisterAccess;
import it.solis.modbus.RegisterSource;
import it.solis.modbus.RegisterType;
import it.solis.modbus.SolisRegister;

@Schema(description = "Definizione di un registro Modbus supportato")
public record RegisterDefinition(
        SolisRegister name,
        @Schema(description = "Indirizzo del primo registro (33xxx input, 43xxx holding)", example = "33079") int address,
        @Schema(description = "Numero di registri a 16 bit occupati", example = "2") int words,
        RegisterType type,
        @Schema(description = "Cifre decimali: valore = grezzo / 10^decimals (negativo = moltiplicatore)", example = "1")
        int decimals,
        String unit,
        Category category,
        String description,
        @Schema(description = "READ_ONLY (input register) o READ_WRITE (holding register, modificabile)",
                example = "READ_ONLY") RegisterAccess access,
        @Schema(description = "Provenienza della definizione", example = "OFFICIAL") RegisterSource source,
        @JsonInclude(JsonInclude.Include.NON_NULL) String publicName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reference) {

    public static RegisterDefinition of(SolisRegister r) {
        return new RegisterDefinition(r, r.address(), r.words(), r.type(), r.decimals(), r.unit(), r.category(),
                r.description(), r.access(), r.source(), r.publicName(), r.reference());
    }
}
