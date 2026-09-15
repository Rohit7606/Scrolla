# Runbook — setting up the two distribution channels

One-time setup for BUILD_PLAN 2.1a. Everything here is free; the Play Store is
ruled out.

After this, `./scripts/release.sh` publishes to both channels in one command.

**Run everything in Git Bash, not PowerShell.** PowerShell rewrites binary
streams on `>` — measured on 2026-09-14 turning a 655,360-byte database into
768,060 bytes of unusable file. The same hazard applies to an APK.

---

## Part A — Firebase App Distribution

The managed channel. Testers get a notification on their phone when a build
lands, and you can see who is running which version — which is what makes
"it's broken" answerable once fifty people have it.

### A1. Turn it on (it has never been used)

Checked 2026-09-14: the API returns `404 NOT_FOUND` for this project, so App
Distribution has not been provisioned yet. The CLI cannot create a group until
it is.

1. [Firebase console](https://console.firebase.google.com/project/scrolla-9986e) → **Scrolla**
2. Left sidebar → **Release & Monitor** → **App Distribution**
3. Click **Get started**

That is the only step that must happen in the browser.

### A2. Create the tester group

Back in Git Bash:

```bash
export PATH="$PATH:$HOME/AppData/Roaming/npm"
firebase appdistribution:groups:create "Scrolla Testers" scrolla-testers --project scrolla-9986e
```

> **The alias must be exactly `scrolla-testers`.** That second argument is the
> alias, not the display name, and it is what `app/build.gradle.kts` refers to
> in `groups = "scrolla-testers"`. The display name can be anything. Getting
> these the wrong way round is the usual way this fails — the upload succeeds
> and nobody is notified.

Verify:

```bash
firebase appdistribution:groups:list --project scrolla-9986e
```

### A3. Add testers

> **Substitute real addresses before running this.** On 2026-09-14 this
> runbook's example was run verbatim and created two testers called
> `friend1@gmail.com` and `friend2@gmail.com` — plausible addresses that may
> belong to actual strangers, sitting in the group the next release emails an
> invite to. They were removed. The placeholders below are now obviously fake,
> so running them by accident fails instead of succeeding quietly.

For a handful of people:

```bash
firebase appdistribution:testers:add \
  --group-alias scrolla-testers \
  --project scrolla-9986e \
  REPLACE-ME@example.com ALSO-REPLACE-ME@example.com
```

For fifty, use a file — one address per line. Better anyway, because it keeps
the list out of your shell history:

```bash
# create testers.txt first, one address per line
firebase appdistribution:testers:add --file testers.txt \
  --group-alias scrolla-testers --project scrolla-9986e
```

`testers.txt` is already in `.gitignore` — those are other people's email
addresses and this repo is going to have a public sibling.

Check who is actually on the project before every release:

```bash
firebase appdistribution:testers:list --project scrolla-9986e
```

Remove someone:

```bash
firebase appdistribution:testers:remove --project scrolla-9986e someone@example.com
```

### A3b. Adding a tester does not send them anything

**The email is sent by *distributing a build*, not by adding a tester.** This
cost an afternoon on 2026-09-15, so it is written down rather than rediscovered.

Two separate things behave in ways that look like a broken email service:

- **Invite links send no email, ever.** A link like
  `https://appdistribution.firebase.dev/i/<id>` is the *self-serve* channel:
  **you** send that link to people yourself, over WhatsApp or anything else.
  Someone entering their address on that page joins the tester list and is
  emailed nothing, because the page they are already looking at is the install
  page. Nothing is wrong when this happens — it is the whole design of the
  feature. Firebase counts these under `Invite Link Count` in
  `appdistribution:groups:list`.
- **A tester added after a release gets no email for that release.** Joining a
  group grants access to everything already distributed to it, but the
  notification fires on distribution, not on membership. Someone added today to
  a group whose last build went out yesterday sits there in silence and
  concludes the invite is broken.

So the recovery for "a tester is in the list and has heard nothing" is always
the same: **distribute a build to the group again** (A5). It is cheap, and it
is the only thing that reliably sends mail.

Check what actually exists before assuming the email is at fault:

```bash
firebase appdistribution:groups:list --project scrolla-9986e
```

`Release Count: 0` means no build has ever been distributed to that group, and
no email could have been sent no matter how many testers are in it. The CLI has
no `releases:list`; the Firebase Console is the only place to see release
history and dates.

### A4. What a tester does, once

1. Gets an invite email, accepts it — **check spam and Promotions**, these are
   sent by Google as bulk mail and land there routinely
2. Installs Google's **App Tester** app from the link in that email
3. Installs Scrolla from inside App Tester

From then on they are notified automatically for every new build.

### A5. Distributing a build — the command that sends the email

```bash
./gradlew :app:assembleRelease
./gradlew :app:appDistributionUploadRelease
```

`scripts/release.sh` runs both, plus the tag and the GitHub release. The group
is configured in `app/build.gradle.kts` (`groups = "scrolla-testers"`), which is
why no flags are needed here — and why nothing is ever accidentally uploaded to
nobody.

Watch for this line in the output. It is the one that means mail went out:

```
Added testers/groups successfully 200
```

`Uploaded APK successfully 200` on its own means the build is in App
Distribution and **nobody has been told about it**.

The pure-CLI equivalent, if the Gradle plugin is ever not available:

```bash
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app 1:166613112223:android:9ec684283d45b8f6d919aa \
  --groups scrolla-testers \
  --release-notes "what changed" \
  --project scrolla-9986e
```

**`--groups` is the whole point.** Without it the binary uploads, the command
succeeds, and not one person is notified — which is indistinguishable from a
broken email service.

A timeout part-way through the upload (`Read timed out`) is not a failure worth
debugging; it happened once on 2026-09-15 and the identical command succeeded on
the retry. Nothing partial is distributed, because the tester step runs after
the upload completes.

**They will still meet the Android 13+ restricted-settings step**, because an
app installed this way is still sideloaded. Only a Play Store install avoids
it. Onboarding handles it — verified working on OPPO/ColorOS 15 — so it is a
step, not a blocker.

---

## Part B — GitHub Releases, for Obtainium

The channel that does not depend on Firebase continuing to be free, and the one
that gives testers automatic updates without any Google account involvement.

### B1. Create a separate, public, APK-only repo

**Not this repo.** This one's source and commit history are not written for an
audience, and publishing them is a decision nobody has taken. The release repo
holds APKs and nothing else.

`gh` is already authenticated as `Rohit7606`, so:

```bash
gh repo create scrolla-releases --public \
  --description "Scrolla release builds. Source is private." \
  --add-readme
```

### B2. Point the release script at it

```bash
export SCROLLA_RELEASE_REPO=Rohit7606/scrolla-releases
```

To make it permanent rather than per-shell, add that line to `~/.bashrc`.

### B3. What a tester does, once

1. Installs [Obtainium](https://github.com/ImranR98/Obtainium) — itself
   distributed this way, from its own GitHub releases
2. Adds `https://github.com/Rohit7606/scrolla-releases` as an app source
3. Obtainium then checks for new releases and installs them

### Never put these in the release repo

- `scrolla-release.jks` or `keystore.properties`. Losing control of the signing
  key means nobody can ever update their install without uninstalling first,
  which deletes their history.
- Anything from `backups/`.

The APK itself is fine to publish. It already contains the `google-services.json`
values, as every distributed Android build does; those are client identifiers,
not secrets.

---

## Part C — first release

```bash
./scripts/release.sh --dry-run   # builds and checks, publishes nothing
./scripts/release.sh             # publishes to both
```

The preflight refuses a dirty tree, refuses a tag that already exists, and —
the one that matters — reads the SHA-1 out of the keystore that just signed the
APK and aborts if it is not registered in `google-services.json`. That is the
launch blocker found on 2026-09-14, where the signed build could not sign in
and every screen sits behind sign-in. It cannot ship that way again silently.
