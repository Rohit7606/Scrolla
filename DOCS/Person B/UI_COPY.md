# UI_COPY.md — Scrolla
**Owner:** Person B
**Purpose:** The authoritative source for every piece of text that appears in the app. B pastes from here — never invents copy inline or lets an AI agent generate it without checking this file first. If a string isn't in here, add it here before adding it to the code.
**AI agents reading this:** When generating Compose screens, import string values from here rather than inventing them. Pay particular attention to the "DO NOT CHANGE" markers — those strings have been deliberately designed and must not be paraphrased, improved, or made friendlier without explicit discussion.

---

## 1. COPY PRINCIPLES — READ FIRST

Every copy decision in this file follows these five rules. When in doubt, come back to these.

**1. Don't be preachy.** Scrolla is not a wellness intervention. It is a game among friends. "You scrolled less today — great job taking care of your mental health!" is wrong. "2.3 km — height of 3 Burj Khalifas" is right. Let the number do the work.

**2. Never shame.** The reverse leaderboard could easily read as a wall of shame. Every screen that shows ranking must frame it as a competition, not a judgment. "Jordan: 6.8 km" is fine. "Jordan scrolled the most again" is not. The number is the fact; the app does not comment on it.

**3. Be specific, not inspirational.** "The height of 3 Burj Khalifas" is specific. "You scrolled a lot today!" is inspirational. Specific is better every time. If a string could apply to any app, it's too vague.

**4. The permission screen is the most important screen in the app.** More users will decide whether to trust Scrolla from what they read on Screen 3 than from anything else. It must be honest, specific, and not defensive. Do not fluff it with reassurances that aren't backed by specifics.

**5. Short, then shorter.** The widget has 2 lines. The Home screen has one insight card. The leaderboard has one name per row. Write for the smallest space first, then the larger ones. A string that reads well at 8 words usually reads well at 20. The reverse is rarely true.

---

## 2. GLOBAL STRINGS — USED ACROSS MULTIPLE SCREENS

```kotlin
object ScrollaStrings {

    // App name
    const val APP_NAME = "Scrolla"

    // Notification (from AGENTS.md — do not change, it's in Constants.kt)
    const val NOTIFICATION_TEXT = "Tracking scroll distance"
    const val NOTIFICATION_CHANNEL_NAME = "Scroll Tracking"

    // Common actions
    const val ACTION_CONTINUE = "Continue"
    const val ACTION_SKIP = "Skip for now"
    const val ACTION_RETRY = "Try again"
    const val ACTION_CANCEL = "Cancel"
    const val ACTION_DONE = "Done"
    const val ACTION_SHARE = "Share"
    const val ACTION_JOIN = "Join"
    const val ACTION_CREATE = "Create"

    // Common errors
    const val ERROR_NETWORK = "No connection — showing last saved data"
    const val ERROR_GENERIC = "Something went wrong — tap to retry"
    const val ERROR_SYNC_FAILED = "Couldn't sync — will retry automatically"

    // Loading
    const val LOADING_TEXT = "Loading..."

    // Deleted user placeholder
    const val DELETED_USER_NAME = "[deleted]"

    // Left group placeholder (shows in historical leaderboard views)
    const val LEFT_GROUP_SUFFIX = "(left)"
}
```

---

## 3. ONBOARDING SCREENS (Screens 1–4)

### Screen 1 — Sign In

```
Headline:    "Welcome to Scrolla"
Subheadline: "Sign in to save your progress and join your friends"
Button:      "Continue with Google"
Fine print:  "Your scroll data stays on your device. Only your daily total is shared with your group."
```

**Design note:** The fine print exists here, not just on Screen 3, because this is where users decide whether to proceed. Don't remove it to declutter — it reduces sign-in hesitation specifically for an AccessibilityService app.

---

### Screen 2 — Welcome (what the app does)

```
Headline:    "How far do you scroll in a day?"
Body:        "Scrolla measures your scroll distance in real kilometres — then ranks you against friends. The lowest distance wins."
Landmark eg: "Yesterday's average: 2.3 km — the height of 3 Burj Khalifas"
Button:      "See how it works"
```

**Design note:** The landmark example should be hardcoded here as a relatable illustration, not a live number. It's an onboarding screen, not a dashboard.

---

