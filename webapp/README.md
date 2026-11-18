# webapp

Interfaccia web in tema scuro per l'impianto, fatta con Angular 22, Angular Material e Chart.js.

| Pagina                         | Contenuto |
|--------------------------------|-----------|
| **Inverter · Dati attuali**    | Flussi di potenza (FV, batteria, rete, casa, backup), energia del giorno, identificazione, stato e guasti. Sotto, tutti i registri per categoria, con filtro e indicazione ufficiale / non ufficiale / riservato. Si aggiorna ogni 10 s |
| **Inverter · Storico**         | Grafico di fino a 4 registri dal database (scorciatoie: flussi di potenza, batteria, stringhe, energia, temperature), con periodi predefiniti o personalizzati e riepilogo min/media/max |
| **Meteo · Previsioni**         | Condizioni attuali, prossime 24 ore, grafico a 72 ore, irraggiamento previsto su ogni falda, 7 giorni e qualità dell'aria |
| **Meteo · Storico**            | Grandezze orarie salvate (previsioni, irraggiamento per falda, archivio ERA5), stato del database, raccolta immediata e import dall'archivio |
| **Regole e consigli**          | Consiglio attuale con l'esito di ogni regola. Elenco delle regole con interruttore per attivarle e disattivarle, creazione, modifica, duplicazione ed eliminazione. I campi dei parametri sono generati dal tipo di regola, con unità, limiti e valore predefinito. Mostra anche i tipi di regola disponibili, le impostazioni dell'impianto modificabili e gli ultimi consigli |

## Avvio con Docker

Dalla cartella principale, insieme agli altri servizi:

```bash
docker compose up -d --build
```

Poi apri <http://localhost:10088>. La porta si cambia con `WEBAPP_HTTP_PORT`.

Il container usa nginx. Serve l'app e inoltra le chiamate API ai servizi:

| Percorso | Servizio |
|----------|----------|
| `/api/inverter/*` | `inverter` |
| `/api/meteo/*` | `openmeteo` |
| `/api/advice`, `/api/rules`, `/api/settings`, `/api/history` | `advisor` |

Gli indirizzi dei servizi si cambiano con `WEBAPP_INVERTER_URL`, `WEBAPP_OPENMETEO_URL` e `WEBAPP_ADVISOR_URL`.
nginx parte anche se un servizio non è ancora avviato; le pagine che ne dipendono mostrano l'errore finché il
servizio non risponde.

I font (Roboto e Material Symbols) arrivano da Google Fonts: senza Internet l'app funziona, ma le icone appaiono
come testo.

## Build e sviluppo senza Node di sistema

Non serve installare Node.js sul PC. Ci sono due modi, entrambi con la stessa versione di Node (22.23.4).

**Con Maven.** Il `pom.xml` usa `frontend-maven-plugin`, che installa Node e npm nella cartella `node/` del
progetto (ignorata da git; il pacchetto scaricato resta nella cache di Maven). Poi esegue `npm ci` e la build:

```bash
mvn -P -IC package          # -> dist/webapp/browser
./npmw start                # ng serve su http://localhost:4200, con il Node del progetto
./npmw install <pacchetto>  # qualunque comando npm
```

**Con Docker.** Il servizio `webapp-dev` esegue `ng serve` in un container `node:22.23.4-alpine`, con i sorgenti
montati: le modifiche si vedono subito nel browser.

```bash
docker compose --profile dev up webapp-dev    # dalla cartella principale -> http://localhost:10089
```

In entrambi i casi il proxy di sviluppo (`proxy.conf.mjs`) inoltra le API ai servizi. Per default punta ai
container sull'host:

| Servizio | Indirizzo |
|----------|-----------|
| inverter | `localhost:10080` |
| openmeteo | `localhost:10081` |
| advisor | `localhost:10082` |

Nel container di sviluppo punta invece ai nomi dei servizi Docker. Gli indirizzi si cambiano con le variabili
d'ambiente `INVERTER_URL`, `OPENMETEO_URL` e `ADVISOR_URL`.

Anche il `Dockerfile` compila l'app dentro un container Node (build multi-stage), quindi neanche
`docker compose up --build` richiede Node sul PC.
