
# Macros for setting library-specific flag variables


# MM_LIB_ARG_VARS(var-prefix, name)
AC_DEFUN([MM_LIB_ARG_VARS], [
   AC_ARG_VAR([$1_CPPFLAGS], [preprocessor flags for $2])
   AC_ARG_VAR([$1_CFLAGS], [compiler flags for $2])
   AC_ARG_VAR([$1_LDFLAGS], [linker flags for $2])
   AC_ARG_VAR([$1_LIBS], [library flags for $2])
])


# MM_LIB_ARG_VARS_CXX(var-prefix, name)
AC_DEFUN([MM_LIB_ARG_VARS_CXX], [
   AC_ARG_VAR([$1_CPPFLAGS], [preprocessor flags for $2])
   AC_ARG_VAR([$1_CXXFLAGS], [compiler flags for $2])
   AC_ARG_VAR([$1_LDFLAGS], [linker flags for $2])
   AC_ARG_VAR([$1_LIBS], [library flags for $2])
])


# MM_LIB_CLEAR_FLAGS(var-prefix)
# (For both C and C++)
AC_DEFUN([MM_LIB_CLEAR_FLAGS], [
   $1_CPPFLAGS=
   $1_CFLAGS=
   $1_CXXFLAGS=
   $1_LDFLAGS=
   $1_LIBS=
])


# _MM_LIB_FLAGS_IFELSE(var-prefix, [action-if-set], [action-if-not-set])
AC_DEFUN([_MM_LIB_FLAGS_IFELSE], [
   mm_lib_flags_ifelse_have_$1=
   test -n "$$1_CPPFLAGS" && mm_lib_flags_ifelse_have_$1=yes
   test -n "$$1_CFLAGS" && mm_lib_flags_ifelse_have_$1=yes
   test -n "$$1_LDFLAGS" && mm_lib_flags_ifelse_have_$1=yes
   test -n "$$1_LIBS" && mm_lib_flags_ifelse_have_$1=yes
   AS_IF([test "x$mm_lib_flags_ifelse_have_$1" = xyes], [$2], [$3])
])


# _MM_LIB_FLAGS_IFELSE_CXX(var-prefix, [action-if-set], [action-if-not-set])
AC_DEFUN([_MM_LIB_FLAGS_IFELSE_CXX], [
   mm_lib_flags_ifelse_have_$1=
   test -n "$$1_CPPFLAGS" && mm_lib_flags_ifelse_have_$1=yes
   test -n "$$1_CXXFLAGS" && mm_lib_flags_ifelse_have_$1=yes
   test -n "$$1_LDFLAGS" && mm_lib_flags_ifelse_have_$1=yes
   test -n "$$1_LIBS" && mm_lib_flags_ifelse_have_$1=yes
   AS_IF([test "x$mm_lib_flags_ifelse_have_$1" = xyes], [$2], [$3])
])


# MM_LIB_CHECK_ARG_VARS(var-prefix, name, [action-if-user-provided],
# action-if-check-required)
AC_DEFUN([MM_LIB_CHECK_ARG_VARS], [
   MM_LIB_ARG_VARS([$1], [$2])
   AC_MSG_CHECKING([for user-provided configuration for $2])
   _MM_LIB_FLAGS_IFELSE([$1],
   [
      AC_MSG_RESULT([yes; will skip checks])
      $3
   ],
   [
      AC_MSG_RESULT([no; will perform availability check])
      $4
   ])
])


# MM_LIB_CHECK_ARG_VARS_CXX(var-prefix, name, [action-if-user-provided],
# action-if-check-required)
AC_DEFUN([MM_LIB_CHECK_ARG_VARS_CXX], [
   MM_LIB_ARG_VARS_CXX([$1], [$2])
   AC_MSG_CHECKING([for user-provided configuration for $2])
   _MM_LIB_FLAGS_IFELSE_CXX([$1],
   [
      AC_MSG_RESULT([yes; will skip checks])
      $3
   ],
   [
      AC_MSG_RESULT([no; will perform availability check])
      $4
   ])
])
