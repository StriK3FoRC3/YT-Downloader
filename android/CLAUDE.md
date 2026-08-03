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
| `res/drawable/ic_launcher_*`, `res/drawable/ic_notification.xml`, `res/drawable/ic_splash_*`, `res/animator/splash_*` | The app mark, the status-bar icon and the launch animation. All traced from `../icon.ico`; see "Icon and splash". |

### Share sheet

A shared link **fills the link box; it does not queue anything.** A shared video is
usually the moment the user decides whether they want audio or video, so the format,
resolution and profile pickers have to come before the commit. Successive shares append
a line, so several videos can be collected and configured in one go.

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

### Icon and splash

The launcher icon and the launch animation are traced from `../icon.ico` and live in that
file's 256-unit coordinate space, scaled into the 108dp adaptive canvas by the outer group
in each vector. Do not redraw them by hand — see the root `CLAUDE.md`.

- **The mark is red, the app is purple.** `ic_launcher_background` is `#FFFA3037`, sampled
  from the source. This is the one place the brand and the in-app accent diverge, and it is
  deliberate: the mark predates the palette. Do not "fix" it to `#9F00FF`.
- The glyph is scaled to **0.27874** and centred so it fits inside a 66dp circle. That is
  what keeps every launcher mask — circle, squircle, rounded square — from clipping it.
  Changing the scale means re-checking all three masks.
- `ic_launcher_foreground` carries the play mark as a **second subpath knocked out with
  `evenOdd`**. `ic_splash_logo` cannot do that: a mark scaling up inside a same-coloured
  hole is invisible, so it uses a solid badge with the mark as a separate red path. The two
  files therefore hold different badge geometry on purpose.
- `ic_notification.xml` is the same mark, not a generic arrow, but it is at the edge of
  what 24dp holds: the play mark survives only as a knockout and the tray as a hairline.
  Dropping the tray to buy room does not work — the badge sets the width, so the arrow
  just gets smaller and it reads less like a download.
- **`ic_splash_logo.xml` must hold the FINISHED logo.** It is not just the animation's
  base drawable, it is the *entire* splash below API 31 — see the next bullet — so any
  element parked at `fillAlpha="0"` or behind a zero-area clip is invisible on every
  Android 8-11 device. Holding the animation's first frame here shipped a bare red disc
  with no logo to exactly those devices. Nothing is lost: every animator in
  `res/animator/splash_*.xml` declares its own start (`valueFrom`, or a `fraction="0"`
  keyframe), so API 31+ playback ignores the resting state declared here.
- **The launch animation only plays on API 31+.** `androidx.core.splashscreen` back-ports
  the Android 12 splash *layout*, not its animation: below 31 the icon is composited into
  the window background — painted by the system before the process exists — and the
  library's pre-31 path never calls `Animatable.start()`. Treat pre-31 as a static logo.
- The arrow animates its **clip**, never its position. It sits where the finished logo puts
  it and the clip window grows over it; moving it instead leaves the badge's slot half
  empty mid-flight, which reads as a missing piece rather than a transfer.
- **The splash is held open on purpose**, by `SPLASH_HOLD_MS` in `MainActivity`, **and only
  on API 31+**. Without it the app wins the race and the animation is cut off after a frame
  or two — on a warm start it never becomes legible. That constant is a *pause on the
  finished logo*, not a speed control: the choreography's timing lives in
  `res/animator/splash_*.xml`, and changing one without the other either truncates the
  animation or leaves dead air. Below 31 nothing animates, so the hold is skipped rather
  than spending a second of launch latency on a motionless image.
- `Theme.YtDownloader.Splash` must inherit **`Theme.SplashScreen.IconBackground`**. Only
  that variant maps `windowSplashScreenIconBackgroundColor` onto the platform attribute.
  Under the plain `Theme.SplashScreen` the item is accepted and silently ignored, the red
  disc never appears, and the glyph renders onto the window background as a white badge
  with a red play mark. Nothing warns you; it only shows up on a device.

### Surviving the device

The app is one column on every screen, but "one column" is not "one width", and an Activity
is not a safe place to keep anything.

- **Cap and centre the column.** `MaxContentWidth` (home) and `MaxSettingsWidth` (settings)
  are 640dp. Phones are already narrower, so this is invisible there; without it a 10"
  tablet strands each row's label and value at opposite ends of 1280dp.
- **UI state that a user can see must be `rememberSaveable`, not `remember`.** Rotating,
  folding, or unfolding recreates the Activity. The open picker sheet is the case that
  bit: a plain `remember` dropped it mid-choice.
- **Anything the user typed belongs in `SavedStateHandle`.** A ViewModel survives rotation
  but not process death, and the link box holds work collected over several trips to
  YouTube — being killed in the background is the normal end of that workflow, not an edge
  case.
- Text sizes are `sp` and rows are `defaultMinSize`d rather than fixed-height, so a 1.5x
  font scale grows them instead of clipping.

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

### Release builds

R8 breaks things unit tests cannot see. Two rules were only found by installing the
release APK and reading the deobfuscated trace:

- **Jackson** — `YoutubeDL` holds a static `ObjectMapper`; stripping its internals made the
  static initialiser throw `ExceptionInInitializerError` with no mention of Jackson.
- **Apache Commons Compress** — `ExtraFieldUtils` reflectively instantiates
  `ZipExtraField` implementations, and losing their constructors broke the first-run
  unpack of Python and FFmpeg entirely.

**Always install and launch the release APK before calling a build good.** `assembleRelease`
succeeding proves nothing. When it fails, deobfuscate with
`app/build/outputs/mapping/release/mapping.txt` rather than guessing at the short names.

### Initialisation order in sealed hierarchies

Not every device-only crash is R8's fault, and assuming it is sends you chasing keep rules
for a bug that reproduces on a plain JVM.

A `companion object` field that builds a collection out of the sealed type's own members
**deadlocks on itself**. An interface with a body-carrying property or function — such as
`BitrateSetting.label` — is an interface with a *default method*, so initialising any
implementation must initialise the interface first, which builds the companion, which
reads the implementation's `INSTANCE` while that assignment is still pending. The JVM does
not re-enter an initialiser already running on the same thread; it returns null instead of
blocking, and the collection keeps a null entry for the life of the process.

Defer such collections past class initialisation (`by lazy`). `BitrateSettingInitOrderTest`
guards this one, and shows the shape of the test: it must be the **first thing in its JVM**
to touch the type, or it initialises the classes in the safe order and passes vacuously.

Signing is optional by design: `keystore.properties` and `*.jks` are gitignored, and
`assembleRelease` produces an unsigned APK when they are absent so a fresh clone still
builds.

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

Current baseline: 141 unit tests passing, lint clean apart from two `OldTargetApi`
notices. `lint.xml` silences dependency-freshness noise only — never add a suppression
there to make a real finding go away.

An **API 26 emulator is the cheapest way to catch what a modern phone hides** — the AVD
`api26s7` is configured to the Galaxy S7's metrics (1440x2560, 640dpi, Android 8.0), which
is where the bitrate-picker crash reproduced and a 160dpi API 26 image did not. Drive it
with `adb shell input tap`; races surface on repetition, not on a single pass.

Install the **per-ABI** APK (`app-arm64-v8a-debug.apk`, ~79 MB), not the 232 MB universal
build, and install on a **physical device** — emulators misrepresent both FFmpeg
throughput and thermal behaviour, which are the two things most worth measuring here.

Not yet exercised on hardware: an end-to-end download, SAF writes, the foreground service
across backgrounding, and the WebView sign-in. Everything above that line is verified
only by unit tests and a successful build.

## Child DOX Index

None yet. Add one per package if a package develops rules that do not belong in this file.