### Screen 3 — Permission Ask ⚠️ DO NOT CHANGE WITHOUT DISCUSSION

**This is the most sensitive screen in the app. The copy below was deliberately written. Do not paraphrase, shorten, or "improve" it without explicitly discussing it.**

```
Headline:    "One permission needed"
Subheadline: "Scrolla uses Android's Accessibility feature to measure scroll distance"

What we track:
• "How far you scroll in each app (distance only)"
• "Which apps you're scrolling in"
• "The time of day you scroll most"

What we never see:
• "What you're reading or watching"
• "Text, images, or passwords"
• "Anything you type"

Where your data goes:
• "Scroll distance is saved on your phone"
• "Your daily total (km only) is shared with your group"
• "Your per-app breakdown never leaves your device"

Button: "Grant permission"
Link below button: "Why does Scrolla need this?"
```

**Why this copy is written this way:**
- "Accessibility feature" (not "Accessibility permission" or "Accessibility service") — the word "permission" triggers anxiety; "feature" is accurate and less alarming
- Three explicit bullet lists (track / never see / where it goes) — not a paragraph, because users scan onboarding screens and a paragraph means they'll skip it and feel deceived later
- "Distance only" appears twice — this is deliberate; it's the most important thing to communicate
- "What you're reading or watching" is explicitly listed as something we never see, because that's the #1 fear an informed user would have about an AccessibilityService
- "Your per-app breakdown never leaves your device" is a specific promise that is backed by the `DATA_CONTRACT.md` Section 3 rule — this copy must only exist if that rule is enforced in code

**The "Why does Scrolla need this?" link should expand inline to:**
```
"Android's Accessibility feature lets Scrolla read scroll events — the same system used by apps like TalkBack for screen readers. Scrolla only reads how far views scroll, not what's on screen. You can turn this off at any time in Settings → Accessibility."
```

---

### Screen 4 — Join or Create Group

```
Headline:    "Join your friends"
Subheadline: "Enter a 6-digit code from a friend, or create a new group"

Join tab:
  Input placeholder: "Enter code (e.g. A3KX72)"
  Button:            "Join group"
  Error — not found: "Group not found — check the code and try again"
  Error — own code:  "That's your own group code — share it with a friend to invite them"

Create tab:
  Body:   "Create a group and share the code with friends"
  Button: "Create group"
  After creation:
    "Your group code is [CODE]"
    Subtext: "Share this with up to [N] friends to invite them"
    Button: "Share code"
    Link: "Copy code"

Skip link (below both tabs): "Skip for now — you can join a group later"
```

---

## 4. HOME SCREEN (Screen 5)

### Hero stat

```kotlin
// Primary number — use DistanceFormatter.formatKm()
// e.g. "2.3 km"

// Landmark line — DO NOT CHANGE THE TEMPLATE STRUCTURE
// Template: "about the {landmark}"
// Examples:
"about the height of 3 Burj Khalifas"
"about a marathon's distance"
"about the height of the Eiffel Tower"
"about the distance to the ISS"

// If km is 0.0 (no data yet today):
"0.0 km — start scrolling to see your distance"
```

### Group rank chip

```
Template: "{rank} of {total} in {groupName}"
Examples:
"1st of 5 in College Friends"
"2nd of 5 in College Friends"
"Last of 5 in College Friends"   ← NOT "5th of 5" — "Last" is more natural
"Only one in {groupName}"        ← when group has 1 member, not "1st of 1"
```

**Design note:** "1st" not "Rank 1", "2nd" not "Rank 2" — ordinals read faster at a glance. "Last" instead of the numerical position for the last place — it's softer without being dishonest.

### Rotating insight card

Each insight type has an exact template. Use these, don't invent new phrasings.

**Type 1 — Personal record (new)**
```
Label: "personal best"
Text:  "New personal best! {km} — your lowest day yet"
```

**Type 2 — Personal record (existing, not beaten today)**
```
Label: "personal best"
Text:  "Your lowest day was {km}, {relativeDate}"
// relativeDate examples: "yesterday", "3 days ago", "last Tuesday", "3 weeks ago"
// Never use absolute dates (e.g. "Jan 14") on this card — relative is more readable at a glance
```

