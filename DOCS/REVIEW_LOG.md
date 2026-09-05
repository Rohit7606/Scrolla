
# REVIEW_LOG.md — Scrolla
**Purpose:** Tracks every cross-review that happens on this project. Two types of review are mandatory (defined in `AGENTS.md` Section 2). All others are optional but encouraged.
**AI agents reading this:** Before suggesting a merge for any PR touching `tracking/` or `firestore/` security rules, check this log. If the PR isn't logged here with a completed review, it is not ready to merge regardless of build status.

---

## WHY CROSS-REVIEWS EXIST ON THIS PROJECT

Both mandatory reviews exist because of a specific, documented risk — not as a generic "code review is good" habit:

**B reviews A's `tracking/` code:** The per-view delta tracking in `AGENTS.md` Section 4.2 is the single piece of code most likely to produce a plausible-looking wrong answer — a number that compiles, runs, and looks reasonable but is subtly incorrect (cross-app phantom distance, RecyclerView reset mishandled). Since B is the one whose UI will display that number to users, B has the most at stake if the number is wrong and is the right person to pressure-test A's logic against the contract in `DATA_CONTRACT.md` Section 2.

**A reviews B's Firestore security rules:** The security rules in `scrolla_project_summary.md` Section 10 were the single most consequential gap found in the loophole audit — they didn't exist anywhere in the original plan. A is closer to the data model than B (A owns Room and defines what gets synced) and is better placed to catch a rule that accidentally allows a write path that shouldn't exist, or misses a field-level constraint. A also has to trust that the rules won't let someone else overwrite A's sync output.

---

## MANDATORY REVIEW TYPES

### Type M1: A's delta tracking code (B reviews)
**Triggered by:** Any PR from Person A touching `tracking/ScrollAccessibilityService.kt` or anything in `tracking/` that touches the per-view HashMap or the `accumulateDistance()` call chain.
**Reviewer:** Person B
**What to check (B works through this checklist, not just a read-through):**

- [ ] A single global `lastScrollY` variable does not exist anywhere in the file. The per-view `HashMap<String, Int>` is the only accumulator.
- [ ] The map key is composite: `"${packageName}:${className}:${viewId}"` — not just `packageName` alone.
- [ ] The RecyclerView reset guard is present: a delta more negative than `-ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX` triggers a key reset, not a distance accumulation.
- [ ] `event.getSource()` is only called for `viewIdResourceName` extraction, immediately released, and nothing else is read from it (no text, no content description, no child views).
- [ ] `accumulateDistance()` receives only non-negative values — the `Math.abs()` call or equivalent is present.
- [ ] The batch flush fires in both `onInterrupt()` and `onDestroy()` — not only on the timer.
- [ ] Distance is accumulated in cm, not pixels. The `DistanceFormatter.pxToCm()` call is present before accumulation.
- [ ] No new fields have been added to any Firestore document from within this file. The sync responsibility stays with the sync function only.
- [ ] The unit tests from `AGENTS.md` Section 5.1 cover the four required cases (normal scroll, app switch, RecyclerView reset, first event on a view).

**Verdict options:** Approved / Approved with minor notes / Changes required before merge

---

### Type M2: B's Firestore security rules (A reviews)
**Triggered by:** Any PR from Person B touching `firestore/` rules, deploying to Firebase, or changing the Firestore document structure in any way.
**Reviewer:** Person A
**What to check (A works through this checklist):**

- [ ] The default rule is NOT `allow read, write: if true`. This is the tutorial default and is not a real security rule.
- [ ] A user can only write their own `dailyTotals` document — the rule checks that `docId` starts with `request.auth.uid` AND that `request.resource.data.userId == request.auth.uid`.
- [ ] A user cannot write to another user's `/users/{userId}/` document.
- [ ] The `totalKm` field in a `dailyTotals` document cannot be set to a negative value by a rule-level check, if feasible (e.g. `request.resource.data.totalKm >= 0`).
- [ ] Per-app data fields (`appTotals`, `topApp`, anything resembling an app breakdown) do not exist in any Firestore document path — rules should not be written to allow them either.
- [ ] The rules have been tested in the Firebase Rules Playground for at least one "should succeed" and one "should fail" case per `AGENTS.md` Section 5.3. Screenshots or test case descriptions are noted in the PR description.
- [ ] The `members` array on the group document exists and is updated correctly by group join/leave flows, even if the rules don't enforce membership checks yet (it's there for future rule tightening without a migration).
- [ ] Nothing in the deployed rules exposes `/users/{userId}/` data to anyone other than the user themselves.

