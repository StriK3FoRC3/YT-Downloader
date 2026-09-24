from yt_dlp.postprocessor.common import PostProcessor


class NativeThumbnailPP(PostProcessor):
    """Select an existing thumbnail before download; never resize or re-encode."""

    def __init__(self, downloader=None, size='medium'):
        super().__init__(downloader)
        self.target_width = {'large': 640, 'medium': 320, 'small': 120}[size]

    def run(self, info):
        candidates = []
        for thumbnail in info.get('thumbnails') or []:
            width = thumbnail.get('width')
            if thumbnail.get('url') and isinstance(width, (int, float)) and width > 0:
                candidates.append(thumbnail)
        if not candidates:
            self.report_warning('Thumbnail dimensions unavailable; using highest available.')
            return [], info

        # yt-dlp orders thumbnails by preference; prefer the later entry on ties.
        # Retain alternatives so yt-dlp can fall back when an image is unavailable.
        candidates.sort(key=lambda t: -abs(t['width'] - self.target_width))
        info['thumbnails'] = candidates
        selected = candidates[-1]
        info['thumbnail'] = selected['url']
        self.to_screen('Selected native thumbnail: {}x{}'.format(
            selected['width'], selected.get('height', '?')))
        return [], info
