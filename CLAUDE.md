# bi-analytics-service — CLAUDE.md

Read this in addition to `../CLAUDE.md` whenever you work in this directory. The root file describes how the three repos fit together; this file goes deep on the **legacy MS SQL ERP we read from** and the **analytics math that runs on top of it** — both are non-obvious from the code alone.

## What this service is

Read-only adapter over a Comarch ERP XL database. It does **not** own the schema (no Flyway, `ddl-auto` is unset, all repository queries are native SQL or read-only JPA). Two responsibilities:

1. Detect employee changes in the ERP and publish `EmployeeChangeEvent` to SQS so GearTrackApi can sync its `employees` table.
2. Serve analytics endpoints (`/api/employees/hours`, `/api/analytics/*`, `/api/contractors`, `/api/products`) read by the frontend through the `service2` gateway prefix.

## Local DB (developer machine)

The ERP is mirrored locally as a SQL Server 2022 instance. **Local-dev creds only** — never reuse on a shared/staging box:

```
Host:     localhost
Port:     1433
Database: CDN_F_P_U_H__Piszczek
User:     sa  (myapp also works)
Password: YourPassword123!
JDBC:     jdbc:sqlserver://localhost:1433;databaseName=CDN_F_P_U_H__Piszczek;encrypt=false;trustServerCertificate=true
```

Quick probe from WSL/Linux without installing `sqlcmd`:

```bash
pip3 install --break-system-packages --user pymssql
python3 -c "
import pymssql
c=pymssql.connect(server='localhost',port=1433,user='sa',password='YourPassword123!',database='CDN_F_P_U_H__Piszczek')
cur=c.cursor(); cur.execute('SELECT @@VERSION'); print(cur.fetchone()[0][:80])
"
```

The ERP database is in **Polish** with `varchar` columns (CP1250 / `Polish_CI_AS` collation). When you display them through tools that don't honor the column collation you'll see mojibake (`Pawe³` instead of `Paweł`). Cast through `nvarchar` or use `FOR JSON`/`FOR XML PATH` to get clean Unicode.

## ERP schema map (only what analytics actually uses)

The ERP has hundreds of tables. The ones below are the **entire** surface area the service reads. Row counts are approximate (live snapshot at time of writing; the ERP is active so they grow).

### Production-order graph (`dbo` schema, `Cti*` prefix)

| Table | Rows | Purpose | What one row means |
|---|---|---|---|
| `CtiZlecenieNag` | ~6 800 | Production order header (`ZP/00857/2026`) | One physical/business work order: which `Twr_Kod` to make, how many (`CZN_Ilosc`), when issued (`CZN_DataWystaw`), status (`CZN_Status`: 0 draft → 3 closed). |
| `CtiZlecenieElem` | ~8 500 | BOM lines on an order | One material/sub-product line. **`CZE_Typ` is the row kind**: `1` = the produced item itself (~480), `2` = expected raw material (~7 500, the dominant case the audit uses), `3` = byproduct/waste (~520), `4` = rare. The `expectedIlosc` formula is `CZE_Ilosc * CZN_Ilosc` (per-unit BOM × order qty). |
| `CtiZlecenieZasob` | ~29 300 | Work-time entries on an order | One real-time interval `[ZZs_DataOd, ZZs_DataDo]` of `ZZs_CzasMin` minutes, by employee `ZZs_PrcId` on resource `ZZs_CZID`, against order `ZZs_CZNID`. Open sessions have `ZZs_DataDo IS NULL` and `ZZs_CzasMin = 0` (currently ~5 of these). **`ZZs_PrcId` is the key the analytics joins on**, and `100%` of rows have it populated. |
| `CtiZlecenieDok` | ~13 100 | Document linkage between an order and an external `CDN.TraNag` doc | Glues a production order (`CZD_CZNId`) to a material-issue/receipt document (`CZD_TrnId` → `CDN.TraNag`) and to the worker who scanned it (`CZD_PracownikId` → `CtiZasobPRC.ZsP_PrcId`). |

### Resources (employees & machines), `dbo` schema

