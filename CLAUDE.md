# CLAUDE.md

Root DOX rail for the YT Downloader repository.

## Purpose

Download YouTube videos, audio, and playlists with control over format, quality,
metadata, and title cleanup. Two front ends over the same download rules:

- **Windows** — `src/Program.cs`, a portable .NET Framework 4.x WinForms app. Reference
  implementation and the origin of every behavioural rule.
- **Android** — `android/`, a Kotlin + Jetpack Compose app. Under construction.

Both are orchestrators around yt-dlp and FFmpeg. Neither implements extraction or
transcoding itself.

## Ownership

| Path | Owner |
| --- | --- |
| `src/`, `build.bat`, `app.manifest` | Windows app |
| `icon.ico` | The brand mark. Repo-wide; every platform's icon derives from it. |
| `docs/` | Shared behavioural spec |
| `android/` | Android app |
| `README.md`, `THIRD_PARTY_NOTICES.md` | Repo-wide, user-facing |

The Android app is the active surface; `src/` is stable and should not be edited as a side
effect of Android work.

## Local Contracts

- **`docs/download-rules.md` is the single source of truth for download behaviour.**
  Changing what the app *downloads* — format selectors, bitrate ladder, passthrough
  matrix, title cleanup, metadata mapping — means changing that file first, then both
  implementations. Deliberate platform differences belong in its "Platform divergences"
  table; anything not listed there is a bug in one of the two apps.
- **`icon.ico` is the only source of the app icon.** The Android launcher, notification and
  splash vectors are *traces* of it, in that file's 256-unit pixel space, not independent
  drawings. Redrawing the mark for one platform, or hand-tweaking a derived path, is how
  the front ends end up with subtly different logos. Re-trace instead.
- `src/Program.cs` builds with `/warnaserror+`. Any warning fails the build.
- The four executables (`yt-dlp`, `ffmpeg`, `ffprobe`, `deno`) are gitignored and never
  committed. Their versions and licences are tracked in `THIRD_PARTY_NOTICES.md`.
- Bundled components retain their own licences; the app itself is PolyForm Noncommercial.

## Work Guidance

- Do not "fix" `src/Program.cs` opportunistically while working on Android. Known defects
  in the reference are catalogued in the plan and in `docs/download-rules.md`; port the
  corrected behaviour into Android and record the divergence rather than editing the
  Windows app as a side effect.
- Meaningful diffs go to `/codex:review` before being considered done.

## Verification

- Windows: `build.bat` (requires the .NET Framework 4.x `csc.exe`; Windows only).
- Android: see `android/CLAUDE.md`.

## Child DOX Index

- `docs/CLAUDE.md` — the shared behavioural spec; what belongs in it and what does not.
- `android/CLAUDE.md` — Android app structure, platform constraints, design contract,
  and build/test commands.
