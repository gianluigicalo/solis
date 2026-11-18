package it.solis.advisor.service;

import it.solis.advisor.context.EnergyContext;
import it.solis.advisor.history.HistoryRepository;
import it.solis.advisor.rules.Advice;
import it.solis.advisor.rules.RuleEngine;
import it.solis.advisor.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

@Service
public class AdviceService {

    private static final Logger log = LoggerFactory.getLogger(AdviceService.class);

    private final ContextService contextService;
    private final RuleEngine engine;
    private final StateStore store;
    private final HistoryRepository history;
    private final Clock clock;
    private volatile Advice last;

    public AdviceService(ContextService contextService, RuleEngine engine, StateStore store, HistoryRepository history,
                         Clock clock) {
        this.contextService = contextService;
        this.engine = engine;
        this.store = store;
        this.history = history;
        this.clock = clock;
    }

    /** Valuta le regole sui dati attuali. */
    public Advice evaluateNow() {
        var advice = engine.evaluate(contextService.current(), store.rules());
        remember(advice);
        return advice;
    }

    /** Valuta le regole su dati forniti dal client, senza interrogare inverter e openmeteo. */
    public Advice simulate(EnergyContext context) {
        return engine.evaluate(contextService.complete(context), store.rules());
    }

    public Optional<Advice> last() {
        return Optional.ofNullable(last);
    }

    /** Controllo periodico: salva il consiglio in advice_log e registra nel log ogni cambio. */
    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "${advisor.check-interval}")
    void periodicCheck() {
        try {
            history.insertAdvice(clock.instant(), evaluateNow());
        } catch (RuntimeException e) {
            log.warn("Controllo periodico fallito: {}", e.getMessage());
        }
    }

    private void remember(Advice advice) {
        var previous = last;
        last = advice;
        if (previous == null || previous.action() != advice.action()) {
            log.info("Consiglio: {} ({}) - {}", advice.action(),
                    advice.decidedBy() != null ? advice.decidedBy() : "nessuna regola", advice.reason());
        }
        advice.context().warnings().forEach(w -> log.debug("Avviso: {}", w));
    }
}
