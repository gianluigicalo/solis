package it.solis.modbus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Tabelle di decodifica del protocollo Solis ESINV-33000ID (appendici 2, 5, 6, 7) per gli ibridi monofase LV.
 */
public final class SolisCodes {

    private SolisCodes() {
    }

    /** Appendice 2, colonna "4G / ibridi": stato corrente (registro 33095). */
    private static final Map<Integer, String> INVERTER_STATUS = Map.ofEntries(
            entry(0x0000, "In attesa"),
            entry(0x0001, "Avvio (open run)"),
            entry(0x0002, "Avvio (soft run)"),
            entry(0x0003, "In produzione"),
            entry(0x0004, "Standby (bypass)"),
            entry(0x0005, "Sincronizzazione bypass"),
            entry(0x0006, "Rete verso carico (bypass)"),
            entry(0x000F, "Funzionamento normale"),
            entry(0x1004, "Rete assente"),
            entry(0x1010, "Sovratensione di rete (OV-G-V)"),
            entry(0x1011, "Sottotensione di rete (UN-G-V)"),
            entry(0x1012, "Sovrafrequenza di rete (OV-G-F)"),
            entry(0x1013, "Sottofrequenza di rete (UN-G-F)"),
            entry(0x1014, "Impedenza di rete eccessiva / corrente inversa (G-IMP)"),
            entry(0x1015, "Rete assente (NO-Grid)"),
            entry(0x1016, "Rete sbilanciata (G-PHASE)"),
            entry(0x1017, "Fluttuazione frequenza di rete (G-F-FLU)"),
            entry(0x1018, "Sovracorrente di rete (OV-G-I)"),
            entry(0x1019, "Errore campionamento corrente di rete (IGFOL-F)"),
            entry(0x1020, "Sovratensione DC (OV-DC)"),
            entry(0x1021, "Sovratensione bus DC (OV-BUS)"),
            entry(0x1022, "Sbilanciamento bus DC (UNB-BUS)"),
            entry(0x1023, "Sottotensione bus DC (UN-BUS)"),
            entry(0x1024, "Sbilanciamento bus DC 2 (UNB2-BUS)"),
            entry(0x1025, "Sovracorrente DC canale A (OV-DCA-I)"),
            entry(0x1026, "Sovracorrente DC canale B (OV-DCB-I)"),
            entry(0x1027, "Interferenza DC (DC-INTF)"),
            entry(0x1028, "Polarità DC invertita (Reve-DC)"),
            entry(0x1029, "Guasto messa a terra punto medio FV (PvMidIso)"),
            entry(0x1030, "Protezione interferenza di rete (GRID-INTF)"),
            entry(0x1031, "Protezione inizializzazione DSP (INI-FAULT)"),
            entry(0x1032, "Sovratemperatura (OV-TEM)"),
            entry(0x1033, "Guasto isolamento FV (PV ISO-PRO)"),
            entry(0x1034, "Protezione corrente di dispersione (ILeak-PRO)"),
            entry(0x1035, "Guasto verifica relè (RelayChk-FAIL)"),
            entry(0x1036, "Protezione DSP B (DSP-B-FAULT)"),
            entry(0x1037, "Iniezione componente DC (DCInj-FAULT)"),
            entry(0x1038, "Sottotensione alimentazione 12V (12Power-FAULT)"),
            entry(0x1039, "Verifica corrente di dispersione (ILeak-Check)"),
            entry(0x103A, "Sottotemperatura (UN-TEM)"),
            entry(0x1040, "Guasto autotest AFCI (AFCI-Check)"),
            entry(0x1041, "Arco elettrico rilevato (ARC-FAULT)"),
            entry(0x1042, "Guasto SRAM DSP (RAM-FAULT)"),
            entry(0x1043, "Guasto FLASH DSP (FLASH-FAULT)"),
            entry(0x1044, "Guasto program counter DSP (PC-FAULT)"),
            entry(0x1045, "Guasto registri DSP (REG-FAULT)"),
            entry(0x1046, "Protezione interferenza di rete 02 (GRID-INTF02)"),
            entry(0x1047, "Errore campionamento corrente di rete (IG-AD)"),
            entry(0x1048, "Sovracorrente IGBT (IGBT-OV-I)"),
            entry(0x1050, "Sovracorrente transitoria di rete (OV-IgTr)"),
            entry(0x1051, "Sovratensione hardware batteria (OV-Vbatt-H)"),
            entry(0x1052, "Sovracorrente hardware LLC (OV-ILLC)"),
            entry(0x1053, "Sovratensione batteria (OV-Vbatt)"),
            entry(0x1054, "Sottotensione batteria (UN-Vbatt)"),
            entry(0x1055, "Batteria non collegata (NO-Battery)"),
            entry(0x1056, "Sovratensione uscita backup (OV-VBackup)"),
            entry(0x1057, "Sovraccarico uscita backup (Over-Load)"),
            entry(0x1058, "Errore autodiagnosi DSP (DspSelfChk)"),
            entry(0x2010, "Fail safe"),
            entry(0x2011, "Comunicazione meter assente (MET_Comm_FAIL)"),
            entry(0x2012, "Comunicazione batteria assente (CAN_Comm_FAIL)"),
            entry(0x2014, "Comunicazione DSP assente (DSP_Comm_FAIL)"),
            entry(0x2015, "Allarme BMS (Alarm-BMS)"),
            entry(0x2016, "Batteria selezionata non corrispondente (BatName-FAIL)"),
            entry(0x2017, "Allarme BMS 2 (Alarm2-BMS)"),
            entry(0x2018, "Collegamento DRM fallito (DRM_LINK_FAIL)"),
            entry(0x2019, "Selezione meter errata (MET_SEL_FAIL)"),
            entry(0x2020, "Temperatura alta batteria al piombo"),
            entry(0x2021, "Temperatura bassa batteria al piombo"),
            entry(0xF010, "Sovratensione transitoria di rete (allarme)"),
            entry(0xF011, "Guasto ventola (allarme)"));

