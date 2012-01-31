#######################################################
#
# check for OpenCV library
#
# OPENCV_LDFLAGS="$OPENCV_LDFLAGS -lopencv_video -lopencv_core -lopencv_highgui"
#######################################################
AC_DEFUN([AX_OPENCV],
[
AC_ARG_WITH([opencv],
            AS_HELP_STRING([--with-opencv=DIR],
                           [specify the root directory for the OpenCV library]),
            [
                if test "$withval" = "no"; then
                    want_opencv="no"
                elif test "$withval" = "yes"; then
                    want_opencv="yes"
                    ac_opencv_path=""
                else
                    want_opencv="yes"
                    ac_opencv_path="$withval"
                fi
            ],
            [want_opencv="yes"]
)

succeeded=no
if test "x$want_opencv" = "xyes"; then
    # AC_CHECK_LIB([cv], [cvSub])
    # AC_CHECK_LIB([highgui], [main])
   PKG_CHECK_MODULES([OPENCV], [opencv], [hasopencv=true], [hasopencv=false])
   if test x$hasopencv = xtrue; then
      OPENCV_CPPFLAGS="$OPENCV_CFLAGS"
      OPENCV_LDFLAGS="$OPENCV_LIBS"
      AC_SUBST(OPENCV_CPPFLAGS)
      AC_SUBST(OPENCV_LDFLAGS)
   else
      AC_MSG_CHECKING(for OpenCV)
      AC_REQUIRE([AC_PROG_CC])
      if test "$ac_opencv_path" != ""; then
          if test -d "$ac_opencv_path/include/opencv" && test -r "$ac_opencv_path/include/opencv"; then
             OPENCV_LDFLAGS="-L$ac_opencv_path/lib"
             OPENCV_CPPFLAGS="-I$ac_opencv_path/include"
             succeeded=yes
          fi
      else
          for ac_opencv_path_tmp in /usr /usr/local /opt ; do
              if test -d "$ac_opencv_path_tmp/include/opencv" && test -r "$ac_opencv_path_tmp/include/opencv"; then
                  OPENCV_LDFLAGS="-L$ac_opencv_path_tmp/lib"
                  OPENCV_CPPFLAGS="-I$ac_opencv_path_tmp/include"
                  succeeded=yes
                  break;
              fi
          done
      fi
      if test "$succeeded" = "yes"; then
         hasopencv=true
         AC_MSG_RESULT(yes)
         OPENCV_LDFLAGS="$OPENCV_LDFLAGS -lopencv_core -lopencv_highgui"
         AC_SUBST(OPENCV_CPPFLAGS)
         AC_SUBST(OPENCV_LDFLAGS)
         AC_DEFINE(HAVE_OPENCV,[true],[define if the OpenCV library is available])
      else
         AC_MSG_RESULT(no)
      fi
   fi
fi

])
