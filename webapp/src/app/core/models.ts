// ---------------------------------------------------------------- inverter

/** Previsione archiviata confrontata con il reale nella pagina Affidabilità */
export type ForecastSelection = 'LATEST' | 'PRE_DAWN' | 'EVENING';

export type RegisterSource = 'OFFICIAL' | 'UNOFFICIAL' | 'SOLISCLOUD' | 'RESERVED';
/** READ_ONLY: input register (sola lettura); READ_WRITE: holding register (impostazione modificabile) */
export type RegisterAccess = 'READ_ONLY' | 'READ_WRITE';

export interface PowerFlow {
  readAt: string;
  status: string;
  pvPower: number;
  /** positiva in carica, negativa in scarica */
  batteryPower: number;
  batteryState: 'CARICA' | 'SCARICA' | 'INATTIVA';
  batterySoc: number;
  /** positiva in immissione, negativa in prelievo */
  gridPower: number;
  gridState: 'IMMISSIONE' | 'PRELIEVO' | 'NEUTRO';
  houseLoadPower: number;
  backupLoadPower: number;
  inverterTemperature: number;
  /** energia di oggi in Wh */
  today: {
    pvProduction: number;
    houseConsumption: number;
    gridImport: number;
    gridExport: number;
    batteryCharge: number;
    batteryDischarge: number;
  };
  /** tensioni e correnti istantanee (null se non disponibili) */
  electrical?: {
    pv1Voltage: number | null; pv1Current: number | null;
    pv2Voltage: number | null; pv2Current: number | null;
    dcBusVoltage: number | null; acCurrent: number | null;
    gridVoltage: number | null; gridCurrent: number | null;
  };
}

export interface InverterInfo {
  readAt: string;
  productModel: string;
  modelDefinition: string;
  serialNumber: string;
  dspSoftwareVersion: string;
  hmiSoftwareVersion: string;
  protocolVersion: string;
  acOutputType: string;
  dcInputs: number;
  gridStandard: string;
  batteryModel: string;
  meter: string;
  exportLimit: number;
  inverterClock: string;
  status: string;
  statusDescription: string;
  operatingMode: string;
  storageModes: string[];
  operatingStatus: string[];
  activeFaults: string[];
  faultActive: boolean;
}

export interface Measurement {
  value: number | string | null;
  unit: string | null;
  /** significato del valore secondo le specifiche (codici, bitmask, sì/no); assente per le misure */
  decoded?: string;
  /** valore grezzo del registro, prima della scala */
  raw: number | string | null;
  description: string;
  register: number;
  access: RegisterAccess;
  source: RegisterSource;
  publicName?: string;
  reference?: string;
}

export interface InverterData {
  readAt: string;
  categories: Record<string, Record<string, Measurement>>;
}

export interface RegisterDefinition {
  name: string;
  address: number;
  words: number;
  type: string;
  decimals: number;
  unit: string | null;
  category: string;
  description: string;
  source: RegisterSource;
  publicName?: string;
  reference?: string;
}

// ---------------------------------------------------------------- open-meteo

export interface PanelOrientation { tilt: number; azimuth: number; }

export interface MeteoLocation {
  latitude: number;
  longitude: number;
  timezone: string;
  planes: Record<string, PanelOrientation>;
}

/** Risposta Open-Meteo (forma generica: blocchi current / hourly / daily con le rispettive unità). */
export interface OpenMeteoResponse {
  latitude: number;
  longitude: number;
  elevation: number;
  timezone: string;
  current?: Record<string, number | string>;
  current_units?: Record<string, string>;
  hourly?: Record<string, (number | string | null)[]>;
  hourly_units?: Record<string, string>;
  daily?: Record<string, (number | string | null)[]>;
  daily_units?: Record<string, string>;
}

// ---------------------------------------------------------------- advisor

export type Action = 'CHARGE_FROM_GRID' | 'SELL_TO_GRID' | 'HOLD_BATTERY' | 'SELF_CONSUMPTION';
export type ParameterType = 'NUMBER' | 'INTEGER' | 'TIME' | 'BOOLEAN';
export type ParameterValue = number | string | boolean;

