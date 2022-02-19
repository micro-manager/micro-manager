AC_PREREQ([2.69])
AC_INIT([Micro-Manager], [2])
AC_CONFIG_MACRO_DIR([mmCoreAndDevices/m4])
AC_CONFIG_SRCDIR([mmCoreAndDevices/MMCore/MMCore.cpp])
AC_CANONICAL_BUILD
AC_CANONICAL_HOST
AM_INIT_AUTOMAKE([foreign 1.11])
LT_PREREQ([2.2.6])
LT_INIT([disable-static])
AC_PROG_MKDIR_P

AC_PROG_CC([cc gcc clang])
AC_PROG_CXX([c++ g++ clang++])


# Testing (googletest, googlemock)
# See testing/setuptesting.sh
MM_GMOCK([$srcdir/testing/googletest], [\$(top_srcdir)/testing/googletest])
AM_CONDITIONAL([BUILD_CPP_TESTS], [test "x$have_gmock" = xyes])


# Boost
# TODO Reflect results in configuration
AX_BOOST_BASE([1.48.0])
AX_BOOST_DATE_TIME
AX_BOOST_FILESYSTEM
AX_BOOST_SYSTEM
AX_BOOST_THREAD


# AppleHost dependencies
case $host in
   *apple-darwin*) MMCORE_APPLEHOST_LDFLAGS="-framework CoreFoundation -framework IOKit" ;;
   *) MMCORE_APPLEHOST_LDFLAGS="" ;;
esac
AC_SUBST([MMCORE_APPLEHOST_LDFLAGS])


# TODO Make conditional
can_build_mmcore=yes


##
## MMCoreJ
##

# SWIG
AC_ARG_VAR([SWIG], [Simple Wrapper and Interface Generator])
AC_CHECK_PROGS([SWIG], [swig])
# TODO Provide SWIGFLAGS as precious variable


# Java

# Help for users trying to use old options
if test -n "$with_javaincl"; then
   AC_MSG_ERROR([--with-javaincl has been removed. You can use one of the following:
  ./configure --with-java=/path/to/jdk
  ./configure JNI_CPPFLAGS="-I/path/to/jni/include"
If you set the Java home, the JDK installation in that directory will be used.
If you set JNI_CPPFLAGS, it will override any automatic detection.
See ./configure --help for more options.])
fi

MM_ARG_WITH_JAVA
AS_IF([test "x$want_java" != xno],
[
   AS_IF([test -n "$JAVA_PREFIX"],
   [
      AS_IF([test -n "$JAVA_HOME"],
      [
         AS_IF([test "$JAVA_HOME" != "$JAVA_PREFIX"],
         [
            AC_MSG_ERROR([Path argument to --with-java and JAVA_HOME were both given and differ. Try removing one or the other])
         ])
      ])
   ])
   MM_JAVA_HOME(["$JAVA_PREFIX"])

   have_java=no
   MM_PROG_JAVA(
   [
      MM_HEADERS_JNI(
      [
         have_java=yes
      ])
   ])

   AS_IF([test "x$want_java" = xyes],
   [
      AS_IF([test "x$have_java" = xno],
      [
         AC_MSG_FAILURE([--with-java was given, but test for Java and JNI failed])
      ])
   ])
   use_java=$have_java
],
[
   use_java=no
])

AC_ARG_VAR([JAVACFLAGS], [Java compiler flags])
test "${JAVACFLAGS+set}" = set || JAVACFLAGS="-Xlint:all,-path,-serial"

MM_JNI_LIBRARY_PREFIX_SUFFIX
AM_CONDITIONAL([NONSTD_JNI_PREFIX], [test "x$JNI_PREFIX" != xlib])


AC_ARG_VAR([ANT], [Ant command])
AC_ARG_VAR([ANTFLAGS], [Ant flags])
test "${ANTFLAGS+set}" = set || ANTFLAGS="-logger org.apache.tools.ant.listener.SimpleBigProjectLogger"
MM_ARG_WITH_ANT
AS_IF([test "x$want_ant" != xno],
[
   AS_IF([test -z "$ANT"],
   [
      AS_IF([test -z "$ANTCMD"],
      [
         AC_PATH_PROG([ANTCMD], [ant])
      ])
      AS_IF([test -z "$ANTCMD"],
      [
         have_ant=no
      ],
      [
         have_ant=yes
         AS_IF([test -n "$JAVA_HOME"],
         [
            ANT="JAVA_HOME=$JAVA_HOME $ANTCMD"
         ],
         [
            ANT="$ANTCMD"
         ])
      ])

      AS_IF([test "x$want_ant" = xyes],
      [
         AS_IF([test "x$have_ant" = xno],
         [
            AC_MSG_FAILURE([--with-ant was given, but test for Ant failed])
         ])
      ])
   ],
   [
      have_ant=yes
   ])
   use_ant=$have_ant
],
[
   use_ant=no
])


