
# This ugly list of headers is necessary to trigger the rebuild of the
# SWIG-generated source when a header is modified. The issue cannot be fixed
# here; it is a result of poor implementation hiding in the MMCore headers.
# Unfortunately this list needs to be repeated in MMCoreJ_wrap/Makefile.am, so
# don't forget to update that file.
swig_sources = MMCorePy.i \
	../MMCore/CircularBuffer.h  \
	../MMCore/ConfigGroup.h  \
	../MMCore/Configuration.h \
	../MMCore/CoreCallback.h \
	../MMCore/CoreProperty.h \
	../MMCore/CoreUtils.h \
	../MMCore/Error.h \
	../MMCore/ErrorCodes.h \
	../MMCore/Host.h  \
	../MMCore/MMCore.h  \
	../MMCore/MMEventCallback.h \
	../MMCore/PluginManager.h \
	../MMDevice/ImageMetadata.h \
	../MMDevice/MMDevice.h \
	../MMDevice/MMDeviceConstants.h


# Use MMCorePy.stamp to manage multiple outputs. A fully parallel-safe rule
# would be complex (see Automake manual), but this implementation works most of
# the time and 'make clean' can recover from any problems.
MMCorePy.stamp: $(swig_sources)
	@rm -f MMCorePy.tmp
	@touch MMCorePy.tmp
	$(SWIG) -c++ -python -outdir . -o MMCorePy_wrap.cxx $(srcdir)/MMCorePy.i
	@mv -f MMCorePy.tmp $@
MMCorePy_wrap.h MMCorePy_wrap.cxx MMCorePy.py: MMCorePy.stamp
	@if test -f $@; then :; else \
		rm -f MMCorePy.stamp; \
		$(MAKE) $(AM_MAKEFLAGS) MMCorePy.stamp; \
	fi


wrappermodule_LTLIBRARIES = _MMCorePy.la
nodist__MMCorePy_la_SOURCES = MMCorePy_wrap.h MMCorePy_wrap.cxx
_MMCorePy_la_CPPFLAGS = $(BOOST_CPPFLAGS) $(PYTHON_CPPFLAGS) $(NUMPY_CPPFLAGS) -DBOOST_THREAD_VERSION=2
_MMCorePy_la_LDFLAGS = -module -avoid-version -shrext .so
_MMCorePy_la_LIBADD = ../MMCore/libMMCore.la

install-exec-hook:
	$(MKDIR_P) $(DESTDIR)/$(wrappermoduledir)
	$(INSTALL_DATA) MMCorePy.py $(DESTDIR)$(wrappermoduledir)

uninstall-hook:
	rm -f $(DESTDIR)$(wrappermoduledir)/MMCorePy.py

CLEANFILES = MMCorePy.stamp MMCorePy.py MMCorePy_wrap.h MMCorePy_wrap.cxx

EXTRA_DIST = license.txt
