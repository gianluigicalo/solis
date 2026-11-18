import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/** Nome dell'app, davanti al nome della sezione: "Solis Inverter", "Solis Registri inverter", ... */
export const APP_NAME = 'Solis';

/** Titolo completo di una sezione. */
export const appTitle = (section: string | undefined | null) => (section ? `${APP_NAME} ${section}` : APP_NAME);

/** Titolo della scheda del browser (e dell'app installata) sempre "Solis <nome sezione>". */
@Injectable({ providedIn: 'root' })
export class AppTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.title.setTitle(appTitle(this.buildTitle(snapshot)));
  }
}
