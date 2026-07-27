# Third-party notices

YT Downloader invokes the following third-party programs as separate processes. They are not part of the YT Downloader source code and retain their own copyrights and licenses.

## Release components

### yt-dlp 2026.07.04

- Project: https://github.com/yt-dlp/yt-dlp
- Source release: https://github.com/yt-dlp/yt-dlp/releases/tag/2026.07.04
- License information: https://github.com/yt-dlp/yt-dlp#licensing

yt-dlp's own source is provided under The Unlicense. The official Windows executable is a PyInstaller bundle containing third-party components and is distributed under GPLv3-or-later as described by the yt-dlp project.

### FFmpeg and ffprobe 8.1.2

- Project: https://ffmpeg.org/
- Windows build provider: https://www.gyan.dev/ffmpeg/builds/
- Corresponding source revision: https://github.com/FFmpeg/FFmpeg/commit/38b88335f9
- GPL version 3: https://www.gnu.org/licenses/gpl-3.0.html

The included Gyan essentials build is a 64-bit static GPLv3 build. Its configuration can be inspected with `ffmpeg.exe -version`.

### Deno 2.9.4

- Project: https://github.com/denoland/deno
- Source release: https://github.com/denoland/deno/releases/tag/v2.9.4
- License: https://github.com/denoland/deno/blob/v2.9.4/LICENSE.md

Deno is distributed under the MIT License and includes additional third-party components listed by the Deno project.

## Updating components

YT Downloader's Check For Updates button may replace these executables with newer versions. After an update, use each component's version command and official project page for the license and source information corresponding to the installed version.
