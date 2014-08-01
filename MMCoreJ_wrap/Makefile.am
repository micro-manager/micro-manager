
AUTOMAKE_OPTIONS = foreign subdir-objects

# We use -fno-strict-aliasing (which disables certain optimizations in GCC and
# Clang), because SWIG documentation calls for it (when wrapping for Java).
# Strictly speaking, this should only be necessary when compiling
# SWIG-generated code; if our own code changes behavior depending on whether
# this flag is used or not, it should be considered a bug in our code.
#
# (We used to globally use -O by default, rather than -O2, on Linux, presumably
# for the above reason (though the intent was never documented). But there is
# nothing Linux-specific about this.)
# TODO The flag should come from configure.
AM_CXXFLAGS = -fno-strict-aliasing
AM_CPPFLAGS = $(JNI_CPPFLAGS) $(BOOST_CPPFLAGS) -DBOOST_THREAD_VERSION=2


# This ugly list of headers is necessary to trigger the rebuild of the
# SWIG-generated source when a header is modified. The issue cannot be fixed
# here; it is a result of poor implementation hiding in the MMCore headers.
# Unfortunately this list needs to be repeated in MMCorePy_wrap/Makefile.am, so
# don't forget to update that file.
swig_sources = MMCoreJ.i \
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


# Use MMCoreJ.stamp to manage multiple outputs. A fully parallel-safe rule
# would be complex (see Automake manual), but this implementation works most of
# the time and 'make clean' can recover from any problems.
MMCoreJ.stamp: $(swig_sources)
	@rm -f MMCoreJ.tmp
	@touch MMCoreJ.tmp
	$(MKDIR_P) gensrc/mmcorej
	$(SWIG) -c++ -java -package mmcorej -outdir gensrc/mmcorej -module MMCoreJ \
		"-DMMCOREJ_LIBRARY_PATH=\"$(MMCOREJ_LIBRARY_PATH)\"" \
		-o MMCoreJ_wrap.cxx $(srcdir)/MMCoreJ.i
	@mv -f MMCoreJ.tmp $@
MMCoreJ_wrap.h MMCoreJ_wrap.cxx: MMCoreJ.stamp
	@if test -f $@; then :; else \
		rm -f MMCoreJ.stamp; \
		$(MAKE) $(AM_MAKEFLAGS) MMCoreJ.stamp; \
	fi


wrappermodule_LTLIBRARIES = libMMCoreJ_wrap.la
libMMCoreJ_wrap_la_SOURCES = MMCoreJ_wrap.h MMCoreJ_wrap.cxx
libMMCoreJ_wrap_la_LDFLAGS = -module -avoid-version -shrext $(JNI_SUFFIX) -no-undefined
libMMCoreJ_wrap_la_LIBADD = ../MMCore/libMMCore.la


jar_DATA = MMCoreJ.jar

gensrc/mmcorej/TaggedImage.java: TaggedImage.java
	$(MKDIR_P) gensrc/mmcorej
	cp $(srcdir)/TaggedImage.java gensrc/mmcorej

# Use MMCoreJ_wrap.{h,cxx} to ensure SWIG has been run, but use the phony
# target FORCE to always run Ant so that the Java source mtime is checked
MMCoreJ.jar: gensrc/mmcorej/TaggedImage.java MMCoreJ_wrap.h MMCoreJ_wrap.cxx FORCE
	$(ANT) -Dmm.javacflags="$(JAVACFLAGS)" $(ANTFLAGS) -Dsrcdir=gensrc jar

.PHONY: FORCE

FORCE:


if NONSTD_JNI_PREFIX
# The suffix (.so vs .jnilib vs .dll) can be handled by libtool's -shrext flag,
# but there is no clean way to parameterize the prefix in the Automake rules.
# We adjust it at install time instead:

install-exec-hook:
	cd $(DESTDIR)$(jardir); \
	mv -f libMMCoreJ_wrap$(JNI_SUFFIX) $(JNI_PREFIX)MMCoreJ_wrap$(JNI_SUFFIX)

uninstall-hook:
	-rm -f $(DESTDIR)$(jardir)/$(JNI_PREFIX)MMCoreJ_wrap$(JNI_SUFFIX)

endif # NONSTD_JNI_PREFIX


CLEANFILES = MMCoreJ.stamp MMCoreJ_wrap.h MMCoreJ_wrap.cxx

clean-local:
	$(ANT) $(ANTFLAGS) -Dsrcdir=gensrc clean
	-rm -rf gensrc


EXTRA_DIST = license.txt
