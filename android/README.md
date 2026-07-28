# YT Downloader for Android

A Kotlin + Jetpack Compose port of the Windows app, sharing its download rules and its
look. The Windows app in `src/` is unchanged and still builds via `build.bat`.

<!-- Screenshots: add android/screenshots/*.png -->

## What carries over

The download behaviour is identical, because both apps implement one written contract:
[`docs/download-rules.md`](../docs/download-rules.md).

- Audio in MP3, M4A, FLAC, WAV, OGG, OPUS or AAC
- Video in MP4, MKV or WEBM, up to 4320p
- Playlist expansion, parallel downloads, live/upcoming rejection
- Download profiles: title cleanup, artist-prefix removal, per-field metadata, thumbnails
- Automatic audio bitrate with **passthrough** — a matching source stream is copied rather
  than re-encoded
- The purple-on-near-black palette, verbatim

## What is different, and why

Android is not Windows. Each of these is a platform constraint rather than a choice, and
every one is recorded in the spec's "Platform divergences" table.

| | |
| --- | --- |
| **No bundled `.exe` files** | Since API 29 an app may only execute binaries from `nativeLibraryDir`. yt-dlp, FFmpeg, Python and QuickJS come from [`youtubedl-android`](https://github.com/yausername/youtubedl-android) as APK-bundled native libraries. |
| **No `--cookies-from-browser`** | The sandbox forbids reading another app's cookie store. Sign in through the app's own WebView instead; cookies are written to app-private storage and never leave the device except to YouTube. |
| **No Deno** | It has no Android build. QuickJS fills the same role and ships with the library. |
| **Scoped storage** | The download folder is granted through the system picker, not typed as a path. |
| **Foreground service** | Android kills background work, so the queue runs under a notification. |
| **yt-dlp self-updates on launch** | The bundled copy is months old by the time anyone installs the app, and a stale yt-dlp cannot solve YouTube's player challenges — every download then fails with a misleading "Requested format is not available". |

Beyond the constraints, a few deliberate improvements: `best*` added to the video selector
fallback chain, queue deduplication by canonical video identity, actionable error text, and
Automatic bitrate targeting the best the format can do rather than scaling to the source.

## Building

Requires JDK 17+ and the Android SDK.

```bash
cd android
./gradlew testDebugUnitTest      # 140 unit tests over the download rules
./gradlew assembleDebug
./gradlew assembleRelease
```

Release builds are unsigned unless `android/keystore.properties` exists:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

That file and any `.jks` are gitignored. Without them `assembleRelease` still succeeds and
simply produces an unsigned APK, so the project builds from a fresh clone.

Install the **per-ABI** APK — `app-arm64-v8a-release.apk` (~59 MB) covers phones from
roughly 2017 on. The universal APK is 212 MB because it carries Python and FFmpeg for four
architectures.

## Structure

| Package | Holds |
| --- | --- |
| `core/` | The download rules as pure Kotlin. No Android imports, runs on a plain JVM, fully unit-tested. |
| `data/` | Room for the queue and profiles, DataStore for scalar settings. |
| `ytdlp/` | The `youtubedl-android` wrapper, logging, output finalisation, cookie providers. |
| `work/` | Bounded-concurrency queue and the foreground service. |
| `ui/` | Compose screens, theme and motion. |

`core/` being Android-free is the boundary that made the port tractable: the rules are
testable without a device, and `docs/download-rules.md` can be asserted directly.

## Testing

Unit tests cover the rules, not the plumbing — format selectors, the bitrate ladder and
passthrough matrix, title-cleanup regexes, metadata mapping, queue parsing, progress
parsing, URL canonicalisation and output naming.

Verified by hand on a Pixel 8 Pro: downloads from 5 MB to 208 MB, playlist expansion,
share-sheet input, cancel, SAF output, and the release build under R8.

## Known gaps

- `MicroGCookieProvider` is a deliberately unimplemented spike. The `weblogin:` token type
  it would need is unverified, and a silently-failing provider in the settings list would
  be worse than an explicit gap.
- No instrumented UI tests.
- Not tested below Android 13, nor on any device other than a Pixel 8 Pro.

## Responsible use

Unchanged from the Windows app: download only what you own or have permission to, and
follow YouTube's Terms of Service. Signing in makes downloads attributable to your
account, and yt-dlp warns that accounts can be restricted — consider a throwaway.

Not affiliated with YouTube, Google, yt-dlp, FFmpeg, Deno or Gyan. Bundled components keep
their own licences; see [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
