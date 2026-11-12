# Implementation Plan --- Vehicle Registration & ELM327 Setup

## 1. Goal

Implement the **Meus veículos** flow in the mobile app. A user can
register a vehicle, configure an ELM327 Wi-Fi connection, test adapter
and ECU communication, discover supported OBD-II PIDs, and save the
vehicle and its capabilities for later telemetry collection.

Treat the **vehicle** and the **ELM327 adapter configuration** as
separate entities. A vehicle remains registered if its adapter is
disconnected or replaced.

## 2. Scope

### Included

-   Vehicle registration and editing.
-   ELM327 TCP connection settings (IP address and port).
-   Adapter connection and initialization tests.
-   ECU communication test.
-   Mode 01 supported-PID discovery.
-   Supported-PID review and rediscovery.
-   Persistence of vehicle details, connection settings and discovered
    capabilities.
-   Clear loading, success, disconnected and error states.

### Not included in this phase

-   Full telemetry collection/background scheduling.
-   Trip analytics or driver-behavior scoring.
-   Fuel-consumption calculations.
-   DTC interpretation and repair recommendations.
-   Automatic vehicle identification.
-   Bluetooth support (the current project scope specifies Wi-Fi).

## 3. User flow

``` text
Meus veículos
  -> Adicionar veículo
  -> Informações do veículo
  -> Configuração ELM327 (IP + porta)
  -> Testar conexão com adaptador
  -> Inicializar ELM327
  -> Testar comunicação com ECU
  -> Descobrir PIDs suportados
  -> Revisar parâmetros encontrados
  -> Salvar veículo
```

Allow the user to cancel without saving an incomplete vehicle. If saving
without a successful connection is supported, mark the vehicle as **Not
configured** or **Disconnected**; do not imply that its ECU capabilities
are known.

## 4. Screens and behavior

### 4.1 Meus veículos

Each vehicle item should show: - Nickname or make/model. - Year and
license plate, when provided. - Whether it is selected as the active
vehicle. - Connection state: Connected, Disconnected, Not configured or
Error. - Last successful ECU communication, when available.

Actions: add, open details, select as active, edit and remove (with
confirmation).

Do not treat "selected", "connected" and "collecting" as interchangeable
states.

### 4.2 Vehicle information

Fields: - Make (required). - Model (required). - Year (according to
product validation rules). - License plate (optional). - Nickname
(optional).

Do not infer supported PIDs from make/model/year. The ECU response is
the source of truth.

### 4.3 ELM327 connection

Fields: - IP address. - TCP port.

For emulator development, allow manual entry. For a physical Wi-Fi
adapter, the phone must join the adapter's Wi-Fi network and the app
must use that adapter's actual address and port. Do not assume every
adapter uses the same IP or port.

Provide a **Test connection** action, progress feedback and actionable
error messages. Keep manual network settings in an advanced section if
the normal physical-adapter flow can determine them automatically.

### 4.4 Adapter connection and initialization

Use a TCP socket with connection and read timeouts. A typical
initialization sequence is:

  Command   Purpose
  --------- --------------------------------------------------------
  `ATZ`     Reset adapter
  `ATE0`    Disable command echo
  `ATL0`    Disable line feeds
  `ATS0`    Disable spaces, if compatible with the response parser
  `ATSP0`   Request automatic OBD protocol selection

Implementation requirements: - Read until the ELM327 prompt (`>`), not
merely until the first newline. - Handle command echo if present. -
Normalize line endings and whitespace before parsing. - Serialize
commands: do not send the next command until the previous response is
complete. - Apply timeouts and close the socket on cancellation or
failure. - Verify the initialization sequence against the emulator and
physical adapters.

An `OK` response to an AT command proves adapter-level communication
only. It does not prove that the vehicle ECU is responding.

### 4.5 ECU communication test

After adapter initialization, send:

``` text
0100
```

This requests the Mode 01 supported-PID bitmap for PIDs `01`--`20`.

Example response:

``` text
4100 BE1FA813
```

Distinguish these outcomes: - Adapter reachable and ECU responding. -
Adapter reachable but no valid ECU response. - Malformed/unexpected
response. - Timeout. - Socket/network failure.

Do not show "Vehicle connected" merely because the TCP socket opened.

### 4.6 Supported-PID discovery

-   Decode the four-byte bitmap returned by `0100`.
-   Each bit indicates support for a PID in that range.
-   The continuation bit indicates whether another supported-PID range
    is available.
-   If indicated, query the next range (`0120`, then `0140`, etc.).
-   Continue only while the ECU advertises another range.
-   Validate the response service and requested PID before accepting a
    bitmap.
-   Handle `NO DATA`, timeouts, malformed and incomplete responses.

Store discovered PID identifiers and the discovery timestamp. An
advertised PID is not a guarantee that every later request will always
return a valid value.

Maintain PID metadata in an app/domain catalog: - PID identifier
(e.g. `010C`). - Display name. - Unit. - Response decoder. - Whether
enabled for collection.

Do not hardcode supported PIDs based on vehicle model.

### 4.7 Review and save

Display the actual discovered parameters for the connected ECU. Example
only:

  Parameter                PID      ECU support
  ------------------------ -------- -------------
  Engine RPM               `010C`   Supported
  Vehicle speed            `010D`   Supported
  Coolant temperature      `0105`   Supported
  Calculated engine load   `0104`   Supported

If collection preferences are included in this phase, users may enable
only PIDs discovered as supported. The sample table is illustrative, not
a promise that every vehicle supports those PIDs.