export interface ParameterDefinition {
  name: string;
  type: ParameterType;
  defaultValue: ParameterValue;
  min?: number;
  max?: number;
  unit?: string;
  description: string;
}

export interface RuleType {
  id: string;
  description: string;
  action: Action;
  parameters: ParameterDefinition[];
}

export interface RuleDefinition {
  id: string;
  name: string;
  type: string;
  enabled: boolean;
  priority: number;
  parameters: Record<string, ParameterValue>;
}

export interface RuleRequest {
  id?: string;
  name: string;
  type: string;
  enabled: boolean;
  priority: number;
  parameters: Record<string, ParameterValue>;
}

export interface RuleEvaluation {
  ruleId: string;
  ruleName: string;
  type: string;
  priority: number;
  action: Action | null;
  matched: boolean;
  decisive: boolean;
  reason: string;
}

export interface StringForecast {
  peakPowerW: number;
  todayWh: number;
  todayRemainingWh: number;
  tomorrowWh: number;
}

export interface SolarForecast {
  todayWh: number;
  todayRemainingWh: number;
  tomorrowWh: number;
  todayRemainingAboveAcWh: number;
  tomorrowAboveAcWh: number;
  byString: Record<string, StringForecast> | null;
  hourly: { time: string; wh: number }[] | null;
}

export interface InverterLimits { maxPvInputW: number; ratedOutputW: number; maxBatteryPowerW: number; }

export interface SiteSettings {
  batteryCapacityWh: number;
  performanceRatio: number;
  panelPeakPowerW: number;
  inverter: InverterLimits;
  strings: Record<string, Record<string, number>>;
}

export interface EnergyContext {
  time: string;
  inverter: { batterySoc: number | null; pvPower: number | null; houseLoadPower: number | null; gridPower: number | null; status: string | null } | null;
  forecast: SolarForecast | null;
  settings: SiteSettings;
  warnings: string[];
}

export interface Advice {
  evaluatedAt: string;
  action: Action;
  actionDescription: string;
  decidedBy: string | null;
  reason: string;
  evaluations: RuleEvaluation[];
  context: EnergyContext;
}

// ---------------------------------------------------------------- storico

export interface MeasurementPoint { time: string; value: number | null; textValue: string | null; }
export interface WeatherPoint { time: string; value: number; unit: string | null; fetchedAt: string; }
export interface AdviceLogEntry { time: string; action: Action; decidedBy: string | null; reason: string; }
export interface TableStatus { last: string | null; rowsLast24h: number; }

export interface ProblemDetail { title?: string; detail?: string; status?: number; }

// ------------------------------------------------------------ advisor: affidabilità previsioni

export type AccuracyVerdict = 'AFFIDABILE' | 'DISCRETA' | 'INAFFIDABILE' | 'DATI_INSUFFICIENTI';
export type AccuracyHourStatus = 'OK' | 'NIGHT' | 'INCOMPLETE' | 'LIMITED' | 'NO_FORECAST';

export interface AccuracyReport {
  from: string;
  to: string;
  forecast: ForecastSelection;
  performanceRatio: number;
  summary: {
    hoursCompared: number;
    hoursLimited: number;
    hoursIncomplete: number;
    hoursWithoutForecast: number;
    actualWh: number;
    forecastWh: number;
    modelWh: number | null;
    forecastErrorPct: number | null;
    forecastHourlyErrorPct: number | null;
    modelErrorPct: number | null;
    modelHourlyErrorPct: number | null;
    suggestedPerformanceRatio: number | null;
    verdict: AccuracyVerdict;
    verdictText: string;
  };
  days: { day: string; hours: number; actualWh: number; forecastWh: number; modelWh: number | null; forecastErrorPct: number | null }[];
  strings: Record<string, { peakW: number; actualWh: number | null; forecastWh: number; modelWh: number | null; forecastErrorPct: number | null }>;
  hours: {
    /** fine dell'ora */
    time: string;
    actualWh: number | null;
    forecastWh: number | null;
    modelWh: number | null;
    forecastIssuedAt: string | null;
    coverage: number;
    batterySoc: number | null;
    status: AccuracyHourStatus;
    actualByString: Record<string, number>;
    forecastByString: Record<string, number>;
  }[];
  notes: string[];
}