build_mmcorej=yes
test "x$use_ant" = xno && { build_mmcorej=no; nobuild_mmcorej_reason="(Apache Ant not available)"; }
test "x$use_java" = xno && { build_mmcorej=no; nobuild_mmcorej_reason="(Java not available)"; }
test -z "$SWIG" && { build_mmcorej=no; nobuild_mmcorej_reason="(SWIG not available)"; }
test "x$can_build_mmcore" = xno && { build_mmcorej=no; nobuild_mmcorej_reason="(cannot build MMCore)"; }
AM_CONDITIONAL([BUILD_MMCOREJ], [test "x$build_mmcorej" = xyes])


enable_hardcoded_mmcorej_library_path_default=yes
test "x$enable_imagej_plugin" != xno && enable_hardcoded_mmcorej_library_path_default=no
AC_ARG_ENABLE([hardcoded-mmcorej-library-path],
   [AS_HELP_STRING([--disable-hardcoded-mmcorej-library-path],
                   [Do not use a fallback absolute path to locate the
                    MMCoreJ_wrap native library (default: disable if building
                    as ImageJ plugin; otherwise enable)])],
   [],
   [enable_hardcoded_mmcorej_library_path=yes])
if test "x$enable_hardcoded_mmcorej_library_path" = xyes; then
   # The path to be hard-coded into MMCoreJ's Java class loading code. This
   # should be the value of pkglibdir at build time (not configuration time),
   # so we use an escaped make variable substitution (recall that normal make
   # variables are recursively expanded).
   MMCOREJ_LIBRARY_PATH="\$(pkglibdir)"
else
   MMCOREJ_LIBRARY_PATH=
fi
AC_SUBST([MMCOREJ_LIBRARY_PATH])


build_mmcore=no
test "x$build_mmcorej" = xyes && build_mmcore=yes
AM_CONDITIONAL([BUILD_MMCORE], [test "x$build_mmcore" = xyes])


##
## Java application (MMStudio and plugins)
##

AC_ARG_ENABLE([java-app],
   [AS_HELP_STRING([--disable-java-app], [do not build the Java app])],
   [], [enable_java_app=auto])

build_java_app=yes
test "x$build_mmcorej" = xno && { build_java_app=no; nobuild_java_app_reason="(cannot build MMCoreJ)"; }
test "x$enable_java_app" = xno && { build_java_app=no; nobuild_java_app_reason="(disabled)"; }
# TODO Require dependency JARs

AS_IF([test "x$enable_java_app" = xyes],
[
   AS_IF([test "x$build_java_app" = xno],
   [
      AC_MSG_ERROR([--enable-java-app was given, but prerequisites were not satisfied])
   ])
])

AM_CONDITIONAL([BUILD_JAVA_APP], [test "x$build_java_app" = xyes])


if test -n "$with_imagej"; then
   AC_MSG_ERROR([--with-imagej has been removed. Please use --enable-imagej-plugin and (optionally) --with-ij-jar.])
fi


# Set the default install directories used in Makefile.am's.
MM_INSTALL_DIRS


# Allow user to override location of ij.jar (whether or not we are building as
# ImageJ plugin). If we are building as ImageJ plugin, default to the ij.jar in
# the target ImageJ directory. Otherwise do not set imagejjar, and use the copy
# fetched by Ivy.
AC_ARG_WITH([ij-jar],
   [AS_HELP_STRING([--with-ij-jar=FILE],
      [Use specified ImageJ ij.jar for building and running])],
   [], [with_ij_jar=auto])
case $with_ij_jar in
   yes) AC_MSG_ERROR([--with-ij-jar requires argument (ij.jar path)]) ;;
   no)  imagejjar="";;
   *)   imagejjar="$with_ij_jar";;
esac
if test "x$imagejjar" = xauto; then
   if test -n "$imagejdir"; then
      AC_MSG_CHECKING([for ij.jar under $imagejdir])
      # Select the first ij.jar found
      imagejjar=`find "$imagejdir" -name ij.jar | head -n 1`
      if test -z "$imagejjar"; then
         AC_MSG_RESULT([no])
         AC_MSG_ERROR([use --with-ij-jar=FILE to specify where to find ij.jar])
      fi
      # But make sure all copies are equal (this is for cases like the OS X
      # version, where there is one copy each within ImageJ.app and
      # ImageJ64.app)
      for ij_jar in `find "$imagejdir" -name ij.jar`
      do
         if cmp -s "$imagejjar" "$ij_jar"; then :; else
            AC_MSG_RESULT([multiple non-identical copies])
            AC_MSG_ERROR([use --with-ij-jar=FILE to specify which ij.jar to use])
         fi
      done
      AC_MSG_RESULT([$imagejjar])
   else
      imagejjar=
   fi
fi
AC_SUBST([imagejjar])


