
# Author: Mark Tsuchida
# Copyright: University of California, San Francisco, 2014
# License: BSD

# MM_LIB_SET_FLAGS_PKGCONFIG(var-prefix, pkg-config-name, [hook],
# [action-if-success], [action-if-failure])
#
# The hook is the name of a macro with the following signature, used to repair
# the flags returned by pkg-config. It should not print a message (except to
# the log file).
# my_hook(cflags-var-name, libs-var-name, ok-var-name)
# The three parameters are names of shell variables. The hook macro should emit
# shell code that repairs the cflags and libs, and return yes or no in the
# variable named by the third parameter.
#
# This macro avoids calling PKG_CHECK_MODULES, because we don't want the
# precious variables (FOO_CFLAGS and FOO_LIBS) added by that macro. Instead, we
# want to stick to our own stand ard scheme (FOO_CPPFLAGS, FOO_CFLAGS,
# FOO_LDFLAGS, and FOO_LIBS).
#
AC_DEFUN([MM_LIB_SET_FLAGS_PKGCONFIG], [
   PKG_PROG_PKG_CONFIG([0.20])
   mm_pkgconfig_$1_ok=
   AS_IF([test -n "$PKG_CONFIG"],
   [
      AC_MSG_CHECKING([for pkg-config metadata for $2])
      PKG_CHECK_EXISTS([$2],
      [
         AC_MSG_RESULT([yes])

         AC_MSG_CHECKING([for $2 cflags from pkg-config])
         mm_pkgconfig_$1_cflags=`$PKG_CONFIG --cflags "$2" 2>&AS_MESSAGE_LOG_FD`
         AS_IF([test "x$?" = x0],
         [
            AC_MSG_RESULT([$mm_pkgconfig_$1_cflags])
         ],
         [
            AC_MSG_RESULT([error])
            mm_pkgconfig_$1_ok=no
         ])

         AS_IF([test "x$mm_pkgconfig_$1_ok" != xno],
         [
            AC_MSG_CHECKING([for $2 libs from pkg-config])
            mm_pkgconfig_$1_libs=`$PKG_CONFIG --libs "$2" 2>&AS_MESSAGE_LOG_FD`
            AS_IF([test "x$?" = x0],
            [
               AC_MSG_RESULT([$mm_pkgconfig_$1_libs])
            ],
            [
               AC_MSG_RESULT([error])
               mm_pkgconfig_$1_ok=no
            ])
         ])

         AS_IF([test "x$mm_pkgconfig_$1_ok" != xno],
         [
            m4_ifval([$3],
            [
               # Apply cflags/libs repair hook
               AC_MSG_CHECKING([for repaired flags for $2])
               $3([mm_pkgconfig_$1_cflags], [mm_pkgconfig_$1_libs], [mm_pkgconfig_$1_ok])
               AC_MSG_RESULT([$mm_pkgconfig_$1_ok])
            ],
            [
               mm_pkgconfig_$1_ok=yes
            ])
            
            _MM_PKGCONFIG_SPLIT_CFLAGS([$1], [$mm_pkgconfig_$1_cflags])
            _MM_PKGCONFIG_SPLIT_LIBS([$1], [$mm_pkgconfig_$1_libs])
         ])
      ],
      [
         AC_MSG_RESULT([no])
         mm_pkgconfig_$1_ok=no
      ])
   ],
   [
      mm_pkgconfig_$1_ok=no
   ])
   AS_IF([test "x$mm_pkgconfig_$1_ok" = xyes], [$4], [$5])
])


#
# Sub-macros
#

AC_DEFUN([_MM_PKGCONFIG_SPLIT_CFLAGS], [
   $1_CPPFLAGS=
   $1_CFLAGS=
   for flag in $2
   do
      noIprefix=`echo $flag | sed 's%^-I%%'`
      if test "x$noIprefix" = "x$flag"; then # no -I prefix
         $1_CFLAGS="$$1_CFLAGS $flag"
      else
         $1_CPPFLAGS="$$1_CPPFLAGS $flag"
      fi
   done
])


AC_DEFUN([_MM_PKGCONFIG_SPLIT_LIBS], [
   # Note: -framework X goes into LDFLAGS, not LIBS; this is intentional
   $1_LDFLAGS=
   $1_LIBS=
   for flag in $2
   do
      nolprefix=`echo $flag | sed 's%^-l%%'`
      if test "x$nolprefix" = "x$flag"; then # no -l prefix
         nosuffix=`echo $flag | sed 's%\.dylib$%%
                                     s%\.a$%%
                                     s%\.la$%%'`
         if test "x$nosuffix" = "x$flag"; then # no suffix
            $1_LDFLAGS="$$1_LDFLAGS $flag"
         else # has suffix
            $1_LIBS="$$1_LIBS $flag"
         fi
      else # has -l prefix
         $1_LIBS="$$1_LIBS $flag"
      fi
   done
])
