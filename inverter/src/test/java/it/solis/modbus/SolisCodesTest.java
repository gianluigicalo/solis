package it.solis.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SolisCodesTest {

    @Test
    void storageModeBit5SetMeansGridChargeAllowed() {
        // valore letto dall'inverter e confermato da SolisCloud: solo autoconsumo
        assertEquals(List.of("Autoconsumo", "Carica da rete non consentita"), SolisCodes.storageModes(0x0001));
        // 33 = autoconsumo + carica da rete (combinazione impostata da SolisCloud)
        assertEquals(List.of("Autoconsumo", "Carica da rete consentita"), SolisCodes.storageModes(33));
    }

    @Test
    void storageModeDecodesPeakShaving() {
        // 2080 = peak shaving + carica da rete
        assertEquals(List.of("Peak shaving", "Carica da rete consentita"), SolisCodes.storageModes(2080));
    }

    @Test
    void batteryModelNames() {
        assertEquals("Lithium Battery LV", SolisCodes.batteryModel(0x0063));
        assertEquals("PYLON_LV", SolisCodes.batteryModel(1));
        assertEquals("PYLON_HV", SolisCodes.batteryModel(0x0100));
        assertEquals("Nessuna batteria", SolisCodes.batteryModel(0));
        assertEquals("Codice 0x00FE", SolisCodes.batteryModel(0xFE));
    }

    @Test
    void solisCloudSettingsWithValuesReadOnThisInverter() {
        assertEquals("Risparmio batteria disattivo", SolisCodes.describe(SolisRegister.FUNCTION_FLAGS_SETTING, "0x0004"));
        // 43302 = 1: il bit 8 è 0, neutro collegato
        assertEquals("Neutro di rete collegato", SolisCodes.describe(SolisRegister.AUX_CONTROL_FLAGS_SETTING, "0x0001"));
        assertEquals("Backup2Load automatico", SolisCodes.describe(SolisRegister.HYBRID_FUNCTION_FLAGS_SETTING, "0x0010"));
        assertEquals("Disattiva", SolisCodes.describe(SolisRegister.DRM_SETTING, java.math.BigDecimal.valueOf(2)));
        assertEquals("Tensione 0,1 V, tempo 0,01 s, frequenza 0,01 Hz",
                SolisCodes.describe(SolisRegister.GRID_PROTECTION_PRECISION_SETTING, java.math.BigDecimal.ONE));
        assertEquals("Pompa di calore disattiva", SolisCodes.describe(SolisRegister.HEAT_PUMP_SETTING, "0x0000"));
    }
}
