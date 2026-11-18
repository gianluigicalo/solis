import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'inverter' },
  { path: 'inverter', title: 'Inverter', loadComponent: () => import('./pages/inverter/inverter-page').then((m) => m.InverterPage) },
  { path: 'inverter/storico', title: 'Storico inverter', loadComponent: () => import('./pages/inverter-history/inverter-history-page').then((m) => m.InverterHistoryPage) },
  { path: 'registri', title: 'Registri inverter', loadComponent: () => import('./pages/registers/registers-page').then((m) => m.RegistersPage) },
  { path: 'meteo', title: 'Meteo', loadComponent: () => import('./pages/meteo/meteo-page').then((m) => m.MeteoPage) },
  { path: 'meteo/storico', title: 'Storico meteo', loadComponent: () => import('./pages/meteo-history/meteo-history-page').then((m) => m.MeteoHistoryPage) },
  { path: 'meteo/affidabilita', title: 'Affidabilità previsioni', loadComponent: () => import('./pages/forecast-accuracy/forecast-accuracy-page').then((m) => m.ForecastAccuracyPage) },
  { path: 'regole', title: 'Regole e consigli', loadComponent: () => import('./pages/rules/rules-page').then((m) => m.RulesPage) },
  { path: '**', redirectTo: 'inverter' },
];
