# Audimmory (Android)

Native Android audiobook client for [Grimmory](https://github.com/grimmory-tools/grimmory),
the self-hosted book server. The app is Android-only and offline-first: browse
the audiobooks in your Grimmory libraries, download them with their covers, play
without a connection, and sync playback progress, bookmarks, and listening
sessions back to the server over its token-authenticated REST API.

Both kinds of Grimmory audiobook are supported:

- **single-file** books (`.m4b`, `.m4a`, `.mp3`, `.opus`), with embedded
  chapters when the file has them;
- **folder-based** books, a directory of audio files (typically `.mp3`), which
  play as one continuous book with each file shown as a chapter.

Audimmory is a fork of [Pageless Mobile](https://github.com/dcaixinha/pageless-mobile)
by the Pageless contributors, adapted to Grimmory. See [`NOTICE`](NOTICE).

Built with Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit/OkHttp, Room,
DataStore, WorkManager, Coil, and Media3/ExoPlayer.

## Status

The core listening flow works against Grimmory v3.5:

- Sign in with a Grimmory username and password. Access tokens are renewed
  automatically with Grimmory's refresh token.
- Browse Home (continue listening, recently added, listen again) and the
  Library from Room-backed offline-first data. Only books with an audiobook
  file are shown. The Library filters offline by authors, narrators, genres
  (Grimmory categories), series, shelves, magic shelves, publisher, language,
  libraries, and progress.
- Open book details with metadata, chapters, progress, bookmarks, downloads, and
  listening history.
- Download books for offline playback (audio files, chapters and cover), when
  the Grimmory account has the download permission or is an admin.
- Play streamed or downloaded audio with Media3 background playback,
  notification/lock-screen controls, mini-player, and full Now Playing screen.
- Sync progress and bookmarks with Grimmory, so the position is shared with
  Grimmory's web player, and record finished listening sessions as Grimmory
  reading sessions.
- Configure player behavior in Settings: jump intervals, notification seeking,
  headset bookmark button, chapter tracks, and bookmark context time.

## Requirements

- **JDK 17 or newer** (Gradle 9.6.1 runs on JDK 17–26). Android Studio's
  bundled JBR works. Point `JAVA_HOME` at it or at any other JDK 17+.
- **Android SDK** with:
  - Platform `android-36`
  - Build Tools `36.0.0` (the minimum for AGP 9)
  - Platform Tools (`adb`)
- Set `ANDROID_HOME` to the SDK root, or create a git-ignored `local.properties`
  with `sdk.dir=/home/you/Android/Sdk`. Keep every package under **one** root:
  if `platform-tools` lives in a different root than `platforms`/`build-tools`,
  `assembleDebug` still works but `:app:installDebug` fails with
  `Cannot run program ".../platform-tools/adb"`.

Install SDK packages headlessly with Android command-line tools:

```sh
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

## Build, Test, Install

From the repository root:

```sh
export JAVA_HOME=/path/to/jdk-17-or-newer
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest      # run JVM unit tests
./gradlew assembleDebug          # build a debug APK
./gradlew :app:installDebug      # install on a connected device/emulator
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

`./gradlew assembleRelease` produces an **unsigned** release APK. Sign it with
your own key (see `keystore.properties` / `AUDIMMORY_UPLOAD_*` in
`app/build.gradle.kts`) or let a store such as F-Droid sign it. The APKs on
[GitHub Releases](https://github.com/enboig/audimmory/releases) are signed by
the release workflow with the maintainer's key (certificate SHA-256
`5D:45:71:E0:54:57:C4:FF:76:4B:A5:2D:9A:41:65:33:45:7E:16:96:3C:36:2C:79:4E:D2:70:EA:DD:E3:10:61`);
install them directly or track the repository with Obtainium.

To release from your own machine instead of GitHub Actions (needs
`keystore.properties`, a logged-in `gh`, and a clean, pushed `master`):

```sh
scripts/release.sh "What changed, for users (max 500 characters)"
```

It runs the same checks as CI, creates the same release commit and tag as the
workflow, builds and verifies the signed APK, then pushes and publishes the
GitHub Release. `--skip-checks` skips ktlint, Lint and the unit tests. Audimmory is not
yet published on F-Droid; see [`fdroid/README.md`](fdroid/README.md).

## Languages

The app is available in English, Catalan and Spanish, and follows the phone's
language. On Android 13 and later you can pick a different language just for
Audimmory in **Settings → Language** (or Android's per-app language settings).
Translations live in `app/src/main/res/values*/strings.xml`; see AGENTS.md
for the style rules.

## Connecting To A Server

**Debug builds** default to `http://10.0.2.2:6060`, Android Emulator's alias for
the host machine's `localhost:6060` (Grimmory's default port). On a physical
device, enter your computer's LAN IP on the login screen, for example
`http://192.168.50.96:6060`.

Debug builds allow cleartext HTTP for LAN development via
`app/src/debug/res/xml/network_security_config.xml`.

**Release builds** also allow `http://` server URLs, because Grimmory is often
reached over plain HTTP inside an encrypted private network such as Tailscale
(`http://100.x.y.z:6060` or a MagicDNS name), which Android cannot allowlist by
host. Use HTTPS whenever the network itself is not encrypted: over plain HTTP on
an untrusted network the password and tokens travel unencrypted. Release
defaults the URL field to `https://` and uses
`app/src/main/res/xml/network_security_config.xml`.

The certificate may be issued by a publicly trusted CA **or by a private CA the
user has installed on the device** (Android Settings → Security → Encryption &
credentials → Install a certificate → CA certificate). Certificates that are
neither system-trusted nor installed by the user are rejected.

OIDC-only accounts are not supported yet; the account needs a local Grimmory
password.

## Server Compatibility

Tested against Grimmory v3.5.0. The app uses these Grimmory endpoints:

| Purpose | Endpoint |
| --- | --- |
| Sign in / renew / sign out | `POST /api/v1/auth/login`, `/refresh`, `/logout` |
| Account and permissions | `GET /api/v1/users/me`, `GET /api/v1/version` |
| Libraries | `GET /api/v1/libraries` |
| Books (audiobooks only) | `GET /api/v1/app/books?fileType=AUDIOBOOK`, `GET /api/v1/app/books/{id}` |
| Home shelves | sorted and status-filtered `GET /api/v1/app/books` lists |
| Audio layout | `GET /api/v1/audiobooks/{id}/info` |
| Audio | `GET /api/v1/audiobooks/{id}/stream`, `/track/{n}/stream` |
| Covers | `GET /api/v1/media/book/{id}/audiobook-cover` (or `/cover`) |
| Progress | `GET`/`PUT /api/v1/app/books/{id}/progress` |
| Bookmarks | `/api/v1/bookmarks` |
| Shelves | `GET /api/v1/app/shelves`, `/app/shelves/magic` |
| Listening sessions | `POST /api/v1/reading-sessions` |

Two endpoints are deliberately not used because of Grimmory v3.5.0 bugs:
`GET /api/v1/app/libraries` answers HTTP 500, and
`GET /api/v1/app/books/continue-listening` is always empty for admin accounts.

### How Grimmory concepts map onto the app

- **IDs** are Grimmory's numeric IDs, stored as strings.
- **Series, narrators, genres, publishers** are free-text names in Grimmory, so
  the name doubles as the facet ID. Series are derived from the audiobook list.
- **Collections** in the app are Grimmory **shelves**; **playlists** are
  Grimmory **magic shelves**. Both are read-only on mobile.
- **Folder-based positions**: Grimmory stores progress and bookmarks of a
  folder-based book as a track index plus a position inside that track. The app
  converts to and from one continuous timeline (`core/TrackTimeline`).
- **Bookmarks** get their ID from the server. A bookmark created offline keeps
  a temporary local UUID until the next sync creates it on the server.
- **Listening history**: sessions are recorded locally (with play/pause/seek
  events) and each finished session is sent once as a Grimmory reading session.
  Individual events stay on the device.

## Offline And Sync Behavior

- Room is the local source of truth for books, metadata facets, libraries,
  chapters, progress, bookmarks, downloads, and listening history.
- Repositories read from Room and refresh/sync against the server.
- A successful Library refresh authoritatively replaces the scoped book/facet
  catalog. Cache writes and account/server cache clearing are serialized so an
  old session cannot repopulate Room after logout or a server switch.
- Progress and bookmarks use dirty/tombstone flags for offline-first sync.
  Grimmory has no change feed, so the pull side asks for the progress and
  bookmarks of recently played books (the server's in-progress books plus
  locally known progress).
- Playback progress uses last-write-wins by `lastPlayedAt`.
- Listening sessions are pushed once they end; a session left open by a killed
  app is pushed after it has been idle for 30 minutes.
- Periodic sync is scheduled with WorkManager every 15 minutes when network is
  connected. Active playback also attempts server progress sync every 60 seconds.
- Pull-to-refresh on Home, Library, and Book screens refreshes server data; the
  Library refresh also pushes queued progress/bookmark/history sync.
- Offline downloads store audio under app-private files, one directory per book
  with one file per track, and track completed downloads in Room. Removing a download removes both audio and cached cover
  paths.

## Playback

- `PlaybackService : MediaSessionService` hosts the main ExoPlayer and
  MediaSession.
- `PlayerConnection` exposes player state and commands to Compose screens.
- The mini-player owns the bottom of the app; there is no bottom navigation bar.
- Now Playing can be opened from the mini-player and dismissed by tapping the
  chevron or swiping down on the top/cover area.
- Media notification seeking is controlled by the setting “Allow position seeking
  on media notification controls”. When disabled, external controllers get a
  reduced seek command set so the notification/lock-screen scrub bar is hidden;
  the in-app player still has full seek support.
- External transport controls — Bluetooth headphones, the media notification and
  the lock screen — jump backward/forward by the configured “Jump
  backward/forward amount” instead of skipping tracks, and are labelled with
  matching skip icons. A book is loaded as a single media item, so Media3's stock
  previous would otherwise restart the book from the beginning.
- A folder-based book is still a single media item: `AudiobookMediaSourceFactory`
  joins its tracks with Media3's `ConcatenatingMediaSource2`, so seeking, chapters
  and jump controls work across file boundaries. MP3 files without a seek table
  use constant-bitrate seeking.
- The setting “Bookmark with the next-track button” (off by default) makes a
  press of next on a Bluetooth headset, car controls or a wired remote create a
  bookmark at the current position instead of jumping forward, confirmed by a
  short vibration. Useful for bookmarking without looking at the phone, since
  headsets tend to swallow multi-press gestures. The notification's forward
  button is unaffected and keeps jumping.
- Bookmark previews use a separate ExoPlayer instance inside the bookmark dialog
  and do not affect normal book progress.

## Pure Logic

`core/` holds pure, JVM-testable rules (finished threshold, chapter lookup, time
formatting, progress merge, ISO-8601 handling, and the folder-track timeline).
They were inherited from Pageless, where they mirrored that server; they are now
the app's own rules. Keep `core/` free of Android, Compose, Retrofit, and Room
imports.

## Project Layout

```text
app/src/main/java/org/audimmory/mobile/
  core/            # pure, layer-agnostic logic (no Android imports)
  data/download/   # WorkManager downloads + offline audio/cover file caching
  data/local/      # Room entities/DAOs, database, DataStore stores
  data/remote/     # Grimmory Retrofit API + adapter, DTOs, auth + base-url interceptors
  data/repository/ # offline-first repositories + mappers
  data/sync/       # WorkManager sync worker/scheduler
  di/              # Hilt modules
  playback/        # Media3 PlaybackService + PlayerConnection
  ui/              # Compose screens, components, navigation, theme
```

## Useful Notes

- `AudimmoryDatabase` currently uses
  `fallbackToDestructiveMigration(dropAllTables = true)`. Room schema bumps wipe
  local data, which is acceptable during this dev stage but can orphan
  downloaded files.
- The app-wide authenticated `OkHttpClient` is reused for Retrofit, Coil cover
  loading, ExoPlayer streaming, and download/cover caching so protected assets
  carry the bearer token.
- Brand icon assets live in `res/drawable-*/ic_brand.png`; notification small
  icon is `R.drawable.ic_stat_audimmory`; launcher assets live under `mipmap-*`.
  All of them, plus the store images, are generated by
  `scripts/icons/make_icons.py` from Grimmory's book logo with added
  headphones (see `NOTICE`).
- Native splash/window background is `@color/splash_background`, matching the
  app's dark purple surface tint.

## License

Copyright (C) 2026 Audimmory contributors
Copyright (C) 2026 Pageless contributors

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see <https://www.gnu.org/licenses/>.

Audimmory is a modified version of Pageless Mobile; see [`NOTICE`](NOTICE) for
the modification notice required by section 5(a) of the license.
