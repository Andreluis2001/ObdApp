# DriveData — Screen Layout and Functionality

This document describes every screen in the DriveData Android app, its visual layout, the actions available on it, and how it connects to the rest of the navigation.

The UI is built with Jetpack Compose and uses a light content area (`HomeBackground`) with dark navy headers (`LoginBackgroundTop`), white rounded cards (`HomeSurface`), and blue/green/orange/red status accents.

---

## Navigation model

### Auth gate

`MainActivity` decides what to show before any screen:

| State | What is shown |
| --- | --- |
| Auth session still loading | Full-screen background with a centered blue circular progress indicator |
| No signed-in user | **Login** screen (no bottom bar) |
| Signed-in user | Main app shell with the bottom navigation bar |

### Bottom navigation

The bottom bar is always visible in the signed-in app shell and contains five tabs:

| Tab | Label | Icon | Opens |
| --- | --- | --- | --- |
| Home | "Início" | House | Dashboard |
| Vehicles | "Veículos" | Car | Vehicle list |
| Metrics | "Leituras" | Bar chart | Parameters screen |
| Offline | "Offline" | Cloud download | Local data summary |
| Profile | "Perfil" | Person | Profile screen |

Selected tabs use the filled icon and brand blue; unselected tabs use the outlined icon and muted gray.

### Sub-routes

- **Vehicle details** is a sub-route of Vehicles (or of Connection when opened from a discovery result). While it is open, no bottom tab is highlighted. Back returns to the screen that opened it.
- **Connection** is not a bottom tab. It is opened from Profile. While it is open, the Profile tab remains highlighted.
- **Offline** contains its own internal routes: Summary → Stored records → Record details.
- Android back is handled explicitly for vehicle details, Connection, and Offline internal routes.

### Global overlay

A **VIN mismatch dialog** can appear over any screen while collection is active. It warns that the connected vehicle reports a different VIN than the selected vehicle, shows both VINs (or explains that the VIN could not be read), and offers "Entendi" to dismiss.

---

## 1. Login

**File:** `ui/login/LoginScreen.kt`

### Purpose

Authenticate the user against the backend and start a session. This screen is shown only when no user is signed in.

### Layout

| Region | Content |
| --- | --- |
| Background | Full-screen vehicle photo (`login_background`) with a dark vertical gradient overlay |
| Logo | Centered app logo, 140 dp |
| Title | "Telemetria Veicular" |
| Subtitle | "Dados do seu veículo, mais controle para sua jornada." |
| Username field | Outlined field with person icon, label "E-mail ou usuário", single line, Next IME action |
| Password field | Outlined field with lock icon, label "Senha", show/hide password trailing icon, Done IME action |
| Error text | Inline red message below the fields when login fails |
| Submit button | Full-width blue "Entrar" button; replaced by a small white spinner while submitting |

The content is vertically scrollable, respects system bars and the keyboard inset, and forces dark system-bar icons to stay legible over the photo.

### Functionality

- Typing updates the ViewModel state; fields are disabled while a request is in flight.
- Show/hide password toggles between masked and plain text.
- Pressing Done on the keyboard or tapping "Entrar" submits the credentials.
- The button is enabled only when the form is valid and no request is running.
- On success, the auth session emits a signed-in user and the app shell replaces this screen automatically.
- On failure, one of two messages is shown:
  - "Usuário ou senha incorretos."
  - "Não foi possível entrar. Tente novamente."

---

## 2. Home / Dashboard

**File:** `ui/home/HomeScreen.kt`

### Purpose

Give a quick overview of the active vehicle, live collection state, the four main OBD-II parameters, sync status, and connection health.

### Layout

#### Header (dark navy, status-bar padded)

- Connection line: small colored dot (green when the adapter is connected, gray otherwise) followed by "Veículo conectado" or "Veículo desconectado".
- Vehicle selector: rounded dark field with a car icon, the active vehicle name (or "Nenhum veículo ativo"), and a chevron.
- Tapping the selector opens a dropdown:
  - Vehicles ordered with the active one first; the active vehicle has a check icon.
  - A divider and a "Meus veículos" item at the bottom.
