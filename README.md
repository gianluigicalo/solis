# solis

Monitoraggio e consigli energetici per l'impianto con inverter ibrido Solis S6-EH1P6K-L-PLUS.

```bash
docker compose up -d --build                   # tutti i servizi
docker compose --profile dev up webapp-dev     # in più: webapp in sviluppo con ricarica automatica
```

| Servizio    | Cartella     | Porta sull'host | Contenuto |
|-------------|--------------|-----------------|-----------|
| `webapp`    | `webapp/`    | **10088**       | Interfaccia web (Angular, tema scuro) |
| `inverter`  | `inverter/`  | 10080           | API dei dati dell'inverter via Modbus TCP, Swagger su `/swagger-ui.html` |
| `openmeteo` | `openmeteo/` | 10081           | API Open-Meteo sulle coordinate dell'impianto, Swagger su `/swagger-ui.html` |
| `advisor`   | `advisor/`   | 10082           | Regole, consigli carica/vendita, raccolta dello storico, Swagger su `/swagger-ui.html` |
| `database`  | `database/`  | 15432           | PostgreSQL 17 + TimescaleDB: storico, regole, impostazioni |
| `webapp-dev`| `webapp/`    | 10089           | Solo con `--profile dev`: `ng serve` dentro un container Node |

Le porte sull'host si cambiano con le variabili `WEBAPP_HTTP_PORT`, `INVERTER_HTTP_PORT`, `OPENMETEO_HTTP_PORT`,
`ADVISOR_HTTP_PORT`, `DATABASE_PORT` e `WEBAPP_DEV_PORT`, ad esempio in un file `.env` in questa cartella.
Nello stesso file vanno anche le credenziali del database (`POSTGRES_PASSWORD`, ...).

Dentro la rete Docker i servizi si raggiungono con il nome del servizio sulla porta 8080 (il database sulla 5432).

## Accesso

- **Webapp (10088):** è l'unico servizio raggiungibile **da tutti gli indirizzi**, IPv4 e IPv6
  (`http://<ip-del-pc>:10088`). Per limitarla a questa macchina: `WEBAPP_BIND_ADDRESS=127.0.0.1` nel file `.env`.
- **Tutti gli altri servizi** (inverter, openmeteo, advisor, database, webapp-dev): pubblicati solo su `127.0.0.1` e
  `::1`, quindi raggiungibili **solo da questa macchina**. È più sicuro che affidarsi al firewall, perché le porte
  pubblicate da Docker scavalcano le regole di ufw/iptables dell'host.

Attenzione: la webapp inoltra le chiamate API ai servizi. Chi la raggiunge può quindi anche modificare o cancellare
regole e impostazioni, senza autenticazione.

- I servizi continuano a parlarsi tra loro nella rete interna di Docker. Anche le connessioni in uscita, verso
  l'inverter e verso Open-Meteo, non cambiano.
- Per aprire anche gli altri servizi alla rete locale: `BIND_ADDRESS=0.0.0.0` e `BIND_ADDRESS6=[::]` nel file
  `.env` (o indirizzi specifici dell'host). In quel caso **cambia la password del database**.

## IPv6

- La webapp è pubblicata su tutti gli indirizzi IPv6 (`http://[indirizzo-ipv6-del-pc]:10088`). Le altre porte
  sono pubblicate anche su `::1`, quindi `localhost` funziona anche quando risolve in IPv6.
- La rete interna comune ai servizi (`solis_default`, definita in `compose.yaml`) ha `enable_ipv6: true`:
  - Docker (dalla versione 27) le assegna da solo una subnet ULA `fd…` e fa il NAT verso l'esterno, senza
    modifiche a `/etc/docker/daemon.json`;
  - i container hanno un indirizzo IPv4 e uno IPv6 e possono raggiungere Open-Meteo anche in IPv6;
  - l'inverter resta raggiungibile sul suo indirizzo IPv4.
- nginx (webapp) e `ng serve` (webapp-dev) ascoltano anche su IPv6. Le app Spring Boot e PostgreSQL lo fanno già.
- Se la rete esisteva già senza IPv6, va ricreata: `docker compose down && docker compose up -d` (i dati del
  database restano nel volume).
- La rete con IPv6 c'è solo avviando dal compose principale; un progetto avviato da solo usa IPv4 nella
  propria rete. Le porte su `::1` funzionano comunque.
- Se avvii un progetto da solo e lo fai puntare agli altri tramite `host.docker.internal`, quelle porte devono
  essere aperte almeno verso la rete Docker: con l'accesso solo locale avvia tutto dal compose principale.

Build senza Docker:
- progetti Java: `mvn -P -IC package` nella cartella del progetto;
- webapp: `mvn -P -IC package` installa un Node.js nella cartella del progetto e compila l'app.

Ogni cartella ha il suo README.
# solis
