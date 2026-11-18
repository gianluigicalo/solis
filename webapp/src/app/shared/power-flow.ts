import { afterNextRender, Component, computed, DestroyRef, ElementRef, inject, input, signal } from '@angular/core';
import { balanceConsumption, fmtInt, fmtNumber, fmtPower, fmtWh } from '../core/format';
import { PowerFlow } from '../core/models';

/** Una stringa fotovoltaica: potenza attuale e potenza di picco installata. */
export interface PvString {
  label: string;
  /** potenza attuale in W (null se non disponibile) */
  power: number | null;
  /** tensione attuale in V (null se non disponibile) */
  voltage: number | null;
  /** corrente attuale in A (null se non disponibile) */
  current: number | null;
  panels: number;
  panelPeakW: number;
}

type NodeId = 'pv1' | 'pv2' | 'pv' | 'grid' | 'load' | 'battery' | 'backup';
interface Pt { x: number; y: number; }

/** Dimensioni dei caratteri (unità SVG): uguali in tutti i cerchi. */
interface Fonts {
  /** descrizioni: "Produzione oggi", "MPPT 1", "Tensione bus DC" */
  caption: number;
  /** valori sotto le descrizioni e righe "etichetta valore" */
  value: number;
  /** potenza principale del cerchio */
  power: number;
  /** tensioni e correnti */
  electric: number;
  /** icone */
  icon: number;
}

interface Layout {
  width: number;
  height: number;
  fonts: Fonts;
  center: Pt;
  centerRadius: number;
  /** raggio di ogni cerchio: più grande dove ci sono più dati */
  radii: Record<NodeId, number>;
  nodes: Record<NodeId, Pt>;
  /**
   * percorso dal nodo alla destinazione (inverter, o cerchio del fotovoltaico per gli MPPT), verso "entrante".
   * Il primo e l'ultimo punto vengono portati sul bordo dei cerchi lungo il primo e l'ultimo tratto.
   */
  routes: Record<NodeId, Pt[]>;
}

interface Ring { fraction: number; color: string; }

/** Pezzo di testo di una riga: classe (v valore, b grassetto, m descrizione, u unità) e colore facoltativo. */
interface Part { text: string; cls: 'v' | 'b' | 'm' | 'u'; color?: string | null; size?: number; }

/** Riga dentro un cerchio: testo, icona, o disegno (batteria, inverter). */
interface Line { kind: 'text' | 'icon' | 'battery' | 'inverter'; size: number; parts: Part[]; }

/** Riga già posizionata: y è la linea di base (testo) o il centro (disegni), relativa al centro del cerchio. */
interface PlacedLine extends Line { y: number; scale: number; }

interface FlowDef {
  id: NodeId;
  color: string;
  /** potenza assoluta in W (null se non nota) */
  watts: number | null;
  /** true se l'energia va dalla destinazione verso il nodo (es. inverter -> casa) */
  outward: boolean;
  ring?: Ring;
  lines: Line[];
}

interface FlowView {
  id: NodeId;
  color: string;
  active: boolean;
  path: string;
  arrows: { begin: string; dur: string }[];
  key: string;
}

interface NodeView {
  id: string;
  pos: Pt;
  r: number;
  color: string | null;
  center: boolean;
  ring?: Ring;
  ringDash?: string;
  lines: PlacedLine[];
}

/** Sotto questa potenza (W) un flusso è considerato fermo. */
const IDLE_W = 10;

const P = (x: number, y: number): Pt => ({ x, y });

/**
 * Schermo largo: a sinistra MPPT, fotovoltaico e batteria; al centro l'inverter; a destra rete, casa e backup.
 */