**Type 3 — Peak hour insight** ⚠️ DO NOT CHANGE THE TEMPLATE
```
Label: "peak scroll time"
Text:  "Most of your scrolling happens {hourRange} — that's your commute distance, but at {hourStart}"

// hourRange format: "10pm–11pm", "8am–9am", "12am–1am"
// hourStart format: "midnight", "8am", "10pm"
// Special case for midnight:
"Most of your scrolling happens 12am–1am — that's your commute distance, but at midnight"
```

**Why this template:** The specific phrasing "that's your commute distance, but at midnight" was designed to reframe the number as a physical thing happening at an inconvenient time, not as a moral failing. Do not replace "but at midnight" with "late at night" or "when you should be sleeping" — the original is specific and non-judgmental.

**Type 4 — App comparison nudge**
```
Label: "quick win"
Text:  "Cutting {topAppName} by 20% would put you in {potentialRank}"

// potentialRank: "1st", "2nd", etc. — same format as the rank chip
// topAppName: display name, not package name
//   "Instagram" not "com.instagram.android"
//   "Reddit" not "com.reddit.frontpage"
// If cutting 20% wouldn't change rank:
Text: "{topAppName} is your biggest source — {topAppKm} today"
```

**Type 4 — Placeholder (no data yet)**
```
Label: "today's insight"
Text:  "Scroll today to see your insights here"
```

---

## 5. LEADERBOARD SCREEN (Screen 6)

### Leaderboard rows

```
Format: "{rank}  {displayName}  {km}"
Your row: "{rank}  You  {km}"    ← always "You", never the user's actual display name on their own row

Rank labels:
1st place: "1st"
2nd place: "2nd"
3rd place: "3rd"
4th+:      "4th", "5th", etc.
Last:      Show numerical rank (e.g. "5th"), NOT "Last" — "Last" on the Home chip is a friendly glance;
           on the leaderboard it could read as singling someone out next to other numbered ranks

Departed member: "{displayName} {LEFT_GROUP_SUFFIX}"
                 e.g. "Jordan (left)"
```

### Most improved banner

```
Template: "{displayName} is most improved this week"
Examples:
"Priya is most improved this week"
"You're most improved this week"    ← when it's the current user

// Show at top of leaderboard, above the ranked list
// Only show if the improvement is ≥ 10% — suppress if everyone's numbers are flat
// If suppressed:
// [No banner shown — don't show "No one improved this week", just show nothing]
```

### Most consistent recognition

```
Template: "{displayName} is most consistent this week"
// Show alongside most improved, or alone if only one is applicable
// Same 10% threshold applies — don't show for flat weeks
```

### Group stats bar (today / yesterday / 7-day avg)

```
"today"     → DistanceFormatter.formatKm(todayKm)
"yesterday" → DistanceFormatter.formatKm(yesterdayKm)
"7-day avg" → DistanceFormatter.formatKm(sevenDayAvgKm)
```

### Hall of fame teaser (bottom of leaderboard screen)

```
Template: "group's best day: {recordKm} by {recordHolder}"
Example:  "group's best day: 0.4 km by Priya"
If no record set yet: "no record set yet — be the first"
Tap label: "Hall of fame →"
```

### Empty states

```
Group of 1 member:
  Headline: "You're the only one here"
  Body:     "Share your group code to invite friends. Until then, here's your personal progress."
  [Show personal stats — 7-day chart — instead of leaderboard]

No data yet today:
  [Show yesterday's leaderboard with label "yesterday" — don't show empty rows]
  If no data at all: "No data yet — start scrolling"

Network error:
  "Couldn't load — showing last known ranking"
  [Show cached data with a timestamp: "Last updated {relativeTime}"]
```

---

## 6. INSIGHTS SCREEN (Screen 7)

### Section labels

```
Weekly chart title:   "this week"
Top apps title:       "top apps by distance"
Peak hour title:      "peak scroll time"
Privacy note:         "App breakdown stays on this device, never shared with your group"

// The privacy note must always be visible on this screen — do not hide it in a tooltip or collapse it
```

### Top apps format

```
Template: "{appName}   {km}"
Examples:
"Instagram   1.2 km"
"Reddit   0.8 km"
"Twitter   0.3 km"

// Show max 5 apps
// If fewer than 3 apps have data:
"Keep scrolling to see your top apps"
```

### Peak hour format

```
Same template as Home insight Type 3:
"Most of your scrolling happens {hourRange} — that's your commute distance, but at {hourStart}"

If no peak hour data yet:
"Your peak time will appear after a few days of data"
```

