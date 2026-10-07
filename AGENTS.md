# Audimmory — agent guide

Native **Android** audiobook client (Kotlin/Jetpack Compose) for the
[Grimmory](https://github.com/grimmory-tools/grimmory) self-hosted book server
(Java/Spring, a BookLore fork). Offline-first: browse the audiobooks in the
user's Grimmory libraries, download them (single `.m4b`/`.m4a`/`.mp3`/`.opus`
files, or folder-based books made of many `.mp3` tracks), play them without a
connection, and sync progress, bookmarks and listening sessions back to the
server over its JWT-authed REST API.

Audimmory is a **fork of Pageless Mobile** (GPL-3.0-or-later). Keep `LICENSE`,
the upstream copyright lines and `NOTICE` (the GPL §5(a) modification notice)
intact; add a dated line to `NOTICE` for substantial changes. The Grimmory
server is a separate project (AGPL-3.0); never copy its code into this app —
talk to it only over the network API. See `README.md` for the build/run/connect
docs and the endpoint table; this file captures conventions and gotchas.

## Toolchain & build

- **JDK 17 or newer.** AGP 9 requires JDK 17+ and Gradle 9.6.1 runs on JDK
  17–26, so Android Studio's bundled JBR (`/opt/android-studio/jbr`, currently
  JBR 25) is fine, as is `~/.jdks/jbr-21.0.11`. CI uses JDK 21. Do not pin
  `org.gradle.java.home` in the repository: F-Droid and CI use different JDK
  paths. Android Studio's Gradle JVM comes from `.idea/gradle.xml`
  (`#GRADLE_LOCAL_JAVA_HOME`) plus `java.home` in `.gradle/config.properties`.
  Historical note: AGP 8.7.3 rejected JDK 25 with a bare
  `* What went wrong: 25.0.2` error, which is why the JDK used to be pinned to
  21. The AGP 9 upgrade removed that constraint.
- **Android SDK**: platform `android-36`, build-tools `36.0.0` (AGP 9 minimum),
  platform-tools. Export `ANDROID_HOME=~/.Android` (or set `sdk.dir` in
  `local.properties`) before running Gradle. The SDK root is `~/.Android` — a
  single root holding
  `platforms/`, `build-tools/`, `platform-tools/`, and `emulator/`. Keep it that
  way: a split layout (build packages in one root, `platform-tools` in another)
  builds fine but fails `:app:installDebug` with
  `Cannot run program ".../platform-tools/adb"`. Put the SDK's `platform-tools`
  ahead of `/bin` on `PATH` so its `adb` beats a distro `android-tools` build;
  mismatched versions restart the adb server underneath running tools.
- `namespace` / `applicationId` = **`org.audimmory.mobile`**; `minSdk 26`,
  `compileSdk`/`targetSdk 36`.
- **AGP 9 uses built-in Kotlin**: `org.jetbrains.kotlin.android` is deliberately
  **not** applied — it is incompatible with AGP 9's new DSL, and AGP compiles
  Kotlin itself. Do not re-add it. For the same reason there is no
  `android { kotlinOptions { … } }` block; the JVM target lives in a top-level
  `kotlin { compilerOptions { jvmTarget … } }` block in `app/build.gradle.kts`.
  The legacy variant API (`applicationVariants` and friends) is gone; use
  `androidComponents` if build logic ever needs per-variant hooks.
- Common commands (from repo root):

  ```sh
  export JAVA_HOME=/opt/android-studio/jbr
  export ANDROID_HOME=~/.Android
  ./gradlew :app:installDebug      # build + install on the connected device
  ./gradlew testDebugUnitTest      # run the pure-core unit tests
  ./gradlew assembleDebug          # build a debug APK
  ./gradlew ktlintFormat           # auto-format Kotlin (run before committing)
  ./gradlew ktlintCheck            # verify formatting (also enforced in CI)
  ./gradlew lintDebug              # Android Lint (also enforced in CI)
  ```
- **CI** (`.github/workflows/ci.yml`) runs ktlintCheck, lintDebug,
  testDebugUnitTest, and assembleDebug on push/PR to `main`/`master`.
  `.github/workflows/release.yml` (manual dispatch) runs CI, commits static
  `version.properties` plus the version-code Fastlane changelog, tags that
  commit, builds a release APK, and publishes a GitHub Release. Its required
  notes input is the user-facing changelog and is limited to 500 characters.
  ktlint config lives in `.editorconfig` (Compose `function-naming` is disabled
  there).
