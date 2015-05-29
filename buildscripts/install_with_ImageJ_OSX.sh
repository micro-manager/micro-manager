#!/bin/bash


set -e

usage() {
   echo "Usage: $0 DESTDIR" 1>&2
   echo "  Run this after a successful configure/make to install" 1>&2
   echo "  ImageJ together with Micro-Manager, set up to run as" 1>&2
   echo "  an ImageJ plugin." 1>&2
   echo "  This script may be removed in the future if a more elegant" 1>&2
   echo "  solution is found." 1>&2
   exit 1
}

pushd "`dirname $0`/.."; MM_SRCDIR=`pwd`; popd
MM_STAGEDIR="$1"
[ -z "$MM_STAGEDIR" ] && usage

#
#
#

cd "$MM_SRCDIR"

MM_JARDIR="$MM_STAGEDIR/plugins/Micro-Manager"
make install pkglibdir="$MM_STAGEDIR" pkgdatadir="$MM_STAGEDIR" jardir="$MM_JARDIR"
rm -f "$MM_STAGEDIR"/*.la

# Stage other files
cp -R "$MM_SRCDIR"/bindist/any-platform/* "$MM_STAGEDIR"
cp -R "$MM_SRCDIR"/bindist/MacOSX/* "$MM_STAGEDIR"

# Stage third-party JARs.
cp "$MM_SRCDIR"/dependencies/artifacts/{compile,runtime}/*.jar "$MM_JARDIR"
cp "$MM_SRCDIR"/dependencies/artifacts/imagej/ij-*.jar "$MM_STAGEDIR"/ij.jar

# Ensure SVN data is removed.
find "$MM_STAGEDIR" -name .svn -prune -exec rm -rf {} +
