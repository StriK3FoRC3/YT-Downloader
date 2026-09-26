package com.strik3forc3.ytdownloader.core

/**
 * Resolves output filename collisions.
 *
 * Contract: `docs/download-rules.md` §10.
 * Ported from `MoveToNumberedPath` (reference line 1620).
 *
 * The reference couples this rule to a `File.Exists`/`File.Move` loop that runs up to
 * 100,000 times and races with the other parallel downloads. Here the rule is a pure
 * function over an existence predicate, so the caller can supply whichever storage
 * backend applies — a real directory or a SAF tree — and test the naming independently.
 */
object OutputNaming {

    private const val MAX_ATTEMPTS = 10_000

    /**
     * Returns [desired] when free, otherwise `name (2).ext`, `name (3).ext`, and so on.
     *
     * @param exists whether a given filename is already taken in the destination.
     */
    fun uniqueName(desired: String, exists: (String) -> Boolean): String {
        if (!exists(desired)) return desired

        val base = desired.substringBeforeLast('.', desired)
        val extension = desired.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"

        for (number in 2..MAX_ATTEMPTS) {
            val candidate = "$base ($number)$suffix"
            if (!exists(candidate)) return candidate
        }
        throw IllegalStateException("Too many files already use the title \"$base\".")
    }
}
