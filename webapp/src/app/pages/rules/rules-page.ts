import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin, Observable } from 'rxjs';
import { Api } from '../../core/api.service';
import { ACTION_LABELS, errorMessage, fmtFullDateTime, fmtInt, fmtNumber, fmtWh } from '../../core/format';
import { Action, Advice, AdviceLogEntry, ParameterValue, RuleDefinition, RuleRequest, RuleType } from '../../core/models';
import { RuleDialog, RuleDialogData } from './rule-dialog';
import { SettingsCard } from './settings-card';

@Component({
  selector: 'app-rules-page',
  imports: [MatButtonModule, MatSlideToggleModule, MatTooltipModule, MatProgressBarModule, SettingsCard],
  templateUrl: './rules-page.html',
  styleUrl: './rules-page.scss',
})
export class RulesPage implements OnInit {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly rules = signal<RuleDefinition[]>([]);
  protected readonly types = signal<RuleType[]>([]);
  protected readonly advice = signal<Advice | null>(null);
  protected readonly history = signal<AdviceLogEntry[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly adviceError = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly fmtFullDateTime = fmtFullDateTime;
  protected readonly fmtNumber = fmtNumber;
  protected readonly fmtWh = fmtWh;
  protected readonly fmtValue = formatValue;
  protected readonly typesById = computed(() => new Map(this.types().map((t) => [t.id, t])));

  ngOnInit(): void {
    this.reload();
    this.loadAdvice();
  }

  protected reload(): void {
    this.loading.set(true);
    forkJoin({ rules: this.api.rules(), types: this.api.ruleTypes(), history: this.api.adviceHistory(20) }).subscribe({
      next: (r) => {
        this.rules.set(r.rules);
        this.types.set(r.types);
        this.history.set(r.history);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected loadAdvice(): void {
    this.api.advice().subscribe({
      next: (a) => {
        this.advice.set(a);
        this.adviceError.set(null);
      },
      error: (e) => this.adviceError.set(errorMessage(e)),
    });
  }

  protected action(a: Action | null) {
    return a ? ACTION_LABELS[a] : { label: '—', icon: 'help', css: '' };
  }

  protected typeAction(rule: RuleDefinition) {
    return this.action(this.typesById().get(rule.type)?.action ?? null);
  }

  /** Parametri con unità, per la tabella. */
  protected params(rule: RuleDefinition): { name: string; text: string; description: string }[] {
    const defs = this.typesById().get(rule.type)?.parameters ?? [];
    return Object.entries(rule.parameters).map(([name, value]) => {
      const def = defs.find((d) => d.name === name);
      return { name, text: `${formatValue(value)}${def?.unit ? ' ' + def.unit : ''}`, description: def?.description ?? '' };
    });
  }

  protected toggle(rule: RuleDefinition, enabled: boolean): void {
    this.api.patchRule(rule.id, { enabled }).subscribe({
      next: (updated) => {
        this.replaceLocal(updated);
        this.snack.open(`${updated.name}: ${enabled ? 'attivata' : 'disattivata'}`, 'OK', { duration: 2500 });
        this.loadAdvice();
      },
      error: (e) => {
        this.snack.open(errorMessage(e), 'OK', { duration: 5000 });
        this.reload();
      },
    });
  }

  protected create(template: RuleDefinition | null = null): void {
    this.openDialog({ rule: null, template, types: this.types() }, (req) => this.api.createRule(req), 'creata');
  }

  protected edit(rule: RuleDefinition): void {
    this.openDialog({ rule, types: this.types() }, (req) => this.api.replaceRule(rule.id, req), 'salvata');
  }

  protected remove(rule: RuleDefinition): void {
    if (!confirm(`Eliminare la regola "${rule.name}" (${rule.id})?`)) return;
    this.api.deleteRule(rule.id).subscribe({
      next: () => {
        this.rules.update((list) => list.filter((r) => r.id !== rule.id));
        this.snack.open('Regola eliminata', 'OK', { duration: 2500 });
        this.loadAdvice();
      },
      error: (e) => this.snack.open(errorMessage(e), 'OK', { duration: 5000 }),
    });
  }

  private openDialog(data: RuleDialogData, call: (req: RuleRequest) => Observable<RuleDefinition>, verb: string): void {
    this.dialog.open<RuleDialog, RuleDialogData, RuleRequest>(RuleDialog, { data, autoFocus: 'first-tabbable', maxWidth: '760px' })
      .afterClosed()
      .subscribe((req) => {
        if (!req) return;
        call(req).subscribe({
          next: (rule) => {
            this.snack.open(`Regola "${rule.name}" ${verb}`, 'OK', { duration: 3000 });
            this.reload();
            this.loadAdvice();
          },
          error: (e) => this.snack.open(errorMessage(e), 'OK', { duration: 6000 }),
        });
      });
  }

  private replaceLocal(rule: RuleDefinition): void {
    this.rules.update((list) => list.map((r) => (r.id === rule.id ? rule : r)));
  }
}

function formatValue(v: ParameterValue): string {
  if (typeof v === 'boolean') return v ? 'sì' : 'no';
  if (typeof v === 'number') return Number.isInteger(v) ? fmtInt(v) : fmtNumber(v, 2);
  return v;
}
