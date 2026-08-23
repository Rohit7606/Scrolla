package com.scrolla.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================
// SURFACE LADDER
//
// Four steps you can see side by side. The previous scheme mapped
// background, surface and surfaceContainer to the same value, which is
// why cards were invisible: a card filled at 15% over its own ground is
// one value away from the page.
//
// Warm-toned rather than neutral so the coral accent sits in the same
// family as the greys instead of floating on top of them.
// =========================================

// Dark
internal val GroundDark = Color(0xFF121110)
internal val CardDark = Color(0xFF1A1817)
internal val RaisedDark = Color(0xFF221E1D)
internal val HairlineDark = Color(0xFF2A2624)
internal val HairlineStrongDark = Color(0xFF332E2B)

internal val TextHiDark = Color(0xFFF5F1EE)
internal val TextMidDark = Color(0xFFA39D98)
internal val TextLowDark = Color(0xFF857E78)   // 4.8:1 on ground — safe for 11sp micro labels
internal val TextFaintDark = Color(0xFF4A4340) // decorative only, never text

// Light
internal val GroundLight = Color(0xFFFAF8F6)
internal val CardLight = Color(0xFFFFFFFF)
internal val RaisedLight = Color(0xFFF1ECE7)
internal val HairlineLight = Color(0xFFE4DDD6)
internal val HairlineStrongLight = Color(0xFFD5CCC3)

internal val TextHiLight = Color(0xFF17150F)
internal val TextMidLight = Color(0xFF625C55)
internal val TextLowLight = Color(0xFF8A837B)
internal val TextFaintLight = Color(0xFFB5ADA4)

// =========================================
// ACCENT — a ROLE, not a hex
//
// The old scheme set primary = Accent500 in both themes, so the light
// theme inherited a 2.9:1 hero number and white-on-coral buttons at
// 3.1:1. The accent now resolves per theme:
//
//   dark   #ED6A5A on #121110 → 6.2:1
//   light  #B83E2B on #FAF8F6 → 5.3:1
//
// It means exactly one thing on every screen: you, here, now.
// =========================================
internal val AccentDark = Color(0xFFED6A5A)
internal val AccentLight = Color(0xFFB83E2B)

internal val OnAccentDark = Color(0xFF2A0F09)  // near-black on coral — 5.5:1
internal val OnAccentLight = Color(0xFFFFFFFF) // white on deep coral — 5.6:1

internal val AccentContainerDark = Color(0xFF3A2F2C)
internal val OnAccentContainerDark = Color(0xFFFBDEDA)
internal val AccentContainerLight = Color(0xFFF7E6E1)
internal val OnAccentContainerLight = Color(0xFF5C1A0F)

// Row wash for "this is you" on the leaderboard
internal val SelfRowDark = Color(0xFF221E1D)
internal val SelfRowLight = Color(0xFFFBF0EC)

// =========================================
// DIRECTION — only ever means up or down
//
// Deliberately NOT coral. In this app more scrolling is the bad
// outcome, so a delta painted in the brand colour reads backwards.
// Jade for improving, amber for worsening; neither is used anywhere else.
// =========================================
val ImprovingDark = Color(0xFF3FBE9C)
val ImprovingLight = Color(0xFF1B7A5E)
val WorseningDark = Color(0xFFE0A34A)
val WorseningLight = Color(0xFF8A5A12)

// =========================================
// SEMANTIC SCALES
// Retuned to the warm ladder; names kept — SettingsScreen imports them.
// =========================================

// Success
val SuccessLight = ImprovingLight
val SuccessDark = ImprovingDark
internal val SuccessContainerLight = Color(0xFFDCEFE7)
val SuccessContainerDark = Color(0xFF102E24)
val OnSuccessContainerLight = Color(0xFF11402F)
val OnSuccessContainerDark = Color(0xFFDCEFE7)

// Warning
val WarningLight = WorseningLight
val WarningDark = WorseningDark
val WarningContainerLight = Color(0xFFF8EBD2)
val WarningContainerDark = Color(0xFF362A12)
val OnWarningContainerLight = Color(0xFF4A3400)
val OnWarningContainerDark = Color(0xFFF8EBD2)

// Error
val ErrorLight = Color(0xFFA3301F)
val ErrorDark = Color(0xFFF0847A)
val ErrorContainerLight = Color(0xFFF9DFDB)
val ErrorContainerDark = Color(0xFF3A1512)
val OnErrorLight = Color(0xFFFFFFFF)
val OnErrorDark = Color(0xFF2A0F09)
val OnErrorContainerLight = Color(0xFF6E1A0F)
val OnErrorContainerDark = Color(0xFFF9DFDB)

// Info
val InfoLight = Color(0xFF1D5FC9)
val InfoDark = Color(0xFF8FBAF5)
val InfoContainerLight = Color(0xFFDDE7F7)
val InfoContainerDark = Color(0xFF16233A)
val OnInfoContainerLight = Color(0xFF0F3068)
val OnInfoContainerDark = Color(0xFFDDE7F7)

// Pending
val PendingLight = Color(0xFF625C55)
val PendingDark = Color(0xFFA39D98)
val PendingContainerLight = Color(0xFFEFEAE5)
val PendingContainerDark = Color(0xFF262220)
val OnPendingContainerLight = Color(0xFF33302C)
val OnPendingContainerDark = Color(0xFFEFEAE5)