**Verdict options:** Approved / Approved with minor notes / Changes required before merge

---

## OPTIONAL REVIEW TYPES

These are encouraged but not gatekeeping. Log them here anyway — it builds a useful record of decisions made and why.

### Type O1: DATA_CONTRACT.md change (both review)
Any change to `DATA_CONTRACT.md` should be looked at by both people before the branch is merged — not because it always carries risk, but because a silent contract change is the fastest way to break the other person's code invisibly. This is already required by the change protocol in `DATA_CONTRACT.md` itself; logging it here just keeps the record in one place.

### Type O2: Room schema migration (B reviews)
If A adds a column, renames a table, or changes a type in any Room entity, the migration code should be spot-checked by B — specifically to confirm that `fallbackToDestructiveMigration()` is not being used in any production config, and that the migration doesn't silently drop data that B's UI is currently displaying.

### Type O3: Any screen that displays a number derived from sensor data (A reviews)
Optional but valuable: if B builds or changes a screen that shows `totalKm`, B can ask A to do a quick sanity check that the data binding path (ViewModel → Compose) actually traces back to `ScrollRepository.getTodayTotalKm()` and not to a stale mock or a hardcoded value. Quick to check, catches a class of "ship day" embarrassment.

---

## REVIEW LOG

Add a row every time a review happens — mandatory or optional. One row per PR, not per file changed.

