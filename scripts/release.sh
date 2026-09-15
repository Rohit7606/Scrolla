#!/usr/bin/env bash
#
# Publish a Scrolla release to both distribution channels (BUILD_PLAN 2.1a).
#
# Run from Git Bash on Windows, never PowerShell — see the note about binary
# redirection at the bottom of this file.
#
#   ./scripts/release.sh            # build, then publish to both channels
#   ./scripts/release.sh --dry-run  # build and report, publish nothing
#
# Two channels on purpose:
#
#   Firebase App Distribution  the managed one. Testers are notified on their
#                              phone, and you can see who is on which build —
#                              which is what makes "it's broken" answerable
#                              when fifty people are running it.
#
#   GitHub Releases            the one that does not depend on Firebase
#                              continuing to be free. Testers using Obtainium
#                              (https://github.com/ImranR98/Obtainium) point it
#                              at the release repo once and get updates
#                              automatically thereafter.
#
# Neither removes the Android 13+ restricted-settings step. Only a Play Store
# install would, and Play is ruled out. Onboarding handles it — verified on
# OPPO/ColorOS 15 on 2026-09-14 — so it is a handled step, not a blocker.

set -euo pipefail

DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

cd "$(dirname "$0")/.."

APK="app/build/outputs/apk/release/app-release.apk"

# The repo that holds only the APK. NOT this repo — publishing this one's
# source and history is a separate decision nobody has taken.
RELEASE_REPO="${SCROLLA_RELEASE_REPO:-}"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
die() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- preflight
say "Preflight"

[[ -f keystore.properties ]] || die "keystore.properties missing — the release build cannot be signed.
       Without the release key, an APK cannot install over an existing one."

git diff --quiet || die "working tree is dirty. Release what is committed, or
       nobody can tell later what a given APK actually contains."

VERSION_CODE="$(git rev-list --count HEAD)"
VERSION_NAME="1.0.${VERSION_CODE}"
TAG="v${VERSION_NAME}"
NOTES="$(git log -1 --pretty=%s)"

echo "  version : ${VERSION_NAME} (code ${VERSION_CODE})"
echo "  tag     : ${TAG}"
echo "  notes   : ${NOTES}"

git rev-parse "${TAG}" >/dev/null 2>&1 && die "tag ${TAG} already exists.
       versionCode comes from the commit count, so commit something first —
       Android refuses an install whose versionCode is not higher."

# ---------------------------------------------------------------- build
say "Building release APK"
./gradlew :app:assembleRelease

[[ -f "$APK" ]] || die "no APK at $APK"
SIZE_MB="$(python -c "import os;print('%.2f' % (os.path.getsize('$APK')/1048576))")"
echo "  built ${APK} (${SIZE_MB} MB)"

# The failure this catches is the one that cost a launch blocker on
# 2026-09-14: the release build could not sign in because its signing
# certificate was never registered in Firebase. If the SHA-1 of the key that
# just signed this APK is not in google-services.json, every user gets a
# sign-in button that fails with code 10 and no way past it.
say "Checking the signing certificate is registered"
KEYTOOL="/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe"
if [[ -x "$KEYTOOL" ]]; then
  STOREPASS="$(grep -E '^storePassword' keystore.properties | cut -d= -f2- | tr -d '\r')"
  ALIAS="$(grep -E '^keyAlias' keystore.properties | cut -d= -f2- | tr -d '\r')"
  SHA1="$(MSYS_NO_PATHCONV=1 "$KEYTOOL" -list -v -keystore scrolla-release.jks \
            -alias "$ALIAS" -storepass "$STOREPASS" 2>/dev/null \
          | grep -iE 'SHA1:' | head -1 | sed 's/.*SHA1: //' | tr -d ': \r' | tr 'A-Z' 'a-z')"
  if [[ -n "$SHA1" ]] && grep -qi "$SHA1" app/google-services.json; then
    echo "  ok — ${SHA1} is in google-services.json"
  else
    die "the release signing certificate (${SHA1:-unknown}) is NOT in
       app/google-services.json. Google sign-in will fail with code 10 and
       everything in Scrolla is behind sign-in.
       Fix: Firebase console -> Project settings -> Add fingerprint, then
       download the regenerated google-services.json and commit it."
  fi
else
  echo "  SKIPPED — keytool not found. Verify by hand before handing this out."
fi

if [[ $DRY_RUN -eq 1 ]]; then
  say "Dry run — nothing published"
  exit 0
fi

# ---------------------------------------------- channel 1: App Distribution
say "Channel 1 — Firebase App Distribution"
if firebase login:list 2>/dev/null | grep -qi "@"; then
  ./gradlew :app:appDistributionUploadRelease
  echo "  uploaded; testers in 'scrolla-testers' are notified"
else
  echo "  SKIPPED — not logged in. Run: firebase login"
fi

# ---------------------------------------------- channel 2: GitHub Releases
say "Channel 2 — GitHub Releases"
if [[ -z "$RELEASE_REPO" ]]; then
  echo "  SKIPPED — set SCROLLA_RELEASE_REPO=owner/repo (a public, APK-only repo)"
elif ! command -v gh >/dev/null 2>&1; then
  echo "  SKIPPED — gh not on PATH"
else
  git tag -a "$TAG" -m "$NOTES"
  git push origin "$TAG"
  # Uploaded as scrolla.apk, not app-release.apk. Two reasons, both about the
  # person downloading it: the filename is what they see in the downloads tray
  # and in the install dialog, and "app-release.apk" arriving from an unknown
  # source is exactly what a malicious file looks like. It also makes
  #
  #   https://github.com/OWNER/REPO/releases/latest/download/scrolla.apk
  #
  # one permanent URL that always serves the newest build — which is the whole
  # install channel now that the App Tester step is gone.
  NAMED_APK="$(dirname "$APK")/scrolla.apk"
  cp "$APK" "$NAMED_APK"
  gh release create "$TAG" "$NAMED_APK" \
    --repo "$RELEASE_REPO" \
    --title "Scrolla ${VERSION_NAME}" \
    --notes "$NOTES"
  echo "  published to ${RELEASE_REPO}"
fi

say "Done — ${VERSION_NAME}"

# --------------------------------------------------------------------------
# Git Bash, not PowerShell.
#
# PowerShell rewrites the byte stream on '>', which silently corrupts binary
# files. Measured on 2026-09-14 pulling the app database: 655,360 bytes became
# 768,060 and sqlite reported "file is not a database". The same hazard applies
# to anything here that moves an APK through a redirect.
