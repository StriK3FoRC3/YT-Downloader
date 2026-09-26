package com.strik3forc3.ytdownloader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Windows app's palette, carried over exactly — and applied very differently.
 *
 * The original puts a yellow, a blue, two purples, a red and a green button on the same
 * row, so nothing reads as the primary action. Here **purple is the only brand accent**,
 * and [Success]/[Error]/[Warning] are reserved strictly for *state* — a queue row's
 * outcome, a progress ring's completion. They never colour a button.
 */
object YtdlColors {
    /** Window background. Reference `MainForm.Bg`. */
    val Background = Color(0xFF191919)

    /** Card and sheet surface. Reference `MainForm.Panel`. */
    val Surface = Color(0xFF232323)

    /** Raised surface: inputs, chips, the row behind a progress ring. `PanelLight`. */
    val SurfaceRaised = Color(0xFF2C2C2C)

    /** Hairline separators and unfocused input outlines. */
    val Outline = Color(0xFF3A3A3A)

    /** Reference `MainForm.Purple`. Fills and strokes — strong enough to sit under white. */
    val Accent = Color(0xFF9F00FF)

    /** Reference `MainForm.PurpleHover`. Pressed and hovered states. */
    val AccentHover = Color(0xFFB537FF)

    /**
     * Purple for *text and icons on the dark background*. #9F00FF only reaches about
     * 3.2:1 against #191919, which is under the 4.5:1 body-text threshold, so tinted
     * labels use this lighter step instead of the fill colour.
     */
    val AccentText = Color(0xFFCBA6FF)

    /** A barely-there purple wash for selected rows and active containers. */
    val AccentContainer = Color(0xFF2A1338)

    val TextPrimary = Color(0xFFF5F5F5)

    /** Reference `MainForm.Muted`. Secondary labels and metadata. */
    val TextMuted = Color(0xFFB4B4B4)

    /** Disabled text and inactive icons. */
    val TextDisabled = Color(0xFF6E6E6E)

    // State only. Never a button fill.
    val Success = Color(0xFF69D26E)
    val Error = Color(0xFFF44336)
    val Warning = Color(0xFFFFA726)

    /** Track behind a progress arc or bar. */
    val Track = Color(0xFF343434)
}
