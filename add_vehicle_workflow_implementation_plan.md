# Add Vehicle Workflow — Implementation Plan

Implementation blueprint for the **"Meus veículos" / Adicionar veículo** flow described in
[`elm327_vehicle_registration_implementation_plan.md`](elm327_vehicle_registration_implementation_plan.md),
adapted to the current Android codebase.

This document supersedes the original plan only where the "Locked decisions" below differ; the
original plan remains the requirements source (scope, user flow, acceptance criteria).

---

## 1. Locked decisions

| Topic | Decision |
| --- | --- |
| Navigation | Extend the existing in-activity route state (`AppDestination` enum pattern). Do **not** add `navigation-compose`. |
| Adapter settings | IP/port persisted **per vehicle** in `elm327_connections`. DataStore keeps the last-used host/port as prefill for the pair dialog. |
| Save gating | A vehicle can be saved after a failed or aborted setup. It is persisted with status **Não configurado** / **Desconectado**, capabilities unknown. |
| Collection scope | Metrics collection polls **only** PIDs discovered for the active vehicle. No active vehicle or no discovery ⇒ no collection. |
| Backend | No vehicle endpoints in `docs/sync-contract.md`; vehicles are local-only in this phase. Uploads keep using the ECU VIN. |
| Transport | Wi-Fi only. Do not add Bluetooth. |

---

## 2. Current baseline (what already works)

| Capability | Where |
| --- | --- |
| TCP socket, connect/read timeouts, Wi-Fi network binding | `obd/transport/WifiObdTransport.kt` |
| Adapter init (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, adaptive timing, `ATSP0`) | `obd/ObdSession.kt` |
| `SEARCHING...` preamble retry | `obd/ObdCommandRunner.kt` |
| ECU probe via `0100` + RPM, protocol description | `obd/ObdSession.kt` |
| Connection state machine + failure reasons | `obd/model/ObdConnectionState.kt` |
| Connection screen with IP/port, status, retry | `ui/connection/*` |
| 8-PID polling, Room persistence, sync | `metrics/*` |
| Room v3 (`metric_records`, `metric_readings`, `users`) | `metrics/dao/DriveDataDatabase.kt` |

**What is missing:** any vehicle domain/persistence, a vehicle list/details/add UI, real bitmap
decoding, PID capability storage, rediscovery, and per-vehicle connection wiring. The vehicle name
shown in Home/Metrics is a hardcoded string (`R.string.home_default_vehicle`, "Chevrolet S10").

Known defects to fix as part of this work:

1. `SupportedPidsDialog` renders `defaultMetricCatalog` — the static catalog is presented as if it
   were the ECU result (`ui/connection/ConnectionScreen.kt`).
2. `verifyEcuCommunication` discards the `0100` bitmap and treats `IllegalArgumentException` as
   "ECU answered" (`obd/ObdSession.kt`).
3. `ObdConnectionManager` serializes single commands only; a discovery loop would interleave with
   the 1 Hz collection poll.
4. `AppContainer` starts collection on sign-in regardless of vehicle/adapter configuration.

---

## 3. Target package layout

```
app/src/main/java/com/app/drivedata/
  vehicle/
    model/
      Vehicle.kt                 // Vehicle + VehicleDetails
      Elm327Connection.kt
      VehicleCapability.kt       // SupportedPid + DiscoveredPid
      ConnectionStatus.kt        // NOT_CONFIGURED | CONNECTED | DISCONNECTED | ERROR
    entity/
      VehicleEntity.kt
      Elm327ConnectionEntity.kt
      SupportedPidEntity.kt
    dao/
      VehicleDao.kt
    VehicleRepository.kt
    VehicleDetailsValidator.kt   // pure validation, unit-testable
  obd/
    PidBitmap.kt                 // raw response -> bitmap, pure
    PidDiscoveryService.kt       // range walk
    model/PidDiscoveryResult.kt
  ui/vehicles/
    VehicleListScreen.kt
    VehicleListViewModel.kt
    VehicleSetupState.kt
    components/VehicleCard.kt
    add/AddVehicleScreen.kt
    add/AddVehicleViewModel.kt
    details/VehicleDetailsScreen.kt
    details/VehicleDetailsViewModel.kt
```

