import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin } from 'rxjs';
import { Api } from '../../core/api.service';
import { errorMessage, fmtFullDateTime, fmtNumber, toWattUnits } from '../../core/format';
import { RegisterDefinition } from '../../core/models';
import { ChartSeries, LineChart } from '../../shared/line-chart';
import { RangePicker, TimeRange } from '../../shared/range-picker';

const MAX_REGISTERS = 4;

const PRESETS: { label: string; registers: string[] }[] = [
  { label: 'Flussi di potenza', registers: ['PV_TOTAL_POWER', 'HOUSE_LOAD_POWER', 'METER_ACTIVE_POWER', 'BATTERY_POWER'] },
  { label: 'Batteria', registers: ['BATTERY_SOC', 'BATTERY_POWER', 'BATTERY_VOLTAGE'] },
  { label: 'Stringhe FV', registers: ['PV1_VOLTAGE', 'PV2_VOLTAGE', 'PV1_CURRENT', 'PV2_CURRENT'] },
  { label: 'Energia di oggi', registers: ['PV_ENERGY_TODAY', 'HOUSE_LOAD_TODAY', 'GRID_IMPORT_TODAY', 'GRID_EXPORT_TODAY'] },
  { label: 'Temperature', registers: ['INVERTER_TEMPERATURE', 'BATTERY_TEMPERATURE_BMS'] },
];

interface Stats { name: string; unit: string | null; min: number; max: number; avg: number; last: number; count: number; }

