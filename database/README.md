# database

PostgreSQL 17 con [TimescaleDB](https://www.timescale.com/). Contiene:

- lo storico dell'inverter;
- lo storico di Open-Meteo;
- le regole dell'advisor, con i loro parametri e il flag per attivarle o disattivarle;
- le impostazioni dell'impianto;
- il registro dei consigli.

I dati li scrive il servizio **advisor**, che raccoglie lo storico e gestisce le regole.

## Avvio

Dalla cartella principale, insieme agli altri servizi:

```bash
docker compose up -d --build
```

Connessione dall'host: `psql -h localhost -p 15432 -U solis solis` (password `solis`).

La porta sull'host è 15432, per non entrare in conflitto con un PostgreSQL locale sulla 5432. Credenziali, nome del database e
porta si cambiano con le variabili `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` e `DATABASE_PORT`, in un
file `.env` nella cartella principale. **Cambia la password** se il database è raggiungibile da altri.

Lo schema viene creato **solo alla prima inizializzazione del volume** `solis-database-data`, dagli script in `init/`:

- `010-schema.sql`: tabelle e indici, solo PostgreSQL standard;
- `020-timescale.sql`: hypertable, compressione e aggregati continui.

Se modifichi gli script dopo il primo avvio, le modifiche vanno applicate a mano (o ricreando il volume, perdendo
i dati).

## Persistenza dei dati

I dati stanno nel volume Docker **`solis-database-data`**, montato su `/var/lib/postgresql/data` (la `PGDATA`
dell'immagine). Il nome è fisso, quindi il volume è lo stesso sia avviando dalla cartella principale sia da
`database/`.

I dati restano dopo:

- il riavvio di Docker o del PC (il container ripare da solo con `restart: unless-stopped`);
- `docker compose stop` / `start` e `docker compose down` / `up`;
- la ricostruzione o l'aggiornamento delle immagini.

Si perdono **solo** con:

- `docker compose down -v`;
- `docker volume rm solis-database-data`;
- `docker volume prune` con il container fermo.

```bash
docker volume inspect solis-database-data      # dove si trova sul disco

# backup e ripristino
docker exec solis-database pg_dump -U solis -Fc solis > solis-$(date +%F).dump
docker exec -i solis-database pg_restore -U solis -d solis --clean --if-exists < solis-AAAA-MM-GG.dump
```

Per il ripristino di un database TimescaleDB vedi anche la documentazione di Timescale su `timescaledb_pre_restore()`
e `timescaledb_post_restore()`.

## Tabelle

| Tabella                       | Contenuto |
|-------------------------------|-----------|
| `inverter_register`           | Definizione dei 251 registri: indirizzo, unità, categoria, descrizione e provenienza (`OFFICIAL`/`UNOFFICIAL`/`RESERVED`) |
| `inverter_measurement`        | **Hypertable.** Una riga per registro e lettura, con valore numerico già scalato (`value`) oppure testuale (`text_value`). Default: una lettura al minuto. Compressa dopo 7 giorni |
| `inverter_measurement_hourly` | **Aggregato continuo.** Media, minimo, massimo e ultimo valore orario di ogni registro numerico, aggiornato ogni 30 minuti |
| `weather_hourly`              | **Hypertable.** Valore più recente di ogni grandezza oraria Open-Meteo: meteo (`source = forecast`), irraggiamento per falda (`solar`), archivio ERA5 importato (`archive`). Le ore passate vengono riscaricate e convergono al dato analizzato |
| `weather_forecast_run`        | **Hypertable.** Ogni previsione scaricata, per confrontare il previsto con il reale. Compressa dopo 30 giorni |
| `rule`                        | Regole dell'advisor: `type`, `enabled`, `priority` e `parameters` (`jsonb`) |
| `site_settings`               | Impostazioni dell'impianto (una riga, `jsonb`) |
| `advice_log`                  | **Hypertable.** Consigli del controllo periodico, con l'esito di ogni regola e i dati usati |

Le colonne hanno un commento nel database: `\d+ nome_tabella` in psql.

## Regole direttamente da SQL

L'advisor rilegge le regole a ogni valutazione, quindi le modifiche valgono subito. I parametri omessi prendono il
valore di default. Se un valore non è valido, la regola non scatta e il motivo compare nella risposta di
`/api/advice`.

```sql
-- disattivare / riattivare una regola
UPDATE rule SET enabled = false WHERE id = 'vendi-picco-serale';

-- cambiare un parametro
UPDATE rule SET parameters = parameters || '{"targetSoc": 90}' WHERE id = 'carica-notturna';

-- aggiungere una regola (tipi e parametri: GET /api/rules/types sull'advisor)
INSERT INTO rule (id, name, type, priority, parameters)
VALUES ('vendi-tardi', 'Vendi tardi', 'EVENING_PEAK_SELL', 40, '{"windowStart": "21:00", "windowEnd": "23:00"}');
```

## Query di esempio

```sql
-- SOC e produzione FV di oggi, medie orarie
SELECT bucket, register, round(avg_value::numeric, 1) AS media
FROM inverter_measurement_hourly
WHERE register IN ('BATTERY_SOC', 'PV_TOTAL_POWER') AND bucket >= date_trunc('day', now())
ORDER BY bucket, register;

-- irraggiamento previsto sulla falda a 17° confrontato con la potenza FV reale
SELECT w.time, w.value AS gti_w_m2, round(i.avg_value::numeric) AS pv_w
FROM weather_hourly w
LEFT JOIN inverter_measurement_hourly i
       ON i.register = 'PV_TOTAL_POWER' AND i.bucket = w.time - INTERVAL '1 hour'
WHERE w.source = 'solar' AND w.series = 'falda17' AND w.variable = 'global_tilted_irradiance'
  AND w.time >= now() - INTERVAL '2 days'
ORDER BY w.time;

-- quanto è cambiata la previsione di temperatura per domani a mezzogiorno
SELECT fetched_at, value
FROM weather_forecast_run
WHERE source = 'forecast' AND variable = 'temperature_2m'
  AND time = date_trunc('day', now()) + INTERVAL '1 day 11 hours'
ORDER BY fetched_at;
```

Gli orari sono `timestamptz`. Per la radiazione, Open-Meteo dà la media dell'ora che *termina* a `time`, per questo
nell'esempio il confronto è con il bucket orario che inizia un'ora prima.
