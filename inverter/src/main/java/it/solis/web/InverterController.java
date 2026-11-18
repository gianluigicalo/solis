package it.solis.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.solis.modbus.Category;
import it.solis.modbus.SolisRegister;
import it.solis.service.InverterService;
import it.solis.web.dto.CategoryData;
import it.solis.web.dto.InverterData;
import it.solis.web.dto.InverterInfo;
import it.solis.web.dto.PowerFlow;
import it.solis.web.dto.RawRegister;
import it.solis.web.dto.RegisterDefinition;
import it.solis.web.dto.RegisterValue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** API in sola lettura dei dati dell'inverter. */
@RestController
@RequestMapping("/api/inverter")
@Tag(name = "Inverter", description = "Dati in sola lettura dell'inverter Solis")
@ApiResponse(responseCode = "503", description = "Inverter non raggiungibile",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class InverterController {

    private final InverterService service;
    private final InverterViewMapper mapper;

    public InverterController(InverterService service, InverterViewMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/summary")
    @Operation(summary = "Flussi di potenza", description = "Produzione FV, batteria, rete e consumi istantanei, più l'energia del giorno.")
    public PowerFlow summary() {
        return mapper.powerFlow(service.snapshot(InverterViewMapper.SUMMARY_REGISTERS));
    }

    @GetMapping("/info")
    @Operation(summary = "Identificazione e stato", description = "Modello, numero di serie, firmware, orologio, stato e presenza di guasti.")
    public InverterInfo info() {
        return mapper.info(service.snapshot(InverterViewMapper.INFO_REGISTERS));
    }

    @GetMapping("/data")
    @Operation(summary = "Tutti i dati", description = "Tutti i registri letti dall'inverter, raggruppati per categoria.")
    public InverterData data() {
        return mapper.data(service.snapshot());
    }

    @GetMapping("/data/{category}")
    @Operation(summary = "Dati di una categoria")
    public CategoryData category(@Parameter(description = "Categoria", example = "BATTERY") @PathVariable Category category) {
        return mapper.category(service.snapshot(InverterViewMapper.registersOf(category)), category);
    }

    @GetMapping("/registers/{register}")
    @Operation(summary = "Valore di un registro")
    public RegisterValue register(@Parameter(description = "Nome del registro", example = "BATTERY_SOC") @PathVariable SolisRegister register) {
        return mapper.register(service.snapshot(java.util.Set.of(register)), register);
    }

    @GetMapping("/registers")
    @Operation(summary = "Elenco dei registri supportati", description = "Mappa dei registri Modbus con indirizzo, tipo e scala. Non interroga l'inverter.")
    public List<RegisterDefinition> registers() {
        return Arrays.stream(SolisRegister.values()).map(RegisterDefinition::of).toList();
    }

    @GetMapping("/raw")
    @Operation(summary = "Lettura grezza di input register",
            description = "Legge direttamente dall'inverter (senza cache) un intervallo di input register e ne restituisce "
                    + "i valori a 16 bit. Utile per esplorare i registri non documentati.")
    public List<RawRegister> raw(
            @Parameter(description = "Primo indirizzo", example = "33000") @RequestParam @Min(30000) @Max(39999) int start,
            @Parameter(description = "Numero di registri (max 50)", example = "50") @RequestParam(defaultValue = "50") @Min(1) @Max(50) int count) {
        int[] values = service.readRaw(start, count);
        Map<Integer, SolisRegister> mapped = Arrays.stream(SolisRegister.values())
                .collect(Collectors.toMap(SolisRegister::address, Function.identity()));
        List<RawRegister> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int address = start + i;
            result.add(new RawRegister(address, values[i], "0x%04X".formatted(values[i]), (short) values[i],
                    mapped.get(address)));
        }
        return result;
    }
}
