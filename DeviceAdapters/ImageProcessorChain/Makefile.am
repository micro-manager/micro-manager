AM_CXXFLAGS = -fpermissive -fweak $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_ImageProcessorChain.la
libmmgr_dal_ImageProcessorChain_la_SOURCES = ImageProcessorChain.cpp ImageProcessorChain.h ../../MMDevice/MMDevice.h
libmmgr_dal_ImageProcessorChain_la_LDFLAGS = -module 
libmmgr_dal_ImageProcessorChain_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install:
	cp .libs/libmmgr_dal_ImageProcessorChain.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ImageProcessorChain$(MMSUFFIX)

EXTRA_DIST = ImageProcessorChain.vcproj license.txt
