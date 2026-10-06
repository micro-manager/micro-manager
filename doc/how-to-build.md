# How to build and install Micro-Manager

## Overview

Currently, Micro-Manager has two build systems: one for Unix (macOS and Linux),
and one for Windows.

The Unix build system uses GNU Autotools (`./configure` and `make`), but calls
Apache Ant to build Java modules.

The Windows build system uses Apache Ant as the main routine, but calls a
Visual Studio solution to build the C++ modules. Developers of C++ modules on
Windows can build directly using Visual Studio only.

The Ant build files (`build.xml`) for Java modules are shared between the two
build systems, but all other Ant files are only used on Windows.

It should be noted that it is not very practical to build a "complete"
Micro-Manager installation outside of the core development team. That requires
having dozens of device vendor SDKs, some of which are hard to obtain or are
not gratis. The Unix build system will generally disable device adapters for
which you do not have dependencies during configuration. The Windows build
system only achieves this effect by ignoring C++ compile errors by default.


## Building on Windows

The Windows C++ build currently requires Microsoft Visual Studio 2019. You can
also use Visual Studio 2022, provided that you select "MSVC v142 - VS 2019 C++
build tools" in the installer (you can modify an existing installation).

(Instructions are to be written here. For now, please refer to the
[website](https://micro-manager.org/Building_MM_on_Windows).)


## Building on macOS and Linux


### Standard build (launched as ImageJ plugin)

The standard build installs Micro-Manager as a plugin inside a copy of ImageJ,
so that it runs together with the ImageJ toolbar. It is launched with the
`mmimagej` script in the installation directory.

You will need some familiarity with the command line. Run the commands in a
directory where you want to keep the source code.

The commands below install ImageJ 1.53t, and all Micro-Manager files are then
installed inside that ImageJ directory. Any existing (preferably fresh) copy
of ImageJ 1.x can be used instead. Official builds use the `ij.jar` version
listed in `buildscripts/ivy.xml` (1.53c as of this writing).

#### Ubuntu 24.04

```sh
sudo apt update
sudo apt install \
    curl git subversion build-essential autoconf automake libtool \
    autoconf-archive pkg-config swig3.0 openjdk-11-jdk ant libboost-all-dev

# Any user-writable directory works
MM_DIR=~/opt/micro-manager

curl -LO https://wsr.imagej.net/distros/cross-platform/ij153.zip
unzip ij153.zip
mkdir -p "$MM_DIR"
mv ImageJ/* "$MM_DIR"

# Java JARs not available from Maven; must be next to micro-manager/
mkdir 3rdpartypublic
pushd 3rdpartypublic
svn checkout https://svn.micro-manager.org/3rdpartypublic/classext
popd

git clone https://github.com/micro-manager/micro-manager.git
pushd micro-manager
git submodule update --init --recursive
export SWIG=/usr/bin/swig3.0
./autogen.sh
./configure --enable-imagej-plugin="$MM_DIR"
make fetchdeps  # Downloads Java dependencies
make -j
make install
popd
```

Micro-Manager can then be launched with:

```sh
"$MM_DIR"/mmimagej
```

#### Ubuntu 26.04

Ubuntu 26.04 no longer packages SWIG 3, and SWIG 4 produces a broken MMCoreJ
([micro-manager/mmCoreAndDevices#37](https://github.com/micro-manager/mmCoreAndDevices/issues/37)).
So SWIG 3 is built from source. Otherwise the steps are the same as for 24.04.

```sh
sudo apt update
sudo apt install \
    curl git subversion build-essential autoconf automake libtool \
    autoconf-archive pkg-config openjdk-11-jdk ant libboost-all-dev

# SWIG 3, built with its own copy of PCRE (which Ubuntu no longer packages)
curl -LO https://prdownloads.sourceforge.net/swig/swig-3.0.12.tar.gz
tar xf swig-3.0.12.tar.gz
pushd swig-3.0.12
curl -LO https://prdownloads.sourceforge.net/pcre/pcre-8.45.tar.bz2
./Tools/pcre-build.sh
./configure --program-suffix=3.0
make -j4
sudo make install
popd

# Any user-writable directory works
MM_DIR=~/opt/micro-manager

curl -LO https://wsr.imagej.net/distros/cross-platform/ij153.zip
unzip ij153.zip
mkdir -p "$MM_DIR"
mv ImageJ/* "$MM_DIR"

# Java JARs not available from Maven; must be next to micro-manager/
mkdir 3rdpartypublic
pushd 3rdpartypublic
svn checkout https://svn.micro-manager.org/3rdpartypublic/classext
popd

git clone https://github.com/micro-manager/micro-manager.git
pushd micro-manager
git submodule update --init --recursive
export SWIG=/usr/local/bin/swig3.0
./autogen.sh
./configure --enable-imagej-plugin="$MM_DIR"
make fetchdeps  # Downloads Java dependencies
make -j
make install
popd
```

Micro-Manager can then be launched with:

```sh
"$MM_DIR"/mmimagej
```

#### Other Linux distributions

On Debian, and on older Ubuntu versions such as 22.04, the same commands should
work, possibly with small differences in package names or availability. If the
`swig3.0` package is not available, build SWIG 3 from source as shown for
Ubuntu 26.04.

Other distributions (such as Fedora, Arch Linux, or openSUSE) use a different
package manager (the tool that installs software, such as `dnf`, `pacman`, or
`zypper`) instead of `apt`, and their packages have different names. Install
the equivalents of the following, then follow the remaining Ubuntu steps:

- C and C++ compiler toolchain
- Git and Subversion
- Autoconf, Automake, Libtool, and autoconf-archive
- pkg-config
- SWIG 3.x (build from source if not available)
- JDK 11 and Apache Ant
- Boost C++ libraries (development headers)
- curl

Library packages may need to be the development (`-devel` or `-dev`) variant.
See the [notes on prerequisites](#notes-on-prerequisites) below.

#### macOS

There are no verified step-by-step instructions for macOS yet. The steps
parallel the Ubuntu standard build, but with the following prerequisites
instead of the `apt` packages. SWIG 3.x is also required (see the
[notes on prerequisites](#notes-on-prerequisites)).

C and C++ compilers: Install the Xcode Command Line Tools
(`xcode-select --install`).

Build tools: `brew install git subversion autoconf automake libtool pkg-config ant`

(On macOS, do not confuse Apple's `/usr/bin/libtool` with GNU Libtool. We need
the latter. Homebrew installs GNU Libtool as `glibtool`.)

Boost C++ libraries: `brew install boost`

JDK: Install Temurin or Zulu JDK 11, and set `JAVA_HOME`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 11 -F)
echo $JAVA_HOME  # Make sure path looks correct
```


### Build variants

These variants follow the standard build steps above, except for the
`configure` command and the steps noted.

#### Standalone (without ImageJ toolbar)

Skip the ImageJ download, and configure with `--prefix` instead of
`--enable-imagej-plugin`:

```sh
./configure --prefix="$MM_DIR"
```

This is a traditional Unix-style layout: Micro-Manager libraries (including
device adapters) are installed into `$prefix/lib/micro-manager` and other
files (including JARs) into `$prefix/share/micro-manager`. (If `--prefix` is
not given, it defaults to `/usr/local`, which requires `sudo make install`.)

Micro-Manager can then be launched with:

```sh
"$MM_DIR"/bin/micromanager
```

It runs without the ImageJ toolbar.

#### Without Java (no GUI, for use with pymmcore-plus)

To build only MMCore and the device adapters:

```sh
./configure --without-java --prefix="$MM_DIR"
```

The JDK, Ant, SWIG, the ImageJ download, the `classext` checkout, and
`make fetchdeps` are only needed for Java, so they can be skipped. The device
adapters are installed into `$prefix/lib/micro-manager`. To use them with
pymmcore-plus, see the
[pymmcore-plus installation instructions](https://pymmcore-plus.github.io/pymmcore-plus/install/#set-the-active-micro-manager-installation).


### Notes on prerequisites

There are several packages that are required to build and/or run
Micro-Manager. It is usually easiest to install these using the distribution's
package manager (on Linux) or using Homebrew (on macOS).

SWIG 4.x currently does not work for building a correct MMCoreJ
([micro-manager/mmCoreAndDevices#37](https://github.com/micro-manager/mmCoreAndDevices/issues/37)).
If SWIG 3.x is not available as a package, build it from source as shown for
Ubuntu 26.04, and set the `SWIG` environment variable to its path before
running `configure`.

A recent version of the Boost C++ libraries is required (1.77.0 has been
tested). If building for local use, you can install it using the package
manager.

To build MMCoreJ and the Java application (Micro-Manager Studio), you will need
a Java Development Kit (JDK). Micro-Manager Java code is written in Java 8
(a.k.a. Java 1.8). For running Micro-Manager, Java 11 is currently recommended.

With JDK 17 and above, errors such as `Unable to make field int
java.awt.Color.value accessible: module java.desktop does not "opens java.awt"
to unnamed module @38a8f1a9` may occur.

Building the Java components also requires Apache Ant.

Many Linux distributions split library packages into runtimes and development
files. If you are using such a distribution, make sure to get the packages
with the `-dev` or `-devel` suffix.

(The requirement for `autoconf-archive` on Ubuntu is likely a bug.)

Some device adapters require additional external libraries. (TODO Document
these.)


### Configuring

#### Generating the configure script

To build from source, you will first need to generate the `configure` script.
This can be done with the command

    ./autogen.sh

#### Configure options and external packages

To get more information about the possible options to `configure`, type

    ./configure --help

You can get help on the flags controlling device-adapter-specific dependency
libraries by typing

    ./configure --help=recursive

As a general rule, the `--with-foo` flags to `configure` will try to autodetect
the package, whereas the all-caps variables (`FOO`) listed at the end of
`./configure --help` will override any automatic detection and be used
unmodified.


### Building and installing

After `configure` succeeds, `make fetchdeps` downloads the Java dependencies,
`make` builds everything, and `make install` installs it. When the
installation is finished, a message will be printed telling you how to run
Micro-Manager Studio (if it was configured to be built).


### Troubleshooting

#### configure does not find Java

If `./configure` does not find your JDK (Java Development Kit), try the
following.

1. On Linux, if the environment variable `$JAVA_HOME` is set, try unsetting it
   before running `configure`. It might be pointing to a Java installation that
   doesn't contain all the required files (e.g. it may be pointing to a JRE
   (Java Runtime Environment) rather than a JDK). Not setting `JAVA_HOME` may
   allow `configure` to autodetect a suitable Java home.

2. On Ubuntu, multiple versions of OpenJDK may be installed on the system. Use
   `java -version` to see which one is active. To list the possibilities, use
   `sudo update-java-alternatives --list`. Use the same command with `--set` to
   switch between installations. Other distributions have similar (but
   different) commands.

3. Find the desirable JDK home on your system. This is a directory that usually
   has "jdk" and the Java version number (such as 11) in its name, and
   contains the directories `bin` (in which `java`, `javac`, and `jar` are
   found) and `include` (in which `jni.h` is found). Pass
   `--with-java=/path/to/java/home` to `configure`. For example:

        ./configure --with-java=/usr/lib/jvm/java-11-openjdk-amd64
        # or, on macOS,
        ./configure --with-java=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home


### Building only selected device adapters

To skip device adapters you don't need (for example, ones that fail to compile
on your machine), remove each adapter (for example, `DemoCamera`) from:

1. the `SUBDIRS` list in `mmCoreAndDevices/DeviceAdapters/Makefile.am`, and
2. the `m4_define` list in `mmCoreAndDevices/DeviceAdapters/configure.ac`.

Then rerun `./autogen.sh` and `./configure`.

If an adapter fails to compile, please report it as an issue.
