
# MM_ARG_WITH_ANT (no args)
# Sets want_ant to yes or no; sets ANTCMD to empty or path.
AC_DEFUN([MM_ARG_WITH_ANT], [
   AC_MSG_CHECKING([if use of Ant was requested])
   AC_ARG_WITH([ant], [AS_HELP_STRING([--with-ant=[[yes|no|CMD]]],
               [use Ant command at CMD (default: auto)])],
      [],
      [with_ant=auto])

   case $with_ant in
      yes | no | auto) want_ant="$with_ant" ;;
      *) ANTCMD="$want_ant"
         want_ant=yes ;;
   esac
   AS_IF([test -n "$ANTCMD"],
      [AC_MSG_RESULT([yes ($ANTCMD)])],
      [AC_MSG_RESULT([$want_ant])])
])
