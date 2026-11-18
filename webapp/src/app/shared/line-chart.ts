import { Component, effect, ElementRef, input, OnDestroy, viewChild } from '@angular/core';
import Chart from 'chart.js/auto';
import { fmtDateTime, fmtNumber } from '../core/format';

const MINUTE = 60_000;
/** Passi possibili delle tacche, in minuti. */
const STEPS_MINUTES = [5, 10, 15, 30, 60, 120, 180, 360, 720, 1440, 2880, 10080, 20160, 43200];

interface TimeAxis { stepMinutes?: number; }

export interface ChartPoint { x: number; y: number | null; }

export interface ChartSeries {
  label: string;
  points: ChartPoint[];
  unit?: string | null;
  color?: string;
  kind?: 'line' | 'bar';
  /** riempie l'area sotto la linea */
  fill?: boolean;
  /** linea a gradini (valori costanti per intervallo) */
  stepped?: boolean;
}

export const PALETTE = ['#7cc4ff', '#ffb86b', '#8be28b', '#ff7b9c', '#c39bff', '#f6e27a', '#6fe3d8'];

Chart.defaults.color = '#c4c7c5';
Chart.defaults.borderColor = 'rgba(255,255,255,0.08)';
Chart.defaults.font.family = 'Roboto, sans-serif';

/**
 * Grafico temporale (asse x = timestamp in ms). Le serie con la prima unità usano l'asse sinistro, quelle con
 * un'unità diversa l'asse destro.
 */
@Component({
  selector: 'app-line-chart',
  template: `
    <div class="wrap" [style.height.px]="height()">
      <canvas #canvas></canvas>
      @if (empty()) { <div class="empty">Nessun dato nel periodo</div> }
    </div>`,
  styles: `
    .wrap { position: relative; width: 100%; }
    .empty { position: absolute; inset: 0; display: grid; place-items: center; color: var(--muted); }`,
})
export class LineChart implements OnDestroy {
  readonly series = input.required<ChartSeries[]>();
  readonly height = input(320);
  /** Estremi dell'asse x; se omessi si adatta ai dati. */
  readonly range = input<{ from: Date; to: Date } | null>(null);

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;
  protected empty = () => this.series().every((s) => s.points.length === 0);

  constructor() {
    effect(() => this.render(this.canvas().nativeElement, this.series(), this.range()));
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private render(canvas: HTMLCanvasElement, series: ChartSeries[], range: { from: Date; to: Date } | null): void {
    this.chart?.destroy();
    const leftUnit = series[0]?.unit ?? '';
    const rightUnit = series.find((s) => (s.unit ?? '') !== leftUnit)?.unit ?? null;

    this.chart = new Chart(canvas, {
      type: 'line',
      data: {
        datasets: series.map((s, i) => {
          const color = s.color ?? PALETTE[i % PALETTE.length];
          return {
            type: s.kind ?? 'line',
            label: s.unit ? `${s.label} (${s.unit})` : s.label,
            data: withGaps(s.points),
            yAxisID: (s.unit ?? '') === leftUnit ? 'left' : 'right',
            borderColor: color,
            backgroundColor: s.kind === 'bar' || s.fill ? color + '55' : color,
            fill: s.fill ?? false,
            stepped: s.stepped ?? false,
            borderWidth: 2,
            pointRadius: s.points.length > 40 ? 0 : 2,
            pointHoverRadius: 4,
            tension: 0.3,
            cubicInterpolationMode: 'monotone',
            spanGaps: false,
          } as never;
        }),
      },
      options: {
        animation: false,
        maintainAspectRatio: false,
        parsing: false,
        normalized: true,
        interaction: { mode: 'nearest', axis: 'x', intersect: false },
        scales: {
          x: {
            type: 'linear',
            min: range?.from.getTime(),
            max: range?.to.getTime(),
            ticks: {
              maxRotation: 0, autoSkip: false,
              callback(value) { return tickLabel(Number(value), (this as unknown as TimeAxis).stepMinutes ?? 60); },
            },
            afterBuildTicks: (axis) => {
              const ticks = timeTicks(axis.min, axis.max);
              (axis as unknown as TimeAxis).stepMinutes = ticks.stepMinutes;
              axis.ticks = ticks.values.map((value) => ({ value }));
            },
          },
          left: { position: 'left', title: { display: !!leftUnit, text: leftUnit } },
          right: {
            position: 'right', display: rightUnit !== null,
            title: { display: !!rightUnit, text: rightUnit ?? '' },
            grid: { drawOnChartArea: false },
          },
        },
        plugins: {
          legend: { labels: { boxWidth: 12, usePointStyle: true } },
          decimation: { enabled: true, algorithm: 'lttb', samples: 800 },
          tooltip: {
            callbacks: {
              title: (items) => (items.length ? fmtDateTime(items[0].parsed.x as number) : ''),
              label: (item) => `${item.dataset.label}: ${fmtNumber(item.parsed.y as number, 2)}`,
            },
          },
        },
      },
    });
  }
}

/** Inserisce un punto nullo dove manca un tratto di dati, così la linea si interrompe invece di unire i buchi. */
function withGaps(points: ChartPoint[]): ChartPoint[] {
  if (points.length < 3) return points;
  const deltas = points.slice(1).map((p, i) => p.x - points[i].x).sort((a, b) => a - b);
  const median = deltas[Math.floor(deltas.length / 2)];
  const result: ChartPoint[] = [points[0]];
  for (let i = 1; i < points.length; i++) {
    if (points[i].x - points[i - 1].x > Math.max(median * 5, 5 * 60_000)) {
      result.push({ x: (points[i].x + points[i - 1].x) / 2, y: null });
    }
    result.push(points[i]);
  }
  return result;
}

/** Tacche allineate a minuti/ore/giorni locali, al massimo 9 sull'asse. */
function timeTicks(min: number, max: number): { values: number[]; stepMinutes: number } {
  const span = (max - min) / MINUTE;
  const step = STEPS_MINUTES.find((m) => span / m <= 9) ?? STEPS_MINUTES[STEPS_MINUTES.length - 1];
  const start = new Date(min);
  start.setSeconds(0, 0);
  if (step >= 1440) {
    start.setHours(0, 0);
  } else if (step >= 60) {
    start.setMinutes(0);
    start.setHours(Math.floor(start.getHours() / (step / 60)) * (step / 60));
  } else {
    start.setMinutes(Math.floor(start.getMinutes() / step) * step);
  }
  const values: number[] = [];
  for (let t = start.getTime(); t <= max; ) {
    if (t >= min) values.push(t);
    const next = new Date(t);
    if (step >= 1440) next.setDate(next.getDate() + step / 1440);
    else next.setMinutes(next.getMinutes() + step);
    t = next.getTime();
  }
  return { values, stepMinutes: step };
}

function tickLabel(value: number, stepMinutes: number): string {
  const d = new Date(value);
  if (stepMinutes >= 1440) return d.toLocaleDateString('it-IT', { day: '2-digit', month: '2-digit' });
  if (d.getHours() === 0 && d.getMinutes() === 0) {
    return d.toLocaleDateString('it-IT', { weekday: 'short', day: '2-digit', month: '2-digit' });
  }
  return d.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' });
}
