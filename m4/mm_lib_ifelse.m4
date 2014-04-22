
# Author: Mark Tsuchida
# Copyright: University of California, San Francisco, 2014
# License: BSD

# MM_LIB_IFELSE(VAR-PREFIX, NAME, [MSG-SUFFIX], [HEADER], [FUNCTION], [ACTION-IF],
# [ACTION-IF-NOT])

# VAR-PREFIX - flag var prefix, e.g. LIBFOO
# NAME - display name, e.g. libfoo
# MSG-SUFFIX - test location message, e.g. `with flags from pkg-config'
# HEADER - header to check for, e.g. foo/foo.h
# FUNCTION - library function to check for, e.g. foo_init

# Used variables (not modified by MM_TEST_LIB)
# $1_CPPFLAGS (e.g. LIBFOO_CPPFLAGS)
# $1_CFLAGS (e.g. LIBFOO_CFLAGS)
# $1_LDFLAGS (e.g. LIBFOO_LDFLAGS)
# $1_LIBS (e.g. LIBFOO_LIBS)

AC_DEFUN([MM_LIB_IFELSE], [
   mm_lib_ifelse_have_$1=

   # Push state
   AC_LANG_ASSERT([C])
   mm_lib_ifelse_$1_old_cppflags="$CPPFLAGS"
   mm_lib_ifelse_$1_old_cflags="$CFLAGS"
   mm_lib_ifelse_$1_old_ldflags="$LDFLAGS"
   mm_lib_ifelse_$1_old_libs="$LIBS"
   CPPFLAGS="$$1_CPPFLAGS $mm_lib_ifelse_$1_old_cppflags"
   CFLAGS="$$1_CFLAGS $mm_lib_ifelse_$1_old_cflags"
   LDFLAGS="$$1_LDFLAGS $mm_lib_ifelse_$1_old_ldflags"
   LIBS="$$1_LIBS $mm_lib_ifelse_$1_old_libs"
   # End push state

   m4_ifval([$4], [
      AC_MSG_CHECKING([for $4 $3])
      dnl Note: We cannot use AC_CHECK_HEADER here, because we may be called
      dnl multiple times with different CPPFLAGS, etc. The cache variable would
      dnl clash. However, we should be checking for compilation, not
      dnl preprocessing: use AC_COMPILE_IFELSE
      AC_PREPROC_IFELSE([AC_LANG_PROGRAM([[#include <][$4][>]])],
      [
         AC_MSG_RESULT([yes])
      ],
      [
         AC_MSG_RESULT([no])
         mm_lib_ifelse_have_$1=no
      ])
   ])

   AS_IF([test "x$mm_lib_ifelse_have_$1" != xno],
   [
      m4_ifval([$5], [
         AC_MSG_CHECKING([for $5 in $2 $3])
         AC_LINK_IFELSE([AC_LANG_CALL([], [$5])],
         [
            AC_MSG_RESULT([yes])
            mm_lib_ifelse_have_$1=yes
         ],
         [
            AC_MSG_RESULT([no])
            mm_lib_ifelse_have_$1=no
         ])
      ],
      [
         mm_lib_ifelse_have_$1=no
      ])
   ])

   # Pop state
   CPPFLAGS="$mm_lib_ifelse_$1_old_cppflags"
   CFLAGS="$mm_lib_ifelse_$1_old_cflags"
   LDFLAGS="$mm_lib_ifelse_$1_old_ldflags"
   LIBS="$mm_lib_ifelse_$1_old_libs"
   # End pop state

   AS_IF([test "x$mm_lib_ifelse_have_$1" = xyes], [$6], [$7])
])
])


# MM_CXXLIB_IFELSE(VAR-PREFIX, NAME, [MSG-SUFFIX], [HEADER], [LINK-TEST-PROGRAM], [ACTION-IF],
# [ACTION-IF-NOT])
# Use AC_LANG_PROGRAM to construct the LINK-TEST-PROGRAM argument.
AC_DEFUN([MM_CXXLIB_IFELSE], [
   mm_cxxlib_ifelse_have_$1=

   # Push state
   AC_LANG_ASSERT([C++])
   mm_cxxlib_ifelse_$1_old_cppflags="$CPPFLAGS"
   mm_cxxlib_ifelse_$1_old_cxxflags="$CXXFLAGS"
   mm_cxxlib_ifelse_$1_old_ldflags="$LDFLAGS"
   mm_cxxlib_ifelse_$1_old_libs="$LIBS"
   CPPFLAGS="$$1_CPPFLAGS $mm_cxxlib_ifelse_$1_old_cppflags"
   CXXFLAGS="$$1_CXXFLAGS $mm_cxxlib_ifelse_$1_old_cxxflags"
   LDFLAGS="$$1_LDFLAGS $mm_cxxlib_ifelse_$1_old_ldflags"
   LIBS="$$1_LIBS $mm_cxxlib_ifelse_$1_old_libs"
   # End push state

   m4_ifval([$4], [
      AC_MSG_CHECKING([for $4 $3])
      dnl Note: We cannot use AC_CHECK_HEADER here, because we may be called
      dnl multiple times with different CPPFLAGS, etc. The cache variable would
      dnl clash. However, we should be checking for compilation, not
      dnl preprocessing: use AC_COMPILE_IFELSE
      AC_PREPROC_IFELSE([AC_LANG_PROGRAM([[#include <][$4][>]])],
      [
         AC_MSG_RESULT([yes])
      ],
      [
         AC_MSG_RESULT([no])
         mm_cxxlib_ifelse_have_$1=no
      ])
   ])

   AS_IF([test "x$mm_cxxlib_ifelse_have_$1" != xno],
   [
      m4_ifval([$5], [
         AC_MSG_CHECKING([if test program can be linked to $2 $3])
         AC_LINK_IFELSE([$5],
         [
            AC_MSG_RESULT([yes])
            mm_cxxlib_ifelse_have_$1=yes
         ],
         [
            AC_MSG_RESULT([no])
            mm_cxxlib_ifelse_have_$1=no
         ])
      ],
      [
         mm_cxxlib_ifelse_have_$1=no
      ])
   ])

   # Pop state
   CPPFLAGS="$mm_cxxlib_ifelse_$1_old_cppflags"
   CXXFLAGS="$mm_cxxlib_ifelse_$1_old_cxxflags"
   LDFLAGS="$mm_cxxlib_ifelse_$1_old_ldflags"
   LIBS="$mm_cxxlib_ifelse_$1_old_libs"
   # End pop state

   AS_IF([test "x$mm_cxxlib_ifelse_have_$1" = xyes], [$6], [$7])
])
])