- If there are no vehicles, tapping the selector goes straight to the Vehicles screen.

#### Scrollable body

1. **Collection card**
   - A 112 dp circular ring, green when collection is running and red when stopped.
   - Car icon and "Coleta ativa" / "Coleta parada".
   - Detail rows:
     - "Conexão OBD": "Wi-Fi · Conectado" when the adapter is connected, otherwise "Desconectado".
     - "Última sincronização": relative/absolute last sync text, or "Nunca".
     - "Dados pendentes": record count and approximate size.
     - "Registros rejeitados": only shown when the rejected count is greater than zero, in red.
     - Sync error message, when present.

2. **"Principais parâmetros (OBD-II)"**
   - Four metric tiles in a 2×2 grid: RPM, Velocidade, Temperatura do motor, Carga do motor.
   - Each tile contains an icon badge, the metric label, the current value (or "--" when unavailable), the unit, a sparkline of recent history, and the configured min/max range.
   - A "Ver todos os parâmetros" row with a chevron opens the Metrics screen.

3. **"Status da conexão"**
   - Three equal-height status cards:
     - "ELM327": "Conectado" (green) or "Desconectado".
     - "Internet": "Disponível" (green) or "Indisponível" (red).
     - "Armazenamento local": used bytes / storage budget.

### Functionality

- Selecting a vehicle from the dropdown changes the active vehicle.
- The "Meus veículos" item opens the Vehicles screen.
- "Ver todos os parâmetros" opens the Metrics screen.
- Values update live from the collection controller's snapshot and history.
- Unavailable readings are displayed as "--", never as zero.

---

## 3. Vehicles

**File:** `ui/vehicles/VehicleListScreen.kt`

### Purpose

List every vehicle registered for the signed-in user, show each adapter status, and provide access to vehicle details.

### Layout

- **Header:** dark navy `ScreenHeader` with a back arrow, title "Meus veículos", and subtitle "Escolha o veículo ativo e gerencie os adaptadores".
- **Scrollable body:**
  - Dismissible error banner at the top when an activation or deletion failed.
  - Loading state: centered blue circular progress indicator.
  - Empty state: large card with a blue car icon in a circle, "Nenhum veículo cadastrado", and instructions to discover the first vehicle from the Connection tab.
  - Vehicle list: one card per vehicle.

#### Vehicle card

- 64 dp rounded car icon.
- Vehicle display name.
- Subtitle: license plate when available, otherwise the VIN.
- Status label with colored dot: "Adaptador não configurado", "Conectado", "Desconectado", or "Falha na última conexão".
- Check icon on the right when the vehicle is active.
- Active cards use a 2 dp blue border; inactive cards use a 1 dp gray border.
- The whole card is tappable.

### Functionality

- Tapping a card opens Vehicle details.
- The back arrow returns to the screen that opened the list (Home or Profile).
- Activation/deletion errors can be dismissed.

---

## 4. Vehicle Details

**File:** `ui/vehicles/details/VehicleDetailsScreen.kt`

### Purpose

Show one vehicle's identity, adapter configuration and history, and its discovered OBD-II capabilities. Allow the user to make it active, remove it, toggle parameters, or rediscover capabilities.

### Layout

