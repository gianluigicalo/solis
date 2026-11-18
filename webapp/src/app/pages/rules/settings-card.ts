import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Api } from '../../core/api.service';
import { errorMessage, fmtInt } from '../../core/format';
import { SiteSettings } from '../../core/models';

/** Impostazioni dell'impianto usate dalle regole (PUT /api/settings). */
@Component({
  selector: 'app-settings-card',
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="card">
      <h2><span class="material-symbols-outlined">tune</span>Impostazioni dell'impianto</h2>
      @if (error(); as e) { <div class="error-box">{{ e }}</div> }
      @if (model(); as m) {
        <div class="fields">
          <mat-form-field><mat-label>Capacità batterie</mat-label><input matInput type="number" step="100" [(ngModel)]="m.batteryCapacityWh"><span matTextSuffix>Wh</span></mat-form-field>
          <mat-form-field><mat-label>Performance ratio</mat-label><input matInput type="number" step="0.01" min="0.1" max="1" [(ngModel)]="m.performanceRatio"></mat-form-field>
          <mat-form-field><mat-label>Potenza pannello</mat-label><input matInput type="number" step="5" [(ngModel)]="m.panelPeakPowerW"><span matTextSuffix>W</span></mat-form-field>
          <mat-form-field><mat-label>Potenza FV max inverter</mat-label><input matInput type="number" step="100" [(ngModel)]="m.inverter.maxPvInputW"><span matTextSuffix>W</span></mat-form-field>
          <mat-form-field><mat-label>Uscita AC inverter</mat-label><input matInput type="number" step="100" [(ngModel)]="m.inverter.ratedOutputW"><span matTextSuffix>W</span></mat-form-field>
          <mat-form-field><mat-label>Potenza batteria max</mat-label><input matInput type="number" step="100" [(ngModel)]="m.inverter.maxBatteryPowerW"><span matTextSuffix>W</span></mat-form-field>
        </div>
        <mat-form-field class="full">
          <mat-label>Pannelli per stringa e falda (JSON)</mat-label>
          <textarea matInput rows="5" [(ngModel)]="stringsJson" spellcheck="false" class="mono"></textarea>
          <mat-hint>{{ peakSummary() }}</mat-hint>
        </mat-form-field>
        <div class="row actions">
          <button mat-button (click)="load()">Annulla modifiche</button>
          <span class="spacer"></span>
          <button mat-flat-button (click)="save()" [disabled]="saving()">Salva impostazioni</button>
        </div>
      }
    </div>`,
  styles: `
    .fields { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 0 12px; }
    .mono { font-family: ui-monospace, monospace; font-size: 13px; }
    .actions { margin-top: 12px; }`,
})
export class SettingsCard implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);

  protected readonly model = signal<SiteSettings | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected stringsJson = '';

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.api.settings().subscribe({
      next: (s) => {
        this.model.set(structuredClone(s));
        this.stringsJson = JSON.stringify(s.strings, null, 2);
        this.error.set(null);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected peakSummary(): string {
    try {
      const strings = JSON.parse(this.stringsJson) as Record<string, Record<string, number>>;
      const w = this.model()?.panelPeakPowerW ?? 0;
      return Object.entries(strings)
        .map(([name, planes]) => {
          const panels = Object.values(planes).reduce((a, b) => a + b, 0);
          return `${name}: ${panels} pannelli, ${fmtInt(panels * w)} W di picco`;
        })
        .join(' · ');
    } catch {
      return 'JSON non valido';
    }
  }

  protected save(): void {
    const m = this.model();
    if (!m) return;
    let strings: SiteSettings['strings'];
    try {
      strings = JSON.parse(this.stringsJson);
    } catch {
      this.error.set('Il JSON delle stringhe non è valido');
      return;
    }
    this.saving.set(true);
    this.api.saveSettings({ ...m, strings }).subscribe({
      next: (s) => {
        this.saving.set(false);
        this.error.set(null);
        this.model.set(structuredClone(s));
        this.snack.open('Impostazioni salvate', 'OK', { duration: 3000 });
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
