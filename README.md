# YT Downloader

A portable Windows desktop application for downloading YouTube videos, audio, and playlists with control over format, quality, metadata, title cleanup, and parallel downloads.

> **Current repository status:** documentation only. The application, source code, binaries, and bundled dependencies have not been uploaded yet.

## Features

- Download individual YouTube videos, audio, or complete playlists.
- Choose between audio-only and video downloads.
- Download 1 to 5 items at once; 2 at once is the default.
- Follow combined progress, current transfer speed in MB/s, and elapsed time.
- Cancel an active job, remove its unfinished files, and reset the progress bar.
- Keep the currently downloading item at the top of the activity list.
- Copy a source URL by clicking it in the Activity or Failed downloads list.
- Reject active and upcoming YouTube livestreams instead of trying to record them.
- Automatically preserve duplicate downloads by adding numbered suffixes such as `Song (2).mp3`.
- Use a dark Windows interface with saved format choices, profile selection, and resizable activity columns.

## Formats and quality

### Audio

| Format | Best use |
| --- | --- |
| OPUS | Best quality per file size |
| M4A / AAC | Best general compatibility without unnecessary conversion when a compatible stream is available |
| MP3 | Best support for older devices; normally requires conversion |
| FLAC / WAV | Lossless output containers, but they cannot restore quality already lost in YouTube's source |
| OGG | Vorbis-based workflows |

Available audio formats: **MP3, M4A, FLAC, WAV, OGG, OPUS, and AAC**.

### Video

| Format | Best use |
| --- | --- |
| MP4 | Widest device and editor compatibility |
| MKV | Most flexible container for different video and audio codec combinations |
| WEBM | Modern web codecs, with less legacy compatibility |

Resolution can be set to **Highest**, **8K (4320p)**, **4K (2160p)**, **1440p**, **1080p**, **720p**, **480p**, or **360p**. A resolution limit never upscales the source.

Default selections are **MP3** for audio, **MP4** for video, **Highest** resolution, and **2 downloads at once**.

## Automatic Audio Bitrate

`Automatic Audio Bitrate` checks the selected YouTube audio stream's codec and bitrate before downloading:

- Compatible M4A/AAC, OPUS, OGG/Vorbis, or MP3 streams are preserved without another lossy conversion.
- When conversion is required, the app chooses a useful codec-appropriate bitrate based on the source.
- MP3 conversions use 192, 256, or 320 kbps; other lossy conversions use 96, 160, 224, or 256 kbps as appropriate.
- Fixed bitrate choices from 320 down to 48 kbps are also available.

A fixed bitrate forces lossy conversion. Choosing a bitrate higher than the source may increase file size without improving the real audio quality.

## Profiles and download options

Profiles save the complete set of download options, including:

- Cookie source.
- Automatic or fixed audio bitrate.
- Thumbnail embedding.
- Title cleanup settings.
- Individual metadata fields.

The **Default** profile starts with title cleanup, thumbnails, and metadata disabled. Cookie selection remains set to **Automatic**. New profiles also start with optional processing disabled.

### Title cleanup

Title cleanup can:

- Remove common bracketed labels such as `Official Video`, `Lyrics`, or `Audio`.
- Remove user-defined bracketed tags.
- Remove an artist prefix so `Bruno Mars - The Lazy Song` becomes `The Lazy Song`.

### Metadata

Metadata has a master switch and individual choices for:

- Title
- Artist / uploader
- Album / series
- Date
- Description
- Source URL
- Genre
- Track and disc information
- Chapters

## Cookies and privacy

The app can ask yt-dlp to read cookies locally from **Automatic**, Firefox, Chrome, Edge, Brave, Opera, Vivaldi, or Chromium. Cookie use can also be disabled, although some YouTube downloads may then fail.

The app itself does not read, store, upload, or write cookie values to its log. Relevant cookies are used by yt-dlp only in authenticated requests to YouTube/Google; they are not sent to the app developer or unrelated services.

YouTube can associate authenticated downloads with the signed-in account. yt-dlp warns that accounts may be temporarily or permanently banned and suggests considering a throwaway account. Higher parallel-download settings also reach request limits faster, so 1 or 2 downloads at once are the safer defaults.

See the [official yt-dlp cookie and account warning](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies).

## Activity, failures, and diagnostics

- Activity and failed-download tabs keep the title, type, status, and source link for each item.
- Failed entries remain available as clickable source links in the app.
- Each run with failures creates `FailedDL_YYYY-MM-DD_HH-MM-SS.txt` in the `Dependencies` folder.
- Detailed downloader output, component-update results, exit codes, and errors are written to `Dependencies\YTD.log`.
- Browser cookie contents are never written to the log.

## Portable folder layout

YT Downloader is designed to keep its files together:

```text
YT Downloader/
├── YTDownloader.exe
├── Dependencies/
│   ├── yt-dlp.exe
│   ├── ffmpeg.exe
│   ├── ffprobe.exe
│   ├── deno.exe
│   ├── settings.ini
│   ├── YTD.log
│   └── FailedDL_*.txt
└── Downloads/
```

`settings.ini` stores preferences, profiles, toggles, selected formats, concurrency, and activity-column layout. Downloads are always saved in the portable `Downloads` folder. The application does not intentionally store its own settings in AppData.

Deno may create `Dependencies\DenoCache` while solving YouTube JavaScript challenges. The folder is kept inside the portable application directory.

## Components

| Component | Purpose |
| --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | YouTube extraction, format discovery, playlists, browser-cookie access, and downloading |
| [FFmpeg](https://ffmpeg.org/) | Merging video and audio, conversion, and embedding metadata or thumbnails |
| [ffprobe](https://ffmpeg.org/ffprobe.html) | Inspecting media streams, including source audio bitrate |
| [Deno](https://deno.com/) | JavaScript runtime used by yt-dlp for YouTube challenge solving |
| .NET Framework / Windows Forms | Native Windows desktop interface |

## Component updates

The **Check For Updates** button checks components only when pressed:

- yt-dlp and Deno use their official update mechanisms.
- FFmpeg and ffprobe are replaced together with the newest stable Windows build from [Gyan](https://www.gyan.dev/ffmpeg/builds/), after its published SHA-256 checksum is verified.
- Temporary updater files are cleaned after a successful update.
- The button does not update YT Downloader itself.

When nothing needs changing, the app reports: `Component Check: All Up To Date.`

## Build

The application is written in C# using Windows Forms and is built as a portable Windows executable. Build instructions will be added when the source code is published.

## Responsible use

Only download content you own or have permission to download. Follow YouTube's Terms of Service and all applicable copyright laws.

YT Downloader is not affiliated with YouTube, Google, yt-dlp, FFmpeg, Deno, or Gyan.

## License

No project license has been selected yet.