- **Header:** back arrow, title set to the vehicle display name (or "Detalhes do veículo"), subtitle "Informações, adaptador e parâmetros".
- **Loading:** centered progress indicator.
- **Not found:** centered "Não foi possível carregar este veículo."
- **Scrollable content:**
  - Dismissible error banner.
  - **Busy card** while an operation runs: spinner plus "Redescobrindo parâmetros", "Conectando ao adaptador…", or "Descobrindo parâmetros suportados…" with discovery range and found count.
  - **Vehicle info card:**
    - 56 dp car avatar.
    - Display name, and VIN or make/model underneath.
    - "Ativo" badge when active.
    - Red delete icon on the right.
    - "Definir como ativo" primary button when the vehicle is not active.
    - License plate row when present.
    - Identity section with VIN, manufacturer, and serial number; unavailable values read "Não disponível".
  - **"Adaptador" section:** card with adapter status label, address (`IP: host:port` or "IP não configurado"), last adapter connection, and last ECU communication.
  - **"Parâmetros suportados" section:** card listing every discovered PID known to the app:
    - Metric icon, label, unit, and PID in hexadecimal.
    - A switch to enable or disable the PID for collection.
    - Discovery timestamp.
    - "Descobrir parâmetros novamente" secondary button, enabled only when an adapter is configured.
    - Empty message when nothing has been discovered.

### Functionality

- "Definir como ativo" makes this the active vehicle used by collection and the dashboard.
- The delete icon opens a confirmation dialog: "Remover veículo" with the vehicle name and the note that already-synced data is kept. Confirming deletes the vehicle and closes the screen.
- Each PID switch enables or disables that parameter for collection.
- Rediscovery connects to the adapter, reads supported PIDs, and updates the capability list.
- Back returns to the previous screen; deletion also closes the screen.

---

## 5. Connection

**File:** `ui/connection/ConnectionScreen.kt`

### Purpose

Configure the ELM327 Wi-Fi adapter, connect to it, verify ECU communication, discover/register a vehicle by VIN, and inspect the supported PIDs and negotiated protocol.

### Layout

- **Custom dark header:** back arrow, title "Conexão", subtitle "ELM327 + Veículo".
- **Scrollable body:**

  1. **Adapter card**
     - 88 dp circle containing the ELM327 illustration.
     - Status label with dot: "Desconectado", "Conectando…", "Inicializando…", "Verificando…", "Conectado", "ECU sem resposta", "Resposta inválida do veículo", or "Falha na conexão".
     - "ELM327 (Wi-Fi)" and the current address.
     - "Endereço IP do adaptador" text field and "Porta" numeric field.
     - Hint: "Informe o endereço do ELM327 na rede Wi-Fi."
     - While discovering: a "Cancelar" primary button.
     - Otherwise: "Descobrir veículo" primary button (enabled only with a valid host/port and no busy operation) and, when connected or failed, a "Desconectar" secondary button.

  2. **Discovery feedback** (one of):
     - **Running card:** spinner, current stage label, and, during PID discovery, the PID range and number of parameters found.
     - **Failure card:** warning icon, "Não foi possível descobrir o veículo", the failing stage, the specific failure message, and a "Tentar novamente" button.
     - **Success card:** green check, "Veículo cadastrado" or "Veículo já cadastrado", an explanatory hint, the vehicle name, VIN, optional manufacturer and serial number, and a "Ver detalhes do veículo" button.

  3. **"Configurações"**
     - "PIDs suportados" row with subtitle "Parâmetros descobertos no veículo ativo".
     - "Protocolo" row showing the negotiated protocol or "Automático"/"Desconhecido".

  4. **Info banner:** explains that the ELM327 does not guarantee support for every parameter.

- **Dialogs:**
  - **Supported PIDs:** title "PIDs suportados", explanatory hint, active vehicle name and discovery date, then one row per supported catalog PID (icon, label, unit, PID). PIDs excluded from collection show "Coleta desativada" in orange. Empty states explain when there is no active vehicle, no discovery yet, or no compatible PIDs.
  - **Protocol:** current protocol, adapter address, and a note that the protocol is negotiated automatically.

### Functionality

- Editing host/port updates the connection configuration.
- "Descobrir veículo" connects, initializes the adapter, verifies the ECU, reads the VIN/identity, discovers supported PIDs, and registers or updates the vehicle.
- Discovery can be cancelled while running.
- "Desconectar" closes the adapter connection.
- "Ver detalhes do veículo" opens Vehicle details for the discovered vehicle.
- Back returns to the previous screen (Profile by default).
- All errors are surfaced as text; success is only shown after discovery actually succeeds.

---

