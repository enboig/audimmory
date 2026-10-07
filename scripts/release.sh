#!/usr/bin/env bash
# Builds a signed release APK on this machine and publishes it as a GitHub
# Release — the local counterpart of .github/workflows/release.yml, using the
# same version scheme, release commit, tag and changelog layout.
#
# Usage: scripts/release.sh [--skip-checks] "Release notes (max 500 chars)"
#        scripts/release.sh [--skip-checks] -F notes.txt
#
# Needs: a git-ignored keystore.properties (see app/build.gradle.kts), a
# logged-in `gh`, ANDROID_HOME, and a clean, up-to-date default branch.
set -euo pipefail

EXPECTED_CERT_SHA256="5d4571e05457c4ff764ba52d9a416533457e16963c362c794ed270eadde31061"

die() { echo "release: $*" >&2; exit 1; }

checks=1
notes=""
while (($#)); do
  case "$1" in
    --skip-checks) checks=0 ;;
    -F) shift; notes="$(cat "${1:?-F needs a file}")" ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) notes="$1" ;;
  esac
  shift
done

cd "$(git rev-parse --show-toplevel)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/.Android}"

[[ -n "${notes//[[:space:]]/}" ]] || die "release notes must not be empty"
((${#notes} <= 500)) || die "release notes must be 500 characters or fewer (${#notes})"
[[ -f keystore.properties ]] || die "keystore.properties not found; the APK would be unsigned"
gh auth status >/dev/null 2>&1 || die "gh is not logged in (run: gh auth login)"

repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
default_branch="$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name)"
branch="$(git rev-parse --abbrev-ref HEAD)"
[[ "$branch" == "$default_branch" ]] || die "releases must be made from $default_branch (on $branch)"
[[ -z "$(git status --porcelain)" ]] || die "working tree is not clean"
git fetch -q origin "$default_branch"
[[ "$(git rev-parse HEAD)" == "$(git rev-parse "origin/$default_branch")" ]] ||
  die "$default_branch is not in sync with origin"

if ((checks)); then
  ./gradlew ktlintCheck lintDebug testDebugUnitTest
fi

now="$(date -u +%s)"
release_timestamp="$(date -u -d "@$now" +%Y%m%d%H%M%S)"
version_name="$(date -u -d "@$now" +%Y-%m-%d)"
version_code=$(((now - 1735689600) / 60))
changelog="fastlane/metadata/android/en-US/changelogs/$version_code.txt"
[[ ! -e "$changelog" ]] || die "changelog already exists: $changelog"

printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$version_name" "$version_code" > version.properties
printf '%s\n' "$notes" > "$changelog"
git add version.properties "$changelog"
git commit -q -m "🔖 Release $version_name ($version_code) [skip ci]"
tag="$release_timestamp-$(git rev-parse --short=9 HEAD)"
git tag -a "$tag" -m "Release $tag"

# Until the push below nothing has left this machine, so a failed build or
# signature check undoes the release commit and tag.
rollback() {
  echo "release: failed; removing release commit and tag $tag" >&2
  git tag -d "$tag" >/dev/null
  git reset -q --hard HEAD~1
}
trap rollback ERR

./gradlew assembleRelease
apk="app/build/outputs/apk/release/app-release.apk"
apksigner="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)"
cert="$("$apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
[[ "$cert" == "$EXPECTED_CERT_SHA256" ]] ||
  { echo "release: APK signed with unexpected certificate '${cert:-none}'" >&2; false; }

asset="$(dirname "$apk")/audimmory-$version_name-$version_code.apk"
cp "$apk" "$asset"

git push -q origin "HEAD:$default_branch" "$tag"
trap - ERR

gh release create "$tag" "$asset" --repo "$repo" --title "$tag" \
  --notes "$notes" --generate-notes --verify-tag
echo "Released $version_name ($version_code) as $tag"
