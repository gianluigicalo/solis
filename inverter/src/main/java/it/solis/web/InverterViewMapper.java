package it.solis.web;

import it.solis.modbus.Category;
import it.solis.modbus.SolisCodes;
import it.solis.modbus.SolisRegister;
import it.solis.service.InverterSnapshot;
import it.solis.web.dto.CategoryData;
import it.solis.web.dto.InverterData;
import it.solis.web.dto.InverterInfo;
import it.solis.web.dto.Measurement;
import it.solis.web.dto.PowerFlow;
import it.solis.web.dto.PowerFlow.BatteryState;
import it.solis.web.dto.PowerFlow.EnergyToday;
import it.solis.web.dto.PowerFlow.GridState;
import it.solis.web.dto.RegisterValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static it.solis.modbus.SolisRegister.*;

/** Trasforma una {@link InverterSnapshot} nelle viste esposte dall'API. */
@Component
class InverterViewMapper {

    private static final List<SolisRegister> FAULT_REGISTERS = List.of(
            FAULT_CODE_01, FAULT_CODE_02, FAULT_CODE_03, FAULT_CODE_04, FAULT_CODE_05, BMS_FAULT_01, BMS_FAULT_02);

    /** Registri usati da {@link #powerFlow}: il riepilogo legge solo questi, non tutto l'inverter. */
    static final Set<SolisRegister> SUMMARY_REGISTERS = Collections.unmodifiableSet(EnumSet.of(
            INVERTER_STATUS, PV_TOTAL_POWER, BATTERY_POWER, BATTERY_CURRENT_DIRECTION, BATTERY_SOC,
            METER_ACTIVE_POWER, HOUSE_LOAD_POWER, BACKUP_LOAD_POWER, INVERTER_TEMPERATURE,
            PV_ENERGY_TODAY, HOUSE_LOAD_TODAY, GRID_IMPORT_TODAY, GRID_EXPORT_TODAY, BATTERY_CHARGE_TODAY,
            BATTERY_DISCHARGE_TODAY,
            PV1_VOLTAGE, PV1_CURRENT, PV2_VOLTAGE, PV2_CURRENT, DC_BUS_VOLTAGE, AC_CURRENT_A, METER_VOLTAGE,
            METER_CURRENT));

    /** Registri usati da {@link #info}. */
    static final Set<SolisRegister> INFO_REGISTERS;

    static {
        EnumSet<SolisRegister> info = EnumSet.of(PRODUCT_MODEL, MODEL_DEFINITION, SERIAL_NUMBER, DSP_SOFTWARE_VERSION,
                HMI_SOFTWARE_VERSION, HMI_SUB_VERSION, PROTOCOL_VERSION, AC_OUTPUT_TYPE, DC_INPUT_TYPE, GRID_STANDARD,
                BATTERY_MODEL, METER1_TYPE_LOCATION, EPM_EXPORT_LIMIT, CLOCK_YEAR, CLOCK_MONTH, CLOCK_DAY, CLOCK_HOUR,
                CLOCK_MINUTE, CLOCK_SECOND, INVERTER_STATUS, OPERATING_MODE, STORAGE_CONTROL_MODE, OPERATING_STATUS);
        info.addAll(FAULT_REGISTERS);
        INFO_REGISTERS = Collections.unmodifiableSet(info);
    }

    /** Registri di una categoria. */
    static Set<SolisRegister> registersOf(Category category) {
        EnumSet<SolisRegister> set = EnumSet.noneOf(SolisRegister.class);
        for (SolisRegister r : SolisRegister.values()) {
            if (r.category() == category) {
                set.add(r);
            }
        }
        return set;
    }