| Table | Rows | Purpose |
|---|---|---|
| `CtiZasobGrupy` | 7 | Resource group dictionary. Codes: `Maszyny`, `Wydziały`, `Narzędzia`, `Gniazda produkcyjne`, `Stanowiska pracy`, `Inne`, **`Pracownicy`** (the one analytics filters on). |
| `CtiZasob` | 23 | All "resources" — employees AND machines/work stations live here, separated by `CZ_CZGID`. Employees (`CZG_Kod = 'Pracownicy'`) carry `CZ_Typ = 80`. Group resources like `Wycinanie`, `Monta¿`, `Malowanie`, `Sprzątanie`, `Naprawa`, `Produkcja` live in group `Inne` with `CZ_Typ = 0`. The shop has ~13 named workers and ~10 group/machine resources. **`CZ_Kod` is the human-readable name and the only string used as `workerId`/`resourceId` downstream.** |
| `CtiZasobPRC` | 24 (15 distinct `ZsP_PrcId`) | Many-to-many: which `ZsP_CZID` (resource) a given `ZsP_PrcId` (logical employee) is allowed to log time as. **Exactly 10 of 15 PrcIds map to a single resource; the other 5 map to multiple** (the named worker resource + the catch-all `Produkcja` group resource). This drives a critical join behavior described below. |

### Punch clock — `dbo.CtiProdukcjaPanelRCP` (~9 900 rows)

| Column | Meaning |
|---|---|
| `IDPracownika` | Joins to `CtiZasobPRC.ZsP_PrcId` (the *employee* key, not a resource). |
| `KodPracownika` | Free-text scan code (looks numeric: `"7"`, `"11"`, etc., NOT the `CZ_Kod` name). |
| `DataOperacji` | Punch timestamp. |
| `Typ` | **`1` = clock-in (~4 968 rows), `2` = clock-out (~4 962 rows).** Only these two values appear; counts are ~equal as expected. |

Date span: 2023-09 → today.

### Material-document tables (`CDN` schema, the trade ledger)

| Table | Rows | Purpose |
|---|---|---|
| `CDN.Towary` | ~1 900 | Product/material dictionary. `Twr_TwrId` PK, `Twr_Kod` is the product code used as `productTypeId` in analytics. |
| `CDN.TraNag` | ~31 700 | Trade-document headers. The columns analytics actually uses: `TrN_TrNID` (PK), `TrN_NumerPelny` (e.g. `RW/127/07/2024`), `TrN_TypDokumentu` (`303`=PW, `304`=RW, others ignored), `TrN_Anulowany` (must be `0`). The query filters on the **`TrN_NumerPelny LIKE 'RW/%'` / `'PW/%'` prefix**, not `TrN_TypDokumentu`. |
| `CDN.TraElem` | ~65 000 | Trade-document line items. Joins via `TrE_TrNId`. `TrE_TwrKod`, `TrE_Ilosc`, `TrE_WartoscNetto`. |

The same `TrN_NumerPelny` can recur (often 2–5x) across different `TrN_TypDokumentu` values — the safe join is always **`TrN_TrNID`**, the prefix is just for filtering.

## How the data flows into "worker efficiency"

Two cached SQL queries → a Caffeine cache → in-memory math in `WorkerStatsCalculator`. Skim the previous chat transcripts if you want the deep formula derivations; the canonical references in code are:

- `WorkerAnalyticsRepository.findWorkerAnalytics()` — the master query, returns one row per `CZN_ID` with a JSON array of worker-time entries. Skips orders that have **zero** `CtiZlecenieZasob` rows (~325 of 6 800 are invisible to analytics for that reason).
- `CtiProdukcjaPanelRCPRepository.getAllEmployeesDailyHours()` — RCP attendance. Pairs each punch with the next via `LEAD()`, keeps only rows whose row's `Typ = 1`, sums `DATEDIFF(MINUTE)/60.0` per (employee, date).
- `WorkerStatsCalculator` (~700 lines) — benchmarks, speed index, daily 10.1 h cap, capped-day reporting, filter logic.
- `MaterialAuditService` — expected vs actual issuance, allowing both `offsetPercent` OR `offsetNumber` tolerance.

### The `prc_single` fallback (the most surprising bit)

In `findWorkerAnalytics()`, the column `workerId` is computed as:

