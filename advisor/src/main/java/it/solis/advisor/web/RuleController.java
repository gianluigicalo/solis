package it.solis.advisor.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.solis.advisor.rules.RuleDefinition;
import it.solis.advisor.rules.RuleTypeRegistry;
import it.solis.advisor.service.RuleService;
import it.solis.advisor.web.dto.RulePatch;
import it.solis.advisor.web.dto.RuleRequest;
import it.solis.advisor.web.dto.RuleTypeView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@Tag(name = "Regole", description = "Gestione delle regole: aggiunta, modifica dei parametri, rimozione")
public class RuleController {

    private final RuleService ruleService;
    private final RuleTypeRegistry registry;

    public RuleController(RuleService ruleService, RuleTypeRegistry registry) {
        this.ruleService = ruleService;
        this.registry = registry;
    }

    @GetMapping("/types")
    @Operation(summary = "Tipi di regola disponibili, con i parametri modificabili, i default e i limiti")
    public List<RuleTypeView> types() {
        return registry.all().stream().map(RuleTypeView::of).toList();
    }

    @GetMapping
    @Operation(summary = "Regole configurate, in ordine di priorità")
    public List<RuleDefinition> list() {
        return ruleService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Una regola")
    public RuleDefinition get(@PathVariable("id") String id) {
        return ruleService.get(id);
    }

    @PostMapping
    @Operation(summary = "Aggiunge una regola")
    public ResponseEntity<RuleDefinition> create(@Valid @RequestBody RuleRequest request) {
        var rule = ruleService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(rule.id()).toUri();
        return ResponseEntity.created(location).body(rule);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sostituisce una regola (i parametri omessi tornano al default)")
    public RuleDefinition replace(@PathVariable("id") String id, @Valid @RequestBody RuleRequest request) {
        return ruleService.replace(id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Modifica nome, abilitazione, priorità o singoli parametri")
    public RuleDefinition patch(@PathVariable("id") String id, @RequestBody RulePatch patch) {
        return ruleService.patch(id, patch);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Rimuove una regola")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
