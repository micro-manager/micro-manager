AM_CXXFLAGS = -fpermissive 
lib_LTLIBRARIES = CSU22.la
CSU22_la_SOURCES = CSU22.cpp CSU22.h CSU22Hub.h CSU22Hub.cpp
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
CSU22_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
CSU22_la_LDFLAGS = -module

install:
	cp .libs/CSU22.so $(IJPATH)/$(MMPREFIX)CSU22
