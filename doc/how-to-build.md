How to build and install Micro-Manager
======================================

Overview
--------

Currently, Micro-Manager has two, more or less orthogonal, build systems: one
for Unix (OS X and Linux), and one for Windows.


Building on Windows
-------------------

The Windows build uses Microsoft Windows SDK 7.1 and Visual Studio 2010 SP1
Express for the C++ components, and Apache Ant for the Java components and
for automating the entire build.

(Instructions are to be written here. For now, please refer to the
[wiki page](https://micro-manager.org/wiki/Building_MM_on_Windows)).


Building on Unix
----------------

The Unix build uses the GNU Build System (aka Autotools, aka configure &
make).

Note: The Unix build scripts were reworked in April, 2014, and some details
have changed.


### Getting the prerequisites

There are several packages that are required to build and/or run
Micro-Manager. It is usually easiest to install these using the distribution's
package manager (on Linux) or using Homebrew (on OS X).

On OS X, start by installing Xcode 5.x. After installing, make sure to open
the Xcode app once and allow it to install the command line tools.

On Linux, ensure that you have gcc and g++ installed.

Packages required for building are:

- autoconf
- automake
- libtool
- pkg-config
- swig

(On OS X, do not confuse Apple's `/usr/bin/libtool` with GNU Libtool. We need
the latter. Homebrew installs GNU Libtool as `glibtool`.)

You will also need subversion to obtain Micro-Manager source code.

Required dependencies are:

- Boost 1.46 or later

To build MMCoreJ and the Java application (Micro-Manager Studio), you will need
a Java Development Kit. Micro-Manager Java code is written in Java 1.6. On OS
X, either Apple's Java 1.6 or Oracle JDK 1.7 will work. On Linux, OpenJDK 1.6
or 1.7 should work well. (For JDK versions, 1.x is the same as x.)

Building the Java components also requires Apache Ant (pre-installed on OS X).

To build MMCorePy, you will need Python and Numpy. Recent versions of either
Python 2 or Python 3 should work.

Many Linux distributions split library packages into runtimes and development
files. If you are using such a distribution, make sure to get the packages
with the `-dev` suffix.

Some device adapters require additional external libraries. (TODO Document
these.)


### Obtaining the source code

Please see the Micro-Manager website for instructions. You will need the main
Micro-Manager source code and the `3rdpartypublic` repository, side by side in
the same parent directory.


### Configuring

To build from an SVN working copy, you will first need to generate the
`configure` script. This can be done with the command

    ./autogen.sh

Now, you will run `./configure`. There are many ways to configure
Micro-Manager, but you will most likely want to choose one of two major
installation styles: a traditional Unix-style installation and installation as
an ImageJ plugin.

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

The ImageJ path should be an existing (preferably fresh) copy of ImageJ 1.48.

To get more information about the possible options to `configure`, type

    ./configure --help

You can get help on the flags controlling device-adapter-specific dependency
libraries by typing

    ./configure --help=recursive


### Building and installing

Assuming `configure` succeeded, you can now run

    make fetchdeps
    make

to build.

To install, type

    make install

When the installation is finished, a message will be printed telling you how
to run Micro-Manager Studio (if it was configured to be built).


### Common configuration issues

#### Failure to detect Java

If `./configure` does not find your JDK (Java Development Kit), try the
following.

1. If the environment variable `$JAVA_HOME` is set, try unsetting it before
   running `configure`. It might be pointing to a Java installation that
   doesn't contain all the required files (e.g. it may be pointing to a JRE
   (Java Runtime Environment) rather than a JDK). Not setting `JAVA_HOME` may
   allow `configure` to autodetect a suitable Java home.

2. Find the desirable JDK home on your system. This is a directory that usually
   has "jdk" and the Java version number (such as 1.6) in its name, and
   contains the directories `bin` (in which `java`, `javac`, and `jar` are
   found) and `include` (in which `jni.h` is found). Pass
   `--with-java=/path/to/java/home` to `configure`. For example:

        ./configure --with-java=/usr/lib64/jvm/java-1.7.0-openjdk-1.7.0
        # or, on OS X,
        ./configure --with-java=/Library/Java/JavaVirtualMachines/1.7.0_55.jdk/Contents/Home

3. On OS X, if you are using Apple's JDK 1.6, the previous step may not be
   sufficient, because the Java home directory
   (`/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contenst/Home`) does
   not contain `jni.h` and related headers. There are two solutions to this
   problem.

   The first is to download and install Apple's Java Development Package (you
   need an Apple Developer account to download it). This will place `jni.h` in
   `/System/Library/Frameworks/JavaVM.framework/Headers`, which should be
   automatically detected by `./configure`.

   The second is to use the copy of `jni.h` and friends included with the OS X
   SDK. To do so, run:

        ./configure JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home JNI_CPPFLAGS="-I/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/System/Library/Frameworks/JavaVM.framework/Headers"

   You may need to adjust the version of the platform SDK from `10.9` to one of
   the versions you have.


#### Specifying where to find external packages

As a general rule, the `--with-foo` flags to `configure` (e.g.
`--with-python=PREFIX`) will try to autodetect the package, whereas the
capitalized variables listed at the end of `./configure --help` (e.g. `PYTHON`)
will override any automatic detection and be used unmodified.
