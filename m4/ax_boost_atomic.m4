# ===========================================================================
#       http://www.gnu.org/software/autoconf-archive/ax_boost_atomic.html
# ===========================================================================
#
# SYNOPSIS
#
#   AX_BOOST_ATOMIC

# DESCRIPTION
#
#   Test for Atomic library from the Boost C++ libraries. The macro requires a
#   preceding call to AX_BOOST_BASE. Further documentation is available at
#   <http://randspringer.de/boost/index.html>.
#
#   This macro calls:
#
#     AC_SUBST(BOOST_ATOMIC_LIB)
#
#   And sets:
#
#     HAVE_BOOST_ATOMIC
#
# LICENSE
#
#   Copyright (c) 2008 Thomas Porschberg <thomas@randspringer.de>
#   Copyright (c) 2008 Pete Greenwell <pete@mu.org>
#
#   Copying and distribution of this file, with or without modification, are
#   permitted in any medium without royalty provided the copyright notice
#   and this notice are preserved. This file is offered as-is, without any
#   warranty.

#serial 16

AC_DEFUN([AX_BOOST_ATOMIC],
[
	AC_ARG_WITH([boost-atomic],
	AS_HELP_STRING([--with-boost-atomic@<:@=special-lib@:>@],
                   [use the ATOMIC library from boost - it is possible to specify a certain library for the linker
                        e.g. --with-boost-atomic=boost_system-gcc41-mt-1_34 ]),
        [
        if test "$withval" = "no"; then
			want_boost="no"
        elif test "$withval" = "yes"; then
            want_boost="yes"
            ax_boost_user_atomic_lib=""
        else
		    want_boost="yes"
		ax_boost_user_atomic_lib="$withval"
		fi
        ],
        [want_boost="yes"]
	)

	if test "x$want_boost" = "xyes"; then
        AC_REQUIRE([AC_PROG_CC])
		CPPFLAGS_SAVED="$CPPFLAGS"
		CPPFLAGS="$CPPFLAGS $BOOST_CPPFLAGS"
		export CPPFLAGS

		LDFLAGS_SAVED="$LDFLAGS"
		LDFLAGS="$LDFLAGS $BOOST_LDFLAGS"
		export LDFLAGS

        AC_CACHE_CHECK(whether the Boost::ATOMIC library is available,
					   ax_cv_boost_atomic,
        [AC_LANG_PUSH([C++])
		 AC_COMPILE_IFELSE([AC_LANG_PROGRAM([[ @%:@include <boost/atomic/atomic.hpp>
											]],
                                  [[

                                    boost::atomic<int> t;
									return 0;
                                   ]])],
                             ax_cv_boost_atomic=yes, ax_cv_boost_atomic=no)
         AC_LANG_POP([C++])
		])
		if test "x$ax_cv_boost_atomic" = "xyes"; then
			AC_DEFINE(HAVE_BOOST_ATOMIC,,[define if the Boost::ATOMIC library is available])
			BN=boost_system
			BOOSTLIBDIR=`echo $BOOST_LDFLAGS | sed -e 's/@<:@^\/@:>@*//'`
            if test "x$ax_boost_user_atomic_lib" = "x"; then
				for ax_lib in `ls $BOOSTLIBDIR/libboost_atomic*.so* $BOOSTLIBDIR/libboost_atomic*.dylib* $BOOSTLIBDIR/libboost_atomic*.a* 2>/dev/null | sed 's,.*/,,' | sed -e 's;^lib\(boost_atomic.*\)\.so.*$;\1;' -e 's;^lib\(boost_atomic.*\)\.dylib.*$;\1;' -e 's;^lib\(boost_atomic.*\)\.a.*$;\1;' ` ; do
				    AC_CHECK_LIB($ax_lib, main, [BOOST_ATOMIC_LIB="-l$ax_lib" AC_SUBST(BOOST_ATOMIC_LIB) link_thread="yes" break],
                                 [link_thread="no"])
				done
            else
               for ax_lib in $ax_boost_user_atomic_lib $BN-$ax_boost_user_atomic_lib; do
				      AC_CHECK_LIB($ax_lib, main,
                                   [BOOST_ATOMIC_LIB="-l$ax_lib" AC_SUBST(BOOST_ATOMIC_LIB) link_atomic="yes" break],
                                   [link_atomic="no"])
                  done

            fi
            if test "x$ax_lib" = "x"; then
                AC_MSG_ERROR(Could not find a version of the library!)
            fi
			if test "x$link_atomic" = "xno"; then
				AC_MSG_ERROR(Could not link against $ax_lib !)
			fi
		fi

		CPPFLAGS="$CPPFLAGS_SAVED"
	LDFLAGS="$LDFLAGS_SAVED"
	fi
])
