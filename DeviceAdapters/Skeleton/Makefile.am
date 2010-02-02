AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Skeleton.la
Skeleton_la_SOURCES = Skeleton.cpp Skeleton.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
Skeleton__la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
Skeleton_la_LDFLAGS = -module

install:
	cp .libs/Skeleton.so $(DESTDIR)$(DEVICEADAPTERPATH)/$(MMPREFIX)Skeleton$(MMSUFFIX)

EXTRA_DIST = license.txt
