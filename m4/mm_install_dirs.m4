AC_DEFUN([MM_INSTALL_DIRS], [

AC_ARG_ENABLE([imagej-plugin],
   [AS_HELP_STRING([--enable-imagej-plugin=IMAGEJDIR],
      [Build for installation as plugin for ImageJ at IMAGEJDIR.])],
   [], [enable_imagej_plugin=no])
case $enable_imagej_plugin in
   yes) AC_MSG_ERROR([--enable-imagej-plugin requires argument (ImageJ directory)]) ;;
   no)  ;;
   *)   imagejdir="$enable_imagej_plugin";;
esac
AC_SUBST([imagejdir])
AM_CONDITIONAL([INSTALL_AS_IMAGEJ_PLUGIN], [test -n "$imagejdir"])


# Set install paths. Note that we entirely avoid using the normal *dir
# variables (bindir, pkgdatadir, etc.) in our makefiles, so that we can
# accomodate the two modes of installation (traditional vs ImageJ plugin).
# However, when --enable-imagej-plugin is _not_ used, we do want to preserve
# the ability to run e.g. 'make install pkglibdir=/foo/bar', so we set all
# install location variables to depend on the make-time value of the normal
# *dir variables.
AM_COND_IF([INSTALL_AS_IMAGEJ_PLUGIN],
[
   # ImageJ config

   wrappermoduledir="\$(imagejdir)"
   deviceadapterdir="\$(imagejdir)"
   mmdatadir="\$(imagejdir)"
   jardir="\$(imagejdir)/plugins/Micro-Manager"
   mmplugindir="\$(imagejdir)/mmplugins"
   mmautofocusdir="\$(imagejdir)/mmautofocus"
   mmscriptdir="\$(imagejdir)/scripts"
   launcherdir="\$(imagejdir)"
],
[
   # Traditional config

   wrappermoduledir="\$(pkglibdir)"
   deviceadapterdir="\$(pkglibdir)"
   mmdatadir="\$(pkgdatadir)"
   jardir="\$(pkgdatadir)/jars"
   mmplugindir="\$(pkgdatadir)/mmplugins"
   mmautofocusdir="\$(pkgdatadir)/mmautofocus"
   mmscriptdir="\$(pkgdatadir)/scripts"
   launcherdir="\$(bindir)"
])
# Native libraries for language wrappers
AC_SUBST([wrappermoduledir])
# Device adapters
AC_SUBST([deviceadapterdir])
# Data files (e.g. demo config)
AC_SUBST([mmdatadir])
# Non-plugin JARs
AC_SUBST([jardir])
# Plugin JARs
AC_SUBST([mmplugindir])
# Autofocus plugin JARs
AC_SUBST([mmautofocusdir])
# Beanshell scripts
AC_SUBST([mmscriptdir])
# Launch script
AC_SUBST([launcherdir])

])