| # | Date | PR / Branch | Type | Reviewer | Verdict | Issues found | Resolved? |
|---|---|---|---|---|---|---|---|
| 1 | 2026-07-10 | a/delta-tracking | M1 | B | Approved | None — see notes above regarding the viewId finding, which is informational only and does not block approval | Yes |
| 2 | 2026-08-16 | Firebase Console | M2 | A | Approved | None — Firebase Rules tested in Rules Playground and deployed in Production Mode. | Yes |
| 3 | 2026-08-23 | b/distance-formatter | O1 | A | Approved | Reviewed DistanceFormatter.kt and DATA_CONTRACT.md §5. Confirmed cmToKm(), formatKm(), formatKmValue(), nearestLandmark(), and adaptive metre/km display (formatDisplayValue/formatDisplayUnit/formatDistance/formatDistanceSpoken) based on on-device scroll measurements. Locale.US pinning prevents comma-decimal issues. All additions verified. | Yes |
| 4 | 2026-08-23 | firestore/firestore.rules | M2 | A | Approved | Reviewed security rules fix: (1) `allow get: if signedIn(); allow list: if false;` on `/groups/{groupId}` prevents collection enumeration while allowing direct lookups; (2) `isSelfJoin()` safely permits `joinGroup()` `arrayUnion` on `members` only without overwriting or deleting existing members; (3) `isRecordImprovement()` safely permits members to write new/improved best day records (`recordKm`, `recordHolder`, `recordDate`) ensuring lowest-wins logic (`<=`). All checks in M2 checklist passed. | Yes |
| 5 | 2026-08-25 | b/health-card-cleanup (firestore.rules) | M2 | A | Approved | Reviewed the account-deletion rules. (1) `isSelfLeave()` permits removing only the caller: `hasOnly` keeps the new member list a subset of the old, `size() == size() - 1` allows exactly one removal, and `!(request.auth.uid in request.resource.data.members)` forces that removal to be self — without the subset check a member could remove someone else and add an impostor while the size arithmetic still passed. (2) The `dailyTotals` write rule split into `create, update` and `delete`: `allow write` covered deletes, but `request.resource` is null on a delete, so the `request.resource.data.userId` check could never pass and deleting your own totals was impossible — account deletion was unimplementable until this was split. Delete is gated on the uid-prefixed doc id, which is the only thing available on a delete. | Yes |
| 6 | 2026-08-26 | b/health-card-cleanup (firestore.rules) | M2 | **B (self-review — A unavailable)** | Approved with caveat | `isGroupRename()`: any member, `hasOnly(['groupName'])`, string, 1–40 chars. **Deliberately not creator-only** — `createdBy` exists and creator-only was the obvious first choice, but a creator can now leave the group (`isSelfLeave`), which under creator-only would strand the name with nobody able to fix even a typo. Blast radius is a shared label among people who chose to be in a group together, and it is trivially reversible. Length bound duplicated in the rule and the client because the rule is the only one a hostile client cannot skip. **CAVEAT: this was not reviewed by a second person.** §2 requires A on rules changes and A was unavailable. Reviews #2 and #4 each caught a real bug this way — #4 found that `allow create` does not grant `allow update`, which meant group joining had never worked. Flag for retro-review. | Yes |
| 7 | 2026-08-26 | b/health-card-cleanup (model/DistanceFormatter.kt) | O1 | **B (self-review — A unavailable)** | Approved with caveat | Sub-metre display fix (PREMIUM_CHECKLIST P4.4). `formatDisplayValue` used `%.0f`, so anything below half a metre rendered "0 m" — found in a real export where Telegram (0.4 m) and the system launcher (0.3 m) were indistinguishable on App Breakdown from apps never scrolled. Now `<1 m`, with `formatDistanceSpoken` returning "less than one metre" so TalkBack does not read the literal "<1 metres". Threshold is 0.5 because `%.0f` rounds half-up. 14 tests added (`DistanceFormatterTest`), pinning this plus two previously shipped bugs. **CAVEAT: `model/` is edit-together per §2 and A signed off its last change (Review #3). Not reviewed by a second person.** Flag for retro-review. | Yes |
| 8 | 2026-09-05 | b/health-card-cleanup (full A-track audit) | **M1** | B | **Changes required** | Full audit of `service/`, `room/`, `device/` — see `DOCS/AUDIT_A_TRACK.md`. **One M1 checklist item is not satisfied:** "the reset guard triggers a key reset, *not* a distance accumulation". The guard's `if` body contains only a `Log.d` call; `computed` is returned unchanged, `pxToCm` applies `Math.abs()`, and the phantom jump is accumulated at full magnitude. Review #1 ticked this item in 2026-07-10 — satisfied by watching the RESET DETECTED line appear, without checking the distance was excluded. 10 further findings: a broken double-checked lock in `getDatabase()` that can build two Room instances over one file; "lowest day ever" including today (proven on device — it returns today at 4.06 m); no index on `scroll_events.day` with `EXPLAIN` showing SCAN at ~400 rows/day and `deleteOlderThan()` never called; `AppTotal` having no DAO at all; repository read failures marking the *service* degraded; and no `startForeground` below API 30 despite `minSdk 24`. All other M1 items pass, and the privacy posture is enforced by config rather than discipline. | No — A to action |

---

## HOW TO LOG A REVIEW (template)

Copy this block, fill it in, and paste it below the table above after the review is done:

```
| [auto-number] | [YYYY-MM-DD] | [branch name or PR #] | [M1 / M2 / O1 / O2 / O3] | [reviewer's name/initial] | [Approved / Approved with notes / Changes required] | [brief description of issues found, or "None"] | [Yes / No / N/A] |
```

**Example completed entry:**
```
| 1 | 2025-02-14 | a/delta-tracking | M1 | B | Approved with notes | HashMap key was missing className component — fixed before merge | Yes |
```

---

## WHAT "APPROVED" ACTUALLY MEANS

Approving a review is not a guarantee that the code is bug-free — it means the reviewer has worked through the relevant checklist above and nothing on the checklist was violated. It is not a rubber-stamp read-through.

If a reviewer is unsure about something on the checklist — particularly something Kotlin-or-Android-specific they don't fully understand yet — the right move is to ask the AI agent to explain the specific line in question ("explain what this line does and whether it violates the per-view tracking rule") rather than approving with uncertainty. Log that the agent was consulted in the Notes column.

---

## WHAT HAPPENS IF CHANGES ARE REQUIRED

1. Reviewer logs "Changes required" in the table and adds the specific checklist item(s) that failed.
2. Author fixes the issues on the same branch, does not open a new PR.
3. Reviewer re-checks only the items that failed — not the entire checklist again.
4. Reviewer updates their log row from "Changes required" to "Approved" once satisfied.
5. PR merges only after this update.

Do not merge a "Changes required" PR while the reviewer is offline or hasn't confirmed the fix. The whole point of the review is to have a second pair of eyes on the specific risk — skipping the re-check defeats that.

---

## ESCALATION

If a review surfaces something neither person knows how to resolve — a security rule ambiguity, a Kotlin type-system edge case, an Android lifecycle question — bring it to an AI agent in a fresh session with `AGENTS.md` and `DATA_CONTRACT.md` attached. Log the agent's response in the Notes column of the review row so the decision is traceable and doesn't have to be re-litigated if the same question comes up again.
