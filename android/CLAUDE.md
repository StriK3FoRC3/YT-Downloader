# CLAUDE.md — android/

## Purpose

The Android port of YT Downloader. Kotlin + Jetpack Compose, wrapping yt-dlp and FFmpeg
via `youtubedl-android`. Feature target for v1: core download + queue, smart bitrate
passthrough, download profiles, and yt-dlp self-update.

## Ownership

| Package | Owns |
| --- | --- |
| `core/` | Pure domain logic. **No Android dependencies** — must run on a plain JVM. `FormatSelector`, `BitrateLadder`, `TitleCleanup`, `MetadataArgs`, `QueueParser`, `ArgsBuilder`, `ProgressParser`, `ProgressTracker`, `OutputNaming`, `NetscapeCookies`. |
| `data/` | Room (`queue_items`, `profiles`) and DataStore (scalar settings). |
| `ytdlp/` | `YtDlpEngine`, `DownloadLogger`, `OutputFinalizer`, `cookie/` providers. |
| `work/` | `DownloadQueue` (bounded-concurrency scheduler) and `DownloadService` (foreground). |
| `ui/` | Compose screens, theme, motion, `components/`. |

### Cookie providers

`WebViewCookieProvider` is the working path and must stay standalone.
`MicroGCookieProvider` is a **deliberately unimplemented spike** — `capture()` returns
false and logs. Do not "finish" it by writing a plausible `getAuthToken` call: the
`weblogin:` token type is unverified, and a silently-failing provider that appears in the
settings list is worse than an explicit gap. Validate on a microG device first; the
procedure is in that file's KDoc.

## Local Contracts

### Behaviour

- Download behaviour comes from `../docs/download-rules.md`. Divergences from Windows are
  permitted only where that file's "Platform divergences" table says so.
- `core/` stays free of Android imports. This is what keeps the rules unit-testable and
  is the boundary that makes the port worth doing.
- Options are **snapshotted into an immutable `DownloadRequest` when an item is queued.**
  Never read UI state from a running download — that is the reference implementation's
  worst structural bug and it must not be reproduced.

### Platform constraints

These are Android facts, not preferences. Do not attempt to work around them.

- Executables run only from `nativeLibraryDir`. Anything shipped in app-writable storage
  cannot be exec'd (API 29+ W^X).
- Only yt-dlp is updatable, because it is a Python payload rather than an exec'd binary.
  FFmpeg ships as an APK-bundled `.so`.
- Deno has no Android build, but the JS-challenge role is covered: the library bundles
  **QuickJS** (`libqjs.so`) and passes `--js-runtimes quickjs:<path>` on every invocation
  itself. Never emit a JS-runtime flag from app code — it would conflict.
- `--cookies-from-browser` cannot work; the sandbox forbids reading another app's cookie
  store. Cookies come from a file produced by an in-app WebView login.
- Scoped storage: direct filesystem access is limited to `Download/` and `Documents/`.
  Anywhere else requires SAF and `ContentResolver`.
- Manifest must set `android:extractNativeLibs="true"` and declare the `dataSync`
  foreground service type.

### Performance

Battery and thermal budget are first-class constraints, not polish.

- One `getInfo()` extraction per item, feeding both the bitrate decision and the
  download. Never probe and then re-extract.
- Parallel downloads default 2, cap 3.
- Audio passthrough over transcode wherever `download-rules.md` §4 permits it. Tell the
  user *before* they commit when a choice forces a re-encode.
- Per-item temp directory then a single move. No directory scans proportional to library
  size, especially not over `ContentResolver`.
- Logging is buffered on a single writer coroutine. Full process output is debug-level
  only.

### Design

The WinForms UI is the **anti-reference**. Keep its palette, reject its execution.

Palette, carried over verbatim:

| Token | Value |
| --- | --- |
| Background | `#191919` |
| Surface | `#232323` |
| Surface raised | `#2C2C2C` |
| Accent | `#9F00FF` |
| Accent hover/pressed | `#B537FF` |
| Text | `#F5F5F5` |
| Muted text | `#B4B4B4` |
| Success | `#69D26E` |
| Error | `#F44336` |

Two rules that fix what is actually wrong with the original:

1. **Purple is the only brand accent.** The Windows app has yellow, blue, purple, red and
   green buttons competing on one row, so nothing reads as primary. Success/error/warning
   colours are reserved for *state* — a row's status, a progress ring's completion — and
   are never used to colour a button.
2. **Hierarchy over density.** The settings screen's flat grid of 20+ checkboxes becomes
   grouped cards with collapsible sections and 48dp touch targets.

Motion is a requirement, not decoration: container transform on item → detail,
`animateItemPlacement` for queue reflow, an animated progress ring that transitions
purple → green on completion, staggered entry as playlists expand, rolling-digit speed
and elapsed counters, spring-based press feedback, shimmer while extracting.

**Motion budget:** animate transform and alpha only. No animation may trigger layout or a
recomposition storm. A phone mid-FFmpeg-transcode has no CPU to spare, and a janky
progress ring during a slow download is worse than none.

## Work Guidance

- Load the `frontend-design` skill before building or reshaping UI.
- A rule ported into `core/` without a unit test is not ported.

## Toolchain

There is no system JDK on this machine. Gradle needs `JAVA_HOME` exported:

```bash
export JAVA_HOME=~/.local/jdk/jdk-21.0.12+8
```

That JDK (Temurin 21 LTS) was installed user-space, no sudo, and can be replaced with any
JDK 17+ without changing the project.

Version floors that are **not** free choices:

- **AGP 9.x is mandatory.** Hilt's Gradle plugin hard-fails below AGP 9.0.0.
- **AGP 9 requires Gradle 9.x** and removes the standalone `org.jetbrains.kotlin.android`
  plugin — Kotlin support is built in. Applying it now fails the build.
- **minSdk 26**, because notification channels are required by the foreground service
  that owns the download queue.

## Verification

```bash
export JAVA_HOME=~/.local/jdk/jdk-21.0.12+8
./gradlew :app:testDebugUnitTest    # core/ logic vs ../docs/download-rules.md
./gradlew :app:assembleDebug
./gradlew :app:lintDebug            # expect 0 errors
```

Current baseline: 95 unit tests passing, lint clean apart from two `OldTargetApi`
notices. `lint.xml` silences dependency-freshness noise only — never add a suppression
there to make a real finding go away.

Install the **per-ABI** APK (`app-arm64-v8a-debug.apk`, ~79 MB), not the 232 MB universal
build, and install on a **physical device** — emulators misrepresent both FFmpeg
throughput and thermal behaviour, which are the two things most worth measuring here.

Not yet exercised on hardware: an end-to-end download, SAF writes, the foreground service
across backgrounding, and the WebView sign-in. Everything above that line is verified
only by unit tests and a successful build.

## Child DOX Index

None yet. Add one per package if a package develops rules that do not belong in this file.