@Component({
  selector: 'app-inverter-history-page',
  imports: [FormsModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, MatChipsModule,
    MatProgressBarModule, LineChart, RangePicker],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Storico inverter</h1>
        <span class="meta">Fino a {{ max }} registri; dati dal database TimescaleDB</span>
      </div>
      @if (error(); as e) { <div class="error-box"><span class="material-symbols-outlined">error</span>{{ e }}</div> }

      <div class="card controls">
        <div class="row">
          @for (p of presets; track p.label) {
            <button mat-stroked-button (click)="selected.set(p.registers); load()">{{ p.label }}</button>
          }
        </div>
        <div class="row">
          <mat-form-field class="search" subscriptSizing="dynamic">
            <mat-label>Cerca registro</mat-label>
            <input matInput [ngModel]="search()" (ngModelChange)="search.set($event)" placeholder="es. SOC, VOLTAGE, 33139">
          </mat-form-field>
          <mat-form-field class="select" subscriptSizing="dynamic">
            <mat-label>Registri</mat-label>
            <mat-select multiple [ngModel]="selected()" (ngModelChange)="pick($event)">
              @for (g of groups(); track g.category) {
                <mat-optgroup [label]="g.category">
                  @for (r of g.registers; track r.name) {
                    <mat-option [value]="r.name" [disabled]="selected().length >= max && !selected().includes(r.name)">
                      {{ r.name }} <span class="muted">{{ r.unit ? '(' + unitLabel(r.unit) + ')' : '' }}</span>
                    </mat-option>
                  }
                </mat-optgroup>
              }
            </mat-select>
          </mat-form-field>
        </div>
        <div class="row">
          <app-range-picker initial="24h" (rangeChange)="range.set($event); load()" />
          <span class="spacer"></span>
          <button mat-flat-button (click)="refresh()" [disabled]="!selected().length">
            <span class="material-symbols-outlined">refresh</span> Aggiorna
          </button>
        </div>
      </div>

      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }

      <div class="card">
        @if (selected().length) {
          <app-line-chart [series]="series()" [range]="shownRange()" [height]="420" />
        } @else {
          <p class="muted">Scegli uno o più registri.</p>
        }
      </div>

      @if (stats().length) {
        <h2 class="section-title">Riepilogo del periodo</h2>
        <div class="card">
          <table class="data">
            <thead><tr><th>Registro</th><th class="num">Minimo</th><th class="num">Media</th><th class="num">Massimo</th><th class="num">Ultimo</th><th class="num">Campioni</th></tr></thead>
            <tbody>
              @for (s of stats(); track s.name) {
                <tr>
                  <td><code>{{ s.name }}</code></td>
                  <td class="num">{{ fmt(s.min) }} {{ s.unit }}</td>
                  <td class="num">{{ fmt(s.avg) }} {{ s.unit }}</td>
                  <td class="num">{{ fmt(s.max) }} {{ s.unit }}</td>
                  <td class="num">{{ fmt(s.last) }} {{ s.unit }}</td>
                  <td class="num">{{ s.count }}</td>
                </tr>
              }
            </tbody>
          </table>
          @if (lastLoad(); as t) { <p class="muted small">Caricato {{ t }}</p> }
        </div>
      }
    </div>`,
  styles: `
    .controls { display: flex; flex-direction: column; gap: 14px; margin-bottom: 16px; }
    .search { width: min(260px, 100%); }
    .select { flex: 1; min-width: min(320px, 100%); }
    .small { font-size: 12px; margin: 8px 0 0; }
    code { font-size: 12px; }`,
})
export class InverterHistoryPage implements OnInit {
  private readonly api = inject(Api);
  private readonly picker = viewChild.required(RangePicker);

  protected readonly max = MAX_REGISTERS;
  protected readonly presets = PRESETS;
  protected readonly registers = signal<RegisterDefinition[]>([]);
  protected readonly selected = signal<string[]>(PRESETS[0].registers);
  protected readonly search = signal('');
  protected readonly range = signal<TimeRange | null>(null);
  protected readonly series = signal<ChartSeries[]>([]);
  /** periodo dell'ultimo caricamento, per gli estremi dell'asse */
  protected readonly shownRange = signal<TimeRange | null>(null);
  protected readonly stats = signal<Stats[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly lastLoad = signal<string | null>(null);
  protected readonly fmt = (v: number) => fmtNumber(v, 2);
  protected readonly unitLabel = (unit: string) => toWattUnits(0, unit).unit;

  protected readonly groups = computed(() => {
    const text = this.search().trim().toLowerCase();
    const byCategory = new Map<string, RegisterDefinition[]>();
    for (const r of this.registers()) {
      const match = !text || r.name.toLowerCase().includes(text) || r.description.toLowerCase().includes(text)
        || String(r.address).includes(text);
      if (match || this.selected().includes(r.name)) {
        byCategory.set(r.category, [...(byCategory.get(r.category) ?? []), r]);
      }
    }
    return [...byCategory.entries()].map(([category, registers]) => ({ category, registers }));
  });

  ngOnInit(): void {
    this.api.inverterRegisters().subscribe({
      next: (r) => {
        this.registers.set(r.filter((x) => x.type !== 'ASCII' && x.type !== 'HEX16'));
        this.load();
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected pick(names: string[]): void {
    this.selected.set(names.slice(0, MAX_REGISTERS));
    this.load();
  }

  /** Ricalcola il periodo rispetto ad adesso e ricarica. */
  protected refresh(): void {
    this.range.set(this.picker().current());
    this.load();
  }

  protected load(): void {
    const names = this.selected();
    const range = this.range();
    if (!names.length || !range || !this.registers().length) return;
    this.loading.set(true);
    forkJoin(names.map((n) => this.api.inverterHistory(n, range.from, range.to))).subscribe({
      next: (results) => {
        // i contatori in kWh sono mostrati in Wh, come nel resto dell'app
        const units = new Map(this.registers().map((r) => [r.name, r.unit]));
        const unitOf = (name: string) => toWattUnits(0, units.get(name)).unit;
        const conv = (name: string, v: number) => toWattUnits(v, units.get(name)).value;
        this.shownRange.set(range);
        this.series.set(results.map((points, i) => ({
          label: names[i],
          unit: unitOf(names[i]),
          points: points.filter((p) => p.value !== null).map((p) => ({ x: Date.parse(p.time), y: conv(names[i], p.value!) })),
        })));
        this.stats.set(results.map((points, i) => {
          const values = points.map((p) => p.value).filter((v): v is number => v !== null).map((v) => conv(names[i], v));
          return {
            name: names[i], unit: unitOf(names[i]), count: values.length,
            min: Math.min(...values), max: Math.max(...values),
            avg: values.reduce((a, b) => a + b, 0) / (values.length || 1), last: values[values.length - 1],
          };
        }).filter((s) => s.count > 0));
        this.error.set(null);
        this.loading.set(false);
        this.lastLoad.set(fmtFullDateTime(new Date()));
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
