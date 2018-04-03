#!/bin/sh

# Run this script to set up C++ unit testing on Unix; then (re)run configure.
# Use `make check' to build and run the tests.

set -e

cd `dirname $0`
rm -rf googletest

GOOGLETEST_VERSION=1.8.0
GOOGLETEST="googletest-$GOOGLETEST_VERSION"
GOOGLETEST_SHA1=667f873ab7a4d246062565fad32fb6d8e203ee73

if test -f $GOOGLETEST.zip
then
   :
else
   curl -o $GOOGLETEST.zip -L https://github.com/google/googletest/archive/release-$GOOGLETEST_VERSION.zip
fi

cat >sha1sums.tmp <<EOF
$GOOGLETEST_SHA1  $GOOGLETEST.zip
EOF
shasum -c sha1sums.tmp
rm sha1sums.tmp

unzip -q $GOOGLETEST.zip
mv googletest-release-$GOOGLETEST_VERSION googletest