    /** Appendice 5: significato dei bit dei registri di guasto 33116-33120. */
    private static final Map<SolisRegister, List<String>> FAULT_BITS = Map.of(
            SolisRegister.FAULT_CODE_01, List.of("Rete assente", "Sovratensione di rete", "Sottotensione di rete",
                    "Sovrafrequenza di rete", "Sottofrequenza di rete", "Rete sbilanciata",
                    "Fluttuazione frequenza di rete", "Corrente inversa verso la rete",
                    "Errore inseguimento corrente di rete", "Comunicazione meter assente", "Fail safe"),
            SolisRegister.FAULT_CODE_02, List.of("Sovratensione uscita backup", "Sovraccarico uscita backup"),
            SolisRegister.FAULT_CODE_03, List.of("Batteria non collegata", "Sovratensione batteria",
                    "Sottotensione batteria"),
            SolisRegister.FAULT_CODE_04, List.of("Sovratensione DC", "Sovratensione bus DC",
                    "Sbilanciamento tensione bus DC", "Sottotensione bus DC", "Sbilanciamento tensione bus DC 2",
                    "Sovracorrente DC circuito A", "Sovracorrente DC circuito B", "Interferenza ingresso DC",
                    "Sovracorrente di rete", "Sovracorrente IGBT", "Interferenza di rete 02", "Autotest AFCI",
                    "Arco elettrico", "Guasto campionamento corrente di rete", "Errore autodiagnosi DSP"),
            SolisRegister.FAULT_CODE_05, List.of("Interferenza di rete", "Componente DC eccessiva",
                    "Sovratemperatura", "Verifica relè", "Sottotemperatura", "Guasto isolamento FV",
                    "Sottotensione 12V", "Corrente di dispersione", "Autotest corrente di dispersione",
                    "Inizializzazione DSP", "Protezione DSP B", "Sovratensione hardware batteria",
                    "Sovracorrente hardware LLC", "Sovracorrente transitoria di rete",
                    "Comunicazione CAN assente", "Comunicazione DSP assente"),
            // Colonna "3-5K LV Hybrid" della tabella dei registri 33145/33146
            SolisRegister.BMS_FAULT_01, List.of("", "BMS: protezione sovratensione", "BMS: protezione sottotensione",
                    "BMS: protezione sovratemperatura", "BMS: protezione sottotemperatura",
                    "BMS: sovratemperatura in carica", "BMS: sottotemperatura in carica",
                    "BMS: sovracorrente di scarica"),
            SolisRegister.BMS_FAULT_02, List.of("BMS: sovracorrente di carica", "", "", "BMS: protezione interna",
                    "BMS: moduli batteria sbilanciati"));

