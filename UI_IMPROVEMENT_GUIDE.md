# DriveData — UI improvement guide for the Home, Vehicles and Vehicle Details screens

You are improving the look of three existing Jetpack Compose screens: `HomeScreen.kt`, `VehicleListScreen.kt` and `VehicleDetailsScreen.kt`. This is a visual pass. The behavior, state, navigation and copy of these screens are already correct, and `screens.md` is the source of truth for all of it. If something is not described in `screens.md`, it does not belong on the screen, however good it would look. That means no new buttons, no new data rows, no new navigation, and no new ViewModel state. Leave the ViewModels alone, and do not change what a screen renders from, only how it renders.

The visual target is `DriveDataMockup.png` for mood and `drivedata-home-vehicles.html` for the concrete result. The HTML is a web approximation of the Compose UI, so treat its proportions and hierarchy as the reference, not its markup. Where the mockup and `screens.md` disagree, `screens.md` wins. The mockup has features the app does not have (Bluetooth pairing, MAP pressure, a "+" button on the vehicle list, trip history, an "Histórico" tab), and none of them should appear.

## How to work

Change one screen at a time and keep each change reviewable. Before editing, read the screen file and the section of `screens.md` that describes it, and write down for yourself the list of elements it must contain. After editing, check that every element on that list still exists, still has the same text, and still handles the same taps. Then add or update a `@Preview` for the screen's main states, so the result can be inspected without running the app. A visual pass that silently drops the "Registros rejeitados" row, which only shows when the count is above zero, or the "--" placeholder for unavailable readings, is a regression even if the screen looks better.

Reuse the existing theme colors (`HomeBackground`, `LoginBackgroundTop`, `HomeSurface` and the existing accents) wherever they already match the values below, and only add a new token when nothing close exists. Put new shared pieces, such as a metric badge or a section title, in one place and reuse them across the three screens instead of copying styling into each file.

## Visual language

The look is a dark navy header over a light content area, with white rounded cards and four status accents. Keep that structure on all three screens.

| Role | Value | Notes |
| --- | --- | --- |
| Header | vertical gradient `#143D70` to `#0D2C52` | Light status-bar icons, as `screens.md` already requires |
| Content background | `#EEF3FA` | |
| Card | white, 16 dp corners, 12 dp padding, a very soft shadow | One radius for cards, 12 dp for inner elements such as selectors and buttons |
| Blue | `#1F6FFF` | Primary actions, selected tab, active vehicle border |
| Green | `#1FA855` | Connected, synced, healthy |
| Orange | `#F29A1A` | Warnings, usage at 90% or more |
| Red | `#E5483C` | Rejected, delete, errors |
| Muted text | `#5D6C82` | Labels and secondary text; keep contrast readable on white |
| Divider | `#E1E8F2` | |

Each of the four metrics keeps its own color, used for its icon badge and its sparkline: RPM green `#12B886`, speed blue `#1F6FFF`, engine temperature orange `#F29A1A`, engine load purple `#8B5CF6`. The badge background is the same hue at about 12% opacity.

Typography should be Roboto, which is the Compose default. Use 18 sp medium for header titles, 12 sp for header subtitles, 14 sp bold for section titles, 24 sp bold for large metric values with the unit at 12 sp in muted color, and 12 sp for labels. Nothing meaningful should be smaller than 11 sp. Spacing is a 14 dp screen padding with 12 dp between cards and 10 dp between items inside a grid.

## Icons

Use the Material icons from `androidx.compose.material.icons`, the same set the rest of the app already uses, and add `material-icons-extended` if a name below is missing. Bottom navigation keeps the rule from `screens.md`: filled icon and brand blue when selected, outlined icon and muted gray when not. Do not draw custom vector icons or use emoji.

| Place | Icon |
| --- | --- |
| Tabs | `Home`, `DirectionsCar`, `BarChart`, `CloudDownload`, `Person` |
| Header | `ArrowBack` (auto-mirrored), `ExpandMore` on the vehicle selector |
| Collection ring and vehicle avatars | `DirectionsCar` |
| Collection details | `Wifi` for OBD connection, `Sync` for last sync, `CloudUpload` for pending data, `ErrorOutline` for rejected |
| Metrics | `AvTimer` for RPM, `Speed` for speed, `Thermostat` for temperature, `DonutLarge` for load |
| Connection status cards | `SettingsInputAntenna` for ELM327, `Wifi` for internet, `Storage` for local storage |
| Rows and actions | `ChevronRight`, `CheckCircle` for the active vehicle, `DeleteOutline`, `Refresh` |

Every icon that carries meaning needs a `contentDescription`, and decorative ones should pass `null`. Touch targets, such as the delete icon and the vehicle selector, must be at least 48 dp even if the drawn icon is smaller.

## Home

The collection card is the hero of the screen, so give it the most visual weight. On the left is a 112 dp ring drawn with `Canvas`, green while collecting and red while stopped, with a car icon and the "Coleta ativa" or "Coleta parada" label inside it. A soft tinted fill inside the ring and rounded stroke caps make it feel finished. On the right are the detail rows from `screens.md`, each with a small muted icon, a muted label and a medium-weight value. The ring is a state indicator, not a progress meter, so it must not imply a percentage that the app does not compute.

The four parameter tiles form a 2×2 grid. Each tile has a small tinted icon badge with the label beside it, the value large with its unit, a sparkline with a soft filled area under the line, and the configured range at the bottom. The sparkline should stretch to the tile width. Unavailable values render as "--", never as zero, and the sparkline is simply empty in that case. "Ver todos os parâmetros" is a card row in blue with a chevron. The three status cards are equal height, centered, with the icon colored by state: green when connected or available, red when internet is unavailable, and neutral for storage.

## Vehicles

Each vehicle is a card with a 64 dp rounded-square car icon on a pale blue background, the name in medium weight, the plate (or VIN when there is no plate) in muted text, and the status line with its colored dot. The active vehicle has the 2 dp blue border and the `CheckCircle` on the right, and inactive vehicles have the 1 dp gray border and no trailing icon. Keep the dismissible error banner, the loading indicator and the empty state from `screens.md` and give them the same card language as the list so they do not look like a different screen. There is no add button on this screen.

## Vehicle details

Group the content into the same sections `screens.md` defines, with clear section titles between cards. The identity card holds the 56 dp circular car avatar, the name with the VIN below it, the "Ativo" badge when active, and the red delete icon. When the vehicle is not active, "Definir como ativo" appears as the primary button directly under that header, followed by the plate and the identity rows (VIN, fabricante, número de série, with "Não disponível" for missing values). The adapter card is a plain list of label and value rows. The supported-parameters card lists each PID with its colored badge, label, unit and hexadecimal PID, and a switch on the right. Switches use blue when on and a light gray when off. "Descobrir parâmetros novamente" is the outlined secondary button with a `Refresh` icon and stays disabled when no adapter is configured. Keep the busy card, the delete confirmation dialog and the not-found state, and style them with the same card language.

## Quality checks before you finish

Verify that every string still comes from `res/values/strings.xml`, and add nothing hard-coded. Check that the screens still look right in the smallest supported width and with large font scaling, since long labels like "Temperatura do motor" and "Armazenamento local" wrap and must not clip. Check text contrast against the card background, and make sure color is never the only carrier of meaning: a status dot always comes with its text. Finally, compare each result with the reference HTML side by side, and list in your summary anything you could not reproduce, such as the vehicle photos and the ELM327 illustration from the mockup, which need image assets the app does not have yet.
