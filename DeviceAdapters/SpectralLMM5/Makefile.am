AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_SpectralLMM5.la
libmmgr_dal_SpectralLMM5_la_SOURCES = SpectralLMM5.cpp SpectralLMM5.h SpectralLMM5Interface.cpp SpectralLMM5Interface.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_SpectralLMM5_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_SpectralLMM5_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_SpectralLMM5.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_SpectralLMM5$(MMSUFFIX)


EXTRA_DIST =  license.txt
