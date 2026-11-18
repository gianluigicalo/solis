import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  AccuracyReport, Advice, AdviceLogEntry, ForecastSelection, InverterData, InverterInfo, MeasurementPoint, MeteoLocation, OpenMeteoResponse,
  PowerFlow, RegisterDefinition, RuleDefinition, RuleRequest, RuleType, SiteSettings, TableStatus, WeatherPoint,
} from './models';

type Params = Record<string, string | number | boolean>;

/**
 * Tutte le chiamate ai tre servizi. I percorsi sono relativi: in Docker li inoltra nginx, con `ng serve` il proxy
 * di sviluppo (proxy.conf.json).
 */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------ inverter
  inverterSummary() { return this.http.get<PowerFlow>('/api/inverter/summary'); }
  inverterInfo() { return this.http.get<InverterInfo>('/api/inverter/info'); }
  inverterData() { return this.http.get<InverterData>('/api/inverter/data'); }
  inverterRegisters() { return this.http.get<RegisterDefinition[]>('/api/inverter/registers'); }

  // ------------------------------------------------------------ open-meteo
  meteoLocation() { return this.http.get<MeteoLocation>('/api/meteo/location'); }
  forecast(params: Params = {}) { return this.http.get<OpenMeteoResponse>('/api/meteo/forecast', { params }); }
  solar(params: Params = {}) { return this.http.get<Record<string, OpenMeteoResponse>>('/api/meteo/solar', { params }); }
  airQuality(params: Params = {}) { return this.http.get<OpenMeteoResponse>('/api/meteo/air-quality', { params }); }

  // ------------------------------------------------------------ advisor: regole e consigli
  advice() { return this.http.get<Advice>('/api/advice'); }
  ruleTypes() { return this.http.get<RuleType[]>('/api/rules/types'); }
  rules() { return this.http.get<RuleDefinition[]>('/api/rules'); }
  createRule(rule: RuleRequest) { return this.http.post<RuleDefinition>('/api/rules', rule); }
  replaceRule(id: string, rule: RuleRequest) { return this.http.put<RuleDefinition>(`/api/rules/${id}`, rule); }
  patchRule(id: string, patch: Partial<RuleRequest>) { return this.http.patch<RuleDefinition>(`/api/rules/${id}`, patch); }
  deleteRule(id: string) { return this.http.delete<void>(`/api/rules/${id}`); }
  settings() { return this.http.get<SiteSettings>('/api/settings'); }
  saveSettings(settings: SiteSettings) { return this.http.put<SiteSettings>('/api/settings', settings); }

  // ------------------------------------------------------------ advisor: storico
  historyStatus() { return this.http.get<Record<string, TableStatus>>('/api/history/status'); }

  inverterHistory(register: string, from: Date, to: Date, limit = 50000) {
    return this.http.get<MeasurementPoint[]>(`/api/history/inverter/${register}`, { params: range(from, to, limit) });
  }

  weatherHistory(source: string, series: string, variable: string, from: Date, to: Date, limit = 50000) {
    const params = range(from, to, limit).set('source', source).set('series', series).set('variable', variable);
    return this.http.get<WeatherPoint[]>('/api/history/weather', { params });
  }

  adviceHistory(limit = 30) {
    const to = new Date();
    const from = new Date(to.getTime() - 7 * 86400_000);
    return this.http.get<AdviceLogEntry[]>('/api/history/advice', { params: range(from, to, limit) });
  }

  backfillWeather(start: string, end: string) {
    return this.http.post<{ start: string; end: string; rows: number }>('/api/history/weather/backfill', null,
      { params: { start, end } });
  }

  forecastAccuracy(from: Date, to: Date, run: ForecastSelection) {
    const params = new HttpParams().set('from', from.toISOString()).set('to', to.toISOString()).set('run', run);
    return this.http.get<AccuracyReport>('/api/history/forecast-accuracy', { params });
  }

  collectNow() { return this.http.post<Record<string, number | string>>('/api/history/collect', null); }
}

function range(from: Date, to: Date, limit: number): HttpParams {
  return new HttpParams().set('from', from.toISOString()).set('to', to.toISOString()).set('limit', limit);
}
