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


### Ubuntu quickstart

These commands should produce a complete build on Ubuntu. See the sections
below for more detail.

```sh
sudo apt install \
    git subversion build-essential autoconf automake libtool autoconf-archive \
    pkg-config swig3.0 openjdk-11-jdk ant libboost-all-dev

# Java JARs not available from Maven; must be next to micro-manager/
mkdir 3rdpartypublic
pushd 3rdpartypublic
svn checkout https://svn.micro-manager.org/3rdpartypublic/classext
popd

git clone https://github.com/micro-manager/micro-manager.git
cd micro-manager
git submodule update --init --recursive

export SWIG=/usr/bin/swig3.0
./autogen.sh
./configure  # ./configure --help=recurse for full details
make fetchdeps
make -j
sudo make install
```

You can avoid using `sudo` for `make install` if you specify the prefix when
using `configure`.

After installing, you can start Micro-Manager from the terminal with the
`micromanager` command.


### Prerequisites

There are several packages that are required to build and/or run
Micro-Manager. It is usually easiest to install these using the distribution's
package manager (on Linux) or using Homebrew (on macOS).

#### macOS

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

#### Ubuntu

C and C++ compilers: `sudo apt install build-essential`

Build tools:

```sh
sudo apt install git subversion build-essential autoconf automake libtool autoconf-archive pkg-config
```

(Requirement for `autoconf-archive` on Ubuntu is likely a bug.)

SWIG 3.x:

```sh
sudo apt install swig3.0
export SWIG=/usr/bin/swig3.0
```

Boost C++ libraries: `sudo apt install libboost-all-dev`

JDK and Ant: `sudo apt install openjdk-11-jdk ant`

#### Notes on prerequisites

SWIG 4.x currently does not work for building a correct MMCoreJ
([micro-manager/mmCoreAndDevices#37](https://github.com/micro-manager/mmCoreAndDevices/issues/37)).

Instead of installing a SWIG 3.x package, you can build it from source:

```sh
sudo apt install libpcre3-dev
curl -LO https://prdownloads.sourceforge.net/swig/swig-3.0.12.tar.gz
tar xzf swig-3.0.12.tar.gz
cd swig-3.0.12
./configure
make -j3
sudo make install
```

This installs `swig` in `/usr/local/bin` by default. Make sure that directory
comes before `/usr/bin` in `PATH` while building Micro-Manager.

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
with the `-dev` suffix.

Some device adapters require additional external libraries. (TODO Document
these.)


### Configuring

#### Generating the configure script

To build from source, you will first need to generate the `configure` script.
This can be done with the command

    ./autogen.sh

#### Choosing an installation style

Now, you will run `./configure`. There are many ways to configure
Micro-Manager, but you will most likely want to choose one of two major
installation styles: a traditional Unix-style installation and installation as
an ImageJ plugin (recommended).

The traditional Unix-style will put Micro-Manager libraries (including device
adapters) into `$prefix/lib/micro-manager` and other files (including JARs)
into `$prefix/share/micro-manager` (`$prefix` is `/usr/local` by default). If
you build the Java application, a script will be installed at
`$prefix/bin/micromanager` which can be used to start Micro-Manager, and
Micro-Manager will run without the ImageJ toolbar.

If you want to install Micro-Manager as an ImageJ plugin, you will have to
tell `configure` where to find the target ImageJ application directory. In
this case, all Micro-Manager files will be installed inside that ImageJ
directory.

To configure Micro-Manager for a traditional Unix-style install, type

    ./configure --prefix=/where/to/install

To configure for installation as an ImageJ plugin, type

    ./configure --enable-imagej-plugin=/path/to/ImageJ

The ImageJ path should be an existing (preferably fresh) copy of ImageJ 1.x.
Official builds use the ImageJ version listed in `buildscripts/ivy.xml`.

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

Assuming `configure` succeeded, you can now run

    make fetchdeps
    make

to build.

To install, type

    make install

When the installation is finished, a message will be printed telling you how
to run Micro-Manager Studio (if it was configured to be built).


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
