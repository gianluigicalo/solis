import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { Api } from '../../core/api.service';
import { errorMessage, fmtFullDateTime, fmtNumber } from '../../core/format';
import { TableStatus } from '../../core/models';
import { ChartSeries, LineChart } from '../../shared/line-chart';
import { RangePicker, TimeRange } from '../../shared/range-picker';

/** Grandezze salvate dall'advisor (advisor.history.*-variables). */
const VARIABLES: Record<string, { id: string; label: string }[]> = {
  forecast: [
    { id: 'temperature_2m', label: 'Temperatura' }, { id: 'apparent_temperature', label: 'Temperatura percepita' },
    { id: 'relative_humidity_2m', label: 'Umidità relativa' }, { id: 'precipitation', label: 'Precipitazioni' },
    { id: 'precipitation_probability', label: 'Probabilità di pioggia' }, { id: 'weather_code', label: 'Codice meteo' },
    { id: 'cloud_cover', label: 'Nuvolosità' }, { id: 'cloud_cover_low', label: 'Nuvolosità bassa' },
    { id: 'pressure_msl', label: 'Pressione' }, { id: 'wind_speed_10m', label: 'Vento' },
    { id: 'wind_direction_10m', label: 'Direzione vento' }, { id: 'wind_gusts_10m', label: 'Raffiche' },
    { id: 'uv_index', label: 'Indice UV' }, { id: 'shortwave_radiation', label: 'Radiazione globale' },
    { id: 'direct_radiation', label: 'Radiazione diretta' }, { id: 'diffuse_radiation', label: 'Radiazione diffusa' },
    { id: 'direct_normal_irradiance', label: 'Irraggiamento normale diretto (DNI)' }, { id: 'is_day', label: 'Giorno/notte' },
  ],
  solar: [{ id: 'global_tilted_irradiance', label: 'Irraggiamento sul piano dei pannelli' }],
  archive: [
    { id: 'temperature_2m', label: 'Temperatura' }, { id: 'apparent_temperature', label: 'Temperatura percepita' },
    { id: 'relative_humidity_2m', label: 'Umidità relativa' }, { id: 'precipitation', label: 'Precipitazioni' },
    { id: 'weather_code', label: 'Codice meteo' }, { id: 'cloud_cover', label: 'Nuvolosità' },
    { id: 'cloud_cover_low', label: 'Nuvolosità bassa' }, { id: 'pressure_msl', label: 'Pressione' },
    { id: 'wind_speed_10m', label: 'Vento' }, { id: 'wind_direction_10m', label: 'Direzione vento' },
    { id: 'wind_gusts_10m', label: 'Raffiche' }, { id: 'shortwave_radiation', label: 'Radiazione globale' },
    { id: 'direct_radiation', label: 'Radiazione diretta' }, { id: 'diffuse_radiation', label: 'Radiazione diffusa' },
    { id: 'direct_normal_irradiance', label: 'Irraggiamento normale diretto (DNI)' }, { id: 'is_day', label: 'Giorno/notte' },
  ],
};

const SOURCES = [
  { id: 'forecast', label: 'Previsioni (meteo)' },
  { id: 'solar', label: 'Irraggiamento per falda' },
  { id: 'archive', label: 'Archivio ERA5 importato' },
];

const TABLE_LABELS: Record<string, string> = {
  inverter_measurement: 'Letture inverter', weather_hourly: 'Meteo orario',
  weather_forecast_run: 'Archivio previsioni', advice_log: 'Consigli',
};