    /** Appendice 6: stato operativo (registro 33121). */
    private static final List<String> OPERATING_STATUS_BITS = List.of("Funzionamento normale", "Inizializzazione",
            "Spegnimento comandato", "Spegnimento per guasto", "Standby",
            "Funzionamento limitato (temperatura, frequenza, ...)", "Funzionamento limitato (causa esterna)",
            "Sovraccarico backup", "Guasto carico", "Guasto rete", "Guasto batteria", "",
            "Sovratensione transitoria di rete (avviso)", "Guasto ventola (avviso)");

    /**
     * Modalità di accumulo (registri 33132 e 43110). Il bit 5 (carica da rete) è gestito a parte per indicarlo
     * sia acceso sia spento: 1 = carica da rete consentita, come in SolisCloud (es. 33 = autoconsumo + carica da rete).
     */
    private static final List<String> STORAGE_MODE_BITS = List.of("Autoconsumo", "Carica a fasce orarie",
            "Off-grid", "Risveglio batteria", "Riserva batteria (backup)", "", "Priorità immissione",
            "", "", "", "", "Peak shaving");

    /** Modelli batteria bassa tensione (registri 33160 e 43009), dal menu "Modello batteria" di SolisCloud. */
    private static final Map<Integer, String> BATTERY_MODELS_LV = Map.ofEntries(
            Map.entry(1, "PYLON_LV"),
            Map.entry(3, "B_BOX_LV BYD"),
            Map.entry(4, "Dyness LV"),
            Map.entry(5, "PureDrive-LV"),
            Map.entry(6, "LG Chem LV"),
            Map.entry(7, "AoBo-LV"),
            Map.entry(8, "GREEN-LV"),
            Map.entry(9, "WECO-LV"),
            Map.entry(10, "FreedomWon-LV"),
            Map.entry(11, "Soluna-LV"),
            Map.entry(12, "GSL-LV"),
            Map.entry(13, "Rita-LV"),
            Map.entry(14, "GS Energy LV"),
            Map.entry(15, "Alpha LV"),
            Map.entry(16, "UZ ENERGY-LV"),
            Map.entry(17, "ATL-LV"),
            Map.entry(18, "Zeta-LV"),
            Map.entry(19, "Highstar-LV"),
            Map.entry(20, "KODAK-LV"),
            Map.entry(21, "FOX-LV"),
            Map.entry(22, "EXIDE"),
            Map.entry(23, "HD Energy"),
            Map.entry(24, "PYTES"),
            Map.entry(25, "ARVIO"),
            Map.entry(26, "PAND"),
            Map.entry(27, "WANKE"),
            Map.entry(28, "Dowell"),
            Map.entry(29, "ROSEN ESS"),
            Map.entry(30, "ZRGP"),
            Map.entry(31, "Narada"),
            Map.entry(32, "BST LV"),
            Map.entry(33, "Cegasa LV"),
            Map.entry(34, "Meterboost"),
            Map.entry(35, "MOURA"),
            Map.entry(36, "Tecloman LV"),
            Map.entry(37, "Upower UE-H"),
            Map.entry(38, "Upower UE-I"),
            Map.entry(39, "SUNWODA LV"),
            Map.entry(40, "CFE LV"),
            Map.entry(41, "DMEGC LV"),
            Map.entry(42, "J-Pack-LV"),
            Map.entry(43, "HY4850"),
            Map.entry(44, "AMPACE"),
            Map.entry(45, "JohnRay"),
            Map.entry(46, "GSO"),
            Map.entry(47, "YOSHOPO LV"),
            Map.entry(48, "Hubble"),
            Map.entry(49, "AlpSolarr LV"),
            Map.entry(50, "Haier LV"),
            Map.entry(51, "NAHUI LV"),
            Map.entry(52, "FEB"),
            Map.entry(53, "shoto"),
            Map.entry(54, "KG"),
            Map.entry(55, "Duracell"),
            Map.entry(56, "NBZK LV"),
            Map.entry(57, "STELTEC LV"),
            Map.entry(61, "ETOWER_LV"),
            Map.entry(62, "Greenrich"),
            Map.entry(63, "PYTES_LV"),
            Map.entry(64, "Autarco_LV"),
            Map.entry(83, "FD"),
            Map.entry(84, "JA-L"),
            Map.entry(85, "Clenergy"),
            Map.entry(86, "Solis-Alpha"),
            Map.entry(97, "SolisStorage RE-L"),
            Map.entry(98, "Lithium Battery LV(RS485)"),
            Map.entry(99, "Lithium Battery LV"));

