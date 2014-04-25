
AUTOMAKE_OPTIONS = foreign subdir-objects

AM_CPPFLAGS = $(GPHOTO2_CPPFLAGS) $(FREEIMAGEPLUS_CPPFLAGS)
# TODO Fix CameraFrontend.cpp so that it compiles with g++ -fno-permissive.
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(GPHOTO2_CFLAGS) $(FREEIMAGEPLUS_CXXFLAGS) -fpermissive
# TODO -framework CoreFoundation (used by gphoto/SimpleCam.cpp) should be Apple-only
AM_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(GPHOTO2_LDFLAGS) $(FREEIMAGEPLUS_LDFLAGS) -framework CoreFoundation

deviceadapter_LTLIBRARIES = libmmgr_dal_GPhoto.la
libmmgr_dal_GPhoto_la_CPPFLAGS = $(AM_CPPFLAGS) -I$(srcdir)/gphoto
libmmgr_dal_GPhoto_la_SOURCES = \
	CameraFrontend.cpp \
	CameraFrontend.h \
	gphoto/SimpleCam.cpp \
	gphoto/SimpleCam.h
libmmgr_dal_GPhoto_la_LIBADD = $(MMDEVAPI_LIBADD) $(GPHOTO2_LIBS) $(FREEIMAGEPLUS_LIBS)

EXTRA_DIST = license.txt

# TODO On Mac OS X, create an AppleScript app to quit PTPCamera.
# This used to be done as part of the 'install' target.
# osacompile -e 'tell application "PTPCamera" to quit' -o $(DEVICEADAPTERPATH)/libgphoto2/KillPtpCamera.app
