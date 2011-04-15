AM_CXXFLAGS = -fpermissive $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_GPhoto.la
libmmgr_dal_GPhoto_la_SOURCES = CameraFrontend.cpp CameraFrontend.h 
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_GPhoto_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_GPhoto_la_LDFLAGS = -module

# FreeImage library
libmmgr_dal_GPhoto_la_LIBADD += $(LIBFREEIMAGE)

# gphoto2 library
AM_CXXFLAGS += -Igphoto 
libmmgr_dal_GPhoto_la_SOURCES += gphoto/SimpleCam.cpp gphoto/SimpleCam.h
#libmmgr_dal_GPhoto_la_LIBADD += $(LIBGPHOTO2)
libmmgr_dal_GPhoto_la_LIBADD += /opt/local/lib/libgphoto2.a /opt/local/lib/libgphoto2_port.a /usr/lib/libltdl.la -ldl -lpthread -lm

install:
	cp .libs/libmmgr_dal_GPhoto.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)

EXTRA_DIST = license.txt
