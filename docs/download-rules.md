# Download rules

Behavioural contract shared by the Windows app (`src/Program.cs`) and the Android app
(`android/`). Extracted from the WinForms implementation, which is the reference.

Every rule below is a pure function of user selections plus probed source info. Both
implementations must agree; the Android unit tests in `core/` assert against this file.

Line references point at `src/Program.cs` at the commit this spec was written from.

---

## 1. Selectable values

| Setting | Values | Default |
| --- | --- | --- |
| Mode | Audio, Video | Audio |
| Audio format | MP3, M4A, FLAC, WAV, OGG, OPUS, AAC | MP3 |
| Video format | MP4, MKV, WEBM | MP4 |
| Resolution | Highest, 4320p (8K), 2160p (4K), 1440p, 1080p, 720p, 480p, 360p | Highest |
| Parallel downloads | 1–5 | 2 |
| Bitrate | Automatic, 320/256/224/192/160/128/96/64/48 kbps | Automatic |
| Cookie source | Automatic, Firefox, Chrome, Edge, Brave, Opera, Vivaldi, Chromium, Disabled | Automatic |

**Resolution → height** (`ResolutionHeight`, line 1904): first `\d+` match in the label.
`Highest` → empty (no limit). `4320p (8K)` → `4320`. `2160p (4K)` → `2160`.
A height limit is a ceiling only — it never upscales.

> Android: the cookie-source list does not survive. See §8.

---

## 2. Video format selector

`DownloadOneAsync`, lines 1425–1440. With `limit = "[height<=<H>]"`, or empty string when
resolution is `Highest`:

| Video format | Selector |
| --- | --- |
| MP4 | `bestvideo{limit}[ext=mp4]+bestaudio[ext=m4a]/bestvideo{limit}+bestaudio/best{limit}` |
| WEBM | `bestvideo{limit}[ext=webm]+bestaudio[ext=webm]/bestvideo{limit}+bestaudio/best{limit}` |
| MKV | `bestvideo{limit}+bestaudio/best{limit}` |

Always accompanied by `--merge-output-format <lowercased video format>`.

Worked example — MP4 @ 1080p:

```
bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]
```

---

## 3. Audio format selector

`AudioFormatSelector`, line 1542. Depends on the target format **and** on whether the
bitrate setting is Automatic, because a forced bitrate means a lossy re-encode is
happening regardless — so the selector deliberately picks a *different* source codec to
avoid a same-codec generational re-encode.

| Target | Automatic bitrate | Fixed bitrate |
| --- | --- | --- |
| M4A / AAC | `bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best` | `bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best` |
| OPUS | `bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best` | `bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best` |
| MP3, FLAC, WAV, OGG | `bestaudio/best` | `bestaudio/best` |

**Container → yt-dlp codec name** (`YtDlpAudioFormat`, line 1537): `OGG` → `vorbis`.
Every other format passes through lowercased unchanged.

---

## 4. Passthrough matrix

`IsNativeAudioCodec`, line 1590. Decides whether the source stream can be remuxed rather
than re-encoded. Source codec is matched case-insensitively by substring.

| Target | Passthrough when source codec contains |
| --- | --- |
| `m4a`, `aac` | `aac` or `mp4a` |
| `opus` | `opus` |
| `vorbis` (OGG) | `vorbis` |
| `mp3` | `mp3` |
| `flac`, `wav` | never (always decoded — see §5) |

Passthrough is the single most important rule on battery-powered hardware: it is the
difference between a stream copy and a CPU-bound FFmpeg transcode.

---

## 5. Bitrate ladder

`AudioTranscodeBitrate`, line 1561. Returns kbps, or `0` meaning "no `--audio-quality`
flag" — i.e. no forced lossy re-encode.

```
if target is flac or wav          → 0   (lossless container; decode, never a bitrate)
if bitrate setting is fixed       → that value
if source codec is native (§4)    → 0   (passthrough)

target == mp3:
    source <= 0 (unknown) or <= 160  → 192
    source <= 224                    → 256
    otherwise                        → 320

any other lossy target:
    source unknown (<= 0)            → 160
    source <= 64                     → 96
    source <= 160                    → 160
    source <= 224                    → 224
    otherwise                        → 256
```

