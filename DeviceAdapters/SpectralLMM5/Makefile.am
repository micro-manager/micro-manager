AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = SpectralLMM5.la
SpectralLMM5_la_SOURCES = SpectralLMM5.cpp SpectralLMM5.h SpectralLMM5Interface.cpp SpectralLMM5Interface.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
SpectralLMM5_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
SpectralLMM5_la_LDFLAGS = -module

install:
	cp .libs/SpectralLMM5.so $(IJPATH)/$(MMPREFIX)SpectralLMM5

EXTRA_DIST =  license.txt
