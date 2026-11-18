import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { appTitle } from './core/app-title';
import { HeaderStatus } from './core/header-status';

/** Sotto questa larghezza il menu diventa a scomparsa (smartphone, tablet in verticale). */
const COMPACT_QUERY = '(max-width: 899.98px)';

interface NavItem { path: string; label: string; icon: string; exact?: boolean; }

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule, MatToolbarModule, MatButtonModule],
  template: `
    <mat-sidenav-container class="shell">
      <!-- schermo largo: menu fisso; schermo piccolo: menu sopra il contenuto, aperto con il pulsante -->
      <mat-sidenav #nav class="nav" [mode]="compact() ? 'over' : 'side'" [opened]="!compact()"
                   [fixedInViewport]="compact()" [autoFocus]="false">
        <div class="brand">
          <span class="material-symbols-outlined">solar_power</span>
          <div><strong>Solis Energy</strong><small>S6-EH1P6K-L-PLUS</small></div>
        </div>
        @for (group of groups; track group.title) {
          <div class="group">{{ group.title }}</div>
          <mat-nav-list>
            @for (item of group.items; track item.path) {
              <a mat-list-item [routerLink]="item.path" routerLinkActive #rla="routerLinkActive"
                 [routerLinkActiveOptions]="{ exact: item.exact ?? false }" [activated]="rla.isActive"
                 (click)="compact() && nav.close()">
                <span matListItemIcon class="material-symbols-outlined">{{ item.icon }}</span>
                <span matListItemTitle>{{ item.label }}</span>
              </a>
            }
          </mat-nav-list>
        }
      </mat-sidenav>
      <mat-sidenav-content>
        @if (compact()) {
          <mat-toolbar class="topbar">
            <button mat-icon-button (click)="nav.toggle()" aria-label="Menu">
              <span class="material-symbols-outlined">menu</span>
            </button>
            <span class="material-symbols-outlined logo">solar_power</span>
            <span class="title">{{ pageTitle() }}</span>
            <span class="spacer"></span>
            @if (status(); as st) { <span class="status">{{ st }}</span> }
          </mat-toolbar>
        }
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>`,
  styles: `
    .shell { height: 100vh; }
    .nav { width: 230px; max-width: 80vw; background: var(--mat-sys-surface-container-low); border-right: 1px solid var(--line); }
    .brand { display: flex; gap: 12px; align-items: center; padding: 20px 18px 8px; }
    .brand .material-symbols-outlined { font-size: 34px; color: var(--mat-sys-tertiary); }
    .brand small { display: block; color: var(--muted); font-size: 11px; }
    .topbar {
      position: sticky; top: 0; z-index: 2; height: 56px; gap: 8px; padding: 0 8px;
      background: var(--mat-sys-surface-container-low); border-bottom: 1px solid var(--line);
    }
    .topbar .logo { color: var(--mat-sys-tertiary); }
    .topbar .title { font: var(--mat-sys-title-medium); }
    .topbar .spacer { flex: 1; }
    .topbar .status { font: 13px Roboto, sans-serif; color: var(--muted); padding-right: 8px; font-variant-numeric: tabular-nums; }
    .group { color: var(--muted); font-size: 11px; text-transform: uppercase; letter-spacing: .08em; padding: 18px 18px 0; }`,
})
export class App {
  protected readonly compact = toSignal(
    inject(BreakpointObserver).observe(COMPACT_QUERY).pipe(map((s) => s.matches)),
    { initialValue: window.matchMedia(COMPACT_QUERY).matches },
  );

  private readonly router = inject(Router);
  /** orario dell'ultima lettura della pagina corrente, a destra nella barra */
  protected readonly status = inject(HeaderStatus).text;

  /** titolo della pagina corrente (il "title" della route), mostrato nella barra in alto su schermo piccolo */
  protected readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => routeTitle(this.router.routerState.snapshot.root)),
    ),
    { initialValue: appTitle(null) },
  );

  protected readonly groups: { title: string; items: NavItem[] }[] = [
    { title: 'Inverter', items: [
      { path: '/inverter', label: 'Dati attuali', icon: 'bolt', exact: true },
      { path: '/inverter/storico', label: 'Storico', icon: 'monitoring' },
    ] },
    { title: 'Registri', items: [
      { path: '/registri', label: 'Tutti i registri', icon: 'table_rows' },
    ] },
    { title: 'Meteo', items: [
      { path: '/meteo', label: 'Previsioni', icon: 'partly_cloudy_day', exact: true },
      { path: '/meteo/storico', label: 'Storico', icon: 'history' },
      { path: '/meteo/affidabilita', label: 'Affidabilità', icon: 'fact_check' },
    ] },
    { title: 'Advisor', items: [
      { path: '/regole', label: 'Regole e consigli', icon: 'rule' },
    ] },
  ];
}

/** "Solis <nome sezione>", come il titolo della scheda del browser */
function routeTitle(route: ActivatedRouteSnapshot): string {
  let title: string | undefined;
  for (let r: ActivatedRouteSnapshot | null = route; r; r = r.firstChild) {
    if (r.title) title = r.title;
  }
  return appTitle(title);
}
