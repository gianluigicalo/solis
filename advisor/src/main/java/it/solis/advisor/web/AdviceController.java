package it.solis.advisor.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.solis.advisor.context.EnergyContext;
import it.solis.advisor.rules.Advice;
import it.solis.advisor.service.AdviceService;
import it.solis.advisor.service.ContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/advice")
@Tag(name = "Consigli", description = "Valutazione delle regole")
public class AdviceController {

    private final AdviceService adviceService;
    private final ContextService contextService;

    public AdviceController(AdviceService adviceService, ContextService contextService) {
        this.adviceService = adviceService;
        this.contextService = contextService;
    }

    @GetMapping
    @Operation(summary = "Consiglio attuale", description = "Legge inverter e previsione e valuta tutte le regole "
            + "abilitate. Se un servizio non risponde le regole che ne hanno bisogno non scattano e il motivo "
            + "compare in context.warnings.")
    public Advice advice() {
        return adviceService.evaluateNow();
    }

    @GetMapping("/last")
    @Operation(summary = "Ultimo consiglio calcolato (anche dal controllo periodico), senza nuove letture")
    public ResponseEntity<Advice> last() {
        return ResponseEntity.of(adviceService.last());
    }

    @GetMapping("/context")
    @Operation(summary = "Dati su cui vengono valutate le regole (inverter, produzione stimata, impostazioni)")
    public EnergyContext context() {
        return contextService.current();
    }

    @PostMapping("/simulate")
    @Operation(summary = "Valuta le regole su dati inventati",
            description = "Utile per provare le regole: si possono indicare ora, SOC e produzione prevista. "
                    + "time e settings omessi prendono i valori attuali.")
    public Advice simulate(@RequestBody EnergyContext context) {
        return adviceService.simulate(context);
    }
}