    /** Modelli batteria alta tensione (byte alto del codice), dal menu "Modello batteria" di SolisCloud. */
    private static final Map<Integer, String> BATTERY_MODELS_HV = Map.ofEntries(
            Map.entry(256, "PYLON_HV"),
            Map.entry(512, "User define"),
            Map.entry(768, "B_BOX_HV BYD"),
            Map.entry(1024, "LG_HVLG"),
            Map.entry(1280, "SOLUNA_HV"),
            Map.entry(1536, "Dyness HV"),
            Map.entry(1792, "Aoboet HV"),
            Map.entry(2048, "WECO HV"),
            Map.entry(2304, "Alpha HV"));

    private static final Map<Integer, String> OPERATING_MODE = Map.of(
            0x0002, "Autoconsumo",
            0x0008, "Priorità immissione",
            0x0200, "Peak shaving");

    private static final Map<Integer, String> METER_LOCATION = Map.of(
            0x01, "rete", 0x02, "carico", 0x03, "rete + FV (due meter)");

    private static final Map<Integer, String> METER_TYPE = Map.of(
            0x01, "generico monofase", 0x02, "Acrel trifase", 0x03, "generico trifase",
            0x04, "Eastron monofase", 0x05, "Eastron trifase", 0x06, "nessun meter");

    private static final Map<Integer, String> AC_OUTPUT_TYPE = Map.of(
            0, "Monofase", 1, "Trifase 4 fili", 2, "Trifase 3 fili", 3, "Trifase 3 o 4 fili");

    public static String inverterStatus(int code) {
        return INVERTER_STATUS.getOrDefault(code, "Codice 0x%04X".formatted(code));
    }

    /** Descrizioni dei bit di guasto attivi nel registro indicato. */
    public static List<String> activeFaults(SolisRegister register, int value) {
        List<String> names = FAULT_BITS.getOrDefault(register, List.of());
        List<String> active = new ArrayList<>();
        for (int bit = 0; bit < 16; bit++) {
            if ((value & (1 << bit)) != 0) {
                String name = bit < names.size() ? names.get(bit) : "";
                active.add(name.isEmpty() ? "%s bit %d".formatted(register.name(), bit) : name);
            }
        }
        return active;
    }

    public static List<String> operatingStatus(int value) {
        return bits(value, OPERATING_STATUS_BITS);
    }

    public static List<String> storageModes(int value) {
        List<String> modes = bits(value, STORAGE_MODE_BITS);
        modes.add((value & (1 << 5)) != 0 ? "Carica da rete consentita" : "Carica da rete non consentita");
        return modes;
    }

    /** Nome del modello batteria (codice dei registri 33160 e 43009). */
    public static String batteryModel(int code) {
        if (code == 0) {
            return "Nessuna batteria";
        }
        String name = (code & 0xFF) == 0 ? BATTERY_MODELS_HV.get(code) : BATTERY_MODELS_LV.get(code);
        return name != null ? name : "Codice 0x%04X".formatted(code);
    }

    public static String operatingMode(int value) {
        return OPERATING_MODE.getOrDefault(value, "Codice 0x%04X".formatted(value));
    }

    public static String meterTypeLocation(int value) {
        String location = METER_LOCATION.getOrDefault(value >> 8, "posizione sconosciuta");
        String type = METER_TYPE.getOrDefault(value & 0xFF, "tipo sconosciuto");
        return "Meter %s sul lato %s".formatted(type, location);
    }

