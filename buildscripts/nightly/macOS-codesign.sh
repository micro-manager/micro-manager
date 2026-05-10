#!/usr/bin/env bash
# ^^^ We need Homebrew bash, not the ancient one bundled with macOS.

set -e

usage() {
    echo "Usage: $0 -d DIR | -1 BINARY [codesign-options]" 1>&2
    echo "  Exactly one of the following options is required" 1>&2
    echo "   -d DIR  -- sign all the binaries in DIR" 1>&2
    echo "   -1 FILE -- sign a single file (or .app bundle, DMG)" 1>&2
    echo 1>&2
    echo "The keychain containing the certificate must be unlocked." 1>&2
    echo 1>&2
    echo "Environment:" 1>&2
    echo "  MM_CODESIGN_CERT -- Name, in keychain, of the certificate" 1>&2
    exit 1
}

sign_binaries=
sign_one_file=
getopts ":d:1:" o
case $o in
   d) sign_binaries="$OPTARG" ;;
   1) sign_one_file="$OPTARG" ;;
   *) usage ;;
esac
# Remove the option and its arg from $@, leaving the codesign options
shift; shift

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
      sign_if_binary "$file" "$@"
   done
fi

if [ ! -z "$sign_one_file" ]; then
   sign_and_verify "$sign_one_file" "$@"
fi
