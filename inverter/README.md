# solis-modbus-reader

API REST **in sola lettura** dei dati di un inverter ibrido Solis (verificato su S6-EH1P6K-L-PLUS, modello `0x3173`)
via Modbus TCP, con [`modbus-master-tcp`](https://github.com/digitalpetri/modbus), Spring Boot 4 e Java 25.

## Avvio con Docker

```bash
cd inverter
docker compose up -d --build
docker compose logs -f
```

Il `Dockerfile` è multi-stage: compila e testa con Maven (da Maven Central) e produce un'immagine con la sola
JRE 25, eseguita da utente non privilegiato. I parametri dell'inverter si cambiano con variabili d'ambiente
(`SOLIS_HOST`, `SOLIS_PORT`, `SOLIS_UNIT_ID`, `SOLIS_TIMEOUT`, `SOLIS_CACHE_TTL`, `INVERTER_HTTP_PORT`), in un file `.env`
accanto a `compose.yaml` oppure da riga di comando: `SOLIS_HOST=192.168.1.50 docker compose up -d`.

## Build e avvio senza Docker

```bash
mvn -P -IC package
java -jar target/solis-modbus-reader-1.0.0-SNAPSHOT.jar
```

Le richieste HTTP sono servite da virtual threads (`spring.threads.virtual.enabled=true`).

Swagger UI: <http://localhost:10080/swagger-ui.html> (Docker; senza Docker porta 8080) — specifica OpenAPI: `/v3/api-docs`

## Configurazione (`src/main/resources/application.yml`)

| Proprietà         | Default        | Descrizione                                                    |
|-------------------|----------------|----------------------------------------------------------------|
| `solis.host`      | 192.168.1.84   | Indirizzo del datalogger / gateway Modbus TCP                  |
| `solis.port`      | 502            | Porta Modbus TCP                                               |
| `solis.unit-id`   | 1              | Slave id dell'inverter                                         |
| `solis.timeout`   | 5s             | Timeout di ogni richiesta Modbus                               |
| `solis.cache-ttl` | 5s             | Per quanto tempo riusare l'ultima lettura (protegge il datalogger) |

Si possono sovrascrivere senza ricompilare, es. `java -jar ... --solis.host=192.168.1.50`
oppure con un `application.yml` nella directory di avvio.

## Endpoint (tutti `GET`)

| Endpoint                              | Contenuto                                                        |
|---------------------------------------|------------------------------------------------------------------|
| `/api/inverter/summary`               | Flussi di potenza (FV, batteria, rete, casa) ed energia del giorno |
| `/api/inverter/info`                  | Modello, seriale, firmware, configurazione, stato e guasti decodificati |
| `/api/inverter/data`                  | Tutti i registri, raggruppati per categoria                      |
| `/api/inverter/data/{category}`       | Una categoria: `INFO`, `CLOCK`, `STATUS`, `PV`, `INVERTER`, `BACKUP`, `BATTERY`, `METER`, `LOAD`, `ENERGY`, `SETTINGS`, `GRID_PROTECTION` |
| `/api/inverter/registers/{register}`  | Un singolo registro (es. `BATTERY_SOC`)                          |
| `/api/inverter/registers`             | Mappa dei registri supportati (non interroga l'inverter)         |
| `/api/inverter/raw?start=&count=`     | Lettura grezza senza cache di max 50 input register (per i registri non documentati) |

Se l'inverter non risponde gli endpoint restituiscono `503` con un `application/problem+json`.

## Note

- Si leggono gli **input register** (function code 0x04) 33000–33300 e 35000; la mappa è in `SolisRegister.java`.
- Fonti della mappa: protocollo Solis *RS485_MODBUS(ESINV-33000ID) Hybrid Inverter* (traduzione 2020.09.15)
  e, per i registri aggiunti dai firmware successivi, l'integrazione open source
  [Pho3niX90/solis_modbus](https://github.com/Pho3niX90/solis_modbus). Le decodifiche di stati e guasti sono in `SolisCodes.java`.
- Convenzioni di segno: `gridPower` positivo = immissione in rete; `batteryPower` positivo = carica.
- Le letture sono serializzate e raggruppate in blocchi da max 50 registri.
- Ogni valore riporta il campo `source`:
  - `OFFICIAL`: definito nel protocollo ufficiale Solis;
  - `UNOFFICIAL`: "Reserved" nel protocollo ma interpretato da progetti open source. Il nome segue quello pubblico,
    la descrizione inizia con `[NON UFFICIALE ...]` e i campi `publicName`/`reference` indicano la fonte;
  - `RESERVED`: riservato e senza interpretazione nota, esposto come valore grezzo con nome `RESERVED_01`…`RESERVED_62`
    (categoria `RESERVED`, numerati in ordine di indirizzo).