    public static String acOutputType(int value) {
        return AC_OUTPUT_TYPE.getOrDefault(value, "Codice " + value);
    }

    private static final List<String> WORKING_MODES = List.of("Nessuna", "Volt-watt", "Volt-var", "cosφ fisso",
            "Q fissa", "P-cosφ", "Rule21 volt-watt");

    private static final List<String> SINGLE_SELFTESTS = List.of("Nessuno", "59.S1", "59.S2", "27.S1", "27.S2",
            "81>.S1", "81<.S1", "81>.S2F", "81<.S2F", "81>.S2S", "81<.S2S");

    private static final List<String> SETTING_FLAG_BITS = List.of("Timeout flash", "Azzeramento energia", "", "", "",
            "", "", "", "Reset datalogger");

    private static final List<String> METER_PLACEMENT_BITS = List.of("Meter sul carico", "Meter sulla rete",
            "CT sulla rete", "CT inverter FV in parallelo", "EPM attivo", "Failsafe attivo");

    private static final List<String> TOU2_PERIOD_BITS = List.of("Carica 1", "Carica 2", "Carica 3", "Carica 4",
            "Carica 5", "Carica 6", "Scarica 1", "Scarica 2", "Scarica 3", "Scarica 4", "Scarica 5", "Scarica 6");

    private static final List<String> EPM_STATUS_BITS = List.of("EPM attivo", "Failsafe attivo");

    /**
     * Significato del valore di un registro secondo le specifiche (codici, bitmask, sì/no), oppure {@code null}
     * se il registro è una misura senza decodifica.
     */
    public static String describe(SolisRegister register, Object value) {
        if (value == null) {
            return null;
        }
        int v = switch (value) {
            case java.math.BigDecimal n -> n.intValue();
            case String s when s.startsWith("0x") -> Integer.parseInt(s.substring(2), 16);
            default -> Integer.MIN_VALUE;
        };
        if (v == Integer.MIN_VALUE) {
            return null;
        }
        return switch (register) {
            case INITIAL_SETUP_DONE, BATTERY_DETECTED, BACKUP_ENABLED, BATTERY_CHARGE_DISCHARGE_ENABLED,
                 VOLTAGE_DROOP_ENABLED -> yesNo(v);
            case PARALLEL_PV_INVERTER_CT -> v == 0 ? "Off" : v == 1 ? "On" : null;
            case AC_OUTPUT_TYPE -> acOutputType(v);
            case DC_INPUT_TYPE -> (v + 1) + (v == 0 ? " ingresso" : " ingressi");
            case MODEL_DEFINITION -> "Protocollo 0x%02X, modello 0x%02X".formatted(v >> 8, v & 0xFF);
            case WORKING_MODE -> v < WORKING_MODES.size() ? WORKING_MODES.get(v) : null;
            case GRID_STANDARD, GRID_CODE_SETTING -> v == 0x003E ? "CEI 0-21" : null;
            case INVERTER_STATUS -> inverterStatus(v);
            case SETTING_FLAGS -> joined(bits(v, SETTING_FLAG_BITS), "Nessun flag");
            case FAULT_CODE_01, FAULT_CODE_02, FAULT_CODE_03, FAULT_CODE_04, FAULT_CODE_05, BMS_FAULT_01,
                 BMS_FAULT_02 -> joined(activeFaults(register, v), "Nessun guasto");
            case OPERATING_STATUS -> joined(operatingStatus(v), "Nessuno");
            case OPERATING_MODE -> operatingMode(v);
            case STORAGE_CONTROL_MODE -> String.join(", ", storageModes(v));
            case TOU_FUNCTION_VERSION -> v == 0xAA55 ? "V2 (slot carica/scarica)" : "V1";
            case BATTERY_CURRENT_DIRECTION, BATTERY_FORCED_DIRECTION -> v == 0 ? "Carica" : v == 1 ? "Scarica" : null;
            case METER_PLACEMENT -> joined(bits(v, METER_PLACEMENT_BITS), "Nessuno");
            case METER1_TYPE_LOCATION -> meterTypeLocation(v);
            case EPM_STATUS -> joined(bits(v, EPM_STATUS_BITS), "Non attivo");
            case EPS_SWITCHING_TIME -> v == 0 ? "Parametri non validi" : null;
            case INVERTER_POWER_SETTING -> v == 190 ? "Acceso" : v == 222 ? "Spento" : null;
            case BACKUP_SUPPLY_SETTING -> v == 1 ? "Attiva" : v == 0 ? "Disattiva" : null;
            // stessa codifica del registro di sola lettura 33132
            case STORAGE_MODE_SETTING -> String.join(", ", storageModes(v));
            case BATTERY_MODEL, BATTERY_MODEL_SETTING -> batteryModel(v);
            case EXPORT_LIMIT_SWITCH_SETTING -> (v & (1 << 4)) != 0 ? "Limite attivo" : "Limite non attivo";
            case TOU2_SWITCH -> joined(bits(v, TOU2_PERIOD_BITS), "Nessuna fascia attiva");
            case ITALY_SINGLE_SELFTEST -> v < SINGLE_SELFTESTS.size()
                    ? (v == 0 ? "Nessuno" : "In corso: " + SINGLE_SELFTESTS.get(v)) : null;
            case ITALY_FULL_SELFTEST -> switch (v) {
                case 0 -> "Nessuno";
                case 1 -> "Singolo in corso";
                case 2 -> "Completo in corso";
                default -> null;
            };
            default -> solisCloud(register, v);
        };
    }

