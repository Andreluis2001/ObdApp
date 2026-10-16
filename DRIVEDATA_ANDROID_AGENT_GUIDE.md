# DriveData Telemetry — Android App Guide

## Goal

Drive Data Telemetry App, Koltin android only app. The app connects to an ELM327 adapter, checks ECU communication, collects supported OBD-II data, stores readings locally, and uploads them to the project's FastAPI backend.

## Required technology

- Kotlin and Android.
- Jetpack Compose for the UI, unless the repository already uses another UI framework.
- [Eltonvs Kotlin OBD API](https://github.com/eltonvs/kotlin-obd-api) for OBD commands and responses.
- Room for local SQLite storage.
- Kotlin coroutines for asynchronous work.
- WorkManager for retrying uploads that need to survive app restarts.

Use compatible stable dependency versions. Follow the existing project's Gradle and package conventions where practical; do not add frameworks or layers without a clear need.

## Before coding

1. Inspect the existing Android project, SDK versions, Gradle setup, and package structure.
2. Read the current Eltonvs library documentation and supported command list. Confirm the dependency version and exact APIs before using them.
3. Confirm which ELM327 transport the target adapter uses. Implement Wi-Fi or Bluetooth as required; do not assume every adapter supports both.

## App structure

Keep the code straightforward and separate responsibilities where useful, FOLLOW DDD STRUCTURE:

- **UI:** Compose screens and ViewModels.
- **OBD:** connect to the adapter, initialize it, issue commands, and collect readings.
- **METRICS:** Metrics domain, has its own model, dao, dto, etc packages.
- **Local data:** Room database and queries for stored readings.
- **API:** Retrofit service for authentication and upload endpoints defined by the backend.
- **Sync:** upload pending records and update their status.

Use Kotlin data classes for app models. Keep database entities and API DTOs separate when their structures differ. Use repositories where they help keep UI code independent from storage or networking, but avoid creating interfaces, use cases, or classes for every small operation.

Compose screens should display state and forward user actions. ViewModels should coordinate screen operations. Keep OBD I/O, database access, and network requests off the main thread. Use coroutines with clear ownership and cancellation; avoid `GlobalScope` and unmanaged threads.

## ELM327 and OBD collection

Use the Eltonvs library for OBD command execution and parsing. It uses an `ObdDeviceConnection` backed by input and output streams; the app is responsible for opening and closing the actual Wi-Fi socket or Bluetooth connection.

- Follow the library's current setup and initialization guidance.
- Create and reuse one `ObdDeviceConnection` for an active adapter connection.
- Run commands sequentially over the connection; do not start competing collection loops.
- Confirm that the adapter can communicate with the ECU before enabling collection.
- Use command classes that actually exist in the selected library version. Check `SUPPORTED_COMMANDS.md` rather than guessing class names.
- Vehicle support varies. A parameter may be unsupported even if the library provides a command for it.
- Treat `NO DATA`, timeouts, and unsupported parameters as unavailable—not as numeric zero.
- Poll at a reasonable interval; do not query every parameter as fast as possible.
- Stop collection and close the socket/streams when the user stops, the connection fails, or the owning component shuts down.

Implement only the transport needed by the target adapter. For Wi-Fi, handle socket timeouts and closure. For Bluetooth, follow the Android permissions and socket lifecycle required by the target OS version and adapter. Do not assume BLE support unless the hardware supports it.

## Local storage

Store collected records in Room so data survives loss of internet, app restarts, and temporary API failures.

Each record should contain the fields required by the backend contract, including where applicable:

- For the first iteration, start only with id, rpm and vin fields.
- User, vehicle, and adapter/device identifiers.
- Collection timestamp.
- Parameter names, values, and units, preserving unavailable values explicitly.
- Upload status, such as pending or uploaded.

Keep Room entities separate from API DTOs. Add a migration when changing the schema; do not discard production data to avoid writing a migration. Do not delete pending records before the API confirms receipt. Handle sign-out so one user's queued records are not uploaded under another user's credentials.

## API and authentication

The backend uses FastAPI, but endpoint paths and payload details must come from its actual API contract.

- Use Retrofit/OkHttp or the existing project's equivalent.
- Use HTTPS/TLS for production requests. Never disable certificate validation.
- Keep environment URLs in build configuration, not scattered through the code.
- Implement JWT handling according to the backend's documented login and token-expiry behavior. Do not invent refresh endpoints.
- Do not store passwords in plain text or log tokens, authorization headers, or sensitive telemetry data.
- Handle network failures, HTTP errors, and expired authentication without losing locally stored records.
- The backend must enforce which vehicles a user can access; client-side checks are not authorization.

## Offline upload and retries

Use WorkManager to retry uploads when network access is available. Do not use WorkManager for frequent live OBD polling.

1. Read pending records from Room in collection-time order.
2. Send them using the backend's agreed single-record or batch endpoint.
3. Mark a record uploaded only after confirmed server acknowledgment.
4. If a request times out or its outcome is unclear, keep the record and retry using the same ID.
5. Retry temporary network/server failures with backoff. Do not endlessly retry permanent validation errors or authentication failures; retain the records and surface the problem.
6. Prevent overlapping sync work from needlessly submitting the same records concurrently. The server should deduplicate using the stable record ID.

Use at-least-once delivery with stable IDs and server-side deduplication. Do not assume the network can guarantee exactly-once delivery.

## UI scope

Implement the screens needed for these workflows, adapting them to the project's existing UI:

- Sign-in/session handling, if supported by the backend API.
- Vehicle selection, if required by the API.
- ELM327 connection and ECU communication status.
- Start/stop collection and display available readings.
- Local records and upload/sync status.
- Clear connection, permission, and synchronization errors.

Do not display unavailable parameters as zero. Do not show success until the operation has actually succeeded. Collect UI strings in Android resources rather than scattering hardcoded strings throughout the code.

## Android permissions and lifecycle

Declare only the permissions required by the chosen transport and supported Android versions. Handle permission denial and revocation. Wi-Fi and Bluetooth adapters have different permission requirements; do not request unrelated permissions by default.

Decide whether collection needs to continue when the app is backgrounded. The requirements do not specify this. If background collection is required, use an Android foreground service with the appropriate notification and permissions. Otherwise, stop collection predictably when the session ends. Do not assume a ViewModel keeps collection alive after the app process is killed.

## Implementation order

1. Inspect the Android repository, OBD library documentation, adapter type, and backend API contract.
2. Set up the required dependencies using the project's existing conventions.
3. Implement adapter connection and ECU verification.
4. Implement sequential OBD reading and display.
5. Save readings in Room.
6. Implement authenticated API upload.
7. Add WorkManager-based retries for pending records.
8. Handle permissions, lifecycle, and user-visible errors.
9. Verify the full flow with the target ELM327 adapter and a compatible vehicle.

Build incrementally and keep the implementation as simple as the requirements allow.

## References

- Eltonvs Kotlin OBD API: https://github.com/eltonvs/kotlin-obd-api
- Supported OBD commands: https://github.com/eltonvs/kotlin-obd-api/blob/master/SUPPORTED_COMMANDS.md
- Android architecture: https://developer.android.com/topic/architecture
- Room: https://developer.android.com/training/data-storage/room
- WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager
- Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
