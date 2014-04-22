
# MM_ARG_WITH_PYTHON  (no args)
# Sets want_python to yes or no; PYTHON_PREFIX to empty or path.
AC_DEFUN([MM_ARG_WITH_PYTHON], [
   AC_MSG_CHECKING([if Python support was requested])
   AC_ARG_WITH([python], [AS_HELP_STRING([--with-python=[[yes|no|DIR]]],
               [use Python in DIR (default: auto)])],
      [],
      [with_python=auto])

   case $with_python in
      yes | no | auto) want_python="$with_python" ;;
      *) PYTHON_PREFIX="$with_python"
         want_python=yes ;;
   esac
   AS_IF([test -n "$PYTHON_PREFIX"],
      [AC_MSG_RESULT([yes ($PYTHON_PREFIX)])],
      [AC_MSG_RESULT([$want_python])])
])


# MM_PROG_PYTHON([search-path])
# If search-path not given, search current path. Use colons to separate
# directories in search-path. Set precious variable PYTHON. If PYTHON has
# already been set, do not perform search.
AC_DEFUN([MM_PROG_PYTHON],
[
   AC_ARG_VAR([PYTHON], [Python interpreter])
   AC_MSG_CHECKING([for user-specified Python interpreter])
   AS_IF([test -n "$PYTHON"],
   [
      AC_MSG_RESULT([yes ($PYTHON)])
   ],
   [
      AC_MSG_RESULT([no; will search for one])

      AC_PATH_PROGS([PYTHON], [python python3 python2], [], [$1])
      AS_IF([test -n "$PYTHON"],
      [
         AC_MSG_CHECKING([if $PYTHON works])
         mm_python_test_output="`$PYTHON -c 'import sys; sys.stdout.write("HelloWorld")' 2>&AS_MESSAGE_LOG_FD`"
         AS_IF([test "x$mm_python_test_output" = "xHelloWorld"],
         [
            AC_MSG_RESULT([yes])
         ],
         [
            AC_MSG_RESULT([no])
            PYTHON=
         ])
      ])
   ])
])


# MM_HEADERS_PYTHON([action-if-found], [action-if-not-found])
# Set precious variable PYTHON_CPPFLAGS
AC_DEFUN([MM_HEADERS_PYTHON], [
   _MM_PYTHON_CPPFLAGS([PYTHON], [Python],
      ['import distutils.sysconfig as dusc, sys; sys.stdout.write(dusc.get_python_inc(True))'],
      [Python.h],
      [$1], [$2])
])


# MM_HEADERS_NUMPY([action-if-found], [action-if-not-found])
# Set precious variable NUMPY_CPPFLAGS
AC_DEFUN([MM_HEADERS_NUMPY], [
   # Push state
   mm_python_numpy_python_old_cppflags="$CPPFLAGS"
   CPPFLAGS="$PYTHON_CPPFLAGS $mm_python_numpy_python_old_cppflags"
   # End push state

   _MM_PYTHON_CPPFLAGS([NUMPY], [NumPy],
      ['import numpy.distutils, sys; sys.stdout.write(" ".join(numpy.distutils.misc_util.get_numpy_include_dirs()))'],
      [numpy/arrayobject.h],
      [$1], [$2])

   # Pop state
   CPPFLAGS="$mm_python_numpy_python_old_cppflags"
   # End pop state
])


# _MM_PYTHON_CPPFLAGS(var-prefix, name, py-script, header, [action-if-found], [action-if-not-found])
AC_DEFUN([_MM_PYTHON_CPPFLAGS], [
   AC_ARG_VAR([$1_CPPFLAGS], [preprocessor flags for $2])
   AC_MSG_CHECKING([for user-provided configuration for $2])
   AS_IF([test -n "$$1_CPPFLAGS"],
   [
      AC_MSG_RESULT([yes; will skip checks])
   ],
   [
      AC_MSG_RESULT([no; will perform availability check])

      AC_MSG_CHECKING([for $2 include path])
      mm_python_$1_incdirs="`$PYTHON -c $3 2>&AS_MESSAGE_LOG_FD`"
      if test "x$?" != x0; then
         mm_python_$1_incdirs=
      fi
      AS_IF([test -n "$mm_python_$1_incdirs"],
      [
         AC_MSG_RESULT([$mm_python_$1_incdirs])
         $1_CPPFLAGS=
         for mm_python_directory in $mm_python_$1_incdirs
         do
            $1_CPPFLAGS="$$1_CPPFLAGS -I$mm_python_directory"
         done

         dnl TODO Factor out state-pushing header check as MM_CHECK_HEADER
         dnl (with the ability to specify prerequisite code).

         # Push state
         AC_LANG_PUSH([C])
         mm_python_$1_old_cppflags="$CPPFLAGS"
         CPPFLAGS="$$1_CPPFLAGS $mm_python_$1_old_cppflags"
         # End push state

         dnl Warning: Using AC_CHECK_HEADER would be incorrect if we were to be
         dnl checking for the same header under different conditions.
         AC_CHECK_HEADER([$4], [], [$1_CPPFLAGS=], [#include <Python.h>])

         # Pop state
         CPPFLAGS="$mm_python_$1_old_cppflags"
         AC_LANG_POP([C])
      ],
      [
         AC_MSG_RESULT([no])
      ])
   ])
   AS_IF([test -n "$$1_CPPFLAGS"], [$5], [$6])
])