    /** Decodifiche delle impostazioni ricavate dai menu delle impostazioni remote di SolisCloud. */
    private static final Map<SolisRegister, Map<Integer, String>> SOLISCLOUD_CODES = new EnumMap<>(SolisRegister.class);

    /** Bit delle impostazioni SolisCloud: testo se il bit è 1, testo se il bit è 0 (null = non mostrato). */
    private record Flag(int bit, String set, String clear) {
    }

    private static final Map<SolisRegister, List<Flag>> SOLISCLOUD_FLAGS = new EnumMap<>(SolisRegister.class);

    static {
        SOLISCLOUD_CODES.put(SolisRegister.DRM_SETTING, Map.of(1, "Attiva", 2, "Disattiva", 11, "Generale", 12, "Germania", 13, "Australia"));
        SOLISCLOUD_FLAGS.put(SolisRegister.FUNCTION_FLAGS_SETTING, List.of(
                new Flag(0, "Carica solo da FV", null),
                new Flag(1, "Modalità bassa rumorosità", null),
                new Flag(2, "Risparmio batteria disattivo", "Risparmio batteria attivo")));
        SOLISCLOUD_FLAGS.put(SolisRegister.AUX_CONTROL_FLAGS_SETTING, List.of(
                new Flag(8, "Neutro di rete scollegato", "Neutro di rete collegato")));
        SOLISCLOUD_CODES.put(SolisRegister.BATTERY_EQUALIZATION_SETTING, Map.of(0, "Disattiva", 1, "Attiva"));
        SOLISCLOUD_CODES.put(SolisRegister.FAULT_RESET_COMMAND, Map.of(0, "Nessun comando"));
        SOLISCLOUD_CODES.put(SolisRegister.PARALLEL_ROLE_SETTING, Map.of(0, "Slave", 1, "Master"));
        SOLISCLOUD_CODES.put(SolisRegister.PARALLEL_PHASE_SETTING, Map.of(1, "Monofase", 2, "Fase A", 3, "Fase B", 4, "Fase C"));
        SOLISCLOUD_CODES.put(SolisRegister.PARALLEL_SYSTEM_SETTING, Map.of(0, "Singolo", 1, "Parallelo"));
        SOLISCLOUD_CODES.put(SolisRegister.DUAL_BATTERY_MODE_SETTING, Map.of(0, "Non impostato", 1, "Batterie indipendenti", 2, "Batterie in parallelo"));
        SOLISCLOUD_CODES.put(SolisRegister.BATTERY_STATE_SOURCE_SETTING, Map.of(0, "Tensione", 1, "SOC"));
        SOLISCLOUD_CODES.put(SolisRegister.OVERLOAD_CUTOFF_SETTING, Map.of(1, "Disattiva", 2, "Attiva"));
        SOLISCLOUD_CODES.put(SolisRegister.OVERLOAD_CUTOFF_PORT_SETTING, Map.of(1, "Solo carico intelligente", 2, "Carico intelligente e backup"));
        SOLISCLOUD_CODES.put(SolisRegister.ABSOLUTE_ZERO_EXPORT_SETTING, Map.of(0, "Disattiva", 1, "Attiva"));
        SOLISCLOUD_CODES.put(SolisRegister.GRID_PROTECTION_PRECISION_SETTING, Map.of(0, "Tensione 1 V, tempo 0,1 s, frequenza 0,1 Hz", 1, "Tensione 0,1 V, tempo 0,01 s, frequenza 0,01 Hz"));
        SOLISCLOUD_FLAGS.put(SolisRegister.GENERATOR_MODE_SETTING, List.of(
                new Flag(0, "Generatore automatico", "Generatore manuale"),
                new Flag(1, "Carica da generatore", null)));
        SOLISCLOUD_CODES.put(SolisRegister.GENERATOR_FORCE_START_SETTING, Map.of(0, "Disattiva", 1, "Attiva"));
        SOLISCLOUD_FLAGS.put(SolisRegister.SMART_PORT_SETTING, List.of(
                new Flag(0, "Generatore lato rete", null),
                new Flag(1, "Generatore presente", null),
                new Flag(2, "Generatore abilitato", null),
                new Flag(3, "Accoppiamento AC sulla porta backup", null),
                new Flag(4, "Accesso generatore", null)));
        SOLISCLOUD_FLAGS.put(SolisRegister.HYBRID_FUNCTION_FLAGS_SETTING, List.of(
                new Flag(0, "Doppio backup", null),
                new Flag(1, "Accoppiamento AC", null),
                new Flag(2, "Carico intelligente sempre acceso in rete", null),
                new Flag(3, "Immissione non consentita", null),
                new Flag(4, "Backup2Load automatico", null),
                new Flag(5, "Backup2Load manuale attivo", null),
                new Flag(6, "Carico intelligente spento off-grid", null),
                new Flag(7, "Peak shaving da rete", null)));
        SOLISCLOUD_CODES.put(SolisRegister.EXPORT_CONTROL_SETTING, Map.of(0, "Nessuno", 1, "In base a potenza immessa e SOC", 2, "Seconda RS485 (solo inverter Solis)"));
        SOLISCLOUD_CODES.put(SolisRegister.AC_COUPLED_GRID_SIDE_SETTING, Map.of(1, "Disattiva", 2, "Attiva"));
        SOLISCLOUD_FLAGS.put(SolisRegister.HEAT_PUMP_SETTING, List.of(
                new Flag(0, "Pompa di calore attiva", "Pompa di calore disattiva"),
                new Flag(1, "Modalità manuale rinforzata", null),
                new Flag(3, "Modalità intelligente", null)));
    }

    private static String solisCloud(SolisRegister register, int value) {
        Map<Integer, String> codes = SOLISCLOUD_CODES.get(register);
        if (codes != null) {
            return codes.get(value);
        }
        List<Flag> flags = SOLISCLOUD_FLAGS.get(register);
        if (flags == null) {
            return null;
        }
        List<String> texts = new ArrayList<>();
        for (Flag flag : flags) {
            String text = (value & (1 << flag.bit())) != 0 ? flag.set() : flag.clear();
            if (text != null) {
                texts.add(text);
            }
        }
        return joined(texts, "Nessuna opzione attiva");
    }

    private static String yesNo(int v) {
        return v == 1 ? "Sì" : v == 0 ? "No" : null;
    }

    private static String joined(List<String> items, String empty) {
        return items.isEmpty() ? empty : String.join(", ", items);
    }

    private static List<String> bits(int value, List<String> names) {
        List<String> active = new ArrayList<>();
        for (int bit = 0; bit < names.size(); bit++) {
            if ((value & (1 << bit)) != 0 && !names.get(bit).isEmpty()) {
                active.add(names.get(bit));
            }
        }
        return active;
    }
}