---

## 7. SETTINGS SCREEN (Screen 9)

**Renamed from "Service Health screen."** Reached via the gear icon on the Profile tab (Screen 8). Two sections on one screen: **Service Health** (unchanged from the original design) and **Account** (relocated here from the old standalone Profile page — content is identical to before, only the location changed; see the decisions log at the bottom of this file).

### Service Health section

#### Status banners

```
Active (green):
  Title:   "Tracking is active"
  Subtitle: "Accessibility permission enabled"

Stopped — permission revoked (red): ⚠️ DO NOT CHANGE — this banner must be non-dismissible
  Title:   "Tracking stopped"
  Body:    "The accessibility permission was turned off — Scrolla can't measure distance without it"
  Button:  "Re-enable in Settings"
  // Button opens Settings → Accessibility directly, not the app's settings page

Degraded — permission on but events not firing (orange):
  Title:   "Tracking may be limited"
  Body:    "Scrolla is enabled but hasn't received recent scroll events — this may be a battery restriction"
  Button:  "Check battery settings"

Stopped — OEM kill suspected (orange):
  Title:   "Tracking was interrupted"
  Body:    "Your phone may have stopped Scrolla to save battery — this is common on {manufacturer} devices"
  Button:  "Fix battery settings"
```

#### Last sync line

```
Template: "Last synced {relativeTime}"
Examples:
"Last synced 4 minutes ago"
"Last synced just now"
"Last synced 2 hours ago"     ← if this shows, something is wrong — triggers the degraded banner
"Never synced"                ← first install before first sync
```

#### Battery whitelisting section header

```
"Battery settings — your phone: {manufacturer}"
```

#### Primary group row

```
Label:   "Widget group"
Subtext: "shown on your home screen widget"
Value:   "{groupName} ›"
```

**Note:** this is the *only* place the primary/widget group is set. The Profile tab's groups card (Section 12) is read-only and just links to the group switcher — don't rebuild a second primary-group toggle there.

#### Permission info link

```
Label: "What this permission can see"
// Opens inline expansion — use the same text as Screen 3's "Why does Scrolla need this?" section
```

#### Reboot note

```
"Permission is re-checked after every restart"
```

### Account section

#### Display name

```
Label:             "Display name"
Placeholder:       "Your name"
Subtext:           "Shown to friends on the leaderboard"
Save confirmation: "Name updated"
```

**Note:** the name itself is *displayed* (read-only) at the top of the Profile tab (Section 12) — editing always happens here.

#### Phone number linking

```
Label:    "Backup sign-in"
Subtext:  "Add a phone number so you can sign in if you lose access to Google"
Button (not linked): "Add phone number"
Button (linked):     "Phone number linked"
Linked subtext:      "+XX XXXXX XXXXX"
```

#### Sign out

```
Button: "Sign out"
Confirmation dialog:
  Title:  "Sign out of Scrolla?"
  Body:   "You can sign back in anytime with your Google account"
  Confirm: "Sign out"
  Cancel:  "Cancel"
```

#### Delete account ⚠️ CONSEQUENCES MUST BE STATED CLEARLY — DO NOT SOFTEN

```
Button (destructive): "Delete account"

Confirmation screen (NOT a dialog — a full screen because of severity):
  Title:  "Delete your Scrolla account?"
  Body:   "This will permanently delete:
           • Your scroll history
           • Your personal records
           • Your group memberships

           Your name in group history will be replaced with '{DELETED_USER_NAME}'.
           This cannot be undone."

  Input:  "Type DELETE to confirm"    ← require explicit typed confirmation, not just a button
  Confirm button (only active after typing DELETE): "Delete my account"
  Cancel: "Keep my account"

In-progress: "Deleting your account..."
Success:     [Returns to sign-in screen with no message — a clean exit is the right UX]
Error:       "Couldn't delete your account — try again or contact support"
```

**Why the delete screen is a full page with typed confirmation:** This is a permanent action that removes auth + Firestore data. The standard two-button confirmation dialog is too easy to confirm by accident. Requiring typed "DELETE" ensures the user has read and understood the consequences. Do not replace this with a simpler dialog to reduce friction — the friction is intentional.

---

## 8. GROUP SCREENS (Screens 10–11)