const WIDE: Layout = {
  width: 910, height: 630,
  fonts: { caption: 11, value: 13, power: 20, electric: 17, icon: 20 },
  center: P(520, 330), centerRadius: 62,
  // raggi appena sufficienti per il contenuto (con i valori più lunghi plausibili)
  radii: { pv1: 60, pv2: 60, pv: 72, grid: 82, load: 86, battery: 96, backup: 40 },
  nodes: {
    pv1: P(80, 330), pv2: P(232, 330), pv: P(300, 120), battery: P(330, 515),
    grid: P(790, 118), load: P(790, 340), backup: P(790, 530),
  },
  routes: {
    pv1: [P(80, 260), P(80, 222), P(288, 222), P(288, 197)],
    pv2: [P(232, 260), P(232, 238), P(312, 238), P(312, 197)],
    pv: [P(378, 120), P(506, 120), P(506, 256)],
    grid: [P(700, 118), P(534, 118), P(534, 256)],
    load: [P(694, 340), P(595, 340)],
    battery: [P(436, 515), P(506, 515), P(506, 404)],
    backup: [P(738, 530), P(534, 530), P(534, 404)],
  },
};

/** Schermo stretto (smartphone): MPPT in alto, poi fotovoltaico e rete, inverter e backup, batteria e casa. */
const COMPACT: Layout = {
  width: 400, height: 795,
  fonts: { caption: 11, value: 13, power: 21, electric: 16, icon: 21 },
  // inverter sulla retta tra fotovoltaico e batteria, a uguale distanza dai due bordi
  center: P(105, 408), centerRadius: 62,
  // raggi appena sufficienti per il contenuto (con i valori più lunghi plausibili)
  radii: { pv1: 60, pv2: 60, pv: 72, grid: 82, load: 86, battery: 96, backup: 40 },
  nodes: {
    pv1: P(150, 64), pv2: P(310, 64), pv: P(100, 220), grid: P(305, 320),
    backup: P(330, 480), battery: P(110, 620), load: P(300, 700),
  },
  routes: {
    pv1: [P(150, 124), P(150, 180)],
    pv2: [P(310, 124), P(310, 205), P(160, 205)],
    pv: [P(102, 292), P(102, 346)],
    grid: [P(305, 402), P(305, 420), P(170, 420)],
    backup: [P(290, 460), P(170, 460)],
    battery: [P(108, 524), P(108, 470)],
    load: [P(300, 614), P(300, 515), P(150, 515), P(150, 451)],
  },
};

/** Larghezza del componente sotto la quale si usa il layout compatto. */
const COMPACT_BELOW_PX = 680;

const PV_COLOR = '#f5d34a';
const STRING_COLOR = '#ffb547';
/** colore delle correnti (A), tendente al rosso */
const AMP_COLOR = '#ff6b57';
const GRID_COLOR = '#4aa8ff';
const MUTED = 'var(--muted)';

/**
 * Flussi di energia dell'impianto: MPPT, fotovoltaico, rete, casa, batteria e backup collegati all'inverter, con
 * frecce animate nel verso del flusso. Più alta la potenza, più veloci le frecce. Dentro ogni cerchio le righe sono
 * impilate e centrate; una riga troppo larga per il cerchio viene rimpicciolita quanto basta.
 */
