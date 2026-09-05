package com.example.ai_camera.ui

import androidx.compose.ui.graphics.Color

/**
 * The camera UI's own palette: a warm, light scheme rather than the usual black camera chrome.
 *
 * Kept separate from the Material theme because these surfaces sit over a live preview and are
 * chosen for legibility against it, not for Material's colour roles.
 */
object CameraPalette {
    /** Page and sheet backgrounds. */
    val Cream = Color(0xFFF7F3EC)
    val CreamDim = Color(0xFFEFE9DF)

    /** Chrome above and below the viewfinder. */
    val Surface = Color(0xFFFBF8F3)

    /** Selected pills and the accent used for the active value. */
    val Accent = Color(0xFFC8A96A)
    /** Legible as text; the pale accent is a fill colour only. */
    val AccentDeep = Color(0xFF8A6A2F)
    val AccentSoft = Color(0xFFEADFC6)

    val TextPrimary = Color(0xFF33302B)
    val TextSecondary = Color(0xFF8A8378)
    /** The muted greige of a primary action button. */
    val Taupe = Color(0xFFA79E90)
    val Divider = Color(0xFFE4DDD1)

    /** Overlays drawn on top of the preview, where the scene behind can be any brightness. */
    val OnPreview = Color(0xFFFFFFFF)
    val OnPreviewScrim = Color(0x66000000)

    val Danger = Color(0xFFC0603F)
    val Good = Color(0xFF6B8E5A)
}