### Group switcher (Screen 10)

```
Title: "Your groups"

Group row format:
  "{groupName}"
  Subtext when primary: "Widget group"

Add group button: "+ Join another group"

Empty state:
  "You're not in any groups yet"
  Button: "Join a group"
```

### Join group (Screen 11)

```
Title:             "Join a group"
Input placeholder: "Enter 6-digit code"
Button:            "Join"
Success:           "You've joined {groupName}"
Error — not found: "Group not found — check the code"
Error — already:   "You're already in this group"
Error — own code:  "That's your own group code"
```

---

## 9. GAMIFICATION SCREENS (Screens 12–14)

### Weekly recap card (Screen 12)

```
Card headline:   "Your week in scroll"
Stat line:       "{weeklyTotalKm} this week"
Landmark line:   "that's {landmark}"
Rank line:       "{rank} in {groupName}"
Badge (if earned): "Most improved" or "Most consistent"
Footer:          "Scrolla"

Share button:    "Share your recap"
Skip button:     "Maybe later"
```

### Personal records (Screen 13)

```
Title: "Your records"

Best day section:
  Label:  "lowest day"
  Value:  "{bestKm}"
  Detail: "{relativeDate}"         e.g. "3 weeks ago" or "yesterday"

Milestone section (first time under a round number):
  "First time under 1 km"   → show if `getPersonalBestKm() < 1.0`
  "First time under 2 km"   → show if `getPersonalBestKm() < 2.0`
  // Only show the lowest milestone achieved, not all of them

7-day average:
  Label: "7-day average"
  Value: "{avgKm}"

Empty state (fewer than 2 days of data):
  "Your records will appear after a couple of days of data"
```

**Note:** this screen is now also reachable from the Profile tab's personal-best teaser card (Section 12), in addition to Home. The content and queries here are unchanged — Profile just adds a second entry point.

### Hall of fame (Screen 14) ⚠️ FRAMING IS DELIBERATE — DO NOT CHANGE

```
Title: "Group records"

Best day row:
  "{displayName}  {recordKm}  {relativeDate}"
  e.g. "Priya   0.4 km   2 weeks ago"

Progress toward record: ⚠️ DO NOT REMOVE — this line is a shame-mitigation design decision
  "You're {gapKm} from the group's best day"
  e.g. "You're 1.9 km from the group's best day"
  // Always show this line for the current user, even if they hold the record
  // If they hold the record: "You hold the group's best day"

No record set:
  Title: "No record set yet"
  Body:  "The first person to complete a day will set the record"
```

**Why "progress toward record" must stay:** The hall of fame without it is purely a display of who has the single best number ever. With it, every user has a personal progress line that's about *them* relative to the record, not about their rank relative to others. This is the specific framing fix from `scrolla_project_summary.md` Section 16 — it reduces the shame potential of showing an absolute ranking of historical bests.

**Note:** this screen is now also reachable from the Profile tab's hall-of-fame teaser card (Section 12), which reuses this exact "progress toward record" line — the same framing rule applies there too.

---

## 10. APP BREAKDOWN DETAIL SCREEN (Screen 15)

```
Title: "App breakdown"
Subtitle: "Today's scroll by app — stays on your device"

App row format:
  "{appName}   {km}"
  Progress bar showing proportion of today's total

Nudge line (show below the #1 app):
  Template: "Cutting {topAppName} by 20% would put you in {potentialRank}"
  Example:  "Cutting Instagram by 20% would put you in 1st"
  If cutting 20% wouldn't change rank:
  "{topAppName} is your biggest source today"

Empty state:
  "No app data yet today — keep scrolling"

Privacy note (always visible, same as Insights screen):
  "App breakdown stays on this device, never shared with your group"
```

---

## 11. (retired) — formerly "PROFILE SCREEN"

This section previously described a single "Profile" screen that mixed account admin with identity content. It's been split in two:
- Account admin (display name editing, phone linking, sign out, delete account) now lives in **Section 7 — Settings (Screen 9)**.
- Identity/achievement content is now the Profile *tab* — see **Section 12** below.

Left as a placeholder heading rather than deleted outright, so anyone searching this file for "Profile" and landing on the old section number gets redirected instead of finding a gap.

---

## 12. PROFILE TAB (Screen 8)