```sql
COALESCE(prc_single.CZ_Kod, cz2.CZ_Kod) AS workerId
LEFT JOIN (
    SELECT zsp.ZsP_PrcId, MAX(cz_prc.CZ_Kod) AS CZ_Kod
    FROM dbo.CtiZasobPrc zsp
    INNER JOIN dbo.CtiZasob cz_prc ON zsp.ZsP_CZID = cz_prc.CZ_ID
    GROUP BY zsp.ZsP_PrcId
    HAVING COUNT(DISTINCT zsp.ZsP_CZID) = 1
) prc_single ON prc_single.ZsP_PrcId = zzs2.ZZs_PrcId
```

Translation: if a given `PrcId` is mapped in `CtiZasobPRC` to **exactly one** `CZ_ID`, that resource's `CZ_Kod` becomes the `workerId`. Otherwise, fall back to whatever resource the work was actually logged on (`cz2.CZ_Kod`). On the live DB right now:

- 10 PrcIds resolve cleanly (`workerId == resourceId == "Paweł Górowski"` etc.).
- 5 PrcIds (Jan Plata, Andrzej Szczęsny, TOMASZ PISZCZEK, Piotr Piszczek, Paweł Górowski — they're also linked to `Produkcja` or other group resources) **fall through and look like group-resource work** in analytics whenever they happen to log on the catch-all resource.
- This is the source of the `(workerId, resourceId)` split (`workerId == resourceId` ⇒ "direct work"; otherwise group resource e.g. `(Kamil Rygiel, Wycinanie)`). 691 entries on the live DB are this `Kamil Rygiel × Wycinanie` shape.
- Group-resource entries **do count** toward production hours but are **skipped** from the per-workerId speed-index numerator (`findWorkerExcludingGroupResource`). The per-(worker,resource) speed index does include them.

### Speed Index — the actual efficiency formula

For each job a worker has time on (filtered cohort):

```
hoursPerUnit  = jobTotalMinutes / 60 / max(quantity, 1)
workerHours   = workerMinutes  / 60 / max(quantity, 1)        ← per-unit hours
benchmark     = avg(hoursPerUnit) over all filtered jobs of that productTypeId
contribution  = workerHours / hoursPerUnit                    ← share of the job

speedIndex(worker) = Σ_jobs (benchmark × contribution) / Σ_jobs workerHours
```

Result: `1.0` = average for the cohort, `>1` faster, `<1` slower. **Benchmarks are recomputed against whatever filter the user picked**, so the same person's number changes when the filter changes. The frontend should label it as "relative to selected cohort", not as an absolute productivity score.

### Daily presence cap = `10.1` hours

`WorkerStatsCalculator.DAILY_PRESENCE_CAP = 10.1`. Per day:

```
presence = max(rcpAttendance, dailyWork)
if presence > 10.1:
    record CappedDayDto
    if dailyWork > 10.1:
        scaleFactor = 10.1 / dailyWork
        production *= scaleFactor;  internal *= scaleFactor
    presence = 10.1
idle = max(0, presence - production - internal)
```

The 0.1 h tolerance is intentional: real ~10 h shifts shouldn't trip the cap.

### `PRACE WEWN` regex split

`Pattern.compile("PRACE\\s*WEWN", CASE_INSENSITIVE)` matches a `productTypeId` like `PRACE WEWNĘTRZNE` or `PRACE WEWN - SERWIS`. Such jobs are categorized as "internal work", separated from "production" hours; if `ignoreInternalWork = true` they're zeroed out and **also** excluded from the speed index. There is no whitelist table — the regex IS the source of truth.

### `Produkcja` is a hard-coded literal

`MaterialAuditRepository.findActualMaterials` filters out `cz.CZ_Kod != 'Produkcja'` to suppress the catch-all resource from appearing as a worker. If anyone renames that ERP resource, the audit silently re-includes it. Update the SQL if it ever changes.

## Caching

`WorkerAnalyticsCacheService` uses Caffeine: `expireAfterWrite = 30 min`, `maximumSize = 10`, two cache regions (`workerAnalyticsCache` and `employeeHoursCache`), each with one canonical key (`'allJobsMapped'` and `'allEmployeeHours'`). Manual eviction via `POST /api/analytics/worker-analytics/refresh-cache`. **No event-driven invalidation** — fresh ERP data is invisible to analytics for up to 30 min after a write. The cache is process-local, so multi-instance deployments get inconsistent reads.

## Endpoints surface

| Path | Verb | Notes |
|---|---|---|
| `/api/employees` | GET | List `Pracownicy` group; uses `findByGroupCode` |
| `/api/employees/hours` | POST | Per-employee monthly hours (legacy N+1 path; analytics path uses cached `getAllEmployeesDailyHours` instead) |
| `/api/analytics/worker-analytics` | POST | Main efficiency endpoint, request body has `dateFrom`, `dateTo`, `selectedProducts`, `excludedWorkers` (composite `workerId|resourceId` supported), `soloOnly`, `ignoreInternalWork` |
| `/api/analytics/worker-analytics/refresh-cache` | POST | Evict both Caffeine caches |
| `/api/analytics/worker-daily-jobs` | GET | `?workerId=…&date=YYYY-MM-DD`, `workerId` may be `"workerId\|resourceId"` |
| `/api/analytics/material-audit` | POST | `dateFrom`, `dateTo`, `offsetPercent`, `offsetNumber` (logical OR) |
| `/api/contractors` / `/api/products` | GET | Reference data |

Only `/api/auth/**` is `permitAll()` in `SecurityConfig` — every analytics call needs a valid GearTrackApi-issued JWT.

## Hot tips when changing analytics

- **Don't add filters to the master SQL** unless you also evict the cache, or you'll spend a debug session staring at stale numbers.
- The master query's `INNER JOIN dbo.CtiZlecenieZasob` is **load-bearing**: orders with no logged work intentionally drop out. If you change it to `LEFT JOIN`, expect every dormant order to suddenly show up with `null` workers and break the JSON-array parse path.
- `findWorkerEntries` returns ALL of a worker's entries; `findWorkerResourceEntries` filters by `(workerId, resourceId)`; `findWorkerExcludingGroupResource` drops group-resource rows from speed-index math. Pick the right one — they have different semantics and using the wrong one is a silent correctness bug.
- Per-(worker, resource) stats use ALL of that worker's per-day work across resources for `production`/`presence`/`idle`. Two stat rows for the same person therefore display **identical** daily totals; only `speedIndex`, `jobCount`, and `dailyDetails`-per-resource differ. That's intentional (a person can't be idle just because they switched resources mid-day).
- The RCP query's `LEAD()` pairs the current punch with the **next** punch regardless of the next punch's `Typ`. Forgotten clock-outs over-attribute hours to the day a worker forgot. Cross-day shifts are attributed to the start day.
- `ROUND_HALF_UP` is used everywhere; if you switch to a different mode (e.g. `BANKERS_ROUNDING`) tests will fail by ±0.01 in many places.

## Useful one-liners

```bash
# Count jobs visible to analytics right now
python3 /tmp/dbq.py "
SELECT COUNT(DISTINCT czn.CZN_ID)
FROM dbo.CtiZlecenieNag czn
INNER JOIN dbo.CtiZlecenieZasob zzs ON zzs.ZZs_CZNID = czn.CZN_ID"

# All employees in the Pracownicy group
python3 /tmp/dbq.py "
SELECT cz.CZ_ID, cz.CZ_Kod
FROM dbo.CtiZasob cz
JOIN dbo.CtiZasobGrupy g ON cz.CZ_CZGID = g.CZG_ID
WHERE g.CZG_Kod = 'Pracownicy' ORDER BY cz.CZ_Kod"

# Detect PrcIds that fall through to the group-resource fallback
python3 /tmp/dbq.py "
SELECT zsp.ZsP_PrcId, COUNT(DISTINCT zsp.ZsP_CZID) AS resources
FROM dbo.CtiZasobPrc zsp
GROUP BY zsp.ZsP_PrcId HAVING COUNT(DISTINCT zsp.ZsP_CZID) > 1"
```

(The `dbq.py` helper is a 30-line `pymssql` wrapper; keep one in `/tmp` or `tools/` for scratch queries — tests use H2 so you can't reproduce ERP shape there.)

## Things this service does NOT do

- Write to the ERP. Hibernate is read-only by convention; no `@Modifying` queries exist.
- Authenticate users. JWT is validated locally with the shared secret, but the source of truth is GearTrackApi.
- Maintain its own schema / migrations.
- Calculate "absolute" worker efficiency. The Speed Index is **cohort-relative** by construction.
