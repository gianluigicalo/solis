# openmeteo-api

API REST che espone le API di [Open-Meteo](https://open-meteo.com) sulle coordinate fisse
**41.1588 N, 16.7667 E** (41° 09' 31.7" N, 16° 46' 00.1" E). Spring Boot 4, Java 25, virtual threads.

I parametri di query vengono inoltrati a Open-Meteo così come sono (vedi la
[documentazione di Open-Meteo](https://open-meteo.com/en/docs)); `latitude` e `longitude` vengono sempre
sostituiti da quelli configurati. Le risposte, anche gli errori `400`, sono restituite senza modifiche.

## Avvio con Docker

```bash
cd openmeteo
docker compose up -d --build
```

Oppure tutti i servizi insieme dalla cartella principale: `docker compose up -d --build`.

Swagger UI: <http://localhost:10081/swagger-ui.html>

## Build e avvio senza Docker

```bash
mvn -P -IC package
java -jar target/openmeteo-api-1.0.0-SNAPSHOT.jar
```

Senza Docker l'app ascolta sulla porta 8080, come in `application.yml`.

## Endpoint (tutti `GET`)

| Endpoint                  | API Open-Meteo                 | Set predefinito (se non si passa `current`/`hourly`/`daily`/`minutely_15`) |
|---------------------------|--------------------------------|------------------------------------------------------------------|
| `/api/meteo/location`     | —                              | Coordinate e impostazioni configurate                            |
| `/api/meteo/forecast`     | `/v1/forecast`                 | Meteo attuale, previsione oraria e giornaliera                   |
| `/api/meteo/solar`        | `/v1/forecast` (una chiamata per falda) | Per ogni falda (`{"falda10": {...}, "falda17": {...}}`): radiazione globale, diretta, diffusa, DNI e irraggiamento sul piano dei pannelli (`global_tilted_irradiance`) |
| `/api/meteo/solar/{plane}` | `/v1/forecast`                | Come sopra per una sola falda (`falda10`, `falda17`), risposta Open-Meteo inalterata; `tilt` e `azimuth` passati in query sostituiscono quelli configurati |
| `/api/meteo/archive`      | `/v1/archive` (ERA5)           | Riepilogo giornaliero; `start_date` ed `end_date` sono obbligatori |
| `/api/meteo/air-quality`  | `/v1/air-quality` (CAMS)       | AQI europeo, PM10, PM2.5, CO, NO₂, O₃, UV                        |

Esempi:

```bash
curl 'localhost:10081/api/meteo/forecast?hourly=temperature_2m,precipitation&forecast_days=2'
curl 'localhost:10081/api/meteo/solar?minutely_15=global_tilted_irradiance'
curl 'localhost:10081/api/meteo/solar/falda17?hourly=global_tilted_irradiance&forecast_days=2'
curl 'localhost:10081/api/meteo/archive?start_date=2026-10-01&end_date=2026-10-31'
```

Le risposte positive sono tenute in cache per `openmeteo.cache-ttl`. L'header `X-Cache` (`HIT`/`MISS`) indica
se la risposta viene dalla cache. Se Open-Meteo non è raggiungibile la risposta è `502` con `application/problem+json`.

## Configurazione

Le proprietà sono in `src/main/resources/application.yml` e si possono sovrascrivere con variabili d'ambiente
(in Docker tramite `compose.yaml` o un file `.env`).

Le falde (gruppi di pannelli con la stessa inclinazione e lo stesso orientamento) si definiscono sotto
`openmeteo.planes`. L'impianto ha pannelli a 10° e a 17°, entrambi orientati a sud-sud-ovest: `falda10` e
`falda17`. Si possono aggiungere altre falde; nelle risposte compaiono in ordine alfabetico. Quanti pannelli di
ogni stringa stanno su ciascuna falda lo sa il servizio advisor, che calcola la produzione.

| Proprietà                 | Variabile                  | Default        |
|---------------------------|----------------------------|----------------|
| `openmeteo.latitude`      | `OPENMETEO_LATITUDE`       | 41.1588        |
| `openmeteo.longitude`     | `OPENMETEO_LONGITUDE`      | 16.7667        |
| `openmeteo.timezone`      | `OPENMETEO_TIMEZONE`       | Europe/Rome    |
| `openmeteo.planes.falda10.tilt`    | `OPENMETEO_PLANES_FALDA10_TILT`    | 10 (gradi, 0 = orizzontale) |
| `openmeteo.planes.falda10.azimuth` | `OPENMETEO_PLANES_FALDA10_AZIMUTH` | 18 (sud-sud-ovest; 0 sud, −90 est, 90 ovest) |
| `openmeteo.planes.falda17.tilt`    | `OPENMETEO_PLANES_FALDA17_TILT`    | 17                          |
| `openmeteo.planes.falda17.azimuth` | `OPENMETEO_PLANES_FALDA17_AZIMUTH` | 18                          |
| `openmeteo.cache-ttl`     | `OPENMETEO_CACHE_TTL`      | 10m            |
| porta host (Docker)       | `OPENMETEO_HTTP_PORT`      | 10081          |

L'uso gratuito di Open-Meteo è consentito solo per scopi non commerciali (circa 10.000 chiamate al giorno).
I dati sono rilasciati con licenza CC BY 4.0.
