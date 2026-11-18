import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { catchError, EMPTY, forkJoin, switchMap, timer } from 'rxjs';
import { Api } from '../../core/api.service';
import { HeaderStatus } from '../../core/header-status';
import { PowerFlowChart, PvString } from '../../shared/power-flow';
import { errorMessage, fmtClock, fmtFullDateTime, fmtNumber } from '../../core/format';
import { InverterInfo, PowerFlow, SiteSettings } from '../../core/models';

const REFRESH_MS = 10_000;
const INFO_REFRESH_MS = 60_000;

/** Impianto di default se advisor non risponde: 9 e 10 pannelli da 615 W. */
const DEFAULT_STRINGS = { pv1: 9, pv2: 10 };
const DEFAULT_PANEL_W = 615;

@Component({
  selector: 'app-inverter-page',
  imports: [MatProgressBarModule, RouterLink, PowerFlowChart],
  templateUrl: './inverter-page.html',
  styleUrl: './inverter-page.scss',
})
export class InverterPage implements OnInit {
  private readonly api = inject(Api);
  private readonly destroyRef = inject(DestroyRef);
  private readonly header = inject(HeaderStatus);

  constructor() {
    this.header.clearOnDestroy();
  }

  protected readonly summary = signal<PowerFlow | null>(null);
  protected readonly info = signal<InverterInfo | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(true);
  /** impostazioni dell'impianto (advisor), per pannelli e potenza di picco delle stringhe */
  private readonly settings = signal<SiteSettings | null>(null);

  /** capacità totale della batteria in Wh, dalle impostazioni dell'advisor */
  protected readonly batteryCapacityWh = computed(() => this.settings()?.batteryCapacityWh ?? null);

  /** tensioni e correnti dal riepilogo (che legge solo i registri dell'animazione, non tutto l'inverter) */
  private readonly electrical = computed(() => this.summary()?.electrical ?? null);
  protected readonly busVoltage = computed(() => this.electrical()?.dcBusVoltage ?? null);
  /** corrente AC in uscita dall'inverter (fase A: l'inverter è monofase) */
  protected readonly acCurrent = computed(() => this.electrical()?.acCurrent ?? null);
  /** tensione e corrente di rete al contatore */
  protected readonly gridVoltage = computed(() => this.electrical()?.gridVoltage ?? null);
  protected readonly gridCurrent = computed(() => this.electrical()?.gridCurrent ?? null);

  /** MPPT: potenza = tensione × corrente */
  protected readonly pvStrings = computed<PvString[]>(() => {
    const e = this.electrical();
    const settings = this.settings();
    return (['pv1', 'pv2'] as const).map((id, i) => {
      const v = (i === 0 ? e?.pv1Voltage : e?.pv2Voltage) ?? null;
      const a = (i === 0 ? e?.pv1Current : e?.pv2Current) ?? null;
      const planes = settings?.strings[id];
      return {
        label: `MPPT ${i + 1}`,
        power: v !== null && a !== null ? v * a : null,
        voltage: v,
        current: a,
        panels: planes ? Object.values(planes).reduce((sum, n) => sum + n, 0) : DEFAULT_STRINGS[id],
        panelPeakW: settings?.panelPeakPowerW ?? DEFAULT_PANEL_W,
      };
    });
  });

  protected readonly fmtNumber = fmtNumber;
  protected readonly fmtFullDateTime = fmtFullDateTime;

  ngOnInit(): void {
    // una volta sola: se advisor non risponde restano i valori di default
    this.api.settings().pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe((settings) => this.settings.set(settings));

    // stato e identificazione cambiano di rado: ogni minuto (ognuna di queste letture costa ~6 richieste Modbus)
    timer(0, INFO_REFRESH_MS).pipe(
      switchMap(() => this.api.inverterInfo().pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((info) => this.info.set(info));

    // animazione: ogni 10 s, legge solo i registri del riepilogo
    timer(0, REFRESH_MS).pipe(
      switchMap(() => forkJoin({ summary: this.api.inverterSummary() })
        .pipe(catchError((e) => {
          // l'errore resta visibile e si riprova al giro successivo
          this.error.set(errorMessage(e));
          this.loading.set(false);
          return EMPTY;
        }))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((r) => {
      this.summary.set(r.summary);
      this.header.text.set(`${fmtClock(r.summary.readAt)} (%d s)`.replace('%d', String(REFRESH_MS / 1000)));
      this.error.set(null);
      this.loading.set(false);
    });
  }
}
