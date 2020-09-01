#!/bin/sh

# Run this script to set up C++ unit testing on Unix; then (re)run configure.
# Use `make check' to build and run the tests.

set -e

cd `dirname $0`
rm -rf gmock

# 1.8.1 is last version supporting pre-C++11 compilers
GTEST_VER=1.8.1
GTEST_SHA1=7b41ea3682937069e3ce32cb06619fead505795e

if test -f $GMOCK.zip
then
   :
else
   curl -L -o googletest.zip https://github.com/google/googletest/archive/release-$GTEST_VER.zip
fi

cat >sha1sums.tmp <<EOF
$GTEST_SHA1  googletest.zip
EOF
shasum -c sha1sums.tmp
rm sha1sums.tmp

unzip -q googletest.zip
mv googletest-release-$GTEST_VER googletest
