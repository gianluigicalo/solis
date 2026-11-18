package it.solis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Avvia tutto il contesto Spring: intercetta gli errori che compaiono solo all'avvio (es. bean senza un costruttore
 * univoco). Nessuna connessione all'inverter: il client Modbus si collega solo alla prima lettura.
 */
@SpringBootTest
class SolisApplicationTest {

    @Test
    void contextLoads() {
    }
}
