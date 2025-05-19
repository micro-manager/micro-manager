#!/usr/bin/env bash
# ^^^ We need Homebrew bash, not the ancient one bundled with macOS.

set -e

usage() {
    echo "Usage: $0 -b STAGEDIR | -D DMGNAME" 1>&2
    echo "   -b STAGEDIR -- sign all the binaries in the stage directory" 1>&2
    echo "   -D DMGNAME  -- sign the given .dmg file" 1>&2
    echo "   -v          -- print some debug info" 1>&2
    echo 1>&2
    echo "The keychain containing the certificate must be set up." 1>&2
    echo 1>&2
    echo "Environment:" 1>&2
    echo "  MM_CODESIGN_CERT -- Name, in keychain, of the certificate" 1>&2
    exit 1
}

sign_binaries=
sign_dmg=
verbose=no
while getopts ":b:D:" o; do
   case $o in
      b) sign_binaries="$OPTARG" ;;
      D) sign_dmg="$OPTARG" ;;
      v) verbose=yes ;;
      *) usage ;;
   esac
done

if [ -z "$MM_CODESIGN_CERT" ]; then
   echo "Certificate name not set (MM_CODESIGN_CERT)" 1>&2
   echo 1>&2
   usage
fi


##
## Sign binaries
##

sign_and_verify() {
   file="$1"
   shift
   echo "Signing: $file"
   codesign --force --sign "$MM_CODESIGN_CERT" --timestamp "$@" "$file"
   codesign -vv --deep "$file"
}

sign_if_binary() {
   file="$1"
   shift
   filetype="$(file "$file")"
   if [[ "$filetype" == *" Mach-O "* ]]; then
      sign_and_verify "$file" "$@"
   elif [ $verbose = yes ]; then
      echo "Not a binary: $file"
   fi
}

if [ ! -z "$sign_binaries" ]; then
   # All Mach-O binaries
   # - Apples tells us to sign in inside-out order, meaning dependencies before
   #   dependents. But we disable library validation, so hopefully that doesn't
   #   matter
   # - Note also that entitlements are not applicable to library code
   # - Loop over files, allowing spaces in paths; we cannot use xargs here
   #   because we're calling a shell function
   readarray -d '' -t files < <(find "$sign_binaries" -type f -print0)
   for file in "${files[@]}"; do
      sign_if_binary "$file" --options runtime
   done

   # The ImageJ.app launcher bundle (which contains a shell script; no binary)
   sign_and_verify "$sign_binaries/ImageJ.app"
fi


##
## Sign disk image
##

if [ ! -z "$sign_dmg" ]; then
   sign_and_verify "$sign_dmg"
fi