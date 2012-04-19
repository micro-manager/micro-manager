## Process this file with automake to produce Makefile.in
AM_CXXFLAGS = $(BOOST_CPPFLAGS)
SWIGLIB_SOURCES = MMCoreJ.i \
	../MMCore/CircularBuffer.h  \
	../MMCore/ConfigGroup.h  \
	../MMCore/Configuration.h \
	../MMCore/CoreCallback.h \
	../MMCore/CoreProperty.h \
	../MMCore/CoreUtils.h \
	../MMCore/Error.h \
	../MMCore/ErrorCodes.h \
	../MMCore/MMCore.h  \
	../MMCore/Host.h  \
	../MMDevice/MMDeviceConstants.h 

MMCoreJ_wrap.cxx: $(SWIGLIB_SOURCES)
	test -d mmcorej || mkdir mmcorej
	touch $(srcdir)/MMCoreJ_wrap.cxx || (chmod +wrx $(srcdir) && touch $(srcdir)/MMCoreJ_wrap.cxx)
	$(SWIG) -c++ -java -package mmcorej -outdir mmcorej -module MMCoreJ \
		-DSWIG_DEVICEADAPTERPATH="\"$(DEVICEADAPTERPATH)\"" \
		-o $(srcdir)/MMCoreJ_wrap.cxx $(srcdir)/MMCoreJ.i 
	cp $(srcdir)/MMCoreJ_wrap.cxx MMCoreJ_wrap.cxx || echo "No problem"
	
INCLUDES = $(JAVAINC)
lib_LTLIBRARIES = libMMCoreJ_wrap.la
nodist_libMMCoreJ_wrap_la_SOURCES = MMCoreJ_wrap.cxx
libMMCoreJ_wrap_la_SOURCES = $(SWIGLIB_SOURCES) \
	$(top_srcdir)/MMDevice/DeviceUtils.cpp \
	$(top_srcdir)/MMDevice/ImgBuffer.cpp \
	$(top_srcdir)/MMDevice/Property.cpp \
	$(top_srcdir)/MMCore/CircularBuffer.cpp \
	$(top_srcdir)/MMCore/Configuration.cpp \
	$(top_srcdir)/MMCore/CoreCallback.cpp \
	$(top_srcdir)/MMCore/CoreProperty.cpp \
	$(top_srcdir)/MMCore/FastLogger.cpp \
	$(top_srcdir)/MMCore/MMCore.cpp \
	$(top_srcdir)/MMCore/PluginManager.cpp \
	$(top_srcdir)/MMCore/Host.cpp
 
#not sure if Boost linking is needed
libMMCoreJ_wrap_la_LDFLAGS = -Wl, -module -ldl $(BOOST_LDFLAGS)

$(srcdir)/MMCoreJ.jar:  MMCoreJ_wrap.cxx \
	libMMCoreJ_wrap.la \
	TaggedImage.java
	cp TaggedImage.java mmcorej/
	$(JAVAC) -source 1.5 -target 1.5 ../mmstudio/src/org/json/*.java mmcorej/*.java
	$(JAR) cvf MMCoreJ.jar mmcorej/*.class

all:
	make $(srcdir)/MMCoreJ_wrap.cxx
	make $(srcdir)/MMCoreJ.jar
	cp MMCoreJ.jar ../bin

macinstall:
	cp .libs/libMMCoreJ_wrap.so $(top_srcdir)/MacInstaller/Package_contents/libMMCoreJ_wrap$(JAVASO)

install:
	install -d $(DESTDIR)$(DEVICEADAPTERPATH)
	install .libs/libMMCoreJ_wrap.so $(DESTDIR)$(DEVICEADAPTERPATH)/libMMCoreJ_wrap$(JAVASO) 
	install -d $(DESTDIR)$(JARPATH)
	install MMCoreJ.jar $(DESTDIR)$(JARPATH)/MMCoreJ.jar 

###IJ-dependent! #todo: split IJ and pure-java code

EXTRA_DIST = license.txt MMCoreJ_wrap.vcproj

clean:
	test -z "libMMCoreJ_wrap.la" || rm -f libMMCoreJ_wrap.la
	rm -rf mmcorej/*.java mmcorej/*.class
	rm -f MMCoreJ_wrap.cxx
	rm -f "./so_locations"
	rm -rf .libs _libs
	rm -f *.o
	rm -f *.lo

dist-hook:
	test -d $(distdir)/mmcorej || mkdir $(distdir)/mmcorej
	touch $(distdir)/mmcorej/empty.txt