# Make the building of Magellan conditional on availability of non-free JARs.
# (This is a bit of a hack, in part since it needs to run before fetchdeps, but
# it will have to suffice for now.)
build_magellan=yes
AC_CHECK_FILE([$srcdir/../3rdparty/classext/DT1.2.jar],
              [],
              [build_magellan=no])
AM_CONDITIONAL([BUILD_MAGELLAN],
               [test "x$build_magellan" = xyes])


# Java dependencies.
AC_ARG_ENABLE([install-dependency-jars],
   [AS_HELP_STRING([--disable-install-dependency-jars],
      [Do not install third-party library JARs])],
   [], [enable_install_dependency_jars=yes])
test "x$build_java_app" = xno && enable_install_dependency_jars=no
AM_CONDITIONAL([INSTALL_DEPENDENCY_JARS],
      [test "x$enable_install_dependency_jars" != xno])


##
## Leftover stuff (needs cleanup)
##

AC_HEADER_STDBOOL
AC_C_CONST
AC_C_INLINE
AC_CHECK_FUNCS([memset])
AC_CHECK_LIB(dl, dlopen)


# Install Device Adapter API library and headers
install_mmdevapi=false
# TODO reinstate this flag in some better form
#AC_ARG_ENABLE(inst-devapi,
#    [  --enable-inst-devapi    Install the Device Adapter API library and headers ],
#    [ install_mmdevapi=true ])
AM_CONDITIONAL([INSTALL_MMDEVAPI], [test x$install_mmdevapi = xtrue])


##
## Subdirectory configuration
##

AC_MSG_CHECKING([for proprietary device adapter source])
AS_IF([test -f "$srcdir/mmCoreAndDevices/SecretDeviceAdapters/configure"],
   [build_secretdeviceadapters=yes], [build_secretdeviceadapters=no])
AM_CONDITIONAL([BUILD_SECRETDEVICEADAPTERS],
   [test "x$build_secretdeviceadapters" = xyes])

AC_CONFIG_SUBDIRS([mmCoreAndDevices/DeviceAdapters])
AS_IF([test "x$build_secretdeviceadapters" = xyes], [
   AC_CONFIG_SUBDIRS([mmCoreAndDevices/SecretDeviceAdapters])
])


##
## Output generation
##

AC_CONFIG_FILES(m4_strip([
   Makefile
   buildscripts/AntExtensions/Makefile
   testing/Makefile
   mmCoreAndDevices/MMDevice/Makefile
   mmCoreAndDevices/MMDevice/unittest/Makefile
   mmCoreAndDevices/MMCore/Makefile
   mmCoreAndDevices/MMCore/unittest/Makefile
   mmCoreAndDevices/MMCoreJ_wrap/Makefile
   mmstudio/Makefile
   acqEngine/Makefile
   libraries/Makefile
   autofocus/Makefile
   plugins/Makefile
   mmAsImageJMacros/Makefile
   scripts/Makefile
   systemtest/Makefile
   systemtest/SequenceTests/Makefile
   bindist/Makefile
]))

AC_OUTPUT

echo ""
echo "m4_text_box([Micro-Manager configuration])"
echo ""

if test "x$use_java" = xyes; then
echo "   JAVA_HOME    = $JAVA_HOME"
echo "   JNI_CPPFLAGS = $JNI_CPPFLAGS"
echo "   JAVA         = $JAVA"
echo "   JAVACFLAGS   = $JAVACFLAGS"
if test "x$use_ant" = xyes; then
echo "   ANT          = $ANT"
fi
echo ""
fi

echo "   Build MMCoreJ (MMCore Java wrapper):    $build_mmcorej $nobuild_mmcorej_reason"
echo "   Build Java application (MMStudio):      $build_java_app $nobuild_java_app_reason"
echo "   Build device adapters:                  yes"
echo "   Build closed-source device adapters:    $build_secretdeviceadapters"
echo ""

if test "x$build_java_app" = xyes; then
AM_COND_IF([INSTALL_AS_IMAGEJ_PLUGIN],
[
echo "   Install MMStudio as ImageJ plugin:      yes"
echo "   Target ImageJ directory:                $imagejdir"
],
[
echo "   Install MMStudio as ImageJ plugin:      no"
echo "   Installation prefix:                    $prefix"
])
if test -n "$imagejjar"; then
echo "   ij.jar:                                 $imagejjar"
else
echo "   ij.jar:                                 from build classpath"
fi
AM_COND_IF([INSTALL_DEPENDENCY_JARS],
[
echo "   Install dependency JARs:                yes"
],
[
echo "   Install dependency JARs:                no"
])
echo ""
fi # build_java_app

echo "Now you can build and install by typing:"
if test "x$use_java" = xyes; then
echo "    make fetchdeps  # To download Java dependencies"
fi
echo "    make"
echo "    make install"
echo ""
