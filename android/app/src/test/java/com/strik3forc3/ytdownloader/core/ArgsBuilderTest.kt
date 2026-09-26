package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** End-to-end argument assembly. Asserts `docs/download-rules.md` §2–§8. */
class ArgsBuilderTest {

    private val template = "/data/tmp/%(title)s.%(ext)s"

    private fun request(
        mode: DownloadMode = DownloadMode.AUDIO,
        audioFormat: AudioFormat = AudioFormat.MP3,
        videoFormat: VideoFormat = VideoFormat.MP4,
        resolution: Resolution = Resolution.HIGHEST,
        profile: Profile = Profile.Default,
    ) = DownloadRequest(
        url = "https://youtu.be/abc",
        title = "Song",
        mode = mode,
        audioFormat = audioFormat,
        videoFormat = videoFormat,
        resolution = resolution,
        profile = profile,
    )

    private fun argv(
        request: DownloadRequest,
        source: AudioSourceInfo? = null,
    ): List<String> = ArgsBuilder.build(request, source, template).toArgv()

    @Test
    fun `common flags are always present and the url comes last`() {
        val args = argv(request())
        assertThat(args).containsAtLeast(
            "--ignore-config", "--no-playlist", "--newline", "--windows-filenames",
        )
        assertThat(args).containsAtLeast("--break-match-filters", "!is_live").inOrder()
        assertThat(args).containsAtLeast("--output", template).inOrder()
        assertThat(args.last()).isEqualTo("https://youtu.be/abc")
    }

    @Test
    fun `deno is never referenced`() {
        // Deno has no Android build; the JS runtime flag must not survive the port.
        assertThat(argv(request()).none { it.contains("deno", ignoreCase = true) }).isTrue()
        assertThat(argv(request()).none { it.contains("--js-runtimes") }).isTrue()
    }

    @Test
    fun `cookies-from-browser is never emitted`() {
        assertThat(argv(request()).none { it.contains("cookies-from-browser") }).isTrue()
    }

    @Test
    fun `audio download carries extract-audio format and codec`() {
        val args = argv(request(audioFormat = AudioFormat.OPUS))
        assertThat(args).contains("--extract-audio")
        assertThat(args).containsAtLeast(
            "--format", "bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best",
        ).inOrder()
        assertThat(args).containsAtLeast("--audio-format", "opus").inOrder()
    }

    @Test
    fun `passthrough emits no audio-quality flag`() {
        val args = argv(
            request(audioFormat = AudioFormat.OPUS),
            source = AudioSourceInfo("251", "opus", 128.0),
        )
        assertThat(args).doesNotContain("--audio-quality")
    }

    @Test
    fun `transcode emits the ladder bitrate with a K suffix`() {
        val args = argv(
            request(audioFormat = AudioFormat.MP3),
            source = AudioSourceInfo("251", "opus", 128.0),
        )
        // Automatic now targets the top of the ladder rather than scaling to the source.
        assertThat(args).containsAtLeast("--audio-quality", "320K").inOrder()
    }

    @Test
    fun `ogg requests the vorbis codec`() {
        assertThat(argv(request(audioFormat = AudioFormat.OGG)))
            .containsAtLeast("--audio-format", "vorbis").inOrder()
    }

    @Test
    fun `video download merges into the chosen container`() {
        val args = argv(request(mode = DownloadMode.VIDEO, resolution = Resolution.R1080))
        assertThat(args).containsAtLeast(
            "--format",
            "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/" +
                "bestvideo[height<=1080]+bestaudio/best[height<=1080]/" +
                "best*[height<=1080]/best*",
        ).inOrder()
        assertThat(args).containsAtLeast("--merge-output-format", "mp4").inOrder()
        assertThat(args).doesNotContain("--extract-audio")
    }

    @Test
    fun `thumbnail is skipped for containers that cannot hold one`() {
        val withThumbs = Profile.Default.copy(embedThumbnail = true)
        assertThat(argv(request(audioFormat = AudioFormat.MP3, profile = withThumbs)))
            .contains("--embed-thumbnail")
        assertThat(argv(request(audioFormat = AudioFormat.WAV, profile = withThumbs)))
            .doesNotContain("--embed-thumbnail")
        assertThat(argv(request(audioFormat = AudioFormat.AAC, profile = withThumbs)))
            .doesNotContain("--embed-thumbnail")
    }

    @Test
    fun `title cleanup emits a three-value replace per rule`() {
        val profile = Profile.Default.copy(
            cleanTitles = true,
            presetRules = listOf("Official video"),
            removeArtistPrefix = true,
        )
        val args = ArgsBuilder.build(request(profile = profile), null, template)

        val replacements = args.filter { it.option == "--replace-in-metadata" }
        assertThat(replacements).hasSize(2)
        replacements.forEach { arg ->
            assertThat(arg.values).hasSize(3)
            assertThat(arg.values[0]).isEqualTo("title")
            assertThat(arg.values[2]).isEmpty()
        }
        assertThat(replacements.last().values[1]).isEqualTo(TitleCleanup.ARTIST_PREFIX_PATTERN)
    }

    @Test
    fun `title cleanup is skipped when the toggle is off`() {
        val profile = Profile.Default.copy(cleanTitles = false, presetRules = listOf("Lyrics"))
        assertThat(argv(request(profile = profile))).doesNotContain("--replace-in-metadata")
    }

    @Test
    fun `values reach argv unquoted and unescaped`() {
        // The reference concatenates a command string and hand-quotes it; this asserts
        // the whole class of quoting bugs is gone.
        val hostile = request().copy(url = """https://x.test/a"b\c d""")
        assertThat(argv(hostile).last()).isEqualTo("""https://x.test/a"b\c d""")
    }

    @Test
    fun `queue probe requests a flat playlist with the print template`() {
        val args = ArgsBuilder.buildQueueProbe("https://youtu.be/list").toArgv()
        assertThat(args).containsExactly(
            "--ignore-config",
            "--flat-playlist",
            "--print", QueueParser.PRINT_TEMPLATE,
            "https://youtu.be/list",
        ).inOrder()
    }
}
