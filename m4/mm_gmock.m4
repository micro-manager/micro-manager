# MM_GMOCK(gtest-src-dir, make-gtest-src-dir)
# Checks for GoogleTest >= 1.8, which includes GoogleMock (in 1.7 it was the
# other way around).
# Sets have_gmock to yes or no; if yes, sets the GMOCK_CPPFLAGS substitution
# variable.
AC_DEFUN([MM_GMOCK], [
   AC_MSG_CHECKING([for googletest/googlemock (>= 1.8) source in $1])
   AS_IF([test -f $1/googlemock/src/gmock-all.cc],
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