### 4.8 Vehicle details and rediscovery

Show vehicle information, adapter status, last successful ECU
communication, supported PIDs and a **Rediscover PIDs** action.

Rediscovery repeats the capability queries. If it fails, retain the last
successful discovery result and its timestamp; do not silently replace
it with an empty list.

## 5. Suggested domain model

``` text
User
  └── Vehicle
        ├── VehicleDetails
        ├── ELM327Connection
        └── VehicleCapabilities
              └── SupportedPID[]
```

### Vehicle

-   `id`, `userId`
-   `nickname`, `make`, `model`, `year`, `licensePlate`
-   `createdAt`, `updatedAt`

### ELM327Connection

-   `vehicleId`
-   `host`, `port`
-   `lastConnectionAt`
-   `lastEcuCommunicationAt`
-   `connectionStatus`

Do not persist transient socket state as a durable connection.
Re-establish the connection when needed.

### SupportedPID

-   `vehicleId`
-   `pid`
-   `discoveredAt`
-   `enabledForCollection`

The PID catalog (name, unit and decoder) can be static application
metadata rather than duplicated for each vehicle.

## 6. Suggested application components

Keep responsibilities separated: - **Vehicle registration UI:** collects
and validates vehicle details. - **Connection configuration UI:**
collects host and port. - **ELM327 client:** owns TCP socket, command
serialization, timeouts and prompt handling. - **ELM327 initializer:**
sends setup commands and interprets adapter responses. - **OBD-II
client:** sends OBD requests and parses ECU responses. - **PID discovery
service:** queries supported-PID ranges and decodes bitmaps. - **Vehicle
repository:** persists vehicle and connection configuration. -
**Capabilities repository:** persists discovered PIDs and timestamps.

The UI should invoke application/domain operations and render their
states. It should not contain socket handling, AT command parsing or
bitmap decoding.

## 7. Connection states

Use explicit, understandable states. For example:

``` text
NOT_CONFIGURED
CONNECTING_TO_ADAPTER
ADAPTER_CONNECTED
INITIALIZING_ADAPTER
TESTING_ECU
DISCOVERING_PIDS
READY
DISCONNECTED
ERROR
```

These are conceptual states; a complex state-machine framework is not
required. Keep adapter connectivity and ECU communication
distinguishable. The adapter may be reachable while the ignition is off
or the ECU does not respond.

## 8. Error handling

Provide actionable messages for: - Invalid IP address or port. - Adapter
unreachable. - Connection timeout. - Missing ELM327 prompt. -
Initialization command rejected. - ECU not responding. - ECU returned
`NO DATA`. - Invalid/incomplete PID bitmap. - Connection lost during
discovery.

Explain which stage failed instead of reporting every failure as "ELM327
not found". Offer retry where appropriate.

## 9. Testing plan

### Unit tests

-   IP/port validation.
-   Response normalization.
-   Prompt detection and response completion.
-   AT command response parsing.
-   `4100` bitmap decoding.
-   Continuation-bit handling.
-   Supported-PID range discovery.
-   Invalid, truncated and `NO DATA` responses.

### Emulator integration tests

1.  Connect to configured IP and port.
2.  Initialize adapter.
3.  Send `0100`.
4.  Parse emulator response.
5.  Discover further ranges when advertised.
6.  Verify timeout and connection-loss handling.

### Physical-device validation

Test with at least one physical Wi-Fi ELM327 and a compatible vehicle.
Emulator success alone does not establish compatibility with all
adapters or vehicles.

## 10. Acceptance criteria

-   [ ] User can register a vehicle with required details.
-   [ ] User can configure ELM327 IP address and TCP port.
-   [ ] Invalid connection settings are rejected clearly.
-   [ ] App connects to the adapter or explains why it cannot.
-   [ ] Adapter initialization completes before ECU discovery.
-   [ ] App distinguishes adapter connectivity from ECU communication.
-   [ ] App sends `0100` and correctly decodes its supported-PID bitmap.
-   [ ] Further PID ranges are queried only when advertised by the ECU.
-   [ ] UI displays only PIDs discovered for that ECU.
-   [ ] Vehicle and discovery information persist after app closure.
-   [ ] User can retry connection and rediscover PIDs.
-   [ ] Failed rediscovery does not silently discard the last successful
    result.
-   [ ] Flow is tested with the emulator and at least one physical
    adapter/vehicle.

## 11. Implementation order

1.  Define vehicle, connection and supported-PID models.
2.  Build vehicle registration and validation screens.
3.  Implement TCP ELM327 client, timeouts and prompt handling.
4.  Implement adapter initialization and tests.
5.  Implement ECU communication test.
6.  Implement bitmap decoding and PID range discovery.
7.  Build supported-PID review screen.
8.  Persist vehicles, connection settings and discovery results.
9.  Implement vehicle details and PID rediscovery.
10. Run end-to-end emulator tests, then validate with physical hardware.

## 12. Technical caveats

-   ELM327 is an interface to vehicle diagnostics; it does not guarantee
    every vehicle supports every standard PID.
-   Vehicle make/model/year must not replace querying the ECU.
-   A successful TCP connection proves only that a host accepted the
    connection.
-   A successful AT command proves adapter communication, not
    necessarily ECU communication.
-   Supported-PID discovery is specific to the responding ECU and should
    have a timestamp.
-   The current project scope specifies Wi-Fi. Do not add Bluetooth
    without an explicit scope decision.
-   Emulator behavior can differ from physical adapters and ECUs;
    validate on real hardware.