- **Release signing is conditional and must stay that way.** `assembleRelease`
  produces an *unsigned* APK unless a signing key is configured through a
  git-ignored `keystore.properties` or the `AUDIMMORY_UPLOAD_*` environment
  variables. The unsigned default is load-bearing: F-Droid and forks build on
  machines with no key, and **F-Droid signs with its own key**, so making
  signing unconditional would break them. Never commit key material.
- **GitHub Releases are signed with the maintainer's key.** By the maintainer's
  decision (2026-10-07) the repository secrets hold the keystore
  (`AUDIMMORY_UPLOAD_KEYSTORE_BASE64`, base64 of the `.jks`) and
  `AUDIMMORY_UPLOAD_STORE_PASSWORD`/`_KEY_ALIAS`/`_KEY_PASSWORD`; `release.yml`
  decodes the keystore into `$RUNNER_TEMP` only for `assembleRelease`, and
  falls back to an unsigned APK when the secrets are absent. Users install
  these APKs with Obtainium, so every release must keep the same key
  (SHA-256 `5D:45:71:E0:…:E3:10:61`) or updates stop installing.
  `scripts/release.sh` is the local equivalent (same version scheme, commit,
  tag and changelog; signs from `keystore.properties`, refuses to publish an
  APK with any other certificate, and rolls back its commit and tag if the
  build fails before pushing). Keep the two in step.
- **Distribution:** not published in any store yet. `fdroid/org.audimmory.mobile.yml`
  is an unsubmitted draft recipe (see `fdroid/README.md`). Pageless itself is on
  F-Droid as `live.pageless.mobile`; that listing belongs to upstream and must
  not be reused. Google Play was evaluated upstream and abandoned (closed
  `pm-a6l` epic).
- **`targetSdk 37` is gated on Local Network Protections.** Android 17 enforces
  them for apps targeting SDK 37+: reaching a LAN address then requires the
  runtime `ACCESS_LOCAL_NETWORK` permission, and the usual Grimmory server is a
  LAN address. A denial blocks login, library, covers, downloads, streaming and
  sync at once, which reads as the app being broken. So the permission flow must
  land in the same change as the bump — see `pm-a6l.20`. Until then, do **not**
  declare or request `ACCESS_LOCAL_NETWORK`: Google's guidance is that apps
  targeting 36 or lower must not, because `INTERNET` already grants local access
  implicitly.
- **The Gradle configuration cache is on** (`org.gradle.configuration-cache=true`).
  It reuses the configuration phase, so build logic must keep declaring its
  inputs in ways Gradle can track. The version plumbing in `app/build.gradle.kts`
  already does — Gradle detects the `version.properties` read and correctly
  reports *"cannot be reused because file 'version.properties' has changed"* — so
  the release workflow's `VERSION_NAME`/`VERSION_CODE` overrides still take
  effect. If a build change starts reading files, environment variables or
  system properties at configuration time in a way Gradle cannot see, the cache
  will serve stale values instead of failing loudly; prefer the `providers.*`
  APIs. Disable per-invocation with `--no-configuration-cache` when debugging.
- Run Gradle commands sequentially. Parallel Gradle invocations have corrupted
  KSP incremental caches in this repo (`app/build/kspCaches/...`); if that
  happens, run `./gradlew --stop` and `./gradlew clean` before rebuilding.

## Project layout

```
app/src/main/java/org/audimmory/mobile/
  core/            # pure, layer-agnostic logic (duplicated from the server)
  data/download/   # WorkManager downloads + offline audio/cover file caching
  data/remote/     # GrimmoryApi + GrimmoryClient adapter, DTOs, auth/refresh + base-url
  data/local/      # Room entities/DAOs, AudimmoryDatabase, DataStore stores
  data/repository/ # offline-first repositories + mappers
  data/sync/       # WorkManager periodic/one-shot sync worker + scheduler
  playback/        # Media3 PlaybackService + PlayerConnection
  di/              # Hilt modules
  ui/              # Compose screens, theme, navigation
```

## Pure logic (`core/`)

