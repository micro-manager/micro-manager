#!/bin/bash

# Common definitions for Micro-Manager macOS binary distribution build

MM_BUILDDIR=`pwd`
[ -z "$MM_DEPS_PREFIX" ] && MM_DEPS_PREFIX="$MM_BUILDDIR/dependencies"
MM_STAGEDIR="$MM_BUILDDIR/stage"

# The correct minimum macOS version is critical when building
# backward-compatible binaries. Omitting this will produce binaries that will
# only run on the macOS version of the build host or newer. Also, mixing
# different minimum versions may cause C++ linking issues.
# 10.9 is the oldest deployment target that uses libc++ (vs libstdc++).
MM_MACOSX_VERSION_MIN=10.9

# We don't use a fixed macOS SDK version, but need a fixed path to the SDK.
MM_MACOSX_SDKROOT=$(xcode-select --print-path)/SDKs/MacOSX.sdk

# TODO Third-party frameworks should be in $MM_DEPS_PREFIX/Library/Frameworks,
# once build supports it.
MM_CPPFLAGS="-I$MM_DEPS_PREFIX/include -F/Library/Frameworks"
MM_CFLAGS="-O2 -g -Wall"
MM_CXXFLAGS="$MM_CFLAGS -std=c++03"
MM_LDFLAGS="-L$MM_DEPS_PREFIX/lib -F/Library/Frameworks"

MM_ARCH="x86_64"
MM_ARCH_FLAGS="-arch x86_64"
MM_CC="clang $MM_ARCH_FLAGS"
MM_CXX="clang++ $MM_ARCH_FLAGS"
MM_CPP="clang -E"
MM_CXXCPP="clang++ -E"

# These are set to strings containing shell syntax that should be expanded
# before use.
MM_CONFIGUREFLAGS_NOCPPLD="CC=\"\$MM_CC\" CXX=\"\$MM_CXX\" CPP=\"\$MM_CPP\" CXXCPP=\"\$MM_CXXCPP\" CFLAGS=\"\$MM_CFLAGS\" CXXFLAGS=\"\$MM_CXXFLAGS\""
MM_CONFIGUREFLAGS="$MM_CONFIGUREFLAGS_NOCPPLD CPPFLAGS=\"\$MM_CPPFLAGS\" LDFLAGS=\"\$MM_LDFLAGS\""
MM_DEPS_CONFIGUREFLAGS_NOCPPLD="--prefix=\"\$MM_DEPS_PREFIX\" $MM_CONFIGUREFLAGS_NOCPPLD"
MM_DEPS_CONFIGUREFLAGS="--prefix=\"\$MM_DEPS_PREFIX\" $MM_CONFIGUREFLAGS"

MM_PARALLELMAKEFLAG=-j$(sysctl -n hw.ncpu)

# Get the appropriate JAVA_HOME, requiring exactly Java 8 (should work with
# temurin, adoptopenjdk, zulu, etc.)
# Would pass '-a x86_64' here, but that doesn't seem to actually work.
MM_JDK_HOME=$(/usr/libexec/java_home -v 1.8 -F)