@Component({
  selector: 'app-power-flow',
  template: `
    @let l = layout();
    <svg [attr.viewBox]="'0 0 ' + l.width + ' ' + l.height" [class.compact]="compact()" role="img"
         [attr.aria-label]="ariaLabel()">
      <defs>
        <marker id="pf-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7"
                orient="auto-start-reverse">
          <path d="M0,0 L10,5 L0,10 z" fill="context-stroke" />
        </marker>
      </defs>

      <!-- linee: base fissa + frecce in movimento -->
      @for (f of flows(); track f.key) {
        <path class="line" [attr.d]="f.path" [style.stroke]="f.active ? f.color : null" [class.idle]="!f.active"
              [attr.marker-end]="f.active ? 'url(#pf-arrow)' : null" />
        @if (f.active && animate) {
          @for (a of f.arrows; track $index) {
            <path class="dot" d="M-6,-5.5 L6,0 L-6,5.5 L-3,0 z" [style.fill]="f.color">
              <animateMotion [attr.path]="f.path" [attr.dur]="a.dur" [attr.begin]="a.begin"
                             repeatCount="indefinite" rotate="auto" />
            </path>
          }
        }
      }

      <!-- cerchi: inverter e nodi, con le righe di testo impilate -->
      @for (n of nodes(); track n.id) {
        <circle class="node" [class.center]="n.center" [attr.cx]="n.pos.x" [attr.cy]="n.pos.y" [attr.r]="n.r"
                [style.stroke]="n.color" [class.idle]="!n.color && !n.center" />
        @if (n.ring; as ring) {
          <!-- anello interno: stato di carica (batteria) o produzione rispetto al picco (fotovoltaico, MPPT) -->
          <circle class="ring-track" [attr.cx]="n.pos.x" [attr.cy]="n.pos.y" [attr.r]="n.r - 5" />
          <circle class="ring" [attr.cx]="n.pos.x" [attr.cy]="n.pos.y" [attr.r]="n.r - 5" [style.stroke]="ring.color"
                  [attr.stroke-dasharray]="n.ringDash" [attr.transform]="'rotate(-90 ' + n.pos.x + ' ' + n.pos.y + ')'" />
        }
        @for (ln of n.lines; track $index) {
          @switch (ln.kind) {
            @case ('icon') {
              <text class="icon" [attr.x]="n.pos.x" [attr.y]="n.pos.y + ln.y" [attr.font-size]="ln.size"
                    [style.fill]="ln.parts[0].color">{{ ln.parts[0].text }}</text>
            }
            @case ('battery') {
              <!-- batteria orizzontale con livello e percentuale -->
              <g class="battery-glyph" [attr.transform]="glyph(n.pos, ln.y, ln.size / 16)" [style.color]="ln.parts[0].color">
                <rect class="shell" x="-17" y="-8" width="31" height="16" rx="2.5" />
                <rect class="cap" x="14.5" y="-3" width="2.5" height="6" rx="1" />
                <rect class="level" x="-15" y="-6" [attr.width]="27 * (n.ring?.fraction ?? 0)" height="12" rx="1.2" />
                <text class="pct" x="-1.5" y="4.3">{{ ln.parts[0].text }}</text>
              </g>
            }
            @case ('inverter') {
              <g class="inverter-glyph" [attr.transform]="glyph(n.pos, ln.y, ln.size / 21)">
                <rect x="-8" y="-11" width="16" height="19" rx="2.5" />
                <line x1="-5" y1="4.5" x2="5" y2="4.5" />
                <line x1="-4.5" y1="8" x2="-4.5" y2="10.5" />
                <line x1="4.5" y1="8" x2="4.5" y2="10.5" />
                <path class="bolt" d="M1.2,-8.5 L-3.2,-2 L-0.3,-2 L-1.2,2.5 L3.2,-4 L0.3,-4 Z" />
              </g>
            }
            @default {
              <text class="txt" [attr.x]="n.pos.x" [attr.y]="n.pos.y + ln.y" [attr.font-size]="ln.size * ln.scale"
                >@for (p of ln.parts; track $index) {<tspan [attr.class]="p.cls" [style.fill]="p.color ?? null"
                [attr.font-size]="p.size ? p.size * ln.scale : null">{{ p.text }}</tspan>}</text>
            }
          }
        }
      }
    </svg>`,
  styles: `
    :host { display: block; }
    svg { display: block; width: 100%; margin: 0 auto; height: auto; overflow: visible; }
    svg:not(.compact) { max-width: 960px; }
    svg.compact { max-width: 480px; }
    .idle { fill: var(--muted); }
    .line { fill: none; stroke-width: 2.5; stroke-linejoin: round; opacity: .9; }
    .line.idle { stroke: var(--line); opacity: .7; }
    .dot { filter: drop-shadow(0 0 3px currentColor); }
    .node { fill: var(--mat-sys-surface-container-lowest); stroke-width: 2.5; }
    .node.idle { stroke: var(--line); }
    .node.center { stroke: var(--mat-sys-tertiary); }
    .icon { font-family: 'Material Symbols Outlined'; text-anchor: middle; font-feature-settings: 'liga'; }
    .inverter-glyph { fill: none; stroke: var(--mat-sys-tertiary); stroke-width: 1.6; stroke-linecap: round; stroke-linejoin: round; }
    .inverter-glyph .bolt { fill: var(--mat-sys-tertiary); stroke: none; }
    .battery-glyph .shell { fill: none; stroke: currentColor; stroke-width: 1.5; }
    .battery-glyph .cap { fill: currentColor; }
    .battery-glyph .level { fill: currentColor; opacity: .3; }
    .battery-glyph .pct { text-anchor: middle; font: 700 12px Roboto, sans-serif; fill: var(--mat-sys-on-surface); }
    .ring-track { fill: none; stroke: var(--line); stroke-width: 4; opacity: .35; }
    .ring { fill: none; stroke-width: 4; stroke-linecap: round; opacity: .6; transition: stroke-dasharray .6s ease; }
    .txt { text-anchor: middle; font-family: Roboto, sans-serif; fill: var(--mat-sys-on-surface); }
    .v { font-weight: 600; }
    .b { font-weight: 800; }
    .m { font-weight: 400; fill: var(--muted); }
    .u { font-weight: 400; fill: var(--muted); }
  `,
})
export class PowerFlowChart {
  readonly data = input.required<PowerFlow>();
  /** le due stringhe fotovoltaiche (vuoto se non note) */
  readonly strings = input<PvString[]>([]);
  /** tensione del bus DC dell'inverter in V (null se non nota) */
  readonly busVoltage = input<number | null>(null);
  /** corrente AC in uscita dall'inverter in A (null se non nota) */
  readonly acCurrent = input<number | null>(null);
  /** tensione di rete al contatore in V (null se non nota) */
  readonly gridVoltage = input<number | null>(null);
  /** corrente di rete al contatore in A (null se non nota) */
  readonly gridCurrent = input<number | null>(null);
  /** capacità totale della batteria in Wh (null se non nota) */
  readonly batteryCapacityWh = input<number | null>(null);

