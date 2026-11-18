package it.solis.advisor.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Parametro modificabile di un tipo di regola")
public record ParameterDefinition(
        @Schema(example = "targetSoc") String name,
        ParameterType type,
        @Schema(description = "Valore usato se non specificato", example = "80") Object defaultValue,
        Double min,
        Double max,
        @Schema(example = "%") String unit,
        String description) {

    public static ParameterDefinition number(String name, double defaultValue, double min, double max, String unit,
                                             String description) {
        return new ParameterDefinition(name, ParameterType.NUMBER, defaultValue, min, max, unit, description);
    }

    public static ParameterDefinition integer(String name, int defaultValue, int min, int max, String unit,
                                              String description) {
        return new ParameterDefinition(name, ParameterType.INTEGER, defaultValue, (double) min, (double) max, unit,
                description);
    }

    public static ParameterDefinition time(String name, String defaultValue, String description) {
        return new ParameterDefinition(name, ParameterType.TIME, defaultValue, null, null, null, description);
    }

    public static ParameterDefinition bool(String name, boolean defaultValue, String description) {
        return new ParameterDefinition(name, ParameterType.BOOLEAN, defaultValue, null, null, null, description);
    }

    /** Controlla tipo e intervallo e restituisce il valore nella forma canonica (Double, Integer, "HH:mm", Boolean). */
    Object normalize(Object raw) {
        if (raw == null) {
            throw invalid("non può essere null");
        }
        return switch (type) {
            case NUMBER -> checkRange(asNumber(raw).doubleValue());
            case INTEGER -> {
                double value = asNumber(raw).doubleValue();
                if (value != Math.rint(value)) {
                    throw invalid("deve essere un numero intero");
                }
                yield (int) checkRange(value);
            }
            case TIME -> {
                try {
                    yield LocalTime.parse(raw.toString()).toString();
                } catch (DateTimeParseException e) {
                    throw invalid("deve essere un orario HH:mm");
                }
            }
            case BOOLEAN -> {
                if (!(raw instanceof Boolean b)) {
                    throw invalid("deve essere true o false");
                }
                yield b;
            }
        };
    }

    private Number asNumber(Object raw) {
        if (raw instanceof Number n) {
            return n;
        }
        throw invalid("deve essere un numero");
    }

    private double checkRange(double value) {
        if ((min != null && value < min) || (max != null && value > max)) {
            throw invalid("deve essere compreso tra " + min + " e " + max);
        }
        return value;
    }

    private InvalidRuleException invalid(String message) {
        return new InvalidRuleException("Parametro '" + name + "' " + message);
    }
}
