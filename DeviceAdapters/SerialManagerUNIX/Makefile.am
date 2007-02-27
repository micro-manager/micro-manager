AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = SerialManager.la
SerialManager_la_SOURCES = SerialManager.cpp SerialManager.h \
         SerialPort.cpp SerialPort.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
SerialManager_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
SerialManager_la_LDFLAGS = -module $(SERIALFRAMEWORKS)

install:
	cp .libs/SerialManager.so $(IJPATH)/$(MMPREFIX)SerialManager

EXTRA_DIST = license.txt