  /** rispetta la preferenza di sistema "riduci movimento": restano le linee con la punta, senza animazione */
  protected readonly animate = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  protected readonly compact = signal(false);
  protected readonly layout = computed(() => (this.compact() ? COMPACT : WIDE));

  /** definizione dei nodi: colore, potenza, verso del flusso e righe di testo */
  private readonly defs = computed<FlowDef[]>(() => {
    const d = this.data();
    const F = this.layout().fonts;
    const wh = fmtWh;
    const capacityWh = this.batteryCapacityWh();
    const battery = d.batteryPower;
    const grid = d.gridPower;
    const soc = Math.round(d.batterySoc);
    const balance = balanceConsumption(d.today);
    const socColor = soc >= 60 ? 'var(--ok)' : soc >= 25 ? 'var(--warn)' : 'var(--bad)';
    const strings = this.strings();
    const stringsActive = strings.some((s) => (s.power ?? 0) > IDLE_W);
    const totalPeak = strings.reduce((sum, s) => sum + s.panels * s.panelPeakW, 0);
    /** energia prelevata dalla rete meno energia prodotta dal fotovoltaico, oggi */
    const gridMinusPv = d.today.gridImport - d.today.pvProduction;
    const sign = (w: number) => (w > IDLE_W ? '+' : w < -IDLE_W ? '−' : '');

    // righe ricorrenti
    const caption = (text: string): Line => ({ kind: 'text', size: F.caption, parts: [{ text, cls: 'm' }] });
    const value = (text: string): Line => ({ kind: 'text', size: F.value, parts: [{ text, cls: 'b' }] });
    const row = (label: string, v: string): Line =>
      ({ kind: 'text', size: F.value, parts: [{ text: label + ' ', cls: 'm', size: F.caption }, { text: v, cls: 'b' }] });
    const icon = (name: string, color: string | null): Line => ({ kind: 'icon', size: F.icon, parts: [{ text: name, cls: 'v', color }] });
    const power = (watts: number | null, prefix = '', bold = false): Line => {
      const p = fmtPower(watts);
      return { kind: 'text', size: F.power, parts: [{ text: prefix + p.value, cls: bold ? 'b' : 'v' }, { text: ' ' + p.unit, cls: 'u', size: F.caption }] };
    };
    /** stessa riga con i caratteri ridotti di "by" punti */
    const small = (ln: Line, by: number): Line =>
      ({ ...ln, size: ln.size - by, parts: ln.parts.map((p) => (p.size ? { ...p, size: p.size - 1 } : p)) });
    const electric = (text: string, color: string | null): Line => ({ kind: 'text', size: F.electric, parts: [{ text, cls: 'v', color }] });

    return [
      ...strings.slice(0, 2).map((s, i): FlowDef => {
        const peak = s.panels * s.panelPeakW;
        const on = (s.power ?? 0) > IDLE_W;
        return {
          id: i === 0 ? 'pv1' : 'pv2', color: STRING_COLOR, watts: s.power, outward: false,
          ring: s.power !== null && peak > 0 ? { fraction: s.power / peak, color: STRING_COLOR } : undefined,
          // caratteri un po' più piccoli degli altri cerchi
          lines: [
            small(caption(s.label), 1),
            small(electric(s.voltage === null ? '—' : `${fmtInt(s.voltage)}\u00a0V`, on ? STRING_COLOR : MUTED), 2),
            small(electric(amps(s.current) || '—', on ? AMP_COLOR : MUTED), 2),
            small(power(s.power), 2),
          ],
        };
      }),
      {
        id: 'pv', color: PV_COLOR, watts: d.pvPower, outward: false,
        ring: totalPeak ? { fraction: d.pvPower / totalPeak, color: PV_COLOR } : undefined,
        lines: [
          icon('solar_power', d.pvPower > IDLE_W ? PV_COLOR : MUTED),
          power(d.pvPower, '', true),
          // somma delle correnti degli MPPT
          ...(strings.some((s) => s.current !== null)
            ? [electric(amps(strings.reduce((sum, s) => sum + (s.current ?? 0), 0)), stringsActive ? AMP_COLOR : MUTED)] : []),
          caption('Produzione oggi'),
          value(wh(d.today.pvProduction)),
        ],
      },
      {
        id: 'grid', color: GRID_COLOR, watts: Math.abs(grid), outward: grid > 0,
        lines: [
          { ...icon('electric_meter', Math.abs(grid) > IDLE_W ? GRID_COLOR : MUTED), size: F.icon + 8 },
          // + in immissione, − in prelievo
          power(Math.abs(grid), sign(grid)),
          ...(this.gridVoltage() !== null || this.gridCurrent() !== null ? [{
            kind: 'text', size: F.electric, parts: [
              { text: this.gridVoltage() === null ? '—' : `${fmtInt(this.gridVoltage()!)}\u00a0V`, cls: 'v' },
              { text: ' · ', cls: 'm' },
              { text: amps(this.gridCurrent()) || '—', cls: 'v', color: AMP_COLOR },
            ],
          } as Line] : []),
          caption('prelievo oggi'),
          value(wh(d.today.gridImport)),
          caption('immissione oggi'),
          value(wh(d.today.gridExport)),
        ],
      },
      {
        id: 'load', color: '#b39dff', watts: d.houseLoadPower, outward: true,
        lines: [
          icon('home', d.houseLoadPower > IDLE_W ? '#b39dff' : MUTED),
          power(d.houseLoadPower),
          // misurato dall'inverter e per bilancio (come l'app Solis, comprende le perdite dell'impianto)
          row('misurato', wh(d.today.houseConsumption)),
          row('bilancio', wh(balance)),
          row('rete − FV', signedWh(gridMinusPv)),
          // bilancio meno consumo misurato: perdite di conversione e della batteria
          row('perdite', wh(Math.max(0, balance - d.today.houseConsumption))),
        ],
      },
      {
        id: 'battery', color: '#62d26f', watts: Math.abs(battery), outward: battery > 0,
        ring: { fraction: soc / 100, color: socColor },
        lines: [
          { kind: 'battery', size: F.icon + 6, parts: [{ text: `${soc}%`, cls: 'v', color: socColor }] },
          { ...power(Math.abs(battery), sign(battery)), parts: power(Math.abs(battery), sign(battery)).parts
            .map((p, i) => (i === 0 && Math.abs(battery) > IDLE_W ? { ...p, color: '#62d26f' } : p)) },
          ...(capacityWh ? [row('disponibili', `${fmtInt((capacityWh * d.batterySoc) / 100)}\u00a0Wh`)] : []),
          row('carica oggi', wh(d.today.batteryCharge)),
          row('scarica oggi', wh(d.today.batteryDischarge)),
        ],
      },
      {
        id: 'backup', color: '#4dd0c8', watts: d.backupLoadPower, outward: true,
        lines: [icon('power', d.backupLoadPower > IDLE_W ? '#4dd0c8' : MUTED), power(d.backupLoadPower)],
      },
    ];
  });

