AM_CXXFLAGS = -fpermissive $(BOOST_CPPFLAGS) $(LIBFREEIMAGE_CXXFLAGS)
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
libmmgr_dal_GPhoto_la_LIBADD += $(LIBGPHOTO2) -lltdl

install:
	cp .libs/libmmgr_dal_GPhoto.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)
	test -d $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2 || mkdir $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2
	test -d $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2 || mkdir $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2
	test -d $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port || mkdir $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port
	rm -rf $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2/*
	rm -rf $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port/*
	cp $(GPHOTO2LIBDIR)/libgphoto2/$(GPHOTO2_VERSION)/*.so $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2/
	cp $(GPHOTO2LIBDIR)/libgphoto2_port/$(GPHOTO2_PORT_VERSION)/*.so $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port/
#install_name_tool -change $(GPHOTO2LIBDIR)/libgphoto2.2.dylib @loader_path/libgphoto2/libgphoto2.2.dylib $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)
#install_name_tool -change $(GPHOTO2LIBDIR)/libgphoto2_port.0.dylib @loader_path/libgphoto2/libgphoto2_port.0.dylib $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)




EXTRA_DIST = license.txt
