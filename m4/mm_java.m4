
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


# MM_JAVA_HOME([candidate-location])
# Declare the precious variable JAVA_HOME. If JAVA_HOME is not set, try to find
# a JDK installation and set it. JAVA_HOME may remain unset even if javac et
# al. are available, if a canonical Java home directory is not found (e.g. as
# with Apple JDK 6).
AC_DEFUN([MM_JAVA_HOME], [
   AC_ARG_VAR([JAVA_HOME], [JDK home directory])

   while true # fake loop to exit via break
   do
      AC_MSG_CHECKING([for user-specified JAVA_HOME])
      AS_IF([test -n "$JAVA_HOME"],
      [
         AC_MSG_RESULT([yes; will skip checks])
         break
      ])
      AC_MSG_RESULT([no; will perform availability check])

      # Check the location suggested by the user (e.g. via --with-java=PATH)
      AS_IF([test -n $1],
      [
         MM_JAVA_HOME_SET_IF_JDK([$1])
         AS_IF([test -n "$JAVA_HOME"],
         [
            break
         ],
         [
            AC_MSG_ERROR([path given with --with-java is not a JDK home directory; please correct it, or set JAVA_HOME instead to force its use])
         ])
      ])

      # OS X has java_home command that prints the user-selected Java
      # installation
      AS_IF([test -x /usr/libexec/java_home],
      [
         # TODO Do we need to allow for the absnece of jni.h in this case?
         MM_JAVA_HOME_SET_IF_JDK([`/usr/libexec/java_home`])
         AS_IF([test -n "$JAVA_HOME"], [break])
      ])

      # Try to find from the location of javac, which is located in
      # $JAVA_HOME/bin in modern JDK installations (but not in Apple JDK 6).
      # The found javac might be a symbolic link to the javac in the JDK home.
      # On the other hand, the JDK home itself might be constructed from
      # symlinks, so we may not want to follow symlinks all the way. So try
      # after resolving each symlink. Give up if we don't have readlink. We do
      # not attempt to resolve symlinks in directory components of the path;
      # this is intentional.
      AC_PATH_PROGS([mm_java_home_javac], [javac])
      AC_CHECK_PROGS([mm_java_home_readlink], [readlink])
      while test -n "$mm_java_home_javac"
      do
         mm_java_home_javac_parent=`dirname $mm_java_home_javac`
         mm_java_home_javac_grandparent=`cd $mm_java_home_javac_parent/..; pwd`
         MM_JAVA_HOME_SET_IF_JDK(["$mm_java_home_javac_grandparent"])
         AS_IF([test -n "$JAVA_HOME"], [break])
         AS_IF([test -z "$mm_java_home_readlink"], [break])
         mm_java_home_javac=`$mm_java_home_readlink $mm_java_home_javac`
      done
      AS_IF([test -n "$JAVA_HOME"], [break])

      break
   done
])


# Subroutine for MM_JAVA_HOME
# Check if the given directory looks like a JDK (not JRE) home directory. If
# so, set JAVA_HOME.
AC_DEFUN([MM_JAVA_HOME_SET_IF_JDK], [
   mm_java_home_candidate=$1
   mm_java_home_candidate_is_jdk=yes
   while true
   do
      AC_MSG_CHECKING([for java in $mm_java_home_candidate/bin])
      AS_IF([test -x "$mm_java_home_candidate/bin/java"],
      [
         AC_MSG_RESULT([yes])
      ],
      [
         AC_MSG_RESULT([no])
         mm_java_home_candidate_is_jdk=no
         break
      ])

      AC_MSG_CHECKING([for javac in $mm_java_home_candidate/bin])
      AS_IF([test -x "$mm_java_home_candidate/bin/javac"],
      [
         AC_MSG_RESULT([yes])
      ],
      [
         AC_MSG_RESULT([no])
         mm_java_home_candidate_is_jdk=no
         break
      ])

      AC_MSG_CHECKING([for jar in $mm_java_home_candidate/bin])
      AS_IF([test -x "$mm_java_home_candidate/bin/jar"],
      [
         AC_MSG_RESULT([yes])
      ],
      [
         AC_MSG_RESULT([no])
         mm_java_home_candidate_is_jdk=no
         break
      ])

      AC_MSG_CHECKING([for jni.h in $mm_java_home_candidate/include])
      AS_IF([test -f "$mm_java_home_candidate/include/jni.h"],
      [
         AC_MSG_RESULT([yes])
      ],
      [
         AC_MSG_RESULT([no])
         # Apple JDK 6 has an incomplete JAVA_HOME lacking the JNI headers. We
         # make an exception and allow this to pass as a JAVA_HOME, since we
         # know how to deal with it specially (see MM_HEADERS_JNI).
         # We do not need to do this for versions of Apple JDK 6 that get
         # installed in /Library/Java/JavaVirtualMachines; those do contain an
         # include directory.
         AS_IF([test "$mm_java_home_candidate" = "/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home"],
         [],
         [
            mm_java_home_candidate_is_jdk=no
            break
         ])
      ])

      break
   done
   AS_IF([test "$mm_java_home_candidate_is_jdk" = yes],
   [
      JAVA_HOME="$mm_java_home_candidate"
   ])
])