  protected readonly flows = computed<FlowView[]>(() => {
    const l = this.layout();
    return this.defs().map(({ id, color, watts, outward }): FlowView => {
      const active = watts !== null && watts > IDLE_W;
      // estremi sul bordo dei cerchi: dal nodo all'inverter (gli MPPT al fotovoltaico)
      const toPv = id === 'pv1' || id === 'pv2';
      const inward = clipRoute(l.routes[id], l.nodes[id], l.radii[id],
        toPv ? l.nodes.pv : l.center, toPv ? l.radii.pv : l.centerRadius);
      // il percorso va sempre nel verso del flusso: le frecce e la punta seguono il percorso
      const route = active && outward ? [...inward].reverse() : inward;
      const length = routeLength(route);
      const dur = arrowDuration(length, watts ?? 0);
      const count = Math.max(1, Math.round(length / 75));
      return {
        id, color, active,
        path: roundedPath(route, this.compact() ? 12 : 16),
        arrows: Array.from({ length: count }, (_, i) => ({ dur: `${dur}s`, begin: `${(-(i * dur) / count).toFixed(2)}s` })),
        // chiave del @for: se cambiano verso o velocità, le animazioni vengono ricreate da capo
        key: `${id}-${active}-${outward}-${dur}-${this.compact()}`,
      };
    });
  });

