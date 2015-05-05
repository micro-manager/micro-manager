#!/bin/sh

# Run this script to set up C++ unit testing on Unix; then (re)run configure.
# Use `make check' to build and run the tests.

set -e

cd `dirname $0`
rm -rf gmock

GMOCK_VERSION=1.7.0
GMOCK="gmock-$GMOCK_VERSION"
GMOCK_SHA1=f9d9dd882a25f4069ed9ee48e70aff1b53e3c5a5

if test -f $GMOCK.zip
then
   :
else
   curl -LO https://googlemock.googlecode.com/files/$GMOCK.zip
fi

cat >sha1sums.tmp <<EOF
$GMOCK_SHA1  $GMOCK.zip
EOF
shasum -c sha1sums.tmp
rm sha1sums.tmp

unzip -q $GMOCK.zip
mv $GMOCK gmock
