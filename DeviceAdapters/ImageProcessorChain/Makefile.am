pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive -fweak $(BOOST_CPPFLAGS)
pkglib_LTLIBRARIES = libmmgr_dal_ImageProcessorChain.la
libmmgr_dal_ImageProcessorChain_la_SOURCES = ImageProcessorChain.cpp ImageProcessorChain.h ../../MMDevice/MMDevice.h
libmmgr_dal_ImageProcessorChain_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) 
libmmgr_dal_ImageProcessorChain_la_LIBADD = $(MMDEVAPI_LIBADD)

EXTRA_DIST = ImageProcessorChain.vcproj license.txt
