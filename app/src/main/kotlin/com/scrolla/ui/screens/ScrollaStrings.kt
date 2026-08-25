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

    // Onboarding join screen — the path for someone with no code.
    // Creating a group lives inside the app, not here.
    const val GROUP_NO_CODE_LABEL = "No code?"
    const val GROUP_SOLO_TITLE = "Go solo for now"
    const val GROUP_SOLO_BODY = "Track your own distance. Join a group whenever."

    // =========================================
    // Screen 5: Home
    // =========================================
    const val HOME_TITLE = "Scrolla"
    const val HOME_UNIT = "km"
    const val HOME_LANDMARK_PREFIX = "about the height of"
    const val HOME_EMPTY_LANDMARK = "start scrolling to see your distance"
    const val HOME_WAITING_FOR_SENSOR = "waiting for the first scroll to be measured"
    const val HOME_STANDING_UNAVAILABLE = "no group results yet"
    const val HOME_INSIGHT_PLACEHOLDER_LABEL = "today's insight"
    const val HOME_INSIGHT_PLACEHOLDER_BODY = "Scroll today to see your insights here"
    const val HOME_INSIGHT_PERSONAL_BEST_LABEL = "personal best"
    const val HOME_INSIGHT_PEAK_HOUR_LABEL = "peak scroll time"
    const val HOME_INSIGHT_QUICK_WIN_LABEL = "quick win"

    // =========================================
    // Screen 6: Leaderboard
    // =========================================
    const val LEADERBOARD_TITLE = "Leaderboard"
    const val LEADERBOARD_MOST_IMPROVED = " has improved most this week"
    const val LEADERBOARD_MOST_CONSISTENT = "is most consistent this week"
    const val LEADERBOARD_STAT_TODAY = "today"
    const val LEADERBOARD_STAT_YESTERDAY = "yesterday"
    const val LEADERBOARD_STAT_WEEK_AVG = "7-day avg"
    const val LEADERBOARD_SELF_NAME = "You"
    const val LEADERBOARD_HALL_OF_FAME_PREFIX = "Group's best day:"
    const val LEADERBOARD_HALL_OF_FAME_LINK = "Hall of fame"
    const val LEADERBOARD_EMPTY_SOLO_HEADLINE = "You're the only one here"
    const val LEADERBOARD_EMPTY_SOLO_BODY = "Share your group code to invite friends. Until then, here's your personal progress."
    const val LEADERBOARD_NO_DATA = "No data yet — start scrolling"
    const val PROFILE_TRACKING_SINCE_UNKNOWN = "Tracking has not started yet"
    const val PROFILE_NO_GROUPS = "Not in a group yet"
    const val LEADERBOARD_NO_GROUP_TITLE = "No group yet"
    const val LEADERBOARD_EMPTY_NO_GROUP = "Join a group to see how you compare."
    const val LEADERBOARD_EMPTY_NO_TOTALS = "No totals have synced for today yet."
    const val LEADERBOARD_NO_RECORD_YET = "No group record set yet"
    const val LEADERBOARD_LEFT_GROUP_SUFFIX = "(left)"

    // Error states (shared)
    const val ERROR_NO_CONNECTION = "No connection — showing last saved data"
    const val ERROR_COULDNT_LOAD = "Couldn't load — showing last known ranking"
    const val ERROR_RETRY = "Something went wrong — tap to retry"
    const val ERROR_CANT_SYNC = "Couldn't sync — will retry automatically"
    const val ERROR_LAST_UPDATED_PREFIX = "Last updated"

    /** Shown when a group read fails. Deliberately not "you have no groups" —
     *  a failed read is not an empty result, and saying so to someone who is in
     *  two groups is worse than saying nothing. */
    const val ERROR_GROUPS_UNAVAILABLE = "Couldn't load your groups — tap to retry"

    // =========================================
    // Screen 7: Insights
    // =========================================
    const val INSIGHTS_TITLE = "Insights"
    const val INSIGHTS_SECTION_THIS_WEEK = "this week"
    const val INSIGHTS_SECTION_TOP_APPS = "top apps by distance"
    const val INSIGHTS_SECTION_PEAK_TIME = "peak scroll time"
    const val INSIGHTS_PRIVACY_NOTE = "App breakdown stays on this device, never shared with your group"
    const val INSIGHTS_EMPTY_TOP_APPS = "Keep scrolling to see your top apps"
    const val INSIGHTS_EMPTY_PEAK_TIME = "Your peak time will appear after a few days of data"
    const val INSIGHTS_NO_DATA_DAY = "no data yet"

    // Accessibility
    const val STATE_LOADING = "Loading"

    // =========================================
    // Screen 8: Profile Tab
    // =========================================
    const val PROFILE_PERSONAL_BEST_LABEL = "Personal best"
    const val PROFILE_PERSONAL_BEST_EMPTY = "Keep scrolling to set your first record"
    const val PROFILE_PERSONAL_BEST_LINK = "Personal records"
    const val PROFILE_HALL_OF_FAME_LINK = "Hall of fame"
    const val PROFILE_HALL_OF_FAME_RECORD_HOLDER = "You hold the group's best day"
    const val PROFILE_HALL_OF_FAME_EMPTY = "No record set yet — be the first"
    const val PROFILE_GROUPS_LINK = "Manage groups"

    // =========================================
    // Screen 9: Settings
    // =========================================
    const val SETTINGS_TITLE = "Settings"

    // Service Health
    const val SETTINGS_HEALTH_SECTION = "Service health"
    const val SETTINGS_HEALTH_ACTIVE_BUTTON = "Battery settings"
    const val SETTINGS_HEALTH_UNKNOWN_BUTTON = "Open accessibility settings"
    const val SETTINGS_HEALTH_LAST_SYNC = "Last synced %s"

    /** When tracking last recorded anything. This is the number that exposes a
     *  dead accessibility service: a card claiming "active" above a "last
     *  recorded 09:34" from this morning is visibly contradicting itself, which
     *  is the whole point of showing it. */
    const val SETTINGS_HEALTH_LAST_RECORDED = "Last recorded %s"
    const val SETTINGS_HEALTH_NEVER_RECORDED = "Nothing recorded yet"
    const val SETTINGS_HEALTH_NEVER_SYNCED = "Not synced yet"
    const val SETTINGS_HEALTH_UNKNOWN_TITLE = "Tracking status unknown"
    const val SETTINGS_HEALTH_UNKNOWN_BODY = "Scrolla has not recorded anything yet, so it cannot confirm tracking is working. Scroll in any app for a moment and check back."
    const val SETTINGS_HEALTH_ACTIVE_TITLE = "Tracking is active"
    const val SETTINGS_HEALTH_ACTIVE_SUBTITLE = "Accessibility permission enabled"
    const val SETTINGS_HEALTH_STOPPED_TITLE = "Tracking stopped"
    const val SETTINGS_HEALTH_STOPPED_BODY = "The accessibility permission was turned off — Scrolla can't measure distance without it"
    const val SETTINGS_HEALTH_STOPPED_BUTTON = "Re-enable in Settings"
    const val SETTINGS_HEALTH_DEGRADED_TITLE = "Tracking may be limited"
    const val SETTINGS_HEALTH_DEGRADED_BODY = "Scrolla is enabled but hasn't received recent scroll events — this may be a battery restriction"
    const val SETTINGS_HEALTH_DEGRADED_BUTTON = "Check battery settings"
    const val SETTINGS_HEALTH_INTERRUPTED_TITLE = "Tracking was interrupted"
    const val SETTINGS_HEALTH_INTERRUPTED_BODY_TEMPLATE = "Your phone may have stopped Scrolla to save battery — this is common on %s devices"
    const val SETTINGS_HEALTH_INTERRUPTED_BUTTON = "Fix battery settings"
    const val SETTINGS_LAST_SYNCED_PREFIX = "Last synced"
    const val SETTINGS_NEVER_SYNCED = "Never synced"
    const val SETTINGS_BATTERY_SECTION_TEMPLATE = "Battery settings — your phone: %s"
    const val SETTINGS_WIDGET_GROUP_LABEL = "Widget group"
    const val SETTINGS_WIDGET_GROUP_SUBTEXT = "shown on your home screen widget"
    const val SETTINGS_PERMISSION_INFO = "What this permission can see"
    const val SETTINGS_REBOOT_NOTE = "Permission is re-checked after every restart"

    // Account
    const val SETTINGS_ACCOUNT_SECTION = "Account"
    const val SETTINGS_NOT_YET_AVAILABLE = "Not yet available"
    const val RECAP_SHARE_TEMPLATE = "I scrolled %s this week. Measured by Scrolla."
    const val SETTINGS_DISPLAY_NAME_LABEL = "Display name"
    const val SETTINGS_DISPLAY_NAME_PLACEHOLDER = "Your name"
    const val SETTINGS_DISPLAY_NAME_SUBTEXT = "Shown to friends on the leaderboard"
    const val SETTINGS_DISPLAY_NAME_SAVED = "Name updated"
    const val SETTINGS_BACKUP_LABEL = "Backup sign-in"
    const val SETTINGS_BACKUP_SUBTEXT = "Add a phone number so you can sign in if you lose access to Google"
    const val SETTINGS_BACKUP_ADD = "Add phone number"
    const val SETTINGS_BACKUP_LINKED = "Phone number linked"
    const val SETTINGS_SIGN_OUT = "Sign out"
    const val SETTINGS_SIGN_OUT_TITLE = "Sign out of Scrolla?"
    const val SETTINGS_SIGN_OUT_BODY = "You can sign back in anytime with your Google account"
    const val SETTINGS_DELETE_ACCOUNT = "Delete account"
    const val SETTINGS_DELETE_TITLE = "Delete your Scrolla account?"
    const val SETTINGS_DELETE_BODY = "This will permanently delete:\n• Your scroll history\n• Your personal records\n• Your group memberships\n\nYour name in group history will be replaced with '[deleted]'.\nThis cannot be undone."
    const val SETTINGS_DELETE_INPUT_HINT = "Type DELETE to confirm"
    const val SETTINGS_DELETE_CONFIRM = "Delete my account"
    const val SETTINGS_DELETE_CANCEL = "Keep my account"
    const val SETTINGS_DELETE_IN_PROGRESS = "Deleting your account..."
    const val SETTINGS_DELETE_ERROR = "Couldn't delete your account — try again or contact support"

    // =========================================
    // Screens 10-11: Groups
    // =========================================
    const val GROUP_SWITCHER_TITLE = "Your groups"
    const val GROUP_SWITCHER_WIDGET_LABEL = "Widget group"
    const val GROUP_SWITCHER_ADD = "+ Join another group"
    const val GROUP_SWITCHER_CREATE = "+ Create a new group"
    const val GROUP_SWITCHER_CODE_LABEL = "Code"
    const val GROUP_SWITCHER_SET_WIDGET = "Use this group on the widget"
    const val GROUP_SHARE_TEMPLATE = "Join my Scrolla group \"%s\" — the one who scrolls least wins. Group code: %s"
    const val GROUP_SWITCHER_EMPTY = "You're not in any groups yet"
    const val GROUP_SWITCHER_EMPTY_BUTTON = "Join a group"
    const val JOIN_GROUP_TITLE = "Join a group"
    const val JOIN_GROUP_PLACEHOLDER = "Enter 6-digit code"
    const val JOIN_GROUP_BUTTON = "Join"
    const val JOIN_GROUP_SUCCESS_TEMPLATE = "You've joined %s"
    const val JOIN_GROUP_ERROR_NOT_FOUND = "Group not found — check the code"
    const val JOIN_GROUP_ERROR_ALREADY = "You're already in this group"
    const val JOIN_GROUP_ERROR_OWN = "That's your own group code"

    // =========================================
    // Screen 12: Weekly Recap
    // =========================================
    const val RECAP_HEADLINE = "Your week in scroll"
    const val RECAP_STAT_SUFFIX = "this week"
    const val RECAP_LANDMARK_PREFIX = "that's"
    const val RECAP_SHARE = "Share your recap"
    const val RECAP_SKIP = "Maybe later"
    const val RECAP_FOOTER = "Scrolla"

    // =========================================
    // Screen 13: Personal Records
    // =========================================
    const val RECORDS_TITLE = "Your records"
    const val RECORDS_BEST_DAY_LABEL = "lowest day"
    const val RECORDS_SEVEN_DAY_LABEL = "7-day average"
    const val RECORDS_EMPTY = "Your records will appear after a couple of days of data"

    // =========================================
    // Screen 14: Hall of Fame
    // =========================================
    const val HALL_OF_FAME_TITLE = "Group records"
    const val HALL_OF_FAME_PROGRESS_TEMPLATE = "You're %s from the group's best day"
    const val HALL_OF_FAME_HOLDER = "You hold the group's best day"
    const val HALL_OF_FAME_NO_RECORD_TITLE = "No record set yet"
    const val HALL_OF_FAME_NO_RECORD_BODY = "The first person to complete a day will set the record"

    // =========================================
    // Screen 15: App Breakdown
    // =========================================
    const val APP_BREAKDOWN_TITLE = "App breakdown"
    const val APP_BREAKDOWN_SUBTITLE = "Today's scroll by app — stays on your device"
    const val APP_BREAKDOWN_NUDGE_TEMPLATE = "Cutting %s by 20%% would put you in %s"
    const val APP_BREAKDOWN_BIGGEST_TEMPLATE = "%s is your biggest source today"
    const val APP_BREAKDOWN_EMPTY = "No app data yet today — keep scrolling"
    const val APP_BREAKDOWN_PRIVACY = "App breakdown stays on this device, never shared with your group"
}

