# energy-advisor

Consiglia se **caricare la batteria dalla rete**, **conservarla**, **vendere** o lasciare l'**autoconsumo** normale.
Usa i dati dei servizi `inverter` (stato attuale) e `openmeteo` (irraggiamento previsto sul piano di ogni stringa)
e valuta un insieme di regole configurabili. Dà solo consigli: l'inverter non viene comandato.

Spring Boot 4, Java 25, virtual threads. I dati dei due servizi vengono letti in parallelo, un virtual thread per
servizio.

## Avvio con Docker

Dalla cartella principale, insieme agli altri servizi:

```bash
docker compose up -d --build
```

Swagger UI: <http://localhost:10082/swagger-ui.html>

Regole, impostazioni e storico sono nel database TimescaleDB del progetto `database`. Se il database non è ancora
pronto, advisor si ferma all'avvio e Docker lo riavvia finché il database non risponde.

## Come funziona

Tutte le potenze sono in W e tutte le energie in Wh: impostazioni, previsioni, parametri delle regole e risposte
dell'API. All'avvio, le impostazioni e le regole salvate con le versioni precedenti in kW e kWh vengono convertite
automaticamente (es. `maxForecastKwh: 6` diventa `maxForecastWh: 6000`).

1. **Contesto.** Per ogni valutazione l'app legge:
   - da `inverter` lo stato attuale: SOC, produzione FV, consumi, rete;
   - da `openmeteo` l'irraggiamento orario su ogni falda (10° e 17°).

   Per ogni stringa somma il contributo delle falde su cui stanno i suoi pannelli:
   `Wh = irraggiamento / 1000 × pannelli × W di picco × performance ratio`.
   Ne ricava la produzione di oggi, quella che resta per oggi e quella di domani, in totale e per stringa
   (`forecast.byString`, confrontabile con PV1/PV2 dell'inverter).
2. **Regole.** Le regole abilitate vengono valutate in ordine di `priority` crescente. Decide la prima soddisfatta.
   Se nessuna è soddisfatta, il consiglio è `SELF_CONSUMPTION`. Ogni regola riporta il motivo dell'esito, anche
   quando non scatta.
3. **Dati mancanti.** Se un servizio non risponde, le regole che ne hanno bisogno non scattano e il problema
   compare in `context.warnings`.
4. **Controllo periodico.** Le regole vengono valutate anche in background, ogni `advisor.check-interval`
   (default 5 minuti). Ogni consiglio viene salvato nella tabella `advice_log`; i cambi di consiglio finiscono
   anche nel log.
5. **Storico.** In background vengono salvati nel database:
   - tutti i registri dell'inverter, ogni minuto (`advisor.history.inverter-interval`);
   - la previsione Open-Meteo corrente e l'irraggiamento per falda, ogni ora (`advisor.history.weather-interval`),
     compreso il giorno precedente (`past-days`), che così converge al dato analizzato (`weather_hourly`);
   - l'archivio delle previsioni (`weather_forecast_run`), 6 volte al giorno (`advisor.history.forecast-runs`):
     un'ora prima dell'alba, due ore dopo l'alba, a mezzogiorno solare, tre ore prima del tramonto, al tramonto e
     alle 21:00 per il giorno dopo. Alba e tramonto vengono da Open-Meteo e sono salvati in `weather_hourly`
     (`source = daily`, `series = sun`). Un orario saltato perché il servizio era fermo viene recuperato al
     controllo successivo (ogni 5 minuti).

   Lo storico meteo precedente si importa dall'archivio ERA5 con `POST /api/history/weather/backfill`. La raccolta
   si disattiva con `advisor.history.enabled=false`.

Regole e impostazioni sono nelle tabelle `rule` e `site_settings` e vengono rilette a ogni valutazione. Si
possono quindi modificare via API oppure direttamente con SQL, ad esempio
`UPDATE rule SET enabled = false WHERE id = '...'`. Al primo avvio, con il database vuoto, vengono inserite le
impostazioni di `advisor.defaults` e le regole di esempio.

## Endpoint

| Metodo e percorso              | Descrizione                                                   |
|--------------------------------|---------------------------------------------------------------|
| `GET /api/advice`              | Consiglio attuale, con l'esito di ogni regola e i dati usati  |
| `GET /api/advice/last`         | Ultimo consiglio calcolato, senza nuove letture               |
| `GET /api/advice/context`      | Solo i dati: inverter, produzione stimata, impostazioni       |
| `POST /api/advice/simulate`    | Valuta le regole su dati inventati (vedi esempio)             |
| `GET /api/rules/types`         | Tipi di regola disponibili, con parametri, default e limiti   |
| `GET /api/rules`               | Regole configurate                                            |
| `GET /api/rules/{id}`          | Una regola                                                    |
| `POST /api/rules`              | Aggiunge una regola                                           |
| `PUT /api/rules/{id}`          | Sostituisce una regola (i parametri omessi tornano al default) |
| `PATCH /api/rules/{id}`        | Cambia nome, abilitazione, priorità o singoli parametri       |
| `DELETE /api/rules/{id}`       | Rimuove una regola                                            |
| `GET`/`PUT /api/settings`      | Impostazioni dell'impianto: capacità batterie, performance ratio, potenza pannello, pannelli per stringa e falda |
| `GET /api/history/status`      | Ultimo dato salvato e righe delle ultime 24 ore per tabella   |
| `POST /api/history/collect`    | Salva subito un campione di inverter e meteo                  |
| `POST /api/history/weather/backfill?start=&end=` | Importa lo storico meteo orario dall'archivio ERA5 (date `yyyy-MM-dd`) |
| `GET /api/history/inverter/{register}?from=&to=` | Storico di un registro (es. `BATTERY_SOC`), default ultime 24 ore |
| `GET /api/history/weather?source=&series=&variable=&from=&to=` | Storico orario di una grandezza Open-Meteo |
| `GET /api/history/advice`      | Consigli registrati dal controllo periodico                   |
| `GET /api/history/forecast-accuracy?from=&to=&run=` | Produzione reale confrontata con la stima da Open-Meteo (vedi sotto) |

Esempi:

```bash
# alzare l'obiettivo di carica notturna al 90%
curl -X PATCH localhost:10082/api/rules/carica-notturna -H 'Content-Type: application/json' \
     -d '{"parameters": {"targetSoc": 90}}'

# aggiungere una seconda regola di vendita più tardi
curl -X POST localhost:10082/api/rules -H 'Content-Type: application/json' \
     -d '{"id": "vendi-tardi", "name": "Vendi tardi", "type": "EVENING_PEAK_SELL", "priority": 40,
          "parameters": {"windowStart": "21:00", "windowEnd": "23:00"}}'

# disattivare una regola senza cancellarla
curl -X PATCH localhost:10082/api/rules/conserva-batteria -H 'Content-Type: application/json' -d '{"enabled": false}'

# cosa succederebbe stanotte alle 23:30 con SOC 25% e 3.500 Wh previsti domani?
curl -X POST localhost:10082/api/advice/simulate -H 'Content-Type: application/json' \
     -d '{"time": "2026-11-15T23:30:00", "inverter": {"batterySoc": 25},
          "forecast": {"todayWh": 0, "todayRemainingWh": 0, "tomorrowWh": 3500}}'
```

I parametri vengono controllati: un nome sconosciuto, un tipo sbagliato o un valore fuori dai limiti danno `400`
con il motivo.

## Affidabilità delle previsioni

`GET /api/history/forecast-accuracy` (pagina **Meteo → Affidabilità** della webapp) dice quanto ci si può fidare
delle previsioni Open-Meteo per costruire regole. Per ogni ora del periodo (default: le ultime 24 ore complete,
massimo 92 giorni) confronta la produzione FV reale (media di `PV_TOTAL_POWER`) con due stime, calcolate con la
stessa formula delle regole:

- **previsione:** l'irraggiamento come era previsto prima dell'ora, preso da `weather_forecast_run`. Con `run` si
  sceglie quale previsione archiviata: `LATEST` = ultima disponibile prima dell'ora, `PRE_DAWN` = scaricata prima
  dell'alba del giorno, `EVENING` = scaricata la sera prima (come la usa la regola di carica notturna);
- **a posteriori:** l'irraggiamento analizzato dopo i fatti (`weather_hourly`). Se sbaglia anche questa, l'errore
  è nel modello dell'impianto (performance ratio, falde, ombre) e non nella previsione. In quel caso la risposta
  suggerisce un `performanceRatio`.

Esclude le ore con letture dell'inverter incomplete e quelle con la batteria carica (SOC medio ≥ 97%), in cui
l'inverter può aver limitato la produzione. Restituisce:

- l'errore sul totale (quello che conta per le regole, che ragionano su Wh giornalieri) e l'errore orario medio;
- un giudizio: `AFFIDABILE` (totale entro ±15%, orario ≤ 35%), `DISCRETA` (entro ±30%, ≤ 60%), `INAFFIDABILE`,
  oppure `DATI_INSUFFICIENTI` (meno di 4 ore di sole o di 500 Wh);
- i totali per giorno e per stringa (reale per stringa = tensione × corrente), il dettaglio orario e delle note.

Lo storico delle previsioni parte da quando advisor le raccoglie: per un giudizio utile servono almeno 7 giorni.

## Regole di esempio

Al primo avvio vengono create tre regole. Si possono modificare o cancellare.

| id                   | Tipo                       | Azione             | Scatta quando (parametri di default) |
|----------------------|----------------------------|--------------------|--------------------------------------|
| `carica-notturna`    | `LOW_FORECAST_GRID_CHARGE` | `CHARGE_FROM_GRID` | Tra 23:00 e 07:00 (`windowStart`/`windowEnd`), se la produzione prevista per la prossima giornata è sotto 6.000 Wh (`maxForecastWh`) e il SOC è sotto l'80% (`targetSoc`) |
| `conserva-batteria`  | `LOW_FORECAST_HOLD`        | `HOLD_BATTERY`     | Tra 08:00 e 19:00, se restano meno di 3.000 Wh di sole oggi (`maxRemainingWh`) e il SOC è sotto il 60% (`maxSoc`) |
| `vendi-picco-serale` | `EVENING_PEAK_SELL`        | `SELL_TO_GRID`     | Tra 19:00 e 22:00, se il SOC supera il 70% (`minSoc`) e domani sono previsti almeno 10.000 Wh (`minTomorrowWh`). Vende fino al 40% (`reserveSoc`) |

Per "prossima giornata" la regola di carica notturna intende il resto di oggi se è prima di mezzogiorno, altrimenti
domani.

## Aggiungere un nuovo tipo di regola

Si crea in `it.solis.advisor.rules.types` una classe `@Component` che implementa `RuleType`, indicando:

- `id()`;
- l'azione;
- i parametri (`ParameterDefinition.number`/`integer`/`time`/`bool`, con default e limiti);
- `evaluate(context, parameters)`.

Viene registrata da sola, compare in `/api/rules/types` e si possono creare regole di quel tipo via API.

## Configurazione

| Proprietà                       | Variabile                  | Default                   |
|---------------------------------|----------------------------|---------------------------|
| `advisor.inverter-url`          | `ADVISOR_INVERTER_URL`     | `http://localhost:10080` (Docker: `http://inverter:8080`) |
| `advisor.openmeteo-url`         | `ADVISOR_OPENMETEO_URL`    | `http://localhost:10081` (Docker: `http://openmeteo:8080`) |
| `spring.datasource.url`         | `SPRING_DATASOURCE_URL`    | `jdbc:postgresql://localhost:15432/solis` (Docker: `...//database:5432/solis`) |
| `spring.datasource.username`/`password` | `POSTGRES_USER`/`POSTGRES_PASSWORD` in Docker | `solis`/`solis` |
| `advisor.history.enabled`       | `ADVISOR_HISTORY_ENABLED`  | `true`                    |
| `advisor.history.inverter-interval` | —                      | `PT1M`                    |
| `advisor.history.weather-interval`  | —                      | `PT1H`                    |
| `advisor.history.forecast-runs.*`   | —                      | Controllo ogni `PT5M`; sera `21:00`; `PT1H` prima dell'alba, `PT2H` dopo l'alba, `PT3H` prima del tramonto |
| `advisor.history.*-variables`   | —                          | Grandezze Open-Meteo salvate (vedi `application.yml`) |
| `advisor.check-interval`        | `ADVISOR_CHECK_INTERVAL`   | `PT5M`                    |
| `advisor.timezone`              | `ADVISOR_TIMEZONE`         | `Europe/Rome`             |
| `advisor.defaults.*`            | —                          | Impostazioni dell'impianto al primo avvio (vedi sotto) |
| porta host (Docker)             | `ADVISOR_HTTP_PORT`        | 10082                     |

`advisor.defaults` serve solo al primo avvio, con il database vuoto. Dopo il primo avvio le impostazioni si cambiano con
`PUT /api/settings`. I valori predefiniti descrivono l'impianto attuale:

```json
{
  "batteryCapacityWh": 20000,
  "performanceRatio": 0.8,
  "panelPeakPowerW": 615,
  "inverter": {"maxPvInputW": 9600, "ratedOutputW": 6000, "maxBatteryPowerW": 6000},
  "strings": {
    "pv1": {"falda10": 7, "falda17": 2},
    "pv2": {"falda17": 10}
  }
}
```

- **Batterie:** due da 10.000 Wh.
- **Pannelli:** 19 Trina bifacciali da 615 W, in totale 11.685 W di picco.
  - Stringa 1: 9 pannelli, 5.535 W di picco.
  - Stringa 2: 10 pannelli, 6.150 W di picco.
- **Falde:** i nomi sono quelli definiti nel servizio openmeteo (`openmeteo.planes`).
- **Inverter:** i limiti sono quelli della scheda tecnica Solis S6-EH1P6K-L-PLUS (Global V2.0, giugno 2024):
  - 9.600 W di potenza FV massima utilizzabile: oltre, la stima della produzione oraria viene tagliata;
  - 6.000 W di uscita AC (casa più immissione in rete): la produzione che supera questo valore può andare solo
    in batteria, e la previsione la riporta in `todayRemainingAboveAcWh` e `tomorrowAboveAcWh`;
  - 6.000 W di carica e scarica della batteria: le regole lo usano per stimare quante ore servono.
- **Guadagno del lato posteriore** dei pannelli bifacciali: non è calcolato a parte. Se la stima risulta bassa
  rispetto alla produzione reale, si può alzare `performanceRatio`.

Senza Docker:

```bash
mvn -P -IC package
java -jar target/energy-advisor-1.0.0-SNAPSHOT.jar --server.port=10082
```

Senza Docker serve comunque il database, raggiungibile su `localhost:15432` (il container del progetto `database`
espone quella porta).
