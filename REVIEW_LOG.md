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
| — | — | — | — | — | — | No reviews yet | — |

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
