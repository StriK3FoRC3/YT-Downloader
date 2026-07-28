package com.strik3forc3.ytdownloader.core

/**
 * Reduces yt-dlp's stderr to one line fit for a list row.
 *
 * Contract: `docs/download-rules.md` §9.
 * Ported from `FriendlyError` (reference line 2034).
 */
object ErrorMapper {

    private const val MAX_LENGTH = 220
    private const val UNKNOWN = "Unknown download error"
    private val ERROR_PREFIX = Regex("""^.*?ERROR:\s*""", RegexOption.IGNORE_CASE)

    fun friendly(stderr: String?): String {
        val lines = stderr.orEmpty()
            .split('\r', '\n')
            .filter { it.isNotEmpty() }

        // Prefer the last explicit ERROR: line — yt-dlp emits warnings above it that
        // read as more alarming than the actual cause.
        val chosen = lines.lastOrNull { it.contains("ERROR:", ignoreCase = true) }
            ?: lines.lastOrNull()

        if (chosen.isNullOrBlank()) return UNKNOWN

        val stripped = ERROR_PREFIX.replace(chosen, "").trim()
        // The reference checks for blankness only *before* stripping, so a line that is
        // nothing but "ERROR:" yields an empty status. Report it as unknown instead.
        if (stripped.isEmpty()) return UNKNOWN

        return if (stripped.length > MAX_LENGTH) {
            stripped.take(MAX_LENGTH - 3) + "..."
        } else {
            stripped
        }
    }

    /**
     * yt-dlp's message plus, where the cause is recognisable, what to do about it.
     *
     * The raw text is frequently misleading. "Requested format is not available" reads
     * like a bad format choice, but on YouTube it almost always means the extractor was
     * served no playable formats at all — the bot check — and no change of format or
     * resolution will help. Repeating yt-dlp verbatim sends people to fiddle with the
     * wrong setting.
     */
    fun describe(stderr: String?): String {
        val message = friendly(stderr)
        val hint = hintFor(stderr.orEmpty().lowercase())
        return if (hint == null) message else "$message\n$hint"
    }

    private fun hintFor(lowercased: String): String? = when {
        lowercased.contains("sign in to confirm") ||
            lowercased.contains("not a bot") ||
            lowercased.contains("requested format is not available") ->
            "YouTube is withholding this video from anonymous downloads. " +
                "Sign in under Settings → YouTube sign-in, then try again."

        lowercased.contains("age") && lowercased.contains("restrict") ->
            "Age-restricted. Signing in under Settings is required."

        lowercased.contains("private video") ->
            "This video is private."

        lowercased.contains("video unavailable") || lowercased.contains("removed by the uploader") ->
            "This video is no longer available."

        lowercased.contains("members-only") || lowercased.contains("join this channel") ->
            "Members-only video. Signing in with a subscribed account is required."

        lowercased.contains("unable to download") && lowercased.contains("network") ->
            "Network problem. Check the connection and try again."

        lowercased.contains("ffmpeg") ->
            "The bundled FFmpeg could not run. Reinstalling the app will restore it."

        else -> null
    }
}