> **Android diverges here** — see "Platform divergences". Automatic on Android does not
> scale to the source; once a transcode is unavoidable it targets 320 (MP3) or 256
> (other lossy). Passthrough is unaffected and still takes priority.

`ParseBitrateSetting` (line 1582) accepts only `48|64|96|128|160|192|224|256|320` followed
by `kbps`, case-insensitive. Anything else — including `Automatic` — is `0`.

> Note the asymmetry at the unknown-source case: MP3 treats unknown as `<= 160` and yields
> 192, while other codecs short-circuit to 160 before the `<= 64` test. This is
> intentional in the reference and both implementations must reproduce it.

**Source probing.** The reference runs a second full yt-dlp invocation per item
(`ProbeAudioSourceAsync`, line 1501) printing `%(format_id)s\t%(acodec)s\t%(abr)s\t%(tbr)s`,
and falls back to `tbr` when `abr` is absent. A `null` result (probe failed) is the
"unknown source" case above. Android obtains the same three fields from a single
`getInfo()` call instead — the *values* must match, the number of network round-trips
need not.

---

## 6. Title cleanup

Applied only when the profile's title-cleanup toggle is on.

**Bracketed-tag removal** (`TitleRemovalPattern`, line 1910). For each rule phrase:

```
(?i)\s*[\(\[][^\)\]]*<Regex.Escape(phrase)>[^\)\]]*[\)\]]
```

Emitted as `--replace-in-metadata title "<pattern>" ""`. Matches the phrase anywhere
inside a `(...)` or `[...]` group and deletes the whole group.

**Preset phrases** (lines 491–499): Official video, Official music video, Official audio,
Lyrics, Lyric video, Music video, Visualizer, Audio, and `HD` + `4K` (one checkbox, two
terms). Users may add arbitrary custom phrases.

**Artist-prefix removal**, when enabled:

```
--replace-in-metadata title "(?i)^\s*.+?\s+[-–—]\s+" ""
```

Strips a leading `Artist - ` using hyphen, en dash, or em dash — so
`Bruno Mars - The Lazy Song` becomes `The Lazy Song`.

Rules are deduplicated case-insensitively and blank entries dropped
(`FilterProfile.RebuildRules`, line 2932).

---

## 7. Metadata

Master toggle plus nine fields: Title, Artist, Album, Date, Description, Source, Genre,
Track, Chapters (`MetadataOptionControls`, line 2094).

Chapters is handled separately from the other eight — `--embed-chapters` when the master
toggle and Chapters are both on, otherwise `--no-embed-chapters`.

The other eight use an inverted model: yt-dlp embeds everything by default, so *disabled*
fields are blanked out with `--parse-metadata ":(?P<meta_FIELD>)"`
(`AppendDisabledMetadataOverrides`, line 2199).

| UI field | Blanked yt-dlp keys |
| --- | --- |
| Title | `title` |
| Artist | `artist`, `composer` |
| Album | `album`, `album_artist`, `show` |
| Date | `date` |
| Description | `description`, `synopsis` |
| Source | `purl`, `comment` |
| Genre | `genre` |
| Track | `track`, `disc` |

Branch selection in `DownloadOneAsync` (lines 1376–1387):

- Master on **and** at least one non-Chapters field on → `--embed-metadata --no-embed-info-json`
  plus the blanking overrides.
- Master off → `--no-embed-metadata --no-embed-info-json`.
- Master on but **only** Chapters selected → *neither branch fires*; no metadata flag is
  emitted and yt-dlp's default embedding applies.

> That third case is a gap in the reference, not a designed behaviour. Android should
> emit `--no-embed-metadata --no-embed-info-json` there, matching the user's evident
> intent. Flagged so the divergence is deliberate and documented rather than accidental.

**Thumbnail embedding**, when enabled: `--embed-thumbnail`, except for audio targets
`wav` and `aac`, which do not support it (line 1412).

---

## 8. Queue construction

`BuildQueueAsync`, line 1252. Per input link:

```
--ignore-config <js args> <cookie args> --flat-playlist
--print "%(title)s\t%(webpage_url)s\t%(live_status|not_live)s" <url>
```

Each output line is split from the **right**: last tab is live status, next-to-last is
URL, everything before is the title (titles may themselves contain tabs). Entries whose
URL does not start with `http` are dropped.

**Rejections**, recorded as failures rather than silently skipped:

| `live_status` | Message |
| --- | --- |
| `is_live` | Active YouTube livestreams cannot be downloaded. |
| `is_upcoming` | Upcoming YouTube livestreams cannot be downloaded. |

If the command fails with no output at all, the raw link is queued as-is and allowed to
fail later with a real error. The final queue is deduplicated by URL, case-insensitively.

Input links are accepted only if they parse as absolute `http`/`https` URIs
(`IsWebLink`, line 1898), trimmed and deduplicated.

The per-item download additionally passes `--no-playlist --newline --windows-filenames
--break-match-filters !is_live`.

> Android: `--windows-filenames` is retained. It is stricter than Android needs, but it
> keeps filenames portable across SAF targets including FAT32 external SD cards.

---

## 9. Errors

`FriendlyError`, line 2034. Take the last line of stderr containing `ERROR:`, else the
last non-blank line; strip everything up to and including `ERROR:`; trim; truncate to 220
characters with a trailing `...`. Empty input yields `Unknown download error`.

---

## 10. Output naming

The reference downloads to `<prefix>%(title)s.%(ext)s` where `prefix` is
`.ytd-<guid>-`, then renames. Collisions get ` (2)`, ` (3)`, … before the extension
(`MoveToNumberedPath`, line 1620).

The *naming* rule is part of this contract. The *mechanism* is not: the reference scans
the entire output directory per item to find its prefixed files, which Android replaces
with a per-item temp directory. Resulting filenames must be identical.

---

## Platform divergences

Deliberate, permanent differences on Android. Not bugs, not TODOs.

| Rule | Divergence |
| --- | --- |
| Cookie source (§1) | `--cookies-from-browser` is impossible in the app sandbox. Replaced by a cookie **file** from an in-app WebView login. The browser picker does not exist. |
| JS runtime | Deno has no Android build, but the role is still filled: `youtubedl-android` ships **QuickJS** as `libqjs.so` and injects `--js-runtimes quickjs:<path>` itself. The app must not emit a JS-runtime flag of its own. |
| Metadata chapters-only (§7) | Android emits `--no-embed-metadata`; Windows emits nothing. |
| Source probing (§5) | One `getInfo()` instead of a second `--simulate` run. Same values, half the extraction cost. |
| Parallel downloads (§1) | Cap 3 rather than 5 — thermal throttling and a shared radio make more counterproductive on a phone. Default stays 2. |
| Output mechanism (§10) | Per-item temp directory instead of whole-folder prefix scans. |
| Video selector (§2) | Android appends `/best*` (and `/best*<limit>` when a height cap is set). The reference's chain ends at `best<limit>`, which matches only a **pre-muxed** format — YouTube frequently publishes none above 360p, so the chain can fail with "Requested format is not available" for a video it could have downloaded. |
| Queue dedup (§8) | Android deduplicates by canonical video identity, not raw URL string. Android's share sheet stamps a per-share `?si=` parameter, so the same video shared twice yields two different URLs and would otherwise queue twice. |
| Automatic bitrate (§5) | Once Automatic is forced to transcode, Android targets the top of the ladder — 320 kbps for MP3, 256 for other lossy formats — instead of scaling to the source's own bitrate. The reference maps a typical ~130 kbps Opus stream onto 192 kbps MP3, which is sound for storage but audibly lossy on dense material: a lossy-to-lossy transcode stacks artefacts and the encoder needs headroom. Passthrough still wins where the codec matches, so the source is still probed. |
| Error text (§9) | Android appends an actionable hint for recognised causes. yt-dlp's "Requested format is not available" is, on YouTube, almost always the bot check rather than a format problem, and repeating it verbatim sends users to change a setting that cannot help. |