`core/` holds pure rules inherited from Pageless, where they mirrored the
Pageless server (`PlaybackRules`, `Chapters.currentIndex`, `TimeFormat`,
`Plural`, `ProgressMerge`, `Iso8601`). They no longer mirror anything; they are
the app's own rules. `TrackTimeline` converts between the app's continuous book
timeline and Grimmory's per-track positions. `PlaybackRules` deliberately
finishes earlier than Grimmory's 99.5% "read" threshold; `GrimmoryClient`
reports 100% when the app considers a book finished so both sides agree.

Keep `core/` free of Android/Compose/Retrofit imports so it stays unit-testable
on the JVM.

## Grimmory API

- **All server access goes through `data/remote/GrimmoryClient`**, which adapts
  the raw Retrofit `GrimmoryApi` (wire DTOs in `GrimmoryDtos.kt`) into the app
  models in `Dtos.kt`. Repositories never call `GrimmoryApi` directly.
- Grimmory is a general book server: always filter to audiobooks
  (`fileType=AUDIOBOOK` on lists) and pass `bookType=AUDIOBOOK` on
  `/api/v1/audiobooks/*` so books that also have an ebook resolve the audio file.
- IDs are numeric `Long`s on the server and `String`s in the app. Series,
  narrators, genres (Grimmory "categories") and publishers are plain names; the
  name is the facet ID. Series are derived locally from the book list.
- **Collections = Grimmory shelves; playlists = Grimmory magic shelves.** The
  internal class/table names stayed `Collection*`/`Playlist*`; user-facing
  labels say "Shelves"/"Magic shelves".
- **Auth:** `POST /api/v1/auth/login` (username, not email) returns an access
  token (2 h) and a refresh token (30 days, rotated on every use, only valid 2
  minutes after issue). `TokenAuthenticator` renews on 401 for every caller of
  the shared `OkHttpClient` (Retrofit, Coil, ExoPlayer, downloads).
- `GET /api/v1/app/libraries` returns HTTP 500 on Grimmory v3.5.0 (Hibernate
  lazy-load bug); use `GET /api/v1/libraries`. `GET /api/v1/app/books/continue-listening`
  is always empty for admins (null library set in an `IN` clause); use
  `/app/books?status=READING&status=RE_READING&sort=lastReadTime`.
- Jackson serialises Lombok `boolean isX` fields as `x` (e.g. permissions
  `admin`, book files `primary`) — use `@SerialName`.
- App page sizes are capped at 50 by the server; `GrimmoryClient` pages.
- **Folder-based books** store progress and bookmarks as `trackIndex` +
  `positionMs` *within the track*; single-file books store an absolute
  `positionMs` with no track index. Convert with `TrackTimeline` at the
  `GrimmoryClient` boundary only. Progress is saved with both `fileProgress`
  (needs the audio `bookFileId`) and the legacy `audiobookProgress`, matching
  Grimmory's web player.
- Downloads respect Grimmory's `canDownload` permission (admins always may),
  even though the stream endpoints would work without it. The user decided
  this; don't remove the gate.

## Data & sync

- **Offline-first**: repositories read from Room and refresh from the server.
  Room entities use the server's IDs (as strings) and carry `dirty` + `deleted`
  flags. Grimmory has no change feed or tombstones: progress sync pushes dirty
  rows, then pulls per book for the server's in-progress books plus
  locally known progress (**last-write-wins by timestamp**, compared as
  instants). Bookmarks are created offline under a temporary UUID and re-keyed
  to the server ID on sync; a pulled per-book list is authoritative, so synced
  rows missing from it are deleted locally.
- Book summaries use structured UUID-backed `authors`, `narrators`, `genres`,
  `series`, and a singular nullable `publisher`; there are no scalar narrator or
  publisher API fields. `BookFacetEntity` and `CachedLibraryEntity` retain the
  IDs/names needed for offline Library filters. Collection and user-owned
  playlist filters derive from their existing Room membership tables. Language
  remains a free-form string and serves as both facet ID and display name.
  Filters use OR within one category and AND across categories.
- A successful full Library refresh is an authoritative scoped snapshot: stale
  books, facets, and libraries must not remain visible. All catalog/detail/cover
  writes and account/server cache clearing must use `CacheCoordinator` so an
  in-flight old-session response cannot repopulate Room after a clear.
- Listening history is captured locally as sessions/events. Each play→pause
  stretch is sent once to Grimmory as a reading session
  (`POST /api/v1/reading-sessions`) when its Pause event syncs; the other
  events stay on the device.