@Component({
  selector: 'app-meteo-history-page',
  imports: [FormsModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, MatProgressBarModule,
    LineChart, RangePicker],
  template: `
    <div class="page">
      <div class="page-header"><h1>Storico meteo</h1></div>
      @if (error(); as e) { <div class="error-box"><span class="material-symbols-outlined">error</span>{{ e }}</div> }

      <div class="card controls">
        <div class="row">
          <mat-form-field subscriptSizing="dynamic">
            <mat-label>Origine</mat-label>
            <mat-select [ngModel]="source()" (ngModelChange)="setSource($event)">
              @for (s of sources; track s.id) { <mat-option [value]="s.id">{{ s.label }}</mat-option> }
            </mat-select>
          </mat-form-field>
          @if (source() === 'solar') {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Falde</mat-label>
              <mat-select multiple [ngModel]="planesSelected()" (ngModelChange)="planesSelected.set($event); load()">
                @for (p of planes(); track p) { <mat-option [value]="p">{{ p }}</mat-option> }
              </mat-select>
            </mat-form-field>
          } @else {
            <mat-form-field subscriptSizing="dynamic" class="vars">
              <mat-label>Grandezze (max 3)</mat-label>
              <mat-select multiple [ngModel]="variables()" (ngModelChange)="variables.set($event.slice(0, 3)); load()">
                @for (v of availableVariables(); track v.id) {
                  <mat-option [value]="v.id" [disabled]="variables().length >= 3 && !variables().includes(v.id)">{{ v.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
        </div>
        <div class="row">
          <app-range-picker initial="7d" (rangeChange)="range.set($event); load()" />
          <span class="spacer"></span>
          <button mat-flat-button (click)="refresh()"><span class="material-symbols-outlined">refresh</span> Aggiorna</button>
        </div>
      </div>

      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
      <div class="card"><app-line-chart [series]="series()" [range]="shownRange()" [height]="420" /></div>

      <div class="grid-2 bottom">
        <div class="card">
          <h2><span class="material-symbols-outlined">database</span>Stato del database</h2>
          <table class="data">
            <thead><tr><th>Tabella</th><th>Ultimo dato</th><th class="num">Righe ultime 24 h</th></tr></thead>
            <tbody>
              @for (t of status(); track t.name) {
                <tr><td>{{ t.label }}</td><td>{{ t.last ? fmtFullDateTime(t.last) : '—' }}</td><td class="num">{{ fmtNumber(t.rows, 0) }}</td></tr>
              }
            </tbody>
          </table>
          <div class="row actions">
            <button mat-stroked-button (click)="collect()" [disabled]="busy()">Raccogli ora</button>
            <button mat-button (click)="loadStatus()">Aggiorna stato</button>
          </div>
        </div>

        <div class="card">
          <h2><span class="material-symbols-outlined">cloud_download</span>Importa dall'archivio</h2>
          <p class="muted">Scarica lo storico orario dall'archivio Open-Meteo (rianalisi ERA5, dal 1940). Gli ultimi
            ~5 giorni possono mancare. I dati finiscono in <em>Archivio ERA5 importato</em>.</p>
          <div class="row">
            <mat-form-field subscriptSizing="dynamic"><mat-label>Dal</mat-label><input matInput type="date" [(ngModel)]="backfillStart"></mat-form-field>
            <mat-form-field subscriptSizing="dynamic"><mat-label>Al</mat-label><input matInput type="date" [(ngModel)]="backfillEnd"></mat-form-field>
            <button mat-flat-button (click)="backfill()" [disabled]="busy() || !backfillStart || !backfillEnd">Importa</button>
          </div>
          @if (busy()) { <mat-progress-bar mode="indeterminate" class="busy" /> }
        </div>
      </div>
    </div>`,
  styles: `
    .controls { display: flex; flex-direction: column; gap: 14px; margin-bottom: 16px; }
    .vars { min-width: min(360px, 100%); }
    .bottom { margin-top: 16px; }
    .actions { margin-top: 12px; }
    .busy { margin-top: 12px; }`,
})
export class MeteoHistoryPage implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  private readonly picker = viewChild.required(RangePicker);

  protected readonly sources = SOURCES;
  protected readonly source = signal('forecast');
  protected readonly variables = signal<string[]>(['temperature_2m', 'cloud_cover']);
  protected readonly planes = signal<string[]>([]);
  protected readonly planesSelected = signal<string[]>([]);
  protected readonly range = signal<TimeRange | null>(null);
  protected readonly series = signal<ChartSeries[]>([]);
  /** periodo dell'ultimo caricamento, per gli estremi dell'asse */
  protected readonly shownRange = signal<TimeRange | null>(null);
  protected readonly status = signal<{ name: string; label: string; last: string | null; rows: number }[]>([]);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly availableVariables = computed(() => VARIABLES[this.source()] ?? []);
  protected readonly fmtFullDateTime = fmtFullDateTime;
  protected readonly fmtNumber = fmtNumber;

  protected backfillStart = `${new Date().getFullYear()}-01-01`;
  protected backfillEnd = new Date(Date.now() - 6 * 86400_000).toISOString().slice(0, 10);

  ngOnInit(): void {
    this.api.meteoLocation().subscribe({
      next: (l) => {
        this.planes.set(Object.keys(l.planes));
        this.planesSelected.set(Object.keys(l.planes));
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.loadStatus();
  }

  protected setSource(source: string): void {
    this.source.set(source);
    const allowed = new Set(this.availableVariables().map((v) => v.id));
    const kept = this.variables().filter((v) => allowed.has(v));
    this.variables.set(kept.length ? kept : [this.availableVariables()[0].id]);
    this.load();
  }

  protected refresh(): void {
    this.range.set(this.picker().current());
    this.load();
    this.loadStatus();
  }

  protected load(): void {
    const range = this.range();
    if (!range) return;
    const source = this.source();
    const requests = source === 'solar'
      ? this.planesSelected().map((p) => ({ label: p, series: p, variable: 'global_tilted_irradiance' }))
      : this.variables().map((v) => ({
        label: this.availableVariables().find((x) => x.id === v)?.label ?? v, series: 'meteo', variable: v,
      }));
    if (!requests.length) {
      this.series.set([]);
      return;
    }
    this.loading.set(true);
    forkJoin(requests.map((r) => this.api.weatherHistory(source, r.series, r.variable, range.from, range.to))).subscribe({
      next: (results) => {
        this.shownRange.set(range);
        this.series.set(results.map((points, i) => ({
          label: requests[i].label,
          unit: points[0]?.unit ?? null,
          points: points.map((p) => ({ x: Date.parse(p.time), y: p.value })),
          kind: requests[i].variable === 'precipitation' ? 'bar' : 'line',
          stepped: requests[i].variable === 'weather_code',
        })));
        this.error.set(null);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected loadStatus(): void {
    this.api.historyStatus().subscribe({
      next: (s) => this.status.set(Object.entries(s).map(([name, t]: [string, TableStatus]) =>
        ({ name, label: TABLE_LABELS[name] ?? name, last: t.last, rows: t.rowsLast24h }))),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected collect(): void {
    this.busy.set(true);
    this.api.collectNow().subscribe({
      next: (r) => {
        this.busy.set(false);
        const errors = Object.entries(r).filter(([k]) => k.endsWith('Error')).map(([, v]) => v);
        this.snack.open(errors.length ? `Raccolta parziale: ${errors.join('; ')}`
          : `Salvate ${r['inverterRows']} letture inverter e ${r['weatherRows']} valori meteo`, 'OK', { duration: 5000 });
        this.loadStatus();
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  protected backfill(): void {
    this.busy.set(true);
    this.api.backfillWeather(this.backfillStart, this.backfillEnd).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.snack.open(`Importati ${fmtNumber(r.rows, 0)} valori dal ${r.start} al ${r.end}`, 'OK', { duration: 6000 });
        this.loadStatus();
        if (this.source() === 'archive') this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
