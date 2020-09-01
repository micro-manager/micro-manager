# MM_GMOCK(gtest-src-dir, make-gtest-src-dir)
# Sets have_gmock to yes or no; if yes, sets the GMOCK_CPPFLAGS substitution
# variable.
# Note: This is named gmock because the gmock package used to contain gtest.
# Now it is the other way around. This test is written only for the particular
# googletest version we use.
AC_DEFUN([MM_GMOCK], [
   AC_MSG_CHECKING([for googletest source in $1])
   AS_IF([test -f $1/googletest/src/gtest-all.cc],
      [
         AC_MSG_RESULT([yes])
         have_gmock=yes
         GTEST_DIR="$2/googletest"
         GMOCK_DIR="$2/googlemock"
         AC_SUBST([GMOCK_CPPFLAGS],
            ["-isystem $GTEST_DIR/include -isystem $GMOCK_DIR/include"])
      ],
      [
         AC_MSG_RESULT([no])
         have_gmock=no
      ])
])