`DriveDataDatabase` stays in `metrics/dao` (moving it would touch the whole persistence layer);
only its entity list, version, DAOs, and migrations change. New tests mirror the existing
`test/` and `androidTest/` structure.

---

## 4. Data model (Room v4)

### 4.1 Entities

**`vehicles`**

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | client UUID |
| `owner_username` | TEXT NOT NULL | `AuthSession.signedInUser` |
| `nickname` | TEXT | optional |
| `make` | TEXT NOT NULL | required |
| `model` | TEXT NOT NULL | required |
| `year` | INTEGER | optional, validated |
| `license_plate` | TEXT | optional, normalized uppercase |
| `is_active` | INTEGER NOT NULL | 0/1, one active per owner |
| `created_at_millis` | INTEGER NOT NULL | |
| `updated_at_millis` | INTEGER NOT NULL | |

Index: `index_vehicles_owner_active (owner_username, is_active)`.

**`elm327_connections`**

| Column | Type | Notes |
| --- | --- | --- |
| `vehicle_id` | TEXT PK | FK → `vehicles.id` ON DELETE CASCADE |
| `host` | TEXT NOT NULL | IP or hostname |
| `port` | INTEGER NOT NULL | 1..65535 |
| `last_connection_at_millis` | INTEGER | last successful adapter link |
| `last_ecu_communication_at_millis` | INTEGER | last verified ECU response |
| `connection_status` | TEXT NOT NULL | `NOT_CONFIGURED`, `CONNECTED`, `DISCONNECTED`, `ERROR` |

Only durable outcomes are stored. `CONNECTING` / `INITIALIZING` / `TESTING_ECU` / `DISCOVERING`
are transient UI states and are never persisted.

**`supported_pids`**

| Column | Type | Notes |
| --- | --- | --- |
| `vehicle_id` | TEXT NOT NULL | PK part 1, FK cascade |
| `pid` | INTEGER NOT NULL | PK part 2, OBD PID byte (0x0C, 0x0D, …) |
| `discovered_at_millis` | INTEGER NOT NULL | discovery timestamp |
| `enabled_for_collection` | INTEGER NOT NULL | 0/1 |

Store the raw PID byte, not a display name. Name/unit/decoder stay in the static catalog.

### 4.2 Migration `3 → 4`

Add `DriveDataDatabase` version 4, `MIGRATION_3_4`, register it in `build()`, export `4.json`, then
extend `DriveDataDatabaseMigrationTest` with a 3→4 case (open at v3, migrate, assert new tables
and that `users` + `metric_records` are preserved).

