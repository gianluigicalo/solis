import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, forkJoin, of } from 'rxjs';
import { Api } from '../../core/api.service';
import { errorMessage, fmtDay, fmtFullDateTime, fmtNumber, fmtTime } from '../../core/format';
import { MeteoLocation, OpenMeteoResponse } from '../../core/models';
import { weatherIcon, weatherLabel } from '../../core/weather-codes';
import { ChartPoint, ChartSeries, LineChart, PALETTE } from '../../shared/line-chart';

const AQI_LEVELS: [number, string, string][] = [
  [20, 'Buona', 'ok'], [40, 'Discreta', 'ok'], [60, 'Moderata', 'warn'], [80, 'Scadente', 'warn'],
  [100, 'Molto scadente', 'bad'], [Infinity, 'Pessima', 'bad'],
];

@Component({
  selector: 'app-meteo-page',
  imports: [MatButtonModule, MatProgressBarModule, MatTooltipModule, LineChart],
  templateUrl: './meteo-page.html',
  styleUrl: './meteo-page.scss',
})
export class MeteoPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly location = signal<MeteoLocation | null>(null);
  protected readonly forecast = signal<OpenMeteoResponse | null>(null);
  protected readonly solar = signal<Record<string, OpenMeteoResponse> | null>(null);
  protected readonly air = signal<OpenMeteoResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly loadedAt = signal<Date | null>(null);

  protected readonly weatherIcon = weatherIcon;
  protected readonly weatherLabel = weatherLabel;
  protected readonly fmtNumber = fmtNumber;
  protected readonly fmtTime = fmtTime;
  protected readonly fmtDay = fmtDay;
  protected readonly fmtFullDateTime = fmtFullDateTime;
  protected readonly planes = computed(() => Object.entries(this.location()?.planes ?? {}));

  /** Prossime 24 ore, a partire dall'ora corrente. */
  protected readonly next24h = computed(() => {
    const h = this.forecast()?.hourly;
    if (!h) return [];
    const now = Date.now() - 3600_000;
    return (h['time'] as string[])
      .map((t, i) => ({
        time: t, temp: h['temperature_2m']?.[i] as number, code: h['weather_code']?.[i] as number,
        rain: h['precipitation_probability']?.[i] as number,
      }))
      .filter((x) => Date.parse(x.time) >= now)
      .slice(0, 24);
  });

  protected readonly days = computed(() => {
    const d = this.forecast()?.daily;
    if (!d) return [];
    return (d['time'] as string[]).map((t, i) => ({
      date: t,
      code: d['weather_code']?.[i] as number,
      max: d['temperature_2m_max']?.[i] as number,
      min: d['temperature_2m_min']?.[i] as number,
      rain: d['precipitation_sum']?.[i] as number,
      rainProb: d['precipitation_probability_max']?.[i] as number,
      sunrise: d['sunrise']?.[i] as string,
      sunset: d['sunset']?.[i] as string,
      sunshineH: ((d['sunshine_duration']?.[i] as number) ?? 0) / 3600,
      uv: d['uv_index_max']?.[i] as number,
      radiation: d['shortwave_radiation_sum']?.[i] as number,
    }));
  });

  protected readonly weatherSeries = computed<ChartSeries[]>(() => {
    const f = this.forecast();
    if (!f?.hourly) return [];
    return [
      { label: 'Temperatura', unit: f.hourly_units?.['temperature_2m'], points: points(f, 'temperature_2m'), color: PALETTE[1] },
      { label: 'Prob. pioggia', unit: '%', points: points(f, 'precipitation_probability'), color: PALETTE[0], kind: 'bar' },
      { label: 'Nuvolosità', unit: '%', points: points(f, 'cloud_cover'), color: '#9aa4ad' },
    ];
  });

  protected readonly solarSeries = computed<ChartSeries[]>(() => {
    const s = this.solar();
    const f = this.forecast();
    const series: ChartSeries[] = Object.entries(s ?? {}).map(([plane, r], i) => ({
      label: `Sul piano ${plane}`, unit: 'W/m²', points: points(r, 'global_tilted_irradiance'),
      color: PALETTE[(i + 2) % PALETTE.length], fill: true,
    }));
    if (f?.hourly?.['shortwave_radiation']) {
      series.push({ label: 'Orizzontale', unit: 'W/m²', points: points(f, 'shortwave_radiation'), color: '#9aa4ad' });
    }
    return series;
  });

  protected readonly aqi = computed(() => {
    const value = this.air()?.current?.['european_aqi'] as number | undefined;
    if (value === undefined) return null;
    const [, label, css] = AQI_LEVELS.find(([limit]) => value <= limit)!;
    return { value, label, css };
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    forkJoin({
      location: this.api.meteoLocation(),
      forecast: this.api.forecast({ forecast_days: 7 }),
      solar: this.api.solar({ hourly: 'global_tilted_irradiance', forecast_days: 3 }),
      air: this.api.airQuality().pipe(catchError(() => of(null))),
    }).subscribe({
      next: (r) => {
        this.location.set(r.location);
        this.forecast.set(r.forecast);
        this.solar.set(r.solar);
        this.air.set(r.air);
        this.error.set(null);
        this.loading.set(false);
        this.loadedAt.set(new Date());
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected current(name: string): number | undefined {
    return this.forecast()?.current?.[name] as number | undefined;
  }

  protected airValue(name: string): string {
    const v = this.air()?.current?.[name] as number | undefined;
    return v === undefined ? '—' : `${fmtNumber(v)} ${this.air()?.current_units?.[name] ?? ''}`;
  }
}

/** Serie oraria di una variabile, limitata alle prossime 72 ore (con 6 ore di contesto). */
function points(r: OpenMeteoResponse, variable: string): ChartPoint[] {
  const times = (r.hourly?.['time'] ?? []) as string[];
  const values = r.hourly?.[variable] ?? [];
  const from = Date.now() - 6 * 3600_000;
  const to = Date.now() + 72 * 3600_000;
  return times
    .map((t, i) => ({ x: Date.parse(t), y: values[i] as number | null }))
    .filter((p) => p.x >= from && p.x <= to);
}
