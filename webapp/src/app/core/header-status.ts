import { DestroyRef, inject, Injectable, signal } from '@angular/core';

/**
 * Testo breve che la pagina corrente mostra nella barra in alto su schermo piccolo
 * (es. orario dell'ultima lettura e intervallo di aggiornamento: "07:03:57 (10 s)").
 */
@Injectable({ providedIn: 'root' })
export class HeaderStatus {
  readonly text = signal<string | null>(null);

  /** Da chiamare nel costruttore della pagina: il testo sparisce quando si lascia la pagina. */
  clearOnDestroy(destroyRef = inject(DestroyRef)): void {
    destroyRef.onDestroy(() => this.text.set(null));
  }
}