```sql
CREATE TABLE IF NOT EXISTS `vehicles` (
  `id` TEXT NOT NULL,
  `owner_username` TEXT NOT NULL,
  `nickname` TEXT,
  `make` TEXT NOT NULL,
  `model` TEXT NOT NULL,
  `year` INTEGER,
  `license_plate` TEXT,
  `is_active` INTEGER NOT NULL DEFAULT 0,
  `created_at_millis` INTEGER NOT NULL,
  `updated_at_millis` INTEGER NOT NULL,
  PRIMARY KEY(`id`)
);
CREATE INDEX IF NOT EXISTS `index_vehicles_owner_active`
  ON `vehicles` (`owner_username`, `is_active`);

CREATE TABLE IF NOT EXISTS `elm327_connections` (
  `vehicle_id` TEXT NOT NULL,
  `host` TEXT NOT NULL,
  `port` INTEGER NOT NULL,
  `last_connection_at_millis` INTEGER,
  `last_ecu_communication_at_millis` INTEGER,
  `connection_status` TEXT NOT NULL,
  PRIMARY KEY(`vehicle_id`),
  FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`)
    ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `supported_pids` (
  `vehicle_id` TEXT NOT NULL,
  `pid` INTEGER NOT NULL,
  `discovered_at_millis` INTEGER NOT NULL,
  `enabled_for_collection` INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY(`vehicle_id`, `pid`),
  FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`)
    ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_supported_pids_vehicle_id`
  ON `supported_pids` (`vehicle_id`);
```

No seed data. The hardcoded "Chevrolet S10" string is removed from Home/Metrics.

### 4.3 DAO contract (`VehicleDao`)

```kotlin
@Query("SELECT * FROM vehicles WHERE owner_username = :owner ORDER BY created_at_millis")
fun observeVehicles(owner: String): Flow<List<VehicleWithConnection>>

@Query("SELECT * FROM vehicles WHERE owner_username = :owner AND is_active = 1 LIMIT 1")
fun observeActiveVehicle(owner: String): Flow<VehicleWithConnection?>

@Transaction
@Query("SELECT * FROM vehicles WHERE id = :vehicleId")
fun observeVehicle(vehicleId: String): Flow<VehicleWithConnection?>

@Upsert suspend fun upsertVehicle(entity: VehicleEntity)
@Query("UPDATE vehicles SET is_active = 0 WHERE owner_username = :owner")
suspend fun clearActive(owner: String)
@Query("UPDATE vehicles SET is_active = 1 WHERE id = :vehicleId")
suspend fun markActive(vehicleId: String)
@Query("DELETE FROM vehicles WHERE id = :vehicleId")
suspend fun deleteVehicle(vehicleId: String)

@Upsert suspend fun upsertConnection(entity: Elm327ConnectionEntity)

@Query("SELECT * FROM supported_pids WHERE vehicle_id = :vehicleId ORDER BY pid")
suspend fun supportedPids(vehicleId: String): List<SupportedPidEntity>
@Query("DELETE FROM supported_pids WHERE vehicle_id = :vehicleId")
suspend fun deleteSupportedPids(vehicleId: String)
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertSupportedPids(entities: List<SupportedPidEntity>)
@Query("UPDATE supported_pids SET enabled_for_collection = :enabled WHERE vehicle_id = :vehicleId AND pid = :pid")
suspend fun setPidEnabled(vehicleId: String, pid: Int, enabled: Boolean)
```

`setActive` is a `@Transaction` method: `clearActive(owner)` then `markActive(id)`.

Helper relations (`VehicleWithConnection`) live in `vehicle/dao/` alongside the DAO.

---

## 5. Domain layer

### 5.1 Models

```kotlin
data class Vehicle(
    val id: String,
    val ownerUsername: String,
    val details: VehicleDetails,
    val connection: Elm327Connection,
    val capabilities: VehicleCapabilities,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class VehicleDetails(
    val nickname: String? = null,
    val make: String,
    val model: String,
    val year: Int? = null,
    val licensePlate: String? = null,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: "$make $model"
}

data class Elm327Connection(
    val host: String,
    val port: Int,
    val lastConnectionAtMillis: Long? = null,
    val lastEcuCommunicationAtMillis: Long? = null,
    val status: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED,
)

data class VehicleCapabilities(
    val supportedPids: List<SupportedPid> = emptyList(),
    val lastDiscoveredAtMillis: Long? = null,
) {
    val hasDiscovery: Boolean get() = lastDiscoveredAtMillis != null
}

data class SupportedPid(val pid: Int, val enabledForCollection: Boolean)
```

### 5.2 Repository behavior (`VehicleRepository`)

- All reads/writes scoped to the signed-in owner; the repository reads `AuthSession.signedInUser`.
- `create(details, connectionConfig)` inserts vehicle + connection in one transaction, status
  `NOT_CONFIGURED` (or `DISCONNECTED` when a host/port was provided but untested).
- `updateDetails(id, details)` bumps `updatedAtMillis`.
- `setActive(id)` scoped to owner.
- `delete(id)` cascades; if the deleted vehicle owns the live connection, disconnect first.
- `recordAdapterConnected(id, atMillis)` → `status = CONNECTED`, `lastConnectionAtMillis`,
  but **not** `lastEcuCommunicationAtMillis`.
- `recordEcuVerified(id, atMillis)` → `lastEcuCommunicationAtMillis`.
- `replaceCapabilities(vehicleId, pids, discoveredAtMillis)` is `@Transaction`:
  delete + insert, **preserving `enabledForCollection`** for PIDs present in both sets (new PIDs
  default `true`). Only called on a successful discovery, so a failed rediscovery leaves the
  previous rows and timestamp untouched.
- `setPidEnabled(vehicleId, pid, enabled)` for the review toggles (persist on save, not per tap,
  unless the details screen edits directly).

---

## 6. PID discovery

### 6.1 Bitmap decoder (`obd/PidBitmap.kt`, pure)

Input: the raw response string of a supported-PID request (e.g. `"4100 BE1FA813"`, possibly with
echo, `SEARCHING...`, CR/LF, spaces, or multiple ECU lines).

Steps:

1. Normalize: uppercase, strip `SEARCHING...`, remove CR/LF/space/`:`, drop echoed command lines.
2. Split on line boundaries; select the frame whose first two bytes are `41` + requested PID byte.
3. Require exactly 4 data bytes after `41 <pid>` (8 hex chars). Otherwise `TRUNCATED`.
4. Decode bit `i` (0..31): `pid = base + 1 + i`; bit order MSB-first per byte.
5. `supportsNextRange = set.contains(base + 0x20)`.
6. `NO DATA` / empty answer → `NO_DATA`; wrong service or PID echo → `SERVICE_MISMATCH`;
   non-hex → `MALFORMED`.

```kotlin
sealed interface BitmapResult {
    data class Ok(val pids: Set<Int>, val supportsNextRange: Boolean) : BitmapResult
    data class Failure(val reason: BitmapFailure) : BitmapResult
}

enum class BitmapFailure { NO_DATA, SERVICE_MISMATCH, TRUNCATED, MALFORMED }
```

`PidBitmap.decode(raw, requestedPidBase)` is unit-tested in isolation.

### 6.2 Discovery service (`obd/PidDiscoveryService.kt`)

- Ranges: requested bases `0x00`, `0x20`, `0x40`, `0x60`, `0x80` (matching kotlin-obd-api
  `AvailablePIDsCommand.AvailablePIDsRanges`: `PIDS_01_TO_20` … `PIDS_81_TO_A0`). Hard cap of
  5 iterations as an infinite-loop guard.
- Query each range through a constructor-injected suspend lambda so the service is testable
  without a socket:

```kotlin
class PidDiscoveryService(
    private val query: suspend (base: Int) -> String?,   // raw response for 01<base>
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun discover(): PidDiscoveryResult
}
```

- DI wires `query = { base -> manager.runCommand(AvailablePIDsCommand(rangeFor(base)))?.rawResponse?.value }`.
- Use the library only as transport; **decode from `rawResponse.value`**, not from
  `ObdResponse.value` (the library handler already flattens bitmaps, which would hide malformed
  frames and continuation semantics).
- Stop at the first failure and return `Failure(stage, reason)`; do not return a partial set.

```kotlin
sealed interface PidDiscoveryResult {
    data class Success(val pids: Set<Int>, val discoveredAtMillis: Long) : PidDiscoveryResult
    data class Failure(
        val stage: DiscoveryStage,
        val reason: DiscoveryFailure,
    ) : PidDiscoveryResult
}

enum class DiscoveryStage { ECU_TEST, RANGE_QUERY }
enum class DiscoveryFailure {
    ECU_NO_DATA, SERVICE_MISMATCH, TRUNCATED, MALFORMED,
    TIMEOUT, CONNECTION_LOST, UNKNOWN,
}
```

### 6.3 Catalog mapping

Extend `MetricDefinition` (and keep `defaultMetricCatalog`):

```kotlin
data class MetricDefinition(
    val id: MetricId,
    val pid: Int,                 // NEW: OBD PID byte
    val unit: String,
    val commandFactory: () -> ObdCommand,
)
```

| Metric | PID |
| --- | --- |
| `ENGINE_LOAD` | `0x04` |
| `COOLANT_TEMPERATURE` | `0x05` |
| `INTAKE_MANIFOLD_PRESSURE` | `0x0B` |
| `RPM` | `0x0C` |
| `SPEED` | `0x0D` |
| `MAF` | `0x10` |
| `THROTTLE_POSITION` | `0x11` |
| `FUEL_LEVEL` | `0x2F` |

Discovery results map to `MetricId` via this table; PIDs outside the catalog are persisted but not
collected (they can be shown as "unmapped PID 0x.." or hidden — recommended: hide, keep persisted).

---

## 7. Connection/session concurrency

Discovery must not interleave with the collection poll. Changes to `ObdConnectionManager`:

1. Extract `private suspend fun runCommandLocked(command)` (current body of `runCommand`).
2. Public `runCommand` keeps acquiring `lifecycleLock` and delegating.
3. Add:

```kotlin
suspend fun <T> withExclusiveSession(block: suspend (runner: suspend (ObdCommand) -> ObdResponse?) -> T): T =
    lifecycleLock.withLock { block { command -> runCommandLocked(command) } }
```

(`lifecycleLock` is a `Mutex`, already non-reentrant-safe; the runner must be the unlocked variant.)

4. `PidDiscoveryService` runs entirely inside `withExclusiveSession`, so a 1 Hz poll waits until
   discovery finishes instead of splitting the command stream.
5. Add `ConnectionFailure.CONNECT_TIMEOUT` and `MISSING_PROMPT` so transport failures are
   distinguishable from `TRANSPORT` (refused) and `ADAPTER_INITIALIZATION` (rejected AT command).
   Map `SocketTimeoutException` → `CONNECT_TIMEOUT` in `ObdConnectionManager.connect`.
6. Fix `verifyEcuCommunication`: decode `0100`; `NO_DATA` ⇒ `NO_COMMUNICATION`;
   malformed ⇒ new `EcuStatus.INVALID_RESPONSE` (or keep `NO_COMMUNICATION` + a distinct failure
   reason — recommended: new status so the UI can say "resposta inválida" instead of "sem resposta").
   Remove the `IllegalArgumentException → ECU answered` heuristic.

**Collection pause (UX):** before a setup/rediscovery run, call
`MetricsCollectionController.pauseForMaintenance()`; resume on completion/failure. The manager
mutex already guarantees correctness; the pause avoids a stale snapshot flash and wasted polls.

---

## 8. UI

### 8.1 Routes

`MainActivity` keeps `AppDestination { HOME, CONNECTION, METRICS }` and adds a nested route for the
vehicle area (no navigation library):

```kotlin
sealed interface VehicleRoute {
    data object List : VehicleRoute
    data object Add : VehicleRoute
    data class Edit(val vehicleId: String) : VehicleRoute
    data class Details(val vehicleId: String) : VehicleRoute
}
```

- Entry point: the Home vehicle card (and the empty-state card "Adicionar veículo").
- Keep the 3 bottom tabs unchanged; the vehicle area is pushed on top of `HOME`.
- `BackHandler(enabled = vehicleRoute != null) { vehicleRoute = parentRoute() }`.
- Connection screen's "PIDs suportados" dialog is fed by the active vehicle's discovered
  capabilities (read-only). Discovery itself lives in the add/details flows.
- If no active vehicle, the dialog shows the existing "no discovery yet" state (never the static
  catalog as if it were ECU data).

### 8.2 Vehicle list

`VehicleListScreen` + `VehicleListViewModel`:

- Cards: `displayName`, `year` + `licensePlate` when present, active badge, connection status
  (`Conectado` / `Desconectado` / `Não configurado` / `Erro`), last ECU communication.
- Actions: **Adicionar**, open details, **Selecionar como ativo**, edit, remove (confirmation
  dialog). Removal of the active vehicle disconnects the adapter.
- Empty state: explanatory card + "Adicionar veículo".

### 8.3 Add vehicle (single screen, stepper)

`AddVehicleViewModel` owns a `VehicleSetupState`; the screen renders one step at a time with
progress and back/cancel.

```kotlin
sealed interface VehicleSetupState {
    data class Form(
        val details: VehicleDetailsDraft,
        val connection: ConnectionDraft,       // prefilled from DataStore
        val errors: FormErrors = FormErrors(),
    ) : VehicleSetupState

    data object ConnectingAdapter : VehicleSetupState
    data object InitializingAdapter : VehicleSetupState
    data object TestingEcu : VehicleSetupState
    data class DiscoveringPids(val rangeBase: Int, val foundCount: Int) : VehicleSetupState
    data class Review(val pids: List<DiscoveredPid>, val discoveredAtMillis: Long) :
        VehicleSetupState

    data class Failed(val stage: Stage, val failure: SetupFailure) : VehicleSetupState
    data object Saving : VehicleSetupState
}
```

Flow:

1. **Vehicle form** — make/model required; year optional (`1886..currentYear+1`); plate optional
   (uppercase, 6–8 alphanumerics); nickname optional (≤40 chars). Validation lives in
   `VehicleDetailsValidator` (pure, unit-tested).
2. **Adapter step** — host/port prefilled from `AppSettingsRepository.currentWifiConfig()`.
   Validate host (IP literal or hostname) and port (1–65535). "Testar conexão" connects through
   `ObdConnectionManager.connect(config)` and persists the config to DataStore (last used).
3. **Adapter init / ECU test / discovery** — driven by `ObdConnectionState` + `PidDiscoveryService`;
   `withExclusiveSession` serializes the sequence.
4. **Review** — list only catalog metrics whose PID is discovered; per-PID enable toggle;
   "Redescobrir" re-runs discovery; show `discoveredAt`.
5. **Save** — allowed from any terminal state (`Review`, `Failed`, or after a successful connect
   without discovery). Behavior:
   - Success + review ⇒ vehicle active if it is the first one; capabilities + timestamp stored.
   - Failure/no discovery ⇒ vehicle + connection config stored; status `NOT_CONFIGURED` (no config)
     or `DISCONNECTED`/`ERROR` (config, unverified); no capability rows for a **new** vehicle.
   - Cancel at any point writes nothing.

### 8.4 Vehicle details

`VehicleDetailsScreen` + `VehicleDetailsViewModel`:

- Vehicle info block (make/model/year/plate/nickname).
- Adapter block: host:port, status, last connection, last ECU communication.
- Capabilities block: discovered PIDs with unit/name and enabled state, `discoveredAt`.
- Actions: **Redescobrir PIDs**, edit, remove (confirm), "Selecionar como ativo".
- Rediscovery failure shows the error and keeps the previous PIDs + timestamp (repository only
  replaces on success).

### 8.5 Strings

Add to `app/src/main/res/values/strings.xml` (pt-BR, existing style), e.g.
`vehicles_title`, `vehicles_empty`, `vehicle_add`, `vehicle_make_label`, `vehicle_model_label`,
`vehicle_year_label`, `vehicle_plate_label`, `vehicle_nickname_label`,
`vehicle_active_badge`, `vehicle_status_not_configured`, `vehicle_last_ecu_communication`,
`vehicle_rediscover`, `vehicle_remove_title/message`, plus discovery failure messages and
step labels. No hardcoded UI text in Compose.

---

## 9. Wiring & lifecycle

- `AppContainer`: add `vehicleRepository` (needs `database.vehicleDao()`), `pidDiscoveryService`,
  and expose them to ViewModels. Construct `PidDiscoveryService` with
  `query = { base -> obdConnectionManager.runCommand(...)?.rawResponse?.value }`.
- Active vehicle flow: `authSession.signedInUser.flatMapLatest { owner ->
  vehicleRepository.observeActiveVehicle(owner) }`.
- Sign-in: do **not** auto-connect. Collection starts only when the active vehicle is configured,
  connected, ECU-verified, and has discovered capabilities.
- Sign-out: stop collection, cancel sync, **disconnect** the adapter (new), and clear the in-memory
  active-vehicle state. Vehicle rows are kept; they are owner-scoped.
- `HomeViewModel` / `MetricsViewModel`: replace `R.string.home_default_vehicle` with
  `activeVehicle?.details?.displayName`; expose `hasVehicle` for empty states.
- `ObdMetricsCollector`: accept `catalog: List<MetricDefinition>` (already a constructor param) and
  receive the active vehicle's enabled metrics. Empty ⇒ `stream()` returns immediately (no poll,
  no snapshot). `MetricsCollectionController.isCollectible()` also requires capabilities.
- Deleting/deactivating a vehicle while connected: `ObdConnectionManager.disconnect()` from
  `VehicleRepository`/ViewModel before/after deletion (avoid racing the FK cascade).

---

## 10. Error taxonomy

| Stage | Reason | User message (pt-BR intent) |
| --- | --- | --- |
| Form | invalid host/port | "Informe um endereço IP e uma porta válidos." (exists) |
| Form | missing make/model | field-level errors |
| Connect | `CONNECT_TIMEOUT` | "O adaptador não respondeu a tempo. Verifique se está ligado e na rede Wi-Fi correta." |
| Connect | `TRANSPORT` | existing "Não foi possível alcançar o adaptador…" |
| Init | `MISSING_PROMPT` | "O adaptador não enviou o prompt esperado (`>`). Pode não ser um ELM327." |
| Init | `ADAPTER_INITIALIZATION` | existing message |
| ECU | `ECU_NO_DATA` | "O adaptador respondeu, mas o veículo não. Verifique a ignição." |
| ECU | `MALFORMED_RESPONSE` | "Resposta inválida do veículo para `0100`." |
| Discovery | `TRUNCATED` / `MALFORMED` / `SERVICE_MISMATCH` | "Bitmap de PIDs inválido ou incompleto." |
| Discovery | `TIMEOUT` / `CONNECTION_LOST` | "A conexão caiu durante a descoberta. Nenhum resultado anterior foi descartado." |

Keep these as enums (not strings) in domain/UI state, resolved through `stringResource` per the
existing `ConnectionFailure.messageRes()` pattern.

---

## 11. Testing plan

### Unit (JVM, `app/src/test`)

- `PidBitmapTest`: `4100 BE1FA813` decode (assert set incl. 0x20 continuation), no continuation
  (`…13` where 0x20 absent), truncated (fewer than 8 hex chars), wrong service (`40…`),
  wrong echo PID (`4101…`), `NO DATA`, lowercase/spaced/`SEARCHING...` normalization,
  multi-line response selection.
- `PidDiscoveryServiceTest`: fake `query` lambda scripting `0100` with continuation → `0120`
  without; assert two calls and merged set; assert stop-at-failure; assert no query when `0100`
  fails; assert timestamp from injected clock.
- `VehicleDetailsValidatorTest`: required fields, year bounds, plate normalization, nickname length.
- `VehicleSetupViewModel`-level test (optional): state transitions and save-on-failure semantics
  using fake repositories.

### Instrumented (`app/src/androidTest`)

- `DriveDataDatabaseMigrationTest`: 3→4 migration keeps users/records, creates new tables,
  FK cascade works.
- `VehicleDaoTest`: insert vehicle + connection, `setActive` transaction (single active per owner),
  delete cascades to connection + pids, `replaceCapabilities` preserves enabled flags.

### Emulator integration

Reuse the `FakeObdTransport`/`elmScript` pattern from `ObdConnectionManagerTest`:

```
ELM327 v1.5 → OK → OK → OK → OK → OK → OK
ISO 15765-4 (CAN 11/500)
41 00 BE 3F A8 13        // continuation (0x20 set)
41 20 80 00 00 00        // next range, no further continuation
41 0C 1A F8              // RPM
```

Then verify: connect → init → ECU test → discovery → persisted capabilities → collection polls only
discovered PIDs. Also script timeouts, `NO DATA`, and a truncated bitmap.

### Physical validation

One Wi-Fi ELM327 + vehicle: register, test, discover, save, reopen app, verify persisted
capabilities, rediscover, and confirm a failed rediscovery keeps the previous result.

---

## 12. Implementation order (PR-sized steps)

> Progress: steps 1–8 are implemented. Room schema `4.json` is exported; `testDebugUnitTest` and
> the migration/DAO instrumented tests are green (verified on the connected emulator). Discovery
> was also validated end-to-end against the ELM327 emulator at `192.168.1.119:35000`.
> Step 8 collection gating and active-vehicle naming are covered by unit tests only (no device was
> attached for the last pass).

1. ✅ **Schema v4** — DONE: entities, DAO, `MIGRATION_3_4`, `4.json`, migration test, DAO test.
2. ✅ **Domain + repository** — DONE: models, `VehicleDetailsValidator`, `VehicleRepository`,
   DI registration, validator unit tests.
3. ✅ **Discovery core** — DONE: `PidBitmap`, `PidDiscoveryService`, catalog `pid` field, unit tests.
4. ✅ **Manager refactor** — DONE: `withExclusiveSession`, timeout/prompt failure mapping, ECU status fix.
5. ✅ **Vehicle list**: screen + VM + Home entry point + route state + empty state.
6. ✅ **Add vehicle**: form + adapter step + setup state machine + review + save semantics.
7. ✅ **Details + rediscovery** — DONE: details screen + VM, capabilities with per-PID toggles,
   rediscover-with-preserve, edit (vehicle + adapter) and remove.
8. ✅ **Collection integration**: active-vehicle catalogs, start/stop rules, sign-out disconnect,
   Home/Metrics display name.
9. **Connection screen cleanup**: PID dialog reads capabilities; remove static-catalog claim.
10. **Tests + emulator + hardware validation.**

Each step must keep `./gradlew test` and `./gradlew assembleDebug` green; Room schema changes only
in step 1.

---

## 13. Acceptance criteria mapping

| Original criterion | Covered by |
| --- | --- |
| Register a vehicle with required details | Steps 1, 6 (`VehicleDetailsValidator`) |
| Configure IP + TCP port per vehicle | Steps 1, 6 |
| Invalid settings rejected clearly | Step 6 validation + error taxonomy |
| Connect or explain why not | Step 4 + existing transport |
| Init completes before discovery | Step 4 `withExclusiveSession` sequence |
| Adapter vs ECU distinguished | Step 4 (`EcuStatus`, failure reasons) |
| Send `0100`, decode bitmap | Step 3 |
| Query further ranges only when advertised | Step 3 continuation logic |
| UI shows only discovered PIDs | Steps 3, 8, 9 |
| Persistence across app restarts | Steps 1, 2 |
| Retry connection and rediscover | Steps 6, 7 |
| Failed rediscovery preserves last result | Step 2 `replaceCapabilities` only on success |
| Emulator + physical validation | Step 10 |

---

## 14. Risks / open items

- **Multi-ECU responses:** some vehicles answer `0100` from several ECUs (multi-line). The decoder
  picks the first matching `41 <pid>` frame; document this and add a test. Merging bitmaps across
  ECUs is out of scope.
- **`ATS0` vs parser:** the existing init sends spaces-off while `FakeObdTransport` scripts spaced
  hex. The decoder normalizes spaces, so both work; do not change the init sequence in this work.
- **Library timeout defaults:** `ObdDeviceConnection.run` has its own timeout/retry parameters.
  Discovery relies on those plus socket `soTimeout`; tune only if emulator/physical tests show
  hangs. No new timeout layer.
- **Active vehicle uniqueness** is enforced transactionally, not by a DB constraint (Room cannot
  express a partial unique index). Concurrent `setActive` calls from two screens are serialized by
  Room's transaction, but the UI should not allow double taps.
- **Owner changes:** capabilities/connection rows are per vehicle and cascade on delete; sign-out
  keeps them. If multi-user support on one device is used, each owner sees only their vehicles.
- **No backend vehicle sync** in this phase; VIN remains the only server-side vehicle identity.
