package it.solis.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import it.solis.modbus.SolisRegister;

@Schema(description = "Valore grezzo di un input register")
public record RawRegister(
        @Schema(example = "33067") int address,
        @Schema(description = "Valore senza segno", example = "600") int value,
        @Schema(example = "0x0258") String hex,
        @Schema(description = "Valore interpretato come intero con segno", example = "600") int signed,
        @Schema(description = "Registro mappato che inizia a questo indirizzo, se esiste") SolisRegister mappedAs) {
}