    InverterData data(InverterSnapshot s) {
        Map<Category, Map<String, Measurement>> categories = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            categories.put(category, measurements(s, category));
        }
        return new InverterData(s.readAt(), categories);
    }

    CategoryData category(InverterSnapshot s, Category category) {
        return new CategoryData(s.readAt(), category, category.description(), measurements(s, category));
    }

    RegisterValue register(InverterSnapshot s, SolisRegister register) {
        return new RegisterValue(s.readAt(), register, register.category(), Measurement.of(register, s.get(register)));
    }

    InverterInfo info(InverterSnapshot s) {
        List<String> faults = FAULT_REGISTERS.stream()
                .flatMap(r -> SolisCodes.activeFaults(r, s.intValue(r)).stream())
                .toList();
        int hmiVersion = (s.intValue(HMI_SOFTWARE_VERSION) & 0xFF) << 8 | (s.intValue(HMI_SUB_VERSION) & 0xFF);
        return new InverterInfo(
                s.readAt(),
                s.text(PRODUCT_MODEL),
                s.text(MODEL_DEFINITION),
                s.text(SERIAL_NUMBER),
                s.text(DSP_SOFTWARE_VERSION),
                "0x%04X".formatted(hmiVersion),
                s.text(PROTOCOL_VERSION),
                SolisCodes.acOutputType(s.intValue(AC_OUTPUT_TYPE)),
                s.intValue(DC_INPUT_TYPE) + 1,
                s.text(GRID_STANDARD),
                SolisCodes.batteryModel(s.intValue(BATTERY_MODEL)),
                SolisCodes.meterTypeLocation(s.intValue(METER1_TYPE_LOCATION)),
                s.number(EPM_EXPORT_LIMIT),
                inverterClock(s),
                s.text(INVERTER_STATUS),
                SolisCodes.inverterStatus(s.intValue(INVERTER_STATUS)),
                SolisCodes.operatingMode(s.intValue(OPERATING_MODE)),
                SolisCodes.storageModes(s.intValue(STORAGE_CONTROL_MODE)),
                SolisCodes.operatingStatus(s.intValue(OPERATING_STATUS)),
                faults,
                !faults.isEmpty());
    }

    PowerFlow powerFlow(InverterSnapshot s) {
        BigDecimal batteryPower = s.number(BATTERY_POWER).abs();
        if (s.intValue(BATTERY_CURRENT_DIRECTION) == 1) {
            batteryPower = batteryPower.negate();
        }
        BigDecimal gridPower = s.number(METER_ACTIVE_POWER);

        return new PowerFlow(
                s.readAt(),
                SolisCodes.inverterStatus(s.intValue(INVERTER_STATUS)),
                s.number(PV_TOTAL_POWER),
                batteryPower,
                switch (batteryPower.signum()) {
                    case 1 -> BatteryState.CARICA;
                    case -1 -> BatteryState.SCARICA;
                    default -> BatteryState.INATTIVA;
                },
                s.number(BATTERY_SOC),
                gridPower,
                switch (gridPower.signum()) {
                    case 1 -> GridState.IMMISSIONE;
                    case -1 -> GridState.PRELIEVO;
                    default -> GridState.NEUTRO;
                },
                s.number(HOUSE_LOAD_POWER),
                s.number(BACKUP_LOAD_POWER),
                s.number(INVERTER_TEMPERATURE),
                new EnergyToday(
                        wattHours(s, PV_ENERGY_TODAY),
                        wattHours(s, HOUSE_LOAD_TODAY),
                        wattHours(s, GRID_IMPORT_TODAY),
                        wattHours(s, GRID_EXPORT_TODAY),
                        wattHours(s, BATTERY_CHARGE_TODAY),
                        wattHours(s, BATTERY_DISCHARGE_TODAY)),
                new PowerFlow.Electrical(
                        s.number(PV1_VOLTAGE), s.number(PV1_CURRENT), s.number(PV2_VOLTAGE), s.number(PV2_CURRENT),
                        s.number(DC_BUS_VOLTAGE), s.number(AC_CURRENT_A), s.number(METER_VOLTAGE),
                        s.number(METER_CURRENT)));
    }

    /** I contatori di energia dell'inverter sono in kWh: nel riepilogo l'energia è in Wh, come le potenze in W. */
    private static BigDecimal wattHours(InverterSnapshot s, SolisRegister register) {
        BigDecimal kwh = s.number(register);
        return kwh == null ? null : kwh.movePointRight(3).stripTrailingZeros().setScale(0, java.math.RoundingMode.HALF_UP);
    }

    private static Map<String, Measurement> measurements(InverterSnapshot s, Category category) {
        Map<String, Measurement> values = new LinkedHashMap<>();
        for (SolisRegister register : SolisRegister.values()) {
            if (register.category() == category) {
                values.put(register.name(), Measurement.of(register, s.get(register)));
            }
        }
        return values;
    }

    private static LocalDateTime inverterClock(InverterSnapshot s) {
        try {
            return LocalDateTime.of(2000 + s.intValue(CLOCK_YEAR), s.intValue(CLOCK_MONTH), s.intValue(CLOCK_DAY),
                    s.intValue(CLOCK_HOUR), s.intValue(CLOCK_MINUTE), s.intValue(CLOCK_SECOND));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
