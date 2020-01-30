# MM_GMOCK(gmock-src-dir, make-gmock-src-dir)
# Sets have_gmock to yes or no; if yes, sets the GMOCK_CPPFLAGS substitution
# variable.
AC_DEFUN([MM_GMOCK], [
   AC_MSG_CHECKING([for gmock source in $1])
   AS_IF([test -f $1/src/gmock-all.cc],
      [
         AC_MSG_RESULT([yes])
         have_gmock=yes
         GMOCK_DIR="$2"
         GTEST_DIR="$GMOCK_DIR/gtest"
         AC_SUBST([GMOCK_CPPFLAGS],
            ["-isystem $GTEST_DIR/include -isystem $GMOCK_DIR/include"])
      ],
      [
         AC_MSG_RESULT([no])
         have_gmock=no
      ])
])
