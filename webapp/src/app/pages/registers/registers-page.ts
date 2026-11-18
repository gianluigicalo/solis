import { NgTemplateOutlet } from '@angular/common';
import { afterRenderEffect, Component, computed, DestroyRef, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, EMPTY, startWith, Subject, switchMap, tap, timer } from 'rxjs';
import { Api } from '../../core/api.service';
import { HeaderStatus } from '../../core/header-status';
import { errorMessage, fmtClock, fmtFullDateTime, fmtNumber, toWattUnits } from '../../core/format';
import { InverterData, Measurement, RegisterAccess, RegisterSource } from '../../core/models';

/** la lista completa legge tutto l'inverter: si aggiorna ogni minuto (o con il pulsante «Aggiorna») */
const REFRESH_MS = 60_000;

const CATEGORY_LABELS: Record<string, string> = {
  INFO: 'Info', CLOCK: 'Orologio', STATUS: 'Stato', PV: 'Fotovoltaico', INVERTER: 'Uscita AC', BACKUP: 'Backup',
  BATTERY: 'Batteria', METER: 'Contatore', LOAD: 'Consumi', ENERGY: 'Energia', SETTINGS: 'Impostazioni',
  GRID_PROTECTION: 'Protezioni', SETTINGS_RW: 'Impostazioni modificabili', TIME_OF_USE: 'Fasce orarie',
  GRID_SETTINGS: 'Rete', SMART_PORT: 'Porta intelligente', HEAT_PUMP: 'Pompa di calore',
  RESERVED: 'Riservati',
};

const ACCESS_LABELS: Record<RegisterAccess, string> = { READ_ONLY: 'sola lettura', READ_WRITE: 'modificabile' };

const SOURCE_LABELS: Record<RegisterSource, string> = {
  OFFICIAL: 'ufficiale', UNOFFICIAL: 'non ufficiale', SOLISCLOUD: 'SolisCloud', RESERVED: 'riservato',
};

interface Row {
  name: string;
  register: number;
  /** valore decodificato dalle specifiche, altrimenti valore fisico */
  value: string;
  unit: string | null;
  decoded: boolean;
  description: string;
  raw: string;
  source: RegisterSource;
  sourceLabel: string;
  access: RegisterAccess;
  accessLabel: string;
  tooltip: string;
  /** testo in minuscolo su cui cerca il filtro: tutte le colonne */
  search: string;
}

interface Section { id: string; label: string; rows: Row[]; }

@Component({
  selector: 'app-registers-page',
  imports: [FormsModule, NgTemplateOutlet, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule,
    MatSlideToggleModule, MatTooltipModule],
  templateUrl: './registers-page.html',
  styleUrl: './registers-page.scss',
})
export class RegistersPage implements OnInit {
  private readonly api = inject(Api);
  private readonly destroyRef = inject(DestroyRef);
  private readonly header = inject(HeaderStatus);

  protected readonly data = signal<InverterData | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly filter = signal('');
  protected readonly showReserved = signal(true);
  protected readonly fmtFullDateTime = fmtFullDateTime;

  /** tutte le sezioni con le righe già pronte per la tabella */
  private readonly sections = computed<Section[]>(() => {
    const d = this.data();
    if (!d) return [];
    return Object.entries(d.categories)
      .filter(([cat]) => this.showReserved() || cat !== 'RESERVED')
      .map(([cat, values]) => ({
        id: cat,
        label: CATEGORY_LABELS[cat] ?? cat,
        rows: Object.entries(values).map(([name, m]) => toRow(name, m)),
      }));
  });

  protected readonly query = computed(() => this.filter().trim().toLowerCase());

  /** con il filtro: le righe che corrispondono in qualsiasi colonna, in tutte le sezioni */
  protected readonly filtered = computed<Section[]>(() => {
    const q = this.query();
    return this.sections()
      .map((s) => ({ ...s, rows: q ? s.rows.filter((r) => r.search.includes(q)) : s.rows }))
      .filter((s) => s.rows.length > 0);
  });

  /**
   * Senza filtro: prima "Tutti" (ogni registro in ordine alfabetico) e "Modificabili" (i soli holding register,
   * per indirizzo), poi le sezioni.
   */
  protected readonly tabs = computed<Section[]>(() => {
    const sections = this.filtered();
    const rows = sections.flatMap((s) => s.rows);
    const all = [...rows].sort((a, b) => a.name.localeCompare(b.name));
    const writable = rows.filter((r) => r.access === 'READ_WRITE').sort((a, b) => a.register - b.register);
    return [{ id: 'ALL', label: 'Tutti', rows: all }, { id: 'WRITABLE', label: 'Modificabili', rows: writable }, ...sections];
  });

  /** sezione mostrata (se sparisce, ad esempio nascondendo i riservati, si torna a "Tutti") */
  private readonly selected = signal('ALL');
  protected readonly current = computed(() => this.tabs().find((t) => t.id === this.selected()) ?? this.tabs()[0]);
  private readonly strip = viewChild<ElementRef<HTMLElement>>('strip');
  protected readonly canScrollLeft = signal(false);
  protected readonly canScrollRight = signal(true);

