#!/usr/bin/env bash
# ^^^ We need Homebrew bash, not the ancient one bundled with macOS.

set -e

usage() {
   echo "Usage: $0 -x DIR | -c DIR" 1>&2
   echo "   -x DIR  -- replace .jar files with .jar directories" 1>&2
   echo "   -c DIR  -- replace .jar directories with .jar files" 1>&2
   echo "In both cases, all files/dirs named *.jar under DIR are processed." 1>&2
   echo "For -x, only JARs containing *.dylib or *.jnilib are extracted." 1>&2
   exit 1
}

do_unjar=
do_rejar=
while getopts ":x:c:" o; do
   case $o in
      x) do_unjar="$OPTARG" ;;
      c) do_rejar="$OPTARG" ;;
      *) usage ;;
   esac
done

unjar_one() {
   name="$1"
   parent="$(dirname "$name")"
   temp_dir=$(mktemp -d)
   temp_jar="$temp_dir/$(basename "$name")"

   # Only unjar if it contains *.dylib or *.jnilib.
   jar tf "$name" | grep -qE '\.(dylib|jnilib)' || return 0

   mv "$name" "$temp_jar"
   mkdir "$name"
   pushd "$name" >/dev/null
   jar xf "$temp_jar"
   popd >/dev/null
   rm -rf "$temp_dir"
}

rejar_one() {
   name="$1"
   parent="$(dirname "$name")"
   temp_dir=$(mktemp -d)
   temp_jar="$temp_dir/$(basename "$name")"

   jar cf "$temp_jar" -C "$name" .
   rm -rf "$name"
   mv "$temp_jar" "$name"
   rm -rf "$temp_dir"
}

if [ ! -z "$do_unjar" ]; then
   [ -z "$do_rejar" ] || usage

   readarray -d '' -t files < <(find "$do_unjar" -type f -name '*.jar' -print0)
   for file in "${files[@]}"; do
      unjar_one "$file"
   done
fi

if [ ! -z "$do_rejar" ]; then
   readarray -d '' -t dirs < <(find "$do_rejar" -type d -name '*.jar' -print0)
   for dir in "${dirs[@]}"; do
      rejar_one "$dir"
   done
fi