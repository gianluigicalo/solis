package it.solis.advisor.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.solis.advisor.context.SiteSettings;
import it.solis.advisor.store.StateStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Impostazioni", description = "Caratteristiche dell'impianto usate dalle regole")
public class SettingsController {

    private final StateStore store;

    public SettingsController(StateStore store) {
        this.store = store;
    }

    @GetMapping
    @Operation(summary = "Impostazioni dell'impianto")
    public SiteSettings get() {
        return store.settings();
    }

    @PutMapping
    @Operation(summary = "Modifica le impostazioni dell'impianto")
    public SiteSettings put(@Valid @RequestBody SiteSettings settings) {
        store.saveSettings(settings);
        return settings;
    }
}
