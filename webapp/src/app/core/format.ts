import { HttpErrorResponse } from '@angular/common/http';
import { Action, ProblemDetail } from './models';

const dateTime = new Intl.DateTimeFormat('it-IT', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
const fullDateTime = new Intl.DateTimeFormat('it-IT', { dateStyle: 'short', timeStyle: 'medium' });
const time = new Intl.DateTimeFormat('it-IT', { hour: '2-digit', minute: '2-digit' });
const clock = new Intl.DateTimeFormat('it-IT', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
const day = new Intl.DateTimeFormat('it-IT', { weekday: 'short', day: '2-digit', month: '2-digit' });

export const fmtDateTime = (v: number | string | Date) => dateTime.format(new Date(v));
export const fmtFullDateTime = (v: number | string | Date) => fullDateTime.format(new Date(v));
export const fmtTime = (v: number | string | Date) => time.format(new Date(v));
/** solo l'orario con i secondi: "06:55:28" */
export const fmtClock = (v: number | string | Date) => clock.format(new Date(v));
export const fmtDay = (v: number | string | Date) => day.format(new Date(v));

export function fmtNumber(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return value.toLocaleString('it-IT', { maximumFractionDigits: digits });
}

/** Numeri interi con il separatore delle migliaia anche a 4 cifre (4.200, non 4200). */
export const fmtInt = (v: number) => Math.round(v).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');

/** Energia in Wh, intera: "6.400". */
export function fmtWhValue(wh: number | null | undefined): string {
  return wh === null || wh === undefined || Number.isNaN(wh) ? '—' : fmtInt(wh);
}

/** Energia in Wh con l'unità: "6.400 Wh". */
export function fmtWh(wh: number | null | undefined): string {
  const v = fmtWhValue(wh);
  return v === '—' ? v : `${v}\u00a0Wh`;
}

/** Potenza sempre in W, intera: { value: "4.200", unit: "W" }. */
export function fmtPower(watts: number | null | undefined): { value: string; unit: string } {
  if (watts === null || watts === undefined || Number.isNaN(watts)) return { value: '—', unit: '' };
  return { value: fmtInt(watts), unit: 'W' };
}

/**
 * Alcuni registri dell'inverter (contatori di energia) sono in kWh: per mostrarli come il resto dell'app li porta
 * in Wh. I registri in W restano come sono.
 */
export function toWattUnits(value: number, unit: string | null | undefined): { value: number; unit: string | null } {
  if (unit === 'kWh') return { value: Math.round(value * 1000 * 1000) / 1000, unit: 'Wh' };
  if (unit === 'kW') return { value: Math.round(value * 1000 * 1000) / 1000, unit: 'W' };
  return { value, unit: unit ?? null };
}

/**
 * Consumo di oggi calcolato per bilancio (Wh), come fa l'app Solis:
 * FV + prelievo + scarica batteria − immissione − carica batteria.
 * Rispetto al consumo misurato dall'inverter include anche le perdite dell'impianto (conversione, batteria).
 */
export function balanceConsumption(today: {
  pvProduction: number; gridImport: number; gridExport: number; batteryCharge: number; batteryDischarge: number;
}): number {
  const v = today.pvProduction + today.gridImport + today.batteryDischarge - today.gridExport - today.batteryCharge;
  return Math.round(v);
}

/** Messaggio leggibile da un errore HTTP (ProblemDetail dei servizi Spring, oppure stato HTTP). */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ProblemDetail | string | null;
    if (body && typeof body === 'object' && (body.detail || body.title)) return body.detail ?? body.title!;
    if (error.status === 0) return 'Servizio non raggiungibile';
    return `${error.status} ${error.statusText || ''} — ${error.url ?? ''}`.trim();
  }
  return String(error);
}

export const ACTION_LABELS: Record<Action, { label: string; icon: string; css: string }> = {
  CHARGE_FROM_GRID: { label: 'Carica da rete', icon: 'battery_charging_full', css: 'info' },
  SELL_TO_GRID: { label: 'Vendi in rete', icon: 'sell', css: 'warn' },
  HOLD_BATTERY: { label: 'Conserva batteria', icon: 'battery_saver', css: 'bad' },
  SELF_CONSUMPTION: { label: 'Autoconsumo', icon: 'home', css: 'ok' },
};

/** Date in formato yyyy-MM-ddTHH:mm per gli input datetime-local. */
export function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
