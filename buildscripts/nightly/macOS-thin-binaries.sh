#!/usr/bin/env bash
# ^^^ We need Homebrew bash, not the ancient one bundled with macOS.

set -e

usage() {
   echo "Usage: $0 -a ARCH -d DIR" 1>&2
   echo "   -a ARCH -- thin universal binaries, keeping only ARCH binaries" 1>&2
   echo "   -d DIR  -- thin universal binaries in DIR, recursively" 1>&2
   echo "Both flags are required." 1>&2
   exit 1
}

keep_arch=
thin_dir=
while getopts ":a:d:" o; do
   case $o in
      a) keep_arch="$OPTARG" ;;
      d) thin_dir="$OPTARG" ;;
      *) usage ;;
   esac
done

[ -z "$keep_arch" ] && usage
[ -z "$thin_dir" ] && usage


thin_binary() {
   file="$1"
   echo "Keeping only $keep_arch from $file"
   lipo -extract_family $keep_arch "$file" -output "$file.thin"
   mv "$file.thin" "$file"
}


readarray -d '' -t files < <(find "$thin_dir" -type f -print0)
for file in "${files[@]}"; do
   if file "$file" | grep -q 'Mach-O universal binary'; then
      thin_binary "$file"
   fi
done
