import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Api } from '../../core/api.service';
import { errorMessage, fmtDateTime, fmtDay, fmtInt, fmtNumber, fmtTime, fmtWh, fmtWhValue } from '../../core/format';
import { AccuracyHourStatus, AccuracyReport, AccuracyVerdict, ForecastSelection } from '../../core/models';
import { ChartSeries, LineChart } from '../../shared/line-chart';

const PERIODS = [
  { days: 1, label: '24 ore' },
  { days: 3, label: '3 giorni' },
  { days: 7, label: '7 giorni' },
  { days: 30, label: '30 giorni' },
];

/** Previsioni archiviate dall'advisor: la sera (21:00), prima dell'alba e tre volte durante la produzione */
const RUNS: { run: ForecastSelection; label: string; hint: string }[] = [
  { run: 'LATEST', label: 'Ultima disponibile', hint: 'la previsione più recente prima di ogni ora (corretta durante il giorno)' },
  { run: 'PRE_DAWN', label: "Prima dell'alba", hint: "scaricata prima dell'alba: come per le decisioni della mattina" },
  { run: 'EVENING', label: 'Sera prima', hint: 'scaricata la sera prima: come la regola di carica notturna' },
];

const VERDICTS: Record<AccuracyVerdict, { label: string; icon: string; cls: string }> = {
  AFFIDABILE: { label: 'Affidabile', icon: 'verified', cls: 'ok' },
  DISCRETA: { label: 'Discreta', icon: 'rule', cls: 'warn' },
  INAFFIDABILE: { label: 'Non affidabile', icon: 'gpp_bad', cls: 'bad' },
  DATI_INSUFFICIENTI: { label: 'Dati insufficienti', icon: 'hourglass_empty', cls: '' },
};

const STATUS: Record<AccuracyHourStatus, { label: string; cls: string }> = {
  OK: { label: 'Confrontata', cls: 'ok' },
  NIGHT: { label: 'Notte', cls: '' },
  INCOMPLETE: { label: 'Letture incomplete', cls: 'bad' },
  LIMITED: { label: 'Batteria carica', cls: 'warn' },
  NO_FORECAST: { label: 'Senza previsione', cls: 'bad' },
};

@Component({
  selector: 'app-forecast-accuracy-page',
  imports: [FormsModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatSelectModule,
    MatProgressBarModule, MatSlideToggleModule, LineChart],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Affidabilità previsioni</h1>
        @if (report(); as r) {
          <span class="meta">{{ fmtDateTime(r.from) }} → {{ fmtDateTime(r.to) }}</span>
        }
      </div>
      <p class="intro">
        Confronta la produzione fotovoltaica reale con quella stimata da Open-Meteo, calcolata con la stessa formula
        usata dalle regole (irraggiamento per falda × pannelli × performance ratio). Serve a capire quanto puoi
        fidarti delle previsioni prima di basarci una regola.
      </p>

      <div class="controls card">
        <mat-button-toggle-group [value]="days()" (change)="days.set($event.value); load()" aria-label="Periodo">
          @for (p of periods; track p.days) {
            <mat-button-toggle [value]="p.days">{{ p.label }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
        <mat-form-field subscriptSizing="dynamic" class="lead">
          <mat-label>Previsione usata</mat-label>
          <mat-select [value]="run()" (selectionChange)="run.set($event.value); load()">
            @for (r of runs; track r.run) {
              <mat-option [value]="r.run">{{ r.label }}</mat-option>
            }
          </mat-select>
          <mat-hint>{{ runHint() }}</mat-hint>
        </mat-form-field>
        <button mat-flat-button (click)="load()"><span class="material-symbols-outlined">refresh</span> Aggiorna</button>
      </div>

      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
      @if (error(); as e) { <div class="error-box"><span class="material-symbols-outlined">error</span>{{ e }}</div> }

      @if (report(); as r) {
        @let s = r.summary;
        @let v = verdicts[s.verdict];
        <div class="card verdict" [class]="v.cls">
          <span class="material-symbols-outlined big">{{ v.icon }}</span>
          <div>
            <div class="verdict-label">{{ v.label }}</div>
            <div class="verdict-text">{{ s.verdictText }}</div>
          </div>
        </div>

        <div class="grid">
          <div class="stat">
            <div class="label"><span class="material-symbols-outlined">solar_power</span>Produzione reale</div>
            <div class="value">{{ fmtWhValue(s.actualWh) }}<small>Wh</small></div>
            <div class="sub">{{ s.hoursCompared }} ore di sole confrontate</div>
          </div>
          <div class="stat">
            <div class="label"><span class="material-symbols-outlined">partly_cloudy_day</span>Stima da previsione</div>
            <div class="value">{{ fmtWhValue(s.forecastWh) }}<small>Wh</small></div>
            <div class="sub" [class]="errClass(s.forecastErrorPct)">{{ pct(s.forecastErrorPct) }} sul totale</div>
          </div>
          <div class="stat">
            <div class="label"><span class="material-symbols-outlined">schedule</span>Errore orario medio</div>
            <div class="value" [class]="hourlyClass(s.forecastHourlyErrorPct)">
              {{ s.forecastHourlyErrorPct === null ? '—' : fmtNumber(s.forecastHourlyErrorPct, 0) }}<small>%</small>
            </div>
            <div class="sub">somma |stima − reale| / reale</div>
          </div>
          <div class="stat">
            <div class="label"><span class="material-symbols-outlined">history</span>Stima a posteriori</div>
            <div class="value">{{ fmtWhValue(s.modelWh) }}<small>Wh</small></div>
            <div class="sub" [class]="errClass(s.modelErrorPct)">{{ pct(s.modelErrorPct) }} · misura il modello dell'impianto</div>
          </div>
          <div class="stat">
            <div class="label"><span class="material-symbols-outlined">tune</span>Performance ratio</div>
            <div class="value">{{ fmtNumber(r.performanceRatio, 2) }}</div>
            <div class="sub">
              @if (s.suggestedPerformanceRatio !== null) { suggerito dai dati: {{ fmtNumber(s.suggestedPerformanceRatio, 2) }} }
              @else { — }
            </div>
          </div>
          <div class="stat">
            <div class="label"><span class="material-symbols-outlined">block</span>Ore escluse</div>
            <div class="value">{{ s.hoursIncomplete + s.hoursLimited + s.hoursWithoutForecast }}</div>
            <div class="sub">{{ s.hoursIncomplete }} incomplete · {{ s.hoursLimited }} batteria carica · {{ s.hoursWithoutForecast }} senza previsione</div>
          </div>
        </div>

        @if (r.notes.length) {
          <div class="card notes">
            <h2><span class="material-symbols-outlined">lightbulb</span>Come leggerlo</h2>
            <ul>@for (n of r.notes; track $index) { <li>{{ n }}</li> }</ul>
          </div>
        }

        <h2 class="section-title">Produzione oraria</h2>
        <div class="card">
          <app-line-chart [series]="chartSeries()" [height]="320" [range]="{ from: toDate(r.from), to: toDate(r.to) }" />
          <div class="chart-note">Barre: produzione reale di ogni ora. Linee: stima dalla previsione e a posteriori.
            Le ore escluse dal confronto non hanno la barra.</div>
        </div>

        <div class="grid-2 tables">
          <div class="card">
            <h2><span class="material-symbols-outlined">calendar_month</span>Per giorno</h2>
            @if (r.days.length) {
              <div class="table-scroll">
                <table class="data">
                  <thead><tr><th>Giorno</th><th class="num">Ore</th><th class="num">Reale</th><th class="num">Previsione</th><th class="num">Errore</th><th class="num">A posteriori</th></tr></thead>
                  <tbody>
                    @for (d of r.days; track d.day) {
                      <tr>
                        <td>{{ fmtDay(d.day) }}</td>
                        <td class="num">{{ d.hours }}</td>
                        <td class="num">{{ fmtWh(d.actualWh) }}</td>
                        <td class="num">{{ fmtWh(d.forecastWh) }}</td>
                        <td class="num" [class]="errClass(d.forecastErrorPct)">{{ pct(d.forecastErrorPct) }}</td>
                        <td class="num">{{ fmtWh(d.modelWh) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else { <p class="muted">Nessuna ora di sole confrontabile nel periodo.</p> }
          </div>
          <div class="card">
            <h2><span class="material-symbols-outlined">solar_power</span>Per stringa</h2>
            <table class="data">
              <thead><tr><th>Stringa</th><th class="num">Picco</th><th class="num">Reale</th><th class="num">Previsione</th><th class="num">Errore</th><th class="num">A posteriori</th></tr></thead>
              <tbody>
                @for (e of strings(); track e.name) {
                  <tr>
                    <td>{{ e.name }}</td>
                    <td class="num">{{ fmtInt(e.peakW) }} W</td>
                    <td class="num">{{ fmtWh(e.actualWh) }}</td>
                    <td class="num">{{ fmtWh(e.forecastWh) }}</td>
                    <td class="num" [class]="errClass(e.forecastErrorPct)">{{ pct(e.forecastErrorPct) }}</td>
                    <td class="num">{{ fmtWh(e.modelWh) }}</td>
                  </tr>
                }
              </tbody>
            </table>
            <p class="muted small">Reale per stringa = tensione × corrente letta dall'inverter (lato DC).</p>
          </div>
        </div>

        <div class="hours-header">
          <h2 class="section-title">Dettaglio orario</h2>
          <mat-slide-toggle [checked]="showNight()" (change)="showNight.set($event.checked)">Mostra le ore di notte</mat-slide-toggle>
        </div>
        <div class="card">
          <div class="table-scroll">
            <table class="data">
              <thead><tr><th>Ora</th><th class="num">Reale</th><th class="num">Previsione</th><th class="num">Diff.</th><th class="num">A posteriori</th><th>Previsione del</th><th class="num">SOC</th><th>Stato</th></tr></thead>
              <tbody>
                @for (h of visibleHours(); track h.time) {
                  <tr>
                    <td>{{ hourLabel(h.time) }}</td>
                    <td class="num">{{ wh(h.actualWh) }}</td>
                    <td class="num">{{ wh(h.forecastWh) }}</td>
                    <td class="num">{{ diff(h.forecastWh, h.actualWh) }}</td>
                    <td class="num">{{ wh(h.modelWh) }}</td>
                    <td>{{ h.forecastIssuedAt ? fmtDateTime(h.forecastIssuedAt) : '—' }}</td>
                    <td class="num">{{ h.batterySoc === null ? '—' : fmtNumber(h.batterySoc, 0) + '%' }}</td>
                    <td><span class="badge" [class]="status[h.status].cls">{{ status[h.status].label }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </div>`,
  styles: `
    .intro { color: var(--muted); margin: -4px 0 16px; max-width: 900px; line-height: 1.5; }
    .controls { display: flex; flex-wrap: wrap; gap: 16px; align-items: center; margin-bottom: 16px; }
    .controls .lead { width: min(260px, 100%); }
    .verdict { display: flex; gap: 16px; align-items: center; margin-bottom: 16px; }
    .verdict .big { font-size: 44px; }
    .verdict-label { font: var(--mat-sys-title-large); }
    .verdict-text { color: var(--mat-sys-on-surface); margin-top: 4px; line-height: 1.5; }
    .verdict.ok { border-color: var(--ok); } .verdict.ok .big, .verdict.ok .verdict-label { color: var(--ok); }
    .verdict.warn { border-color: var(--warn); } .verdict.warn .big, .verdict.warn .verdict-label { color: var(--warn); }
    .verdict.bad { border-color: var(--bad); } .verdict.bad .big, .verdict.bad .verdict-label { color: var(--bad); }
    .verdict .big, .verdict-label { color: var(--muted); }
    .stat .sub.ok, .num.ok, .value.ok { color: var(--ok); }
    .stat .sub.warn, .num.warn, .value.warn { color: var(--warn); }
    .stat .sub.bad, .num.bad, .value.bad { color: var(--bad); }
    .notes { margin-top: 16px; }
    .notes ul { margin: 0; padding-left: 20px; line-height: 1.6; }
    .chart-note, .muted { color: var(--muted); font-size: 12px; }
    .chart-note { margin-top: 8px; }
    .small { margin: 8px 0 0; }
    .tables { margin-top: 16px; }
    .hours-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
  `,
})
export class ForecastAccuracyPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly periods = PERIODS;
  protected readonly runs = RUNS;
  protected readonly verdicts = VERDICTS;
  protected readonly status = STATUS;
  protected readonly fmtNumber = fmtNumber;
  protected readonly fmtInt = fmtInt;
  protected readonly fmtWh = fmtWh;
  protected readonly fmtWhValue = fmtWhValue;
  protected readonly fmtDateTime = fmtDateTime;
  protected readonly fmtDay = fmtDay;

  protected readonly days = signal(1);
  protected readonly run = signal<ForecastSelection>('LATEST');
  protected readonly showNight = signal(false);
  protected readonly report = signal<AccuracyReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly runHint = computed(() => RUNS.find((r) => r.run === this.run())?.hint ?? '');

  protected readonly strings = computed(() =>
    Object.entries(this.report()?.strings ?? {}).map(([name, s]) => ({ name, ...s })));

  protected readonly visibleHours = computed(() => {
    const hours = [...(this.report()?.hours ?? [])].reverse();
    return this.showNight() ? hours : hours.filter((h) => h.status !== 'NIGHT');
  });

  protected readonly chartSeries = computed<ChartSeries[]>(() => {
    const hours = this.report()?.hours ?? [];
    // valori orari disegnati a metà dell'ora (time = fine dell'ora)
    const x = (time: string) => new Date(time).getTime() - 30 * 60_000;
    return [
      { label: 'Reale', unit: 'Wh', kind: 'bar', color: '#f5d34a',
        points: hours.map((h) => ({ x: x(h.time), y: h.status === 'OK' || h.status === 'NIGHT' ? h.actualWh : null })) },
      { label: 'Previsione', unit: 'Wh', color: '#7cc4ff', points: hours.map((h) => ({ x: x(h.time), y: h.forecastWh })) },
      { label: 'A posteriori', unit: 'Wh', color: '#c39bff', points: hours.map((h) => ({ x: x(h.time), y: h.modelWh })) },
    ];
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const to = new Date();
    const from = new Date(to.getTime() - this.days() * 86_400_000);
    this.loading.set(true);
    this.error.set(null);
    this.api.forecastAccuracy(from, to, this.run()).subscribe({
      next: (r) => { this.report.set(r); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  protected toDate(v: string): Date { return new Date(v); }

  protected pct(v: number | null): string {
    return v === null ? '—' : `${v > 0 ? '+' : ''}${fmtNumber(v, 0)}%`;
  }

  /** colore dell'errore sul totale: entro 15% bene, entro 30% discreto */
  protected errClass(v: number | null): string {
    if (v === null) return '';
    const a = Math.abs(v);
    return a <= 15 ? 'ok' : a <= 30 ? 'warn' : 'bad';
  }

  protected hourlyClass(v: number | null): string {
    if (v === null) return '';
    return v <= 35 ? 'ok' : v <= 60 ? 'warn' : 'bad';
  }

  protected wh(v: number | null): string {
    return fmtWh(v);
  }

  protected diff(estimated: number | null, actual: number | null): string {
    if (estimated === null || actual === null) return '—';
    const d = estimated - actual;
    return `${d > 0 ? '+' : ''}${fmtInt(d)} Wh`;
  }

  /** "16/11 09:00–10:00" (time = fine dell'ora) */
  protected hourLabel(end: string): string {
    const e = new Date(end);
    const s = new Date(e.getTime() - 3_600_000);
    return `${fmtDay(s)} ${fmtTime(s)}–${fmtTime(e)}`;
  }
}
