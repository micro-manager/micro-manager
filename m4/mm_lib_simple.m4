
# Author: Mark Tsuchida
# Copyright: University of California, San Francisco, 2014
# License: BSD

# TODO Support for C++ libraries, with CXXFLAGS, CXXCPPFLAGS (but most of the
# libraries we use are C libraries)

# MM_LIB_SIMPLE(var-prefix, name, [path-prefix], [libs], [header], [function],
# [action-if-found], [action-if-not-found])
AC_DEFUN([MM_LIB_SIMPLE], [
   AC_LANG_ASSERT([C])
   mm_lib_simple_have_$1=
   MM_LIB_CHECK_ARG_VARS([$1], [$2],
   [
      mm_lib_simple_have_$1=yes
   ],
   [
      _MM_LIB_SIMPLE_DO_TEST([$1], [$2], [$3], [], [$4], [$5], [$6],
      [
         mm_lib_simple_have_$1=yes
      ],
      [
         mm_lib_simple_have_$1=no
         MM_LIB_CLEAR_FLAGS([$1])
      ])
   ])
   AS_IF([test "x$mm_lib_simple_have_$1" = xyes], [$7], [$8])
])


# MM_LIB_SIMPLE_CXX(var-prefix, name, [path-prefix], [libs], [header], [link-test-prog],
# [action-if-found], [action-if-not-found])
AC_DEFUN([MM_LIB_SIMPLE_CXX], [
   AC_LANG_ASSERT([C++])
   mm_lib_simple_cxx_have_$1=
   MM_LIB_CHECK_ARG_VARS_CXX([$1], [$2],
   [
      mm_lib_simple_cxx_have_$1=yes
   ],
   [
      _MM_LIB_SIMPLE_CXX_DO_TEST([$1], [$2], [$3], [], [$4], [$5], [$6],
      [
         mm_lib_simple_cxx_have_$1=yes
      ],
      [
         mm_lib_simple_cxx_have_$1=no
         MM_LIB_CLEAR_FLAGS([$1])
      ])
   ])
   AS_IF([test "x$mm_lib_simple_cxx_have_$1" = xyes], [$7], [$8])
])


# MM_LIB_WITH_PKG_CONFIG(var-prefix, name, pkg-config-name,
# [pkg-config-flags-hook], [path-prefix], [libs], [header], [function],
# [action-if-found], [action-if-not-found])
#
# If path-prefix is given, just try it. Otherwise, first try pkg-config. If
# that fails, try the given flags (the libs argument).
AC_DEFUN([MM_LIB_WITH_PKG_CONFIG], [
   AC_LANG_ASSERT([C])
   mm_lib_with_pkg_config_have_$1=
   MM_LIB_CHECK_ARG_VARS([$1], [$2],
   [
      mm_lib_with_pkg_config_have_$1=yes
   ],
   [
      mm_lib_with_pkg_config_skip_pkg_config=no
      m4_ifval([$5], [test -n "$5" && mm_lib_with_pkg_config_skip_pkg_config=yes])
      AS_IF([test "x$mm_lib_with_pkg_config_skip_pkg_config" = xno],
      [
         MM_LIB_SET_FLAGS_PKGCONFIG([$1], [$3], [$4],
         [
            MM_LIB_IFELSE([$1], [$2], [with flags from pkg-config],
                          [$7], [$8],
                          [mm_lib_with_pkg_config_have_$1=yes],
                          [MM_LIB_CLEAR_FLAGS([$1])])
         ])
      ])
      AS_IF([test "x$mm_lib_with_pkg_config_have_$1" != xyes],
      [
         _MM_LIB_SIMPLE_DO_TEST([$1], [$2], [$5], [with hard-coded flags],
                                [$6], [$7], [$8],
                                [mm_lib_with_pkg_config_have_$1=yes],
                                [mm_lib_with_pkg_config_have_$1=no
                                 MM_LIB_CLEAR_FLAGS([$1])])
      ])
   ])
   AS_IF([test "x$mm_lib_with_pkg_config_have_$1" = xyes],
         [$9], m4_argn([10], $@))
])


# _MM_LIB_SIMPLE_DO_TEST(var-prefix, name, [path-prefix], [message-suffix],
# [libs], [header], [function], [action-if], [action-else])
AC_DEFUN([_MM_LIB_SIMPLE_DO_TEST], [
   m4_ifval([$3], AS_IF([test -n "$3"],
   [
      $1_CPPFLAGS="-I$3/include"
      $1_LDFLAGS="-L$3/lib"
   ]))
   $1_LIBS="$5"
   MM_LIB_IFELSE([$1], [$2], [$4], [$6], [$7], [$8], [$9])
])


# _MM_LIB_SIMPLE_CXX_DO_TEST(var-prefix, name, [path-prefix], [message-suffix],
# [libs], [header], [link-test-prog], [action-if], [action-else])
AC_DEFUN([_MM_LIB_SIMPLE_CXX_DO_TEST], [
   m4_ifval([$3], AS_IF([test -n "$3"],
   [
      $1_CPPFLAGS="-I$3/include"
      $1_LDFLAGS="-L$3/lib"
   ]))
   $1_LIBS="$5"
   MM_CXXLIB_IFELSE([$1], [$2], [$4], [$6], [$7], [$8], [$9])
])
