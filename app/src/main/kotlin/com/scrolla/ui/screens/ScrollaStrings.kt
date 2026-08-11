package com.scrolla.ui.screens

object ScrollaStrings {
    // Global
    const val APP_NAME = "Scrolla"

    // Screen 1: Sign In
    const val SIGN_IN_HEADLINE = "How many kilometres did you scroll today?"
    const val SIGN_IN_SUBHEADLINE = "Start measuring the physical distance of your digital habits."
    const val SIGN_IN_BUTTON = "Continue with Google"
    const val SIGN_IN_FINE_PRINT = "Your scroll data stays on your device. Only your daily total is shared with your group."

    // Onboarding — Phase 1: The Question
    const val ONBOARDING_QUESTION = "How far do you think you scroll each day?"
    const val ONBOARDING_GUESS_FEW = "A few centimetres"
    const val ONBOARDING_GUESS_METRE = "About a metre"
    const val ONBOARDING_GUESS_KM = "Several kilometres"

    // Onboarding — Phase 2: The Reveal
    const val ONBOARDING_REVEAL_NUMBER = "2.8"
    const val ONBOARDING_REVEAL_UNIT = "km"
    const val ONBOARDING_REVEAL_CONTEXT_1 = "A 35-minute walk."
    const val ONBOARDING_REVEAL_CONTEXT_2 = "With your thumb."

    // Onboarding — Phase 3: The Invitation
    const val ONBOARDING_INVITE_HEADLINE = "Your friends scroll too."
    const val ONBOARDING_INVITE_BODY = "The one who scrolls least, wins."

    // Onboarding — Actions
    const val ONBOARDING_ACTION_NEXT = "Continue"
    const val ONBOARDING_ACTION_START = "Start Tracking"

    // Phase 4: Permission Ask
    const val PERMISSION_HEADLINE = "One permission needed"
    const val PERMISSION_SUBHEADLINE = "Scrolla uses Android's Accessibility feature to measure scroll distance"

    const val PERMISSION_TRACK_HEADER = "What we track:"
    const val PERMISSION_TRACK_1 = "How far you scroll in each app (distance only)"
    const val PERMISSION_TRACK_2 = "Which apps you're scrolling in"
    const val PERMISSION_TRACK_3 = "The time of day you scroll most"

    const val PERMISSION_NEVER_HEADER = "What we never see:"
    const val PERMISSION_NEVER_1 = "What you're reading or watching"
    const val PERMISSION_NEVER_2 = "Text, images, or passwords"
    const val PERMISSION_NEVER_3 = "Anything you type"

    const val PERMISSION_DATA_HEADER = "Where your data goes:"
    const val PERMISSION_DATA_1 = "Scroll distance is saved on your phone"
    const val PERMISSION_DATA_2 = "Your daily total (km only) is shared with your group"
    const val PERMISSION_DATA_3 = "Your per-app breakdown never leaves your device"

    const val PERMISSION_BUTTON = "Grant permission"
    const val PERMISSION_WHY_LINK = "Why does Scrolla need this?"
    const val PERMISSION_WHY_BODY = "Android's Accessibility feature lets Scrolla read scroll events — the same system used by apps like TalkBack for screen readers. Scrolla only reads how far views scroll, not what's on screen. You can turn this off at any time in Settings → Accessibility."

    // Phase 4b: Battery Whitelist
    const val BATTERY_HEADLINE = "Keep Scrolla running"
    const val BATTERY_SUBHEADLINE = "Scrolla needs to run in the background to measure distance. Android's battery optimizer might stop it unless you whitelist it."
    const val BATTERY_MANUFACTURER_PREFIX = "Settings for your"
    const val BATTERY_OPEN_SETTINGS_BUTTON = "Open battery settings"
    const val BATTERY_CONTINUE_BUTTON = "I've done this"

    // Phase 5: Join or Create Group
    const val GROUP_HEADLINE = "Join your friends"
    const val GROUP_SUBHEADLINE = "Enter a 6-digit code from a friend"

    const val GROUP_JOIN_PLACEHOLDER = "Enter code (e.g. A3KX72)"
    const val GROUP_JOIN_BUTTON = "Join group"
    const val GROUP_JOIN_ERROR_NOT_FOUND = "Group not found — check the code and try again"
    const val GROUP_JOIN_ERROR_OWN_CODE = "That's your own group code — share it with a friend to invite them"

    const val GROUP_CREATE_BODY = "Create a group and share the code with friends"
    const val GROUP_CREATE_BUTTON = "Create group"
    const val GROUP_CODE_LABEL = "Your group code is"
    const val GROUP_CODE_SHARE_SUBTEXT = "Share this with up to 10 friends to invite them"
    const val GROUP_CODE_SHARE_BUTTON = "Share code"
    const val GROUP_CODE_COPY_LINK = "Copy code"

    const val GROUP_SKIP = "Skip for now — you can join a group later"

    // Accessibility
    const val STATE_LOADING = "Loading"
}
