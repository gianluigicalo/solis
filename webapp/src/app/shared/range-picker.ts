import { Component, input, OnInit, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { toLocalInput } from '../core/format';

export interface TimeRange { from: Date; to: Date; }

const PRESETS = [
  { id: '6h', label: '6 ore', hours: 6 },
  { id: '24h', label: '24 ore', hours: 24 },
  { id: '3d', label: '3 giorni', hours: 72 },
  { id: '7d', label: '7 giorni', hours: 168 },
  { id: '30d', label: '30 giorni', hours: 720 },
];

/** Periodo da visualizzare: preset relativi ad adesso oppure intervallo personalizzato. */
@Component({
  selector: 'app-range-picker',
  imports: [FormsModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="row">
      <mat-button-toggle-group [value]="preset()" (change)="choose($event.value)" hideSingleSelectionIndicator>
        @for (p of presets; track p.id) { <mat-button-toggle [value]="p.id">{{ p.label }}</mat-button-toggle> }
        <mat-button-toggle value="custom">Personalizzato</mat-button-toggle>
      </mat-button-toggle-group>
      @if (preset() === 'custom') {
        <mat-form-field subscriptSizing="dynamic">
          <mat-label>Dal</mat-label>
          <input matInput type="datetime-local" [(ngModel)]="customFrom">
        </mat-form-field>
        <mat-form-field subscriptSizing="dynamic">
          <mat-label>Al</mat-label>
          <input matInput type="datetime-local" [(ngModel)]="customTo">
        </mat-form-field>
        <button mat-flat-button (click)="applyCustom()">Applica</button>
      }
    </div>`,
})
export class RangePicker implements OnInit {
  readonly initial = input('24h');
  readonly rangeChange = output<TimeRange>();

  protected readonly presets = PRESETS;
  protected readonly preset = signal('24h');
  protected customFrom = toLocalInput(new Date(Date.now() - 86400_000));
  protected customTo = toLocalInput(new Date());

  ngOnInit(): void {
    this.choose(this.initial());
  }

  /** Ricalcola il periodo del preset corrente rispetto ad adesso (per gli aggiornamenti). */
  current(): TimeRange {
    const p = PRESETS.find((x) => x.id === this.preset());
    if (!p) return { from: new Date(this.customFrom), to: new Date(this.customTo) };
    const to = new Date();
    return { from: new Date(to.getTime() - p.hours * 3600_000), to };
  }

  protected choose(id: string): void {
    this.preset.set(id);
    if (id !== 'custom') this.rangeChange.emit(this.current());
  }

  protected applyCustom(): void {
    this.rangeChange.emit(this.current());
  }
}