- Sync runs from `SyncWorker` every 15 minutes on connected networks, from
  Library pull-to-refresh, and during active playback for progress.
- `AudimmoryDatabase` currently uses
  `fallbackToDestructiveMigration(dropAllTables = true)` — schema bumps **wipe
  local data** (acceptable for a dev-stage app; can orphan downloaded files).
  Bump `version` when changing entities. `dropAllTables = true` is Room's
  recommended value and clears tables that leave the schema, so renamed or
  removed entities cannot strand rows.
- Networking is bearer-token auth via OkHttp interceptors; the same authed
  `OkHttpClient` is reused for API calls, Coil image loading
  (`ImageLoaderFactory` in `AudimmoryApp`), and ExoPlayer streaming so covers and
  audio carry the token.
- Offline downloads must cache the book detail, chapters, cover image (when
  `hasCover`), its tracks, and every audio file
  (`filesDir/audiobooks/<bookId>/<trackIndex>.<ext>`). Cover failure is a download failure: do not mark
  a book downloaded for offline use unless its cover was cached successfully.
  UI cover models should prefer the Room-tracked local cover path and only fall
  back to the authenticated server URL when no valid local cover exists.
- The **privacy policy has one source of truth**:
  `docs/privacy/privacy-policy.md`. The app links to its GitHub rendering
  through `PRIVACY_POLICY_URL` in `ui/components/PrivacyPolicy.kt`. Never bundle
  policy text into the app — a second copy will drift. Changing the URL
  requires an app update, so treat it as stable. (`docs/privacy/data-safety.md`
  is the inherited Pageless Play audit, not re-done for Audimmory.)
- **Backup and transfer are deliberately off**, and this needs **three**
  manifest pieces, not one: `android:allowBackup="false"` (covers API 26–30),
  `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+), and
  `android:fullBackupContent="@xml/backup_rules"` (legacy counterpart, keeps Lint
  and intent aligned). `allowBackup="false"` alone is **not** sufficient from API
  31: per Google's docs it disables cloud backup but on some OEM devices does
  *not* disable device-to-device transfers, and legacy `fullBackupContent` rules
  never affect D2D. Both XML files exclude every domain from both channels. The
  reason is the plaintext bearer token in the `session` DataStore; everything
  else re-syncs from the server on login. Rationale in
  `docs/privacy/data-safety.md` (finding F1).
- **Release builds permit cleartext HTTP** (`app/src/main/res/xml/network_security_config.xml`).
  This is a deliberate decision by the maintainer: their Grimmory is reached as
  `http://` over Tailscale, which encrypts the transport, and Android cannot
  allowlist tailnet IPs or MagicDNS names by host. Keep the user-CA trust
  anchors; never relax TLS verification itself.

## Playback (Media3)

- `PlaybackService : MediaSessionService` hosts the `ExoPlayer` + `MediaSession`
  and periodically persists progress to `ProgressRepository`.
- Media3 `Player`/`ExoPlayer` objects must be accessed on their application
  thread (main for the service/player connection). Capture player state on main
  before dispatching Room/network work to `Dispatchers.IO`; do not read
  `currentMediaItem`, `duration`, etc. from background coroutines.
- Bookmark previews use a separate ExoPlayer owned by
  `BookmarkPreviewViewModel`; they must not affect the bottom mini-player or save
  normal book progress. Stop and clear media items before releasing the preview
  player to avoid Media3 loader callbacks posting to a dead thread.
- The MediaSession's **session activity** is a `PendingIntent` to `MainActivity`
  so tapping the notification/lock-screen opens the app.