  /** trascinamento della barra con il mouse (al dito ci pensa lo scorrimento nativo) */
  private drag: { x: number; left: number; moved: boolean } | null = null;
  private suppressClick = false;

  protected select(id: string, event: MouseEvent): void {
    if (this.suppressClick) {
      this.suppressClick = false;
      return;
    }
    this.selected.set(id);
    (event.currentTarget as HTMLElement).scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
  }

  protected scrollTabs(strip: HTMLElement, direction: 1 | -1): void {
    strip.scrollBy({ left: direction * strip.clientWidth * 0.8, behavior: 'smooth' });
  }

  protected updateArrows(strip: HTMLElement): void {
    this.canScrollLeft.set(strip.scrollLeft > 1);
    this.canScrollRight.set(strip.scrollLeft + strip.clientWidth < strip.scrollWidth - 1);
  }

  /** rotellina verticale sopra la barra: scorre in orizzontale */
  protected wheel(event: WheelEvent, strip: HTMLElement): void {
    if (Math.abs(event.deltaY) > Math.abs(event.deltaX) && strip.scrollWidth > strip.clientWidth) {
      strip.scrollLeft += event.deltaY;
      event.preventDefault();
    }
  }

  protected dragStart(event: PointerEvent, strip: HTMLElement): void {
    if (event.pointerType !== 'mouse' || event.button !== 0) return;
    this.drag = { x: event.clientX, left: strip.scrollLeft, moved: false };
  }

  protected dragMove(event: PointerEvent, strip: HTMLElement): void {
    if (!this.drag) return;
    const dx = event.clientX - this.drag.x;
    if (!this.drag.moved && Math.abs(dx) > 5) {
      this.drag.moved = true;
      strip.setPointerCapture(event.pointerId);
      strip.classList.add('dragging');
    }
    if (this.drag.moved) strip.scrollLeft = this.drag.left - dx;
  }

  protected dragEnd(event: PointerEvent, strip: HTMLElement): void {
    if (!this.drag) return;
    // dopo un trascinamento il clic che segue subito (stesso gesto) non deve cambiare sezione
    if (this.drag.moved) {
      this.suppressClick = true;
      setTimeout(() => (this.suppressClick = false));
    }
    this.drag = null;
    strip.classList.remove('dragging');
    if (strip.hasPointerCapture(event.pointerId)) strip.releasePointerCapture(event.pointerId);
  }

  protected readonly matches = computed(() => this.filtered().reduce((n, s) => n + s.rows.length, 0));

  constructor() {
    this.header.clearOnDestroy();
    // frecce attive solo se c'è altro da vedere da quel lato: dopo ogni rendering e quando cambia la larghezza
    afterRenderEffect(() => {
      this.tabs();
      const el = this.strip()?.nativeElement;
      if (el) this.updateArrows(el);
    });
    const onResize = () => { const el = this.strip()?.nativeElement; if (el) this.updateArrows(el); };
    window.addEventListener('resize', onResize);
    this.destroyRef.onDestroy(() => window.removeEventListener('resize', onResize));
  }

  /** pulsante «Aggiorna»: rilegge subito e fa ripartire il conteggio dei 60 s */
  private readonly refresh$ = new Subject<void>();
  protected readonly refreshing = signal(false);

  protected refresh(): void {
    this.refresh$.next();
  }

  ngOnInit(): void {
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => timer(0, REFRESH_MS)),
      tap(() => this.refreshing.set(true)),
      switchMap(() => this.api.inverterData().pipe(catchError((e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
        this.refreshing.set(false);
        return EMPTY;
      }))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((d) => {
      this.data.set(d);
      this.refreshing.set(false);
      this.header.text.set(`${fmtClock(d.readAt)} (%d s)`.replace('%d', String(REFRESH_MS / 1000)));
      this.error.set(null);
      this.loading.set(false);
    });
  }
}

function toRow(name: string, m: Measurement): Row {
  const converted = typeof m.value === 'number' ? toWattUnits(m.value, m.unit) : null;
  const value = m.decoded
    ?? (m.value === null || m.value === undefined ? '—' : converted ? fmtNumber(converted.value, 3) : String(m.value));
  const unit = m.decoded ? null : converted ? converted.unit : m.unit || null;
  const raw = m.raw === null || m.raw === undefined ? '—' : String(m.raw);
  const sourceLabel = SOURCE_LABELS[m.source] ?? m.source;
  const access: RegisterAccess = m.access ?? 'READ_ONLY';
  const accessLabel = ACCESS_LABELS[access];
  const tooltip = m.source === 'UNOFFICIAL' || m.source === 'SOLISCLOUD' ? `${m.publicName ?? ''} — ${m.reference ?? ''}` : '';
  return {
    name, register: m.register, value, unit, decoded: !!m.decoded, description: m.description, raw,
    source: m.source, sourceLabel, tooltip, access, accessLabel,
    search: [name, m.register, value, unit, m.description, raw, sourceLabel, accessLabel, m.publicName, m.reference]
      .filter((x) => x !== null && x !== undefined && x !== '').join('\n').toLowerCase(),
  };
}