  /** cerchi da disegnare: l'inverter e i nodi, con le righe già posizionate */
  protected readonly nodes = computed<NodeView[]>(() => {
    const l = this.layout();
    const F = l.fonts;
    const d = this.data();
    const bus = this.busVoltage();
    const stringsActive = this.strings().some((s) => (s.power ?? 0) > IDLE_W);
    const ac = amps(this.acCurrent());
    const inverter: NodeView = {
      id: 'center', pos: l.center, r: l.centerRadius, color: null, center: true,
      lines: placeLines([
        { kind: 'inverter', size: F.icon + 4, parts: [] },
        ...(bus === null ? [] : [
          { kind: 'text', size: F.caption, parts: [{ text: 'Tensione bus DC', cls: 'm' }] } as Line,
          // stesso colore della tensione degli MPPT
          { kind: 'text', size: F.electric, parts: [{ text: `${fmtInt(bus)}\u00a0V`, cls: 'v', color: stringsActive ? STRING_COLOR : MUTED }] } as Line,
        ]),
        ...(ac ? [{ kind: 'text', size: F.electric, parts: [{ text: 'AC ', cls: 'm', size: F.caption }, { text: ac, cls: 'v', color: AMP_COLOR }] } as Line] : []),
        { kind: 'text', size: F.value, parts: [{ text: `${fmtNumber(d.inverterTemperature)}\u00a0°C`, cls: 'b' }] },
      ], l.centerRadius - 4),
    };
    return [inverter, ...this.defs().map((f): NodeView => {
      const r = l.radii[f.id];
      const active = f.watts !== null && f.watts > IDLE_W;
      return {
        id: f.id, pos: l.nodes[f.id], r, color: active ? f.color : null, center: false,
        ring: f.ring,
        ringDash: f.ring ? ringDash(r - 5, f.ring.fraction) : undefined,
        lines: placeLines(f.lines, r - (f.ring ? 8 : 4)),
      };
    })];
  });

