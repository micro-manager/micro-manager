
# MM_ARG_WITH_OPTIONAL_LIB(name, with-suffix, var-prefix)
#
# e.g. MM_ARG_WITH_OPTIONAL_LIB([Foo], [foo], [FOO])
#
# Sets with_foo to argument passed by user (or yes or no or auto) and want_foo
# to yes or no or auto, where foo is with-suffix with dashes replaced with
# underscores. When want_foo is yes and the user passed a directory argument,
# sets FOO_PREFIX to the argument.
#
AC_DEFUN([MM_ARG_WITH_OPTIONAL_LIB], [
   m4_pushdef([withvar], [with_[]m4_bpatsubst([$2], [[-+=;:,.]], [_])])dnl
   m4_pushdef([wantvar], [want_[]m4_bpatsubst([$2], [[-+=;:,.]], [_])])dnl
   AC_MSG_CHECKING([if $1 support was requested])
   AC_ARG_WITH([$2], [AS_HELP_STRING([--with-[]$2[]=[[yes|no|DIR]]],
               [use $1 (default: auto)])],
      [],
      [withvar=auto])

   $3_PREFIX=
   case $withvar in
      yes | no | auto) wantvar="$withvar" ;;
      *) $3_PREFIX="$withvar"
         wantvar=yes ;;
   esac
   AS_IF([test -n "$$3_PREFIX"],
      [AC_MSG_RESULT([yes ($$3_PREFIX)])],
      [AC_MSG_RESULT([$wantvar])])
   m4_popdef([withvar])dnl
   m4_popdef([wantvar])dnl
])


# MM_MSG_OPTIONAL_LIB_FAILURE(name, with-suffix)
AC_DEFUN([MM_MSG_OPTIONAL_LIB_FAILURE], [
   AC_MSG_FAILURE([--with-[]$2[] was given, but test for $1 failed])
])
