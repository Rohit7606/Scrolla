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

```bash
firebase appdistribution:testers:add \
  --group-alias scrolla-testers \
  --project scrolla-9986e \
  friend1@gmail.com friend2@gmail.com
```

Or from a file, one email per line — better for fifty people, and it keeps the
list out of your shell history:

```bash
firebase appdistribution:testers:add --file testers.txt \
  --group-alias scrolla-testers --project scrolla-9986e
```

**Do not commit `testers.txt`.** Add it to `.gitignore` first — those are other
people's email addresses and this repo is going to have a public sibling.

### A4. What a tester does, once

1. Gets an invite email, accepts it
2. Installs Google's **App Tester** app from the link in that email
3. Installs Scrolla from inside App Tester

From then on they are notified automatically for every new build.

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