- **Media-notification seeking is a user setting** ("Allow position seeking on
  media notification controls", default **off**). Implemented by a
  `MediaSession.Callback` that grants external controllers a reduced
  `Player.Commands` set (stripping `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` /
  `COMMAND_SEEK_TO_MEDIA_ITEM`) so Media3 hides the scrub bar; the service
  observes the setting and re-grants commands to connected controllers at
  runtime via `setAvailableCommands`. This only limits the notification/lock
  screen — the in-app player is unaffected.
- **A book is always one `MediaItem`, even when it is many files.** Folder-based
  books put their track URIs and durations in the item's metadata extras;
  `AudiobookMediaSourceFactory` (used by the service *and* the bookmark preview
  player) joins them with `ConcatenatingMediaSource2` into one window. Build
  items through `PlayerConnection.buildItem`/`BookAudioResolver`, never with a
  bare URI. MP3s use constant-bitrate seeking because many lack a seek table.
- **External transport is jump-backward/forward, never track skip.** A book is a
  **single `MediaItem`** (chapters are UI-side arithmetic), so Media3's stock
  previous is actively destructive: `KEYCODE_MEDIA_PREVIOUS` reaches
  `Player.seekToPrevious()`, which finds no previous item and falls through to
  *"seek to 0"*, restarting the whole book. Three pieces implement the
  replacement, and they only work together:
  1. `AudiobookTransportPlayer` (a `ForwardingSimpleBasePlayer` wrapped around
     the ExoPlayer before `MediaSession.Builder`) **withholds**
     `COMMAND_SEEK_TO_PREVIOUS`/`_NEXT` and their `_MEDIA_ITEM` variants, and
     reports `jumpBackwardSeconds`/`jumpForwardSeconds` as the seek-back/forward
     increments. Do **not** switch this to a plain `ForwardingPlayer`:
     `MediaSessionImpl` takes its command set from the
     `onAvailableCommandsChanged` *event*, and `ForwardingPlayer` re-emits the
     delegate's unmodified set, so the change is reverted on every timeline
     change.
  2. `JumpControls` publishes the two buttons as **custom `SessionCommand`s** via
     `MediaSession.setMediaButtonPreferences`, granted to every controller in
     `onConnect` and handled in `Callback.onCustomCommand`.
  3. `Callback.onMediaButtonEvent` intercepts `KEYCODE_MEDIA_PREVIOUS`/`NEXT`
     (and the `SKIP_BACKWARD`/`FORWARD` variants) for Bluetooth headsets. It has
     to be there rather than on the player because Media3 checks command
     availability before dispatching, and step 1 withheld those commands. This
     callback runs before Media3's own key handling, on the app thread.
- **Why custom commands and not just nicer notification icons:** since Android 13
  the shade and lock screen ignore our notification's actions and render their
  own panel from the session's `PlaybackState`. Standard actions like
  `ACTION_SKIP_TO_PREVIOUS` get fixed system track-skip icons that cannot be
  overridden; only `PlaybackStateCompat.CustomAction`s carry an app icon, and
  `PlayerWrapper.createPlaybackStateCompat` only emits those for buttons holding
  a `COMMAND_CODE_CUSTOM` session command. Withholding the player commands
  (step 1) is what removes the system's own prev/next buttons. Verify with
  `adb shell dumpsys media_session`: our session must show `custom actions=[Back
  …, Forward …]` and an `actions` mask with bits 4/5
  (`ACTION_SKIP_TO_PREVIOUS`/`NEXT`) **clear**.
- `AudiobookNotificationProvider` only reorders Media3's own notification to
  "back / play / forward"; `DefaultMediaNotificationProvider` would otherwise
  append custom buttons after play/pause. Media3 only bundles numbered icons for
  5/10/15/30 s; the other values Settings offers fall back to unnumbered arrows.
  `setMediaButtonPreferences` triggers the notification rebuild itself, so no
  manual `onUpdateNotification` is needed when the amounts change.
- Absolute seeks (`COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM`) are untouched throughout,
  which is why the in-app skip and chapter buttons are unaffected. A
  chapter-per-`MediaItem` timeline would make prev/next natively meaningful and
  is the obvious long-term alternative, but it changes progress mapping and
  download/stream URI handling.
- **"Bookmark with the next-track button"** (default off) remaps
  `KEYCODE_MEDIA_NEXT` / `_SKIP_FORWARD` to create a bookmark at the current
  position. It is deliberately scoped to **hardware keys only** — the
  notification/lock-screen forward button is a custom command handled in
  `onCustomCommand` and keeps jumping, because it is visibly labelled "Forward
  N seconds". The point of the feature is the case where the user cannot see a
  screen: headset firmware tends to swallow multi-press gestures, so a single
  press of an existing button is the only reliable trigger.
- That path needs three guards, because **nothing downstream deduplicates**:
  `BookmarkRepository.add` mints a fresh UUID per call and `bookmarks` has no
  unique index on `(bookId, positionSeconds)`. So `mediaButtonKeyCode` drops
  auto-repeat (`repeatCount != 0`), `HeadsetBookmark.shouldCreate` debounces
  double-fires on `SystemClock.elapsedRealtime()`, and a debounced press is
  swallowed rather than falling through to a forward jump. It also mirrors
  `saveNow()`'s `EXTRA_PREVIEW` guard so a bookmark preview cannot bookmark
  itself.
- The bookmark is stored with `note = null`, identical to the in-app dialog with
  the note left blank; position and chapter are derived at render time, never
  stored. Confirmation is a `BookmarkHaptics` pulse (hence
  `android.permission.VIBRATE`): the press happens screen-off and pocketed,
  where toasts are suppressed for background apps on Android 12+ and never
  render on the lock screen. It uses `EFFECT_HEAVY_CLICK`, not the much fainter
  `EFFECT_TICK`, because it has to carry through a pocket while the user is
  listening to something else. Predefined effects have no duration parameter —
  they are vendor-tuned waveforms — so the length is fixed unless the code drops
  to `createOneShot`/`createWaveform` and gives up that tuning. `PlaybackService` otherwise only syncs *progress*,
  so it also calls `SyncScheduler.enqueueNow()` — without it a headset bookmark
  sits dirty for up to 15 minutes.
- The `SystemForegroundService` used by WorkManager needs
  `foregroundServiceType="dataSync"`, and the playback service uses
  `mediaPlayback`; both are declared in the manifest.

## Settings

- Player settings persist in `data/local/PlayerSettingsStore.kt` (DataStore).
  When adding a setting: add the field + default to `PlayerSettings`, a
  `*PreferencesKey` + read mapping + `set…` suspend fn in the store, a
  `viewModelScope`-launched setter in `SettingsViewModel`, and a row in
  `SettingsScreen`. Stores that use `@ApplicationContext` need it injected via
  Hilt.
- Theme mode (`System`, `Dark`, `Light`) is also stored in
  `PlayerSettingsStore` as `ThemeMode` and is collected by `MainActivity` before
  calling `AudimmoryTheme`. Keep new app-wide visual preferences in this same
  DataStore unless they belong to server-synced account settings.

## UI conventions

- Jetpack Compose + Material3. Primary nav is **top tabs** (Home/Library) +
  a hamburger **navigation drawer**; the **mini-player owns the bottom** (there
  is no bottom nav bar). Home/Library support pull-to-refresh.
- Library filtering and sorting are derived locally from Room so they work
  offline. Keep `LibraryFilterEngine` and `LibrarySortEngine` free of Android
  dependencies and unit-tested. Sort labels, default directions, nulls-last
  behavior, case-insensitive titles, compound surnames, and progress timestamp
  fields were aligned with the Pageless web app and are now the app's own.
- Grouped Library search is also a pure Room-derived projection. Keep
  `LibrarySearchEngine` Android-free and aligned with the web behavior: grid
  filtering starts immediately; grouped results start after two characters;
  Books match title/subtitle/authors/narrators/publisher; named facets are shown
  in grouped global results with scoped distinct-book counts; Progress is not a
  textual search group. Selecting a facet replaces all filters but preserves
  sort state.
- Book-detail links for authors, narrators, series, collections, playlists,
  genres, publisher, and language navigate through `Routes.libraryFilter` and
  initialize `LibraryViewModel` from optional route arguments. Keep normalized
  metadata links UUID-based; language uses its exact free-form value.
- Now Playing opens as a full-screen route with a slide-up transition and closes
  with a slide-down transition, including swipe-down gestures from the top/cover
  area.
- The connection status indicator renders next to screen titles and only shows
  warning states: red `WifiOff` for no validated internet, amber `CloudOff` when
  internet exists but server operations fail.
- Brand wordmark uses **JetBrains Mono** (`ui/theme/Type.kt`, bundled in
  `res/font/`). The in-app brand icon is **`R.drawable.ic_brand`** (real design
  PNG exported per density in `drawable-*/`), used in the Home/Library app bars.
- The palette is centralized in `ui/theme/Theme.kt` via `AudimmoryColors`
  (inherited from Pageless: primary purple `#8B5CF6`, dark background
  `#16141F`, dark surface `#1E1B2E`, JetBrains Mono for brand text).
- The Settings screen owns the user-facing theme selector and must expose the
  same three options as the web app: `System`, `Dark`, and `Light`.
- The media-notification small icon is **`R.drawable.ic_stat_audimmory`**, a
  white-tinted monochrome vector. It is deliberately *not* the launcher art:
  the launcher art is offset for the adaptive safe zone and looks lopsided at
  small sizes. When editing it, remember it renders **very small** in the status
  bar — keep bar gaps wide enough to survive downscaling, and verify by
  rendering the vector geometry at ~18px before shipping.
- Launcher icon is adaptive (maskable background + foreground + monochrome
  themed layer) under `mipmap-*` / `mipmap-anydpi-v26`.
- Debug builds use `applicationIdSuffix = ".debug"`, app label `Audimmory Dev`,
  and debug-only launcher assets under `app/src/debug/res/` (rotated 180deg) so
  the locally installed app is visually distinct and can live alongside a
  release/Play Store install.
- Some Material3 APIs (`PullToRefreshBox`, custom `Slider` track) require
  `@OptIn(ExperimentalMaterial3Api::class)`. To hide a `Slider`'s stop-indicator
  dot, supply a custom `track` with `drawStopIndicator = null` (linear progress
  bars use `drawStopIndicator = {}`).

## Testing on a physical device

- Use the machine's **LAN IP** (e.g. `http://192.168.50.96:6060`), *not*
  `10.0.2.2` (that's the emulator-only host alias). Grimmory listens on port
  6060; a throwaway server runs from Grimmory's `deploy/compose/docker-compose.yml`
  (create the admin with `POST /api/v1/setup`, then a library pointing at a
  folder of audiobooks).
- After making Android app changes, run `./gradlew :app:installDebug` when the
  user's physical device is connected so the updated build is installed for
  hands-on testing, unless the user asks not to install it.

## Commit conventions

- **Every commit subject starts with a Gitmoji**, then a space, then an
  imperative summary — e.g. `⬆️ Upgrade to AGP 9, Gradle 9.6.1 and Kotlin 2.3`.
  Use the **literal emoji character**, not the `:shortcode:` form.
- Pick the canonical emoji for the kind of change rather than inventing one. The
  authoritative list of 75 is <https://gitmoji.dev> (source of truth:
  `carloscuesta/gitmoji`).
- Keep the subject imperative and without a trailing period. Wrap the body at
  ~80 columns and use it to explain **why**, plus anything the diff cannot show
  (constraints, rejected alternatives, verification performed).
- Reference beads issues in a trailer: `Closes pm-a6l.17` when the commit
  finishes that issue, `Refs pm-a6l.3` for related context.
- The release workflow's automated commit uses 🔖 and **must keep its
  `[skip ci]` marker** (`.github/workflows/release.yml`); that marker is what
  stops the release commit from re-triggering CI.
- Historical exception: `🌅 Initial commit` (upstream Pageless) predates this convention (Gitmoji's
  equivalent is 🎉 `:tada:`). Leave it alone, but don't copy it.

Beads Dolt auto-push is enabled with a one-minute debounce. Run `bd dolt pull`
at the start of a work session; mutating `bd` commands may sync automatically.
This does not authorize Git commits or Git pushes.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:970c3bf2 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.

## Agent Context Profiles

The managed Beads block is task-tracking guidance, not permission to override repository, user, or orchestrator instructions.

- **Conservative (default)**: Use `bd` for task tracking. Do not run git commits, git pushes, or Dolt remote sync unless explicitly asked. At handoff, report changed files, validation, and suggested next commands.
- **Minimal**: Keep tool instruction files as pointers to `bd prime`; use the same conservative git policy unless active instructions say otherwise.
- **Team-maintainer**: Only when the repository explicitly opts in, agents may close beads, run quality gates, commit, and push as part of session close. A current "do not commit" or "do not push" instruction still wins.

## Session Completion

This protocol applies when ending a Beads implementation workflow. It is subordinate to explicit user, repository, and orchestrator instructions.

1. **File issues for remaining work** - Create beads for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **Handle git/sync by active profile**:
   ```bash
   # Conservative/minimal/default: report status and proposed commands; wait for approval.
   git status

   # Team-maintainer opt-in only, unless current instructions forbid it:
   git pull --rebase
   bd dolt push
   git push
   git status
   ```
5. **Hand off** - Summarize changes, validation, issue status, and any blocked sync/commit/push step

**Critical rules:**
- Explicit user or orchestrator instructions override this Beads block.
- Do not commit or push without clear authority from the active profile or the current user request.
- If a required sync or push is blocked, stop and report the exact command and error.
<!-- END BEADS INTEGRATION -->
