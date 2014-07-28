
# MM_ARG_WITH_JAVA (no args)
# Sets want_java to yes or no; sets JAVA_PREFIX to empty or path.
# (JAVA_HOME is _not_ set because it is a precious variable.)
AC_DEFUN([MM_ARG_WITH_JAVA], [
   AC_MSG_CHECKING([if Java support was requested])
   AC_ARG_WITH([java], [AS_HELP_STRING([--with-java=[[yes|no|JAVA_HOME]]],
               [use Java at JAVA_HOME (default: auto)])],
      [],
      [with_java=auto])

   case $with_java in
      yes | no | auto) want_java="$with_java" ;;
      *) JAVA_PREFIX="$with_java"
         want_java=yes ;;
   esac
   AS_IF([test -n "$JAVA_PREFIX"],
      [AC_MSG_RESULT([yes ($JAVA_PREFIX)])],
      [AC_MSG_RESULT([$want_java])])
])


# MM_PROGS_JAVA([action-if-all-found], [action-if-any-not-found])
# Find java, javac, and jar and set JAVA, JAVAC, and JAR. Do not override if
# set by user. If JAVA_HOME is not empty, search $JAVA_HOME/bin. Otherwise
# search PATH.
AC_DEFUN([MM_PROGS_JAVA],
[
   AC_ARG_VAR([JAVA], [Java virtual machine])
   AC_ARG_VAR([JAVAC], [Java compiler])
   AC_ARG_VAR([JAR], [Java archive tool])

   mm_progs_java_path="$PATH"
   test -n "$JAVA_HOME" && mm_progs_java_path="$JAVA_HOME/bin"

   AC_MSG_CHECKING([for user-specified Java virtual machine])
   AS_IF([test -n "$JAVA"],
   [
      AC_MSG_RESULT([yes ($JAVA)])
   ],
   [
      AC_MSG_RESULT([no; will search for one])
      AC_PATH_PROGS([JAVA], [java kaffe], [], [$mm_progs_java_path])
   ])

   AC_MSG_CHECKING([for user-specified Java compiler])
   AS_IF([test -n "$JAVAC"],
   [
      AC_MSG_RESULT([yes ($JAVAC)])
   ],
   [
      AC_MSG_RESULT([no; will search for one])
      AC_PATH_PROGS([JAVAC], [javac jikes guavac], [], [$mm_progs_java_path])
   ])

   AC_MSG_CHECKING([for user-specified Java archive tool])
   AS_IF([test -n "$JAR"],
   [
      AC_MSG_RESULT([yes ($JAR)])
   ],
   [
      AC_MSG_RESULT([no; will search for one])
      AC_PATH_PROG([JAR], [jar], [], [$mm_progs_java_path])
   ])

   mm_progs_java_all_found=yes
   if test -z "$JAVA"; then mm_progs_java_all_found=no; fi
   if test -z "$JAVAC"; then mm_progs_java_all_found=no; fi
   if test -z "$JAR"; then mm_progs_java_all_found=no; fi
   AS_IF([test "x$mm_progs_java_all_found" = xyes], [$1], [$2])
])


# MM_HEADERS_JNI([action-if-found], [action-if-not-found])
# Set precious variable JNI_CPPFLAGS
AC_DEFUN([MM_HEADERS_JNI],
[
   AC_ARG_VAR([JNI_CPPFLAGS], [preprocessor flags for Java Native Interface])
   AC_MSG_CHECKING([for user-provided configuration for JNI])
   AS_IF([test -n "$JNI_CPPFLAGS"],
   [
      AC_MSG_RESULT([yes; well skip checks])
   ],
   [
      AC_MSG_RESULT([no; will perform availability check])

      AX_JNI_INCLUDE_DIR

      AS_IF([test -n "$JNI_INCLUDE_DIRS"],
      [
         for dir in $JNI_INCLUDE_DIRS
         do
            JNI_CPPFLAGS="$JNI_CPPFLAGS -I$dir"
         done

         # Push state
         AC_LANG_PUSH([C])
         mm_jni_old_cppflags="$CPPFLAGS"
         CPPFLAGS="$JNI_CPPFLAGS $mm_jni_old_cppflags"

         AC_CHECK_HEADER([jni.h], [], [JNI_CPPFLAGS=], [#include <jni.h>])

         # Pop state
         CPPFLAGS="$mm_jni_old_cppflags"
         AC_LANG_POP([C])
      ],
      [
         JNI_CPPFLAGS=
      ])
   ])
   AS_IF([test -n "$JNI_CPPFLAGS"], [$1], [$2])
])


# MM_JNI_LIBRARY_PREFIX_SUFFIX
# Get the prefix and suffix for JNI native library filename. Set JNI_PREFIX and
# JNI_SUFFIX.
AC_DEFUN([MM_JNI_LIBRARY_PREFIX_SUFFIX],
[
   AC_MSG_CHECKING([JNI library filename format])
   # Get the prefix and suffix for JNI native library filename.
   # In theory we can get this "correctly" by compiling and running a Java program
   # (using System.mapLibraryName("ZZZ")), but it is not worth the trouble, and
   # there are complications (such as Oracle Java 7 for OS X returning a .dylib
   # suffix instead of .jnilib). Since by default we load native libraries by
   # exact filename anyway, it is safe for us to dictate the naming scheme.
   case $host in
      *-*-darwin*)
         JNI_PREFIX=lib
         JNI_SUFFIX=.jnilib ;;
      *-*-cygwin* | *-*-mingw*)
         JNI_PREFIX=
         JNI_SUFFIX=.dll ;;
      *)
         JNI_PREFIX=lib
         JNI_SUFFIX=.so ;;
   esac
   AC_MSG_RESULT([${JNI_PREFIX}XYZ${JNI_SUFFIX}])
   AC_SUBST([JNI_PREFIX])
   AC_SUBST([JNI_SUFFIX])
])