  protected readonly ariaLabel = computed(() => {
    const d = this.data();
    const w = (v: number) => `${fmtPower(Math.abs(v)).value} ${fmtPower(Math.abs(v)).unit}`;
    const strings = this.strings().map((s) => `${s.label} ${s.power === null ? 'non disponibile' : w(s.power)}`).join(', ');
    return `Fotovoltaico ${w(d.pvPower)}${strings ? ` (${strings})` : ''}, casa ${w(d.houseLoadPower)}, `
      + `batteria ${Math.round(d.batterySoc)}% `
      + `${d.batteryPower > IDLE_W ? 'in carica' : d.batteryPower < -IDLE_W ? 'in scarica' : 'inattiva'} ${w(d.batteryPower)}, `
      + `rete ${d.gridPower > IDLE_W ? 'in immissione' : d.gridPower < -IDLE_W ? 'in prelievo' : 'neutra'} ${w(d.gridPower)}`;
  });

  /** Posiziona un disegno centrato in (centro.x, centro.y + dy), scalato. */
  protected glyph(center: Pt, dy: number, scale: number): string {
    return `translate(${center.x} ${center.y + dy}) scale(${scale.toFixed(3)})`;
  }

  constructor() {
    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const observer = new ResizeObserver(([entry]) => this.compact.set(entry.contentRect.width < COMPACT_BELOW_PX));
      observer.observe(host);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
}

/** Spazio verticale tra le righe di un cerchio (unità SVG). */
const LINE_GAP = 2;

/** Altezza occupata da una riga. */
function lineHeight(ln: Line): number {
  return ln.kind === 'text' ? ln.size * 1.12 : ln.size;
}

/** Larghezza stimata di una riga di testo (Roboto: circa 0,56 em per carattere, 0,6 in grassetto). */
function lineWidth(ln: Line): number {
  if (ln.kind !== 'text') return ln.kind === 'battery' ? ln.size * 2.1 : ln.size;
  return ln.parts.reduce((w, p) => w + p.text.length * (p.size ?? ln.size) * (p.cls === 'b' || p.cls === 'v' ? 0.57 : 0.5), 0);
}

/**
 * Impila le righe centrate in verticale nel cerchio di raggio utile "inner". Una riga di testo più larga della corda
 * del cerchio alla sua altezza viene rimpicciolita (scale < 1) quanto basta.
 */
function placeLines(lines: Line[], inner: number): PlacedLine[] {
  const total = lines.reduce((h, ln) => h + lineHeight(ln), 0) + LINE_GAP * Math.max(0, lines.length - 1);
  let top = -total / 2;
  return lines.map((ln) => {
    const h = lineHeight(ln);
    const edge = Math.max(Math.abs(top), Math.abs(top + h));
    const chord = 2 * Math.sqrt(Math.max(0, inner * inner - edge * edge)) * 0.97;
    const width = lineWidth(ln);
    const scale = ln.kind === 'text' && width > chord ? Math.max(0.6, chord / width) : 1;
    // testo: linea di base (le lettere stanno sopra); icona Material: occupa l'em sopra la base; disegni: centro
    const y = ln.kind === 'text' ? top + ln.size * 0.9 : ln.kind === 'icon' ? top + ln.size : top + h / 2;
    top += h + LINE_GAP;
    return { ...ln, y, scale };
  });
}

/**
 * Porta il primo punto del percorso sul bordo del cerchio di partenza e l'ultimo sul bordo del cerchio di arrivo,
 * restando sulla retta del primo e dell'ultimo tratto.
 */
function clipRoute(points: Pt[], from: Pt, fromR: number, to: Pt, toR: number): Pt[] {
  const out = [...points];
  out[0] = onCircle(points[1], points[0], from, fromR) ?? points[0];
  const n = points.length;
  out[n - 1] = onCircle(points[n - 2], points[n - 1], to, toR) ?? points[n - 1];
  return out;
}

/** Primo punto in cui la semiretta da "start" verso "toward" incontra il cerchio (null se non lo incontra). */
function onCircle(start: Pt, toward: Pt, c: Pt, r: number): Pt | null {
  const len = Math.hypot(toward.x - start.x, toward.y - start.y) || 1;
  const dx = (toward.x - start.x) / len;
  const dy = (toward.y - start.y) / len;
  const fx = start.x - c.x;
  const fy = start.y - c.y;
  const b = fx * dx + fy * dy;
  const disc = b * b - (fx * fx + fy * fy - r * r);
  if (disc < 0) return null;
  const t = -b - Math.sqrt(disc);
  if (t < 0) return null;
  return { x: +(start.x + dx * t).toFixed(2), y: +(start.y + dy * t).toFixed(2) };
}

/** Corrente: "8,4 A" (vuoto se non nota). */
function amps(a: number | null | undefined): string {
  return a === null || a === undefined ? '' : `${fmtNumber(a, 1)}\u00a0A`;
}

/** Energia con segno: "+1.200 Wh", "−300 Wh". */
function signedWh(wh: number): string {
  return `${wh > 0 ? '+' : wh < 0 ? '−' : ''}${fmtInt(Math.abs(wh))}\u00a0Wh`;
}

/** Arco dell'anello per una frazione 0..1 (limitata a quell'intervallo). */
function ringDash(r: number, fraction: number): string {
  const c = 2 * Math.PI * r;
  const f = Math.min(1, Math.max(0, fraction || 0));
  return `${(c * f).toFixed(2)} ${c.toFixed(2)}`;
}

/**
 * Durata (s) di un giro della freccia: più potenza, più veloce. Arrotondata a 0,5 s perché la durata cambia
 * solo a scatti e le animazioni non ripartono a ogni aggiornamento.
 */
function arrowDuration(length: number, watts: number): number {
  const w = Math.min(watts, 6000);
  const speed = 45 + w * 0.025; // unità SVG al secondo: da 45 (quasi fermo) a 195 (6.000 W)
  return Math.max(0.5, Math.round((length / speed) * 2) / 2);
}

function routeLength(points: Pt[]): number {
  let total = 0;
  for (let i = 1; i < points.length; i++) total += Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y);
  return total;
}

/** Spezzata con gli angoli arrotondati. */
function roundedPath(points: Pt[], radius: number): string {
  let d = `M${points[0].x},${points[0].y}`;
  for (let i = 1; i < points.length - 1; i++) {
    const [prev, p, next] = [points[i - 1], points[i], points[i + 1]];
    const d1 = Math.min(radius, Math.hypot(p.x - prev.x, p.y - prev.y) / 2);
    const d2 = Math.min(radius, Math.hypot(next.x - p.x, next.y - p.y) / 2);
    const a = toward(p, prev, d1);
    const b = toward(p, next, d2);
    d += ` L${a.x},${a.y} Q${p.x},${p.y} ${b.x},${b.y}`;
  }
  const last = points[points.length - 1];
  return `${d} L${last.x},${last.y}`;
}

function toward(from: Pt, to: Pt, distance: number): Pt {
  const len = Math.hypot(to.x - from.x, to.y - from.y) || 1;
  return { x: +(from.x + ((to.x - from.x) / len) * distance).toFixed(2), y: +(from.y + ((to.y - from.y) / len) * distance).toFixed(2) };
}
