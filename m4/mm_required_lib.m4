
# MM_ARG_WITH_REQUIRED_LIB(name, with-suffix, var-prefix)
#
# e.g. MM_ARG_WITH_REQUIRED_LIB([Foo], [foo], [FOO])
#
# Sets with_foo to argument passed by user (or yes, if not given). Fails if
# user attempts to set the --with flag to without/no. If user passes argument
# other than yes or auto, sets FOO_PREFIX to the argument.
#
AC_DEFUN([MM_ARG_WITH_REQUIRED_LIB], [
   m4_pushdef([withvar], [with_[]m4_bpatsubst([$2], [[-+=;:,.]], [_])])dnl
   AC_MSG_CHECKING([if $1 directory was given])
   AC_ARG_WITH([$2], [AS_HELP_STRING([--with-[]$2[]=[[yes|DIR]]],
               [use $1 in DIR (default: yes)])],
      [],
      [withvar=yes])

   $3_PREFIX=
   case $withvar in
      yes | auto) : ;;
      no) AC_MSG_FAILURE([--without-[]$2 was given, but $1 is required]) ;;
      *) $3_PREFIX="$withvar" ;;
   esac
   AS_IF([test -n "$$3_PREFIX"], [AC_MSG_RESULT([$$3_PREFIX])], [AC_MSG_RESULT([no])])
   m4_popdef([withvar])dnl
])


# MM_MSG_REQUIRED_LIB_FAILURE(name, with-suffix)
AC_DEFUN([MM_MSG_REQUIRED_LIB_FAILURE], [
   AC_MSG_FAILURE([test for $1 failed ($1 is required)])
])