**New screen.** Added when Profile was promoted from a settings-only page to a main bottom tab — an identity/achievement hub in the Strava/Duolingo mold, not a settings dump. This is deliberately a **teaser hub**: every card below reuses copy and data already defined for Personal Records (Screen 13) and Hall of Fame (Screen 14) rather than duplicating it. If a future decision moves this to show full stats inline instead, update this section and the design note in `scrolla_project_summary.md` Section 8 together — don't let them drift apart.

### Identity header

```
Display name: {displayName}   // read-only here — editing happens in Settings → Account (Screen 9)
Gear icon: opens Settings (Screen 9)
```

### Personal best teaser card

```
Label: "personal best"
Text:  "{bestKm} · {relativeDate}"
// same underlying value as Personal Records' "lowest day" section (Screen 13) — display it, don't recompute it separately
Empty state (no data yet): "Keep scrolling to set your first record"
Tap label: "Personal records →"
```

### Hall of fame status teaser card ⚠️ SAME FRAMING RULE AS SCREEN 14 — DO NOT SOFTEN

```
Text (not record holder): "You're {gapKm} from the group's best day"
Text (record holder):     "You hold the group's best day"
// identical line to Hall of Fame's "progress toward record" (Screen 14) — the same shame-mitigation
// reasoning applies here: don't drop it, don't reword it into something more "rank"-flavored
Empty state (no group record set yet): "No record set yet — be the first"
Tap label: "Hall of fame →"
```

### Groups teaser card

```
Template: "{groupCount} groups · {primaryGroupName} on widget"
Example:  "2 groups · College Friends on widget"
Tap label: "Manage groups →"
```

**Note:** this card is read-only. The actual primary/widget-group control lives on the Settings screen (Section 7's "Primary group row") — don't build a second editable control here, it'll just create two sources of truth for the same setting.

### Empty state (brand-new user, no scroll data at all)

The personal-best and hall-of-fame cards each show their own empty state individually (above). No separate full-screen empty state is needed — same approach as Home's rotating insight card handling a new user (Section 4, Type 4 placeholder).

---

## 13. WIDGET COPY

### Small widget

```
Line 1: "{km}"                          e.g. "2.3 km"
Line 2: "{landmark (short form)}"       e.g. "3 Burj Khalifas"

// Short landmark form strips the "about the height of" prefix
// Full: "about the height of 3 Burj Khalifas"
// Short: "3 Burj Khalifas"

No data state:
Line 1: "0.0 km"
Line 2: "tap to start"
```

### Medium widget

```
Line 1: "{km}"
Line 2: "{rank} in {groupName}"         e.g. "2nd in College Friends"
Line 3: "{landmark (short form)}"
```

### Large widget

```
Header: "this week"
Chart:  7-day bar chart (simplified, no axis labels — bars only)
Row 1:  "today   {km}   {rank}"
Row 2:  "{topAppName}   {appKm}"        ← top app for today
Footer: "{groupName}"
```

---

## 14. STRINGS NOT YET WRITTEN — TRACK HERE

Add a row whenever B identifies a string that needs to exist but hasn't been designed yet. Do not invent inline copy without adding it here first.

| Screen | Context | Status | Copy |
|---|---|---|---|
| — | — | Nothing pending | — |

---

## 15. COPY DECISIONS LOG — WHAT WAS CHANGED AND WHY

Any time B edits copy that was already in this file, log the old version, the new version, and the reason. This prevents "I wonder why it said that" questions two months later.

| # | Date | Screen | Old copy | New copy | Reason |
|---|---|---|---|---|---|
| 1 | 2026-07-11 | Screens 8/9 (was: Screen 8 "Service Health", Screen 15 "Profile") | Two screens: "Service Health" (Screen 8, tracking/battery only) and "Profile" (Screen 15, admin-only: name, groups, phone, sign-out, delete) | Split into "Profile" (Screen 8, new main tab — identity/achievement hub with teaser cards) and "Settings" (Screen 9 — Service Health + Account sections merged, reached via gear icon on Profile) | Profile promoted to a bottom tab, Strava/Duolingo-style, so it serves as an identity/achievement surface instead of a settings dump. Account admin and service-health content consolidated into one Settings destination rather than living on two separate screens. Screens 9 onward renumbered by +1 as a result. Full reasoning logged in `SOCIAL_PROGRESS.md` Section 9. |
