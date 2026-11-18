package it.solis.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Parametri di connessione all'inverter, letti dal prefisso {@code solis} di application.yml.
 *
 * @param host     indirizzo del datalogger / gateway Modbus TCP
 * @param port     porta Modbus TCP
 * @param unitId   slave id dell'inverter
 * @param timeout  timeout di ogni richiesta Modbus
 * @param cacheTtl        per quanto tempo riusare il valore letto di un input register
 * @param holdingCacheTtl per quanto tempo riusare il valore letto di un holding register (impostazioni modificabili,
 *                        cambiano di rado)
 */
@Validated
@ConfigurationProperties(prefix = "solis")
public record SolisProperties(
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @Min(0) @Max(247) int unitId,
        @NotNull Duration timeout,
        @NotNull Duration cacheTtl,
        @NotNull Duration holdingCacheTtl) {
}
