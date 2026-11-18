package it.solis.service;

import it.solis.modbus.SolisRegister;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Lettura completa dei registri dell'inverter in un dato istante. */
public record InverterSnapshot(Instant readAt, Map<SolisRegister, Object> values) {

    public Object get(SolisRegister register) {
        return values.get(register);
    }

    public BigDecimal number(SolisRegister register) {
        return values.get(register) instanceof BigDecimal n ? n : null;
    }

    public String text(SolisRegister register) {
        Object value = values.get(register);
        return value == null ? null : value.toString();
    }

    public int intValue(SolisRegister register) {
        Object value = values.get(register);
        return switch (value) {
            case BigDecimal n -> n.intValue();
            case String s when s.startsWith("0x") -> Integer.parseInt(s.substring(2), 16);
            case null, default -> 0;
        };
    }
}
