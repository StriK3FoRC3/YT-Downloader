<div align="center">
    <img src="/Screenshot1.png" width="500px"</img> 
</div>
‎ ‎ ‎ ‎ 
<div align="center">
    <img src="/Screenshot2.png" width="500px"</img> 
</div>



# YT Downloader

A completely portable Windows application for downloading YouTube videos, audio, and playlists with control over quality, metadata, title cleanup, and parallel downloads.

⚠️Disclaimer⚠️: This app was created with the help of ChatGPT/Codex. If you don't like vibe coded programs, don't use it. Please report any bugs you find, and feel free to suggest new features. 

## Features

- **Completely portable:** the executable, dependencies, settings, logs, cache, and downloads stay inside the YT Downloader folder. No installer is required.
- **Playlist downloads:** download individual YouTube links or complete playlists.
- **Smart audio downloads:** Automatic Audio Bitrate checks the source stream and avoids unnecessary re-encoding when possible. Available formats are MP3, M4A, FLAC, WAV, OGG, OPUS, and AAC.
- **Video downloads up to 8K:** choose MP4, MKV, or WEBM with resolutions up to 4320p, or use Highest to select the best available quality.
- **Up to 5 downloads at once:** choose between 1 and 5 parallel downloads, with 2 selected by default.
- **Download Profile Creator:** save cookie source, automatic or fixed audio bitrate, thumbnail embedding, metadata fields, title cleanup, artist-prefix removal, and custom bracketed title tags in reusable profiles.
- **Component update checker:** the Check For Updates button checks and updates yt-dlp, Deno, FFmpeg, and ffprobe. FFmpeg downloads are verified against the publisher's SHA-256 checksum; YT Downloader itself is not changed.

## Download

Download the latest portable ZIP from [Releases](https://github.com/StriK3FoRC3/YT-Downloader/releases/latest), extract the complete folder, and run `YTDownloader.exe`.

Keep `YTDownloader.exe`, `Dependencies`, and `Downloads` together. Moving only the executable will leave the required tools behind.

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

## Download profiles

Profiles store the cookie source, automatic or fixed audio bitrate, thumbnail embedding, metadata choices, and title-cleanup settings. The selected profile can be changed from the main window or the Profile Creator in Settings.

Title cleanup can remove common bracketed labels, user-defined custom bracketed tags, and an artist prefix so `Bruno Mars - The Lazy Song` becomes `The Lazy Song`.

Metadata has a master switch and individual choices for title, artist/uploader, album/series, date, description, source URL, genre, track/disc information, and chapters.

The **Default** profile starts with title cleanup, thumbnails, and metadata disabled. Cookie selection remains set to **Automatic**.

## Cookies and privacy

The app can ask yt-dlp to read cookies locally from Automatic, Firefox, Chrome, Edge, Brave, Opera, Vivaldi, or Chromium. Cookie use can also be disabled, although some YouTube downloads may then fail.

The app itself does not read, store, upload, or write cookie values to its log. Relevant cookies are used by yt-dlp only in authenticated requests to YouTube/Google; they are not sent to the app developer or unrelated services.

YouTube can associate authenticated downloads with the signed-in account. yt-dlp warns that accounts may be temporarily or permanently banned and suggests considering a throwaway account. Higher parallel-download settings also reach request limits faster, so 1 or 2 downloads at once are the safer defaults.

See the [official yt-dlp cookie and account warning](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies).

## Portable folder layout

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

`settings.ini` stores preferences, profiles, toggles, selected formats, concurrency, and activity-column layout. Failed downloads are written to timestamped `FailedDL_*.txt` files and detailed diagnostics go to `YTD.log`.

Deno may create `Dependencies\DenoCache` while solving YouTube JavaScript challenges. All of these files remain inside the portable application directory.

## Components

| Component | Purpose |
| --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | YouTube extraction, format discovery, playlists, browser-cookie access, and downloading |
| [FFmpeg](https://ffmpeg.org/) | Merging video and audio, conversion, and embedding metadata or thumbnails |
| [ffprobe](https://ffmpeg.org/ffprobe.html) | Inspecting media streams, including source audio bitrate |
| [Deno](https://deno.com/) | JavaScript runtime used by yt-dlp for YouTube challenge solving |
| .NET Framework / Windows Forms | Native Windows desktop interface |

The Check For Updates button checks components only when pressed. yt-dlp and Deno use their official update mechanisms. FFmpeg and ffprobe are replaced together with the latest stable [Gyan Windows build](https://www.gyan.dev/ffmpeg/builds/) after its published SHA-256 checksum is verified.

Third-party component versions and redistribution information are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Build from source

Requirements:

- Windows with the .NET Framework 4.x C# compiler.
- `yt-dlp.exe`, `ffmpeg.exe`, `ffprobe.exe`, and `deno.exe` placed in the repository root when creating a complete portable build.

Run `build.bat`. The optimized executable and portable folder structure are created under `build`.

The build treats all compiler warnings as errors. Local builds, downloaded dependencies, settings, logs, caches, downloads, and release archives are excluded by `.gitignore`.

## Responsible use

Only download content you own or have permission to download. Follow YouTube's Terms of Service and all applicable copyright laws.

YT Downloader is not affiliated with YouTube, Google, yt-dlp, FFmpeg, Deno, or Gyan.

## License

Image Converter is available under the [PolyForm Noncommercial License 1.0.0](LICENSE) You may use, modify, and distribute it for permitted noncommercial purposes. Commercial use is not granted by this license.
The bundled third-party programs retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
