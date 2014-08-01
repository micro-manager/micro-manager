
AC_PREREQ([2.64])
AC_INIT([Micro-Manager], [1.4], [info@micro-manager.org])
AC_CONFIG_MACRO_DIR([../m4])
AC_CANONICAL_HOST
AM_INIT_AUTOMAKE([foreign 1.11])
LT_PREREQ([2.2.6])
LT_INIT([disable-static])

AC_PROG_CXX([clang++ llvm-g++ g++ c++])
AC_PROG_CC([clang llvm-gcc gcc cc])

AX_BOOST_BASE
AX_BOOST_DATE_TIME

# Checks for typedefs, structures, and compiler characteristics.
AC_HEADER_STDBOOL
AC_C_CONST
AC_C_INLINE

# Checks for library functions.
AC_HEADER_STDC
AC_CHECK_FUNCS([memset])
AC_CHECK_LIB(dl, dlopen) # for dynamic linking on linux


MM_INSTALL_DIRS

# On the Mac we need some frameworks that can be in the way on Linux
case $host in
   *apple-darwin*)
      SERIALFRAMEWORKS="-framework CoreFoundation -framework IOKit"
      AC_MSG_RESULT($SERIALFRAMEWORKS)
      ;;
   *linux*)
      ;;
esac
AC_SUBST(SERIALFRAMEWORKS)


AC_CONFIG_FILES(Makefile CoreTest/Makefile DeviceTest/Makefile LibraryTest/Makefile)


AC_OUTPUT
