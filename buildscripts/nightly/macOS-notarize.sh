#!/usr/bin/env bash
# ^^^ We need Homebrew bash, not the ancient one bundled with macOS.

set -e

usage() {
    echo "Usage: $0 DMGNAME" 1>&2
    echo 1>&2
    echo "Environment:" 1>&2
    echo "  MM_NOTARIZE_APPLE_ID -- Apple ID (email) to use" 1>&2
    echo "  MM_NOTARIZE_PASSWORD -- App-specific password" 1>&2
    echo "  MM_NOTARIZE_TEAM_ID  -- Developer Team ID" 1>&2
    exit 1
}

dmg_name="$1"
if [ -z "$dmg_name" ]; then
   echo "The name of the DMG must be given" 1>&2
   echo 1>&2
   usage
fi

if [ -z "$MM_NOTARIZE_APPLE_ID" ]; then
   echo "Apple ID not set (MM_NOTARIZE_APPLE_ID)" 1>&2
   echo 1>&2
   usage
fi

if [ -z "$MM_NOTARIZE_PASSWORD" ]; then
   echo "Apple ID app-specific password not set (MM_NOTARIZE_PASSWORD)" 1>&2
   echo 1>&2
   usage
fi

if [ -z "$MM_NOTARIZE_TEAM_ID" ]; then
   echo "Team ID not set (MM_NOTARIZE_TEAM_ID)" 1>&2
   echo 1>&2
   usage
fi


##
## Notarize
##

echo Notarizing $dmg_name...
echo "(If this fails, use the submition id to check the reason"
echo "using 'xcrun notarytool log')"

# Set a timeout because there are reports of it taking hours.
# Note that notarytool seems to exit normally even if notarization fails;
# the stapling step below will fail in that case.
xcrun notarytool submit "$dmg_name" --wait \
   --timeout 10m \
   --apple-id "$MM_NOTARIZE_APPLE_ID" \
   --password "$MM_NOTARIZE_PASSWORD" \
   --team-id "$MM_NOTARIZE_TEAM_ID"


##
## Staple
##

echo Stapling ticket to $dmg_name...
xcrun stapler staple "$dmg_name"
