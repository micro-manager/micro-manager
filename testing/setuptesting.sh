#!/bin/sh

# Run this script to set up C++ unit testing on Unix; then (re)run configure.
# Use `make check' to build and run the tests.

set -e

cd `dirname $0`
rm -rf gmock

GMOCK_VERSION=1.7.0
GMOCK="gmock-$GMOCK_VERSION"
GMOCK_SHA1=d6d2aa97886446dd8cbdb13930e451ff94a81481

if test -f $GMOCK.zip
then
   :
else
   curl -o $GMOCK.zip -L https://github.com/google/googlemock/archive/release-$GMOCK_VERSION.zip
fi

cat >sha1sums.tmp <<EOF
$GMOCK_SHA1  $GMOCK.zip
EOF
shasum -c sha1sums.tmp
rm sha1sums.tmp

unzip -q $GMOCK.zip
mv googlemock-release-$GMOCK_VERSION gmock