## 6. Metrics / Parameters

**File:** `ui/metrics/MetricsScreen.kt`

### Purpose

Display live OBD-II readings and connection diagnostics, and let the user start or pause collection.

### Layout

#### Header (dark navy)

- Back arrow.
- Title "Parâmetros do Veículo".
- Subtitle: active vehicle name or "Nenhum veículo ativo".
- Play/pause icon button, shown only when the ECU is verified and collection is possible:
  - Pause icon ("Parar coleta") while collecting.
  - Play icon ("Iniciar coleta") while stopped.

#### Tabs

A 48 dp row with two equally sized tabs and a blue underline on the selected one:

- "Tempo real"
- "Diagnóstico"

#### "Tempo real" tab

- Optional hint banner:
  - "Conecte o ELM327 para receber os parâmetros do veículo." when the ECU is not verified.
  - "Descubra os parâmetros do veículo para iniciar a coleta." when collection is unavailable.
- One card per parameter, in a fixed order:
  - 42 dp tinted icon badge.
  - Parameter label.
  - Current value or "--" when unavailable, plus the unit when available.
  - Min/max range text.
  - Sparkline (82 × 38 dp) of recent history.
- Collection error text in red, when present.
- Disclaimer: not every vehicle supports every parameter.

#### "Diagnóstico" tab

- **"Status" card:**
  - "Conexão": connection state with color.
  - "Protocolo": negotiated protocol or "Indisponível".
  - "Adaptador": "ELM327 (Wi-Fi)".
- **"Informações" card:**
  - "Veículo": active vehicle name or "Indisponível".
  - "Chassi (VIN)".
  - "Última leitura": time/date or "Nenhuma leitura".
  - "Parâmetros recebidos": available count out of total parameters.
- Error text and the same disclaimer.

### Functionality

- The header button toggles collection through the collection controller (start or stop).
- Live values and sparklines update from the current snapshot and rolling history.
- The tab selection is preserved across recompositions.
- Unavailable values are explicitly shown as unavailable, not zero.

---

## 7. Offline / Local Data Summary

**File:** `ui/offline/OfflineScreen.kt`

### Purpose

Summarize locally stored records, upload/sync progress, storage usage, and the last sync time.

### Layout

- **Header:** back arrow, title "Dados offline", subtitle "Armazenamento local".
- **Scrollable body:**
  1. **Pending card:** 68 dp blue storage icon in a circle, pending count ("Nenhum registro pendente" or a pluralized count), approximate size, and the hint that upload happens when internet is available.
  2. **"Sincronização" section:**
     - Indeterminate progress bar while syncing.
     - Otherwise a determinate bar: full green when there are no pending records, empty otherwise.
     - Status text: "Enviando dados…", "Todos os dados foram sincronizados.", "Aguardando conexão com a internet.", or "Preparando envio…".
     - Sync error in orange, when present.
  3. **"Armazenamento" section:** used/budget values, a progress bar (orange at ≥ 90 % usage), and a colored dot with "xx.x% utilizado".
  4. **Last sync card:** "Última sincronização" and the formatted time or "Nunca".
  5. **"Gerenciar dados" section:** a tappable "Ver registros armazenados" row with the stored-record count and a chevron.

### Functionality

- Tapping "Ver registros armazenados" opens Stored records.
- Android back on the summary exits to Home; on internal routes it steps back one level.

---

## 8. Stored Records

**File:** `ui/offline/StoredRecordsScreen.kt`

### Purpose

List the locally stored metric records with their upload status and open a record's details.

### Layout

- **Header:** back arrow, title "Registros armazenados", subtitle "Dados locais".
- **Summary row:** three equal cards with large colored counts:
  - "Pendente" in orange.
  - "Sincronizado" in green.
  - "Rejeitado" in red.
- **Content:**
  - Empty state card with a green inbox icon, "Nenhum registro armazenado", and an explanation that collected records appear here while waiting for upload.
  - Otherwise a lazy list of record cards.
  - If the list reaches the recent-record limit, a centered note says only the most recent records are shown.

#### Record card

- Collection date/time and a status pill ("Pendente", "Sincronizado", "Rejeitado") with the matching color.
- Chevron indicating that the card opens details.
- VIN line, or "VIN não informado".
- Reading count.
- Status detail:
  - Uploaded: "Sincronizado em <date/time>".
  - Pending: "Aguardando sincronização".
  - Rejected: the rejection reason, or just "Rejeitado".
- The entire card is tappable.

### Functionality

- Tapping a record opens Record details.
- Back returns to the Offline summary.

---

## 9. Record Details

**File:** `ui/offline/MetricDetailsScreen.kt`

### Purpose

Show every parameter captured in one stored record, together with its upload status and metadata.

### Layout

- **Header:** back arrow, title "Detalhes da coleta", subtitle "Parâmetros registrados".
- **States:**
  - Loading/idle: centered progress indicator.
  - Not found: card with a green inbox icon, "Registro não encontrado", and an explanation that the record is no longer in local storage.
  - Loaded: scrollable content.
- **Loaded content:**
  1. **Record summary card:** date/time, status pill, VIN line, status detail (uploaded at / waiting / rejection reason), and "Tentativas de envio: N" when attempts are greater than zero.
  2. **"Parâmetros" section:** one card per parameter in the fixed order:
     - Tinted icon badge and parameter label.
     - Value or "Indisponível", plus the unit when available.
     - A progress bar positioned within the parameter's configured range (only when available).
     - "Faixa: min – max unit".

### Functionality

- Read-only view of the stored snapshot.
- Back returns to Stored records.

---

## 10. Profile

**File:** `ui/profile/ProfileScreen.kt`

### Purpose

Show the signed-in user, provide shortcuts to vehicles and connection settings, configure the collection interval, show app information, and sign out.

### Layout

- **Header (dark navy):** title "Perfil" and a settings icon that opens the collection settings dialog.
- **Scrollable body:**
  1. **Identity card:** circular person avatar, display name (or username when the display name is blank), and email when available.
  2. **Menu rows** (icon, title, subtitle, chevron):
     - "Meus veículos" — subtitle with the vehicle count.
     - "Configurações de coleta" — subtitle "Intervalo: N s".
     - "Conexão" — subtitle combining ELM327 and internet state, e.g. "ELM327 conectado · Internet disponível", "ELM327 conectado · Internet indisponível", "ELM327 desconectado · Internet disponível", or "ELM327 desconectado · Internet indisponível".
     - "Sobre o aplicativo" — subtitle "Versão X.Y.Z".
  3. **"Sair" button:** full-width outlined red button.
- **Dialogs:**
  - **Collection settings:** title, pluralized explanation, current value "A cada N s", a stepped slider between the minimum and maximum collection intervals, min/max labels, and "Salvar"/"Cancelar" actions.
  - **About:** title and a short app description with the current version, plus "Fechar".

### Functionality

- "Meus veículos" opens the Vehicles screen; back returns to Profile.
- "Conexão" opens the Connection screen; back returns to Profile.
- The settings icon and "Configurações de coleta" open the interval dialog.
- Saving the interval persists the new collection cadence.
- "Sair" signs the user out; the auth session then returns the app to the Login screen.

---

## Cross-screen behavior

- **State ownership:** screens render state and forward events; ViewModels coordinate auth, vehicles, connection/discovery, metrics collection, sync, and storage.
- **Unavailable values:** always rendered as "--" or "Indisponível", never as zero.
- **Success feedback:** shown only after the underlying operation actually succeeds.
- **Error handling:** activation, deletion, discovery, collection, and sync failures appear as inline banners/text or dedicated cards, with retry where applicable.
- **Loading:** centered progress indicators for screen-level loads; inline spinners for in-flight actions.
- **Localization:** all user-facing text comes from `res/values/strings.xml` (currently Portuguese).
- **System bars:** dark headers request light status-bar icons; the Login screen forces dark system bars over its photo background.
