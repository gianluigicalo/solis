import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher, ShowOnDirtyErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ACTION_LABELS } from '../../core/format';
import { ParameterDefinition, ParameterValue, RuleDefinition, RuleRequest, RuleType } from '../../core/models';

export interface RuleDialogData {
  /** regola da modificare; null = nuova regola (eventualmente copiata da {@link template}) */
  rule: RuleDefinition | null;
  template?: RuleDefinition | null;
  types: RuleType[];
}

/** Creazione e modifica di una regola. I campi dei parametri sono generati dalla definizione del tipo. */
@Component({
  selector: 'app-rule-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatButtonModule, MatTooltipModule],
  // errori visibili mentre si scrive, non solo uscendo dal campo
  providers: [{ provide: ErrorStateMatcher, useClass: ShowOnDirtyErrorStateMatcher }],
  template: `
    <h2 mat-dialog-title>{{ editing ? 'Modifica regola' : 'Nuova regola' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form">
        <div class="row2">
          <mat-form-field>
            <mat-label>Tipo</mat-label>
            <mat-select formControlName="type">
              @for (t of data.types; track t.id) { <mat-option [value]="t.id">{{ t.id }}</mat-option> }
            </mat-select>
            @if (editing) { <mat-hint>Il tipo non si può cambiare</mat-hint> }
          </mat-form-field>
          <mat-form-field>
            <mat-label>Id</mat-label>
            <input matInput formControlName="id" placeholder="generato se vuoto">
            <mat-hint>minuscole, cifre e trattini</mat-hint>
            @if (form.controls.id.hasError('pattern')) { <mat-error>Solo minuscole, cifre e trattini</mat-error> }
          </mat-form-field>
        </div>

        @if (type(); as t) {
          <p class="type-desc">
            <span class="badge" [class]="action(t).css">{{ action(t).label }}</span>
            {{ t.description }}
          </p>
        }

        <mat-form-field class="full">
          <mat-label>Nome</mat-label>
          <input matInput formControlName="name" required>
          @if (form.controls.name.hasError('required')) { <mat-error>Obbligatorio</mat-error> }
        </mat-form-field>

        <div class="row2">
          <mat-form-field>
            <mat-label>Priorità</mat-label>
            <input matInput type="number" formControlName="priority" step="1">
            <mat-hint>minore = valutata prima</mat-hint>
          </mat-form-field>
          <mat-slide-toggle formControlName="enabled">Attiva</mat-slide-toggle>
        </div>

        @if (type(); as t) {
          <h3>Parametri</h3>
          <div class="params" [formGroup]="params()">
            @for (p of t.parameters; track p.name) {
              @switch (p.type) {
                @case ('BOOLEAN') {
                  <div class="param-toggle">
                    <mat-slide-toggle [formControlName]="p.name">{{ p.name }}</mat-slide-toggle>
                    <small class="muted">{{ p.description }}</small>
                  </div>
                }
                @case ('TIME') {
                  <mat-form-field>
                    <mat-label>{{ p.name }}</mat-label>
                    <input matInput type="time" [formControlName]="p.name" [name]="p.name" required>
                    <mat-hint [matTooltip]="p.description">{{ p.description }}</mat-hint>
                    <button matSuffix mat-icon-button type="button" (click)="reset(p)" matTooltip="Valore di default: {{ p.defaultValue }}">
                      <span class="material-symbols-outlined">restart_alt</span>
                    </button>
                  </mat-form-field>
                }
                @default {
                  <mat-form-field>
                    <mat-label>{{ p.name }}</mat-label>
                    <input matInput type="number" [formControlName]="p.name" [name]="p.name" [min]="p.min ?? null" [max]="p.max ?? null"
                           [step]="p.type === 'INTEGER' ? 1 : p.unit === 'Wh' || p.unit === 'W' ? 100 : 0.1" required>
                    @if (p.unit) { <span matTextSuffix>{{ p.unit }}</span> }
                    <button matSuffix mat-icon-button type="button" (click)="reset(p)" matTooltip="Valore di default: {{ p.defaultValue }}">
                      <span class="material-symbols-outlined">restart_alt</span>
                    </button>
                    <mat-hint [matTooltip]="p.description">{{ p.description }}</mat-hint>
                    @if (params().get(p.name)?.invalid) {
                      <mat-error>Valore tra {{ p.min }} e {{ p.max }}{{ p.type === 'INTEGER' ? ', intero' : '' }}</mat-error>
                    }
                  </mat-form-field>
                }
              }
            }
          </div>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annulla</button>
      <button mat-flat-button (click)="save()" [disabled]="form.invalid || params().invalid">Salva</button>
    </mat-dialog-actions>`,
  styles: `
    .form { display: flex; flex-direction: column; gap: 6px; min-width: min(640px, 80vw); padding-top: 6px; }
    .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; align-items: center; }
    .params { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 12px; }
    .param-toggle { display: flex; flex-direction: column; gap: 4px; }
    .type-desc { color: var(--muted); font-size: 13px; margin: 0 0 8px; line-height: 1.5; }
    h3 { font: var(--mat-sys-title-small); margin: 12px 0 4px; }`,
})
export class RuleDialog {
  protected readonly data = inject<RuleDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RuleDialog, RuleRequest>);
  protected readonly editing = this.data.rule !== null;

  private readonly source = this.data.rule ?? this.data.template ?? null;

  protected readonly form = new FormGroup({
    id: new FormControl({ value: this.data.rule?.id ?? '', disabled: this.editing },
      { nonNullable: true, validators: Validators.pattern(/^[a-z0-9][a-z0-9-]{0,63}$/) }),
    name: new FormControl(this.source ? (this.data.rule ? this.source.name : `${this.source.name} (copia)`) : '',
      { nonNullable: true, validators: Validators.required }),
    type: new FormControl({ value: this.source?.type ?? this.data.types[0]?.id ?? '', disabled: this.editing },
      { nonNullable: true, validators: Validators.required }),
    priority: new FormControl(this.source?.priority ?? 100, { nonNullable: true, validators: Validators.required }),
    enabled: new FormControl(this.source?.enabled ?? true, { nonNullable: true }),
  });

  private readonly typeId = toSignal(this.form.controls.type.valueChanges, { initialValue: this.form.controls.type.value });
  protected readonly type = computed(() => this.data.types.find((t) => t.id === this.typeId()) ?? null);
  protected readonly params = signal(new FormGroup<Record<string, FormControl<ParameterValue>>>({}));

  constructor() {
    this.buildParams(this.type(), this.source?.parameters ?? {});
    this.form.controls.type.valueChanges.subscribe((id) =>
      this.buildParams(this.data.types.find((t) => t.id === id) ?? null, {}));
  }

  protected action(t: RuleType) {
    return ACTION_LABELS[t.action];
  }

  protected reset(p: ParameterDefinition): void {
    this.params().get(p.name)?.setValue(p.defaultValue);
  }

  protected save(): void {
    const t = this.type();
    if (!t || this.form.invalid || this.params().invalid) return;
    const raw = this.form.getRawValue();
    const values = this.params().getRawValue();
    const parameters: Record<string, ParameterValue> = {};
    for (const p of t.parameters) {
      const v = values[p.name];
      parameters[p.name] = p.type === 'NUMBER' || p.type === 'INTEGER' ? Number(v) : v;
    }
    this.ref.close({
      id: this.editing ? this.data.rule!.id : raw.id || undefined,
      name: raw.name.trim(),
      type: raw.type,
      enabled: raw.enabled,
      priority: Number(raw.priority),
      parameters,
    });
  }

  private buildParams(type: RuleType | null, values: Record<string, ParameterValue>): void {
    const controls: Record<string, FormControl<ParameterValue>> = {};
    for (const p of type?.parameters ?? []) {
      controls[p.name] = new FormControl<ParameterValue>(values[p.name] ?? p.defaultValue,
        { nonNullable: true, validators: validators(p) });
    }
    this.params.set(new FormGroup(controls));
  }
}

function validators(p: ParameterDefinition): ValidatorFn[] {
  const list: ValidatorFn[] = [];
  if (p.type !== 'BOOLEAN') list.push(Validators.required);
  if (p.min !== undefined) list.push(Validators.min(p.min));
  if (p.max !== undefined) list.push(Validators.max(p.max));
  if (p.type === 'INTEGER') list.push(Validators.pattern(/^-?\d+$/));
  return list;
}