# MM_PROG_JAVA([action-if-found], [action-if-not-found])
# Find java executable and set JAVA. Do not override if set by user. If
# JAVA_HOME is not empty, search $JAVA_HOME/bin. Otherwise search PATH.
AC_DEFUN([MM_PROG_JAVA],
[
   AC_ARG_VAR([JAVA], [Java virtual machine])

   mm_prog_java_path="$PATH"
   test -n "$JAVA_HOME" && mm_prog_java_path="$JAVA_HOME/bin"

   AC_MSG_CHECKING([for user-specified Java virtual machine])
   AS_IF([test -n "$JAVA"],
   [
      AC_MSG_RESULT([yes ($JAVA)])
   ],
   [
      AC_MSG_RESULT([no; will search for one])
      AC_PATH_PROGS([JAVA], [java kaffe], [], [$mm_prog_java_path])
   ])

   AS_IF([test -n "$JAVA"], [$1], [$2])
])


# MM_HEADERS_JNI([action-if-found], [action-if-not-found])
# Set precious variable JNI_CPPFLAGS
AC_DEFUN([MM_HEADERS_JNI],
[
   AC_ARG_VAR([JNI_CPPFLAGS], [preprocessor flags for Java Native Interface])
   AC_MSG_CHECKING([for user-provided configuration for JNI])
   AS_IF([test "${JNI_CPPFLAGS+set}" = set],
   [
      AC_MSG_RESULT([yes; well skip checks])
   ],
   [
      AC_MSG_RESULT([no; will perform availability check])

      AS_IF([test -n "$JAVA_HOME"],
      [
         MM_HEADERS_JNI_SET_IF_USABLE(["$JAVA_HOME/include"])
      ])

      AS_IF([test "${JNI_CPPFLAGS+set}" != set],
      [
         # On OS X, Apple JDK has headers in a nonstandard location. Note that
         # we do not search within /Library/Java/JavaVirtualMachines; JAVA_HOME
         # should have been set if we are using a JDK installed there.
         MM_HEADERS_JNI_SET_IF_USABLE([/System/Library/Frameworks/JavaVM.framework/Headers])
      ])
   ])
   AS_IF([test "${JNI_CPPFLAGS+set}" = set], [$1], [$2])
])


# Subroutine for MM_HEADERS_JNI
# Set JNI_CPPFLAGS if the given directory has jni.h, adding any necessary
# platform-specific subdirectories.
AC_DEFUN([MM_HEADERS_JNI_SET_IF_USABLE],
[
   mm_jni_header_dir=$1

   AC_CHECK_FILE([$mm_jni_header_dir/jni.h],
   [
      mm_jni_cppflags="-I$mm_jni_header_dir"

      # Add platform-specific subdirectory that typically contains jni_md.h
      AC_CHECK_FILE([$mm_jni_header_dir/jni_md.h],
      [
         # With Apple JDK, jni_md.h is next to jni.h; no action needed
      ],
      [
         mm_jni_md_dirs=""
         case "$host_os" in
            darwin*) mm_jni_md_dirs="darwin";;
            linux*) mm_jni_md_dirs="linux genunix";;
            mingw*) mm_jni_md_dirs="win32";;
            cygwin*) mm_jni_md_dirs="win32";;
         esac
         AS_IF([test -z "$mm_jni_md_dirs"],
         [
            # Let's hope that the correct subdirectory is the only one present
            AC_MSG_CHECKING([for jni_md.h])
            mm_jni_md_first=`find "$mm_jni_header_dir" -name jni_md.h | head -n 1`
            for mm_jni_md in `find "$mm_jni_header_dir" -name jni_md.h`
            do
               AS_IF([cmp -s "$mm_jni_md" "$mm_jni_md_first"],
               [],
               [
                  AC_MSG_RESULT([multiple non-identical copies; do not know which to use])
                  AC_MSG_ERROR([please set JNI_CPPFLAGS so that the compiler can find jni.h and the correct jni_md.h])
               ])
            done
            AC_MSG_RESULT([$mm_jni_md_first])
            mm_jni_cppflags="$mm_jni_cppflags -I`dirname $mm_jni_md_first`"
         ],
         [
            for mm_jni_md_dir in $mm_jni_md_dirs
            do
               mm_jni_abs_md_dir="$mm_jni_header_dir/$mm_jni_md_dir"
               AC_CHECK_FILE([$mm_jni_abs_md_dir/jni_md.h],
               [
                  mm_jni_cppflags="$mm_jni_cppflags -I$mm_jni_abs_md_dir"
               ])
            done
         ])
      ])

      # Push state
      AC_LANG_PUSH([C])
      mm_jni_old_cppflags="$CPPFLAGS"
      CPPFLAGS="$mm_jni_cppflags $mm_jni_old_cppflags"

      AC_CHECK_HEADER([jni.h], [JNI_CPPFLAGS="$mm_jni_cppflags"], [],
                      [#include <jni.h>])

      # Pop state
      CPPFLAGS="$mm_jni_old_cppflags"
      AC_LANG_POP([C])
   ])
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
