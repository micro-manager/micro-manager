# sma 2019 

PYLON_ROOT=/opt/pylon5


if PYLON64
PYLONLDFLAGS=-Wl,--enable-new-dtags -Wl,-rpath,/opt/pylon5/lib64,-L/opt/pylon5/lib64
else
PYLONLDFLAGS=-Wl,--enable-new-dtags -Wl,-rpath,/opt/pylon5/lib,-L/opt/pylon5/lib
endif



PYLONCPPFLAGS=-I$(PYLON_ROOT)/include
PYLONLDLIBS=-lpylonbase -lpylonutility -lGenApi_gcc_v3_1_Basler_pylon -lGCBase_gcc_v3_1_Basler_pylon

#PYLONCPPFLAGS   := $(shell $(PYLON_ROOT)/bin/pylon-config --cflags)
#PYLONLDFLAGS    := $(shell $(PYLON_ROOT)/bin/pylon-config --libs-rpath)
#PYLONLDLIBS     := $(shell $(PYLON_ROOT)/bin/pylon-config --libs)
AM_CXXFLAGS=$(MMDEVAPI_CXXFLAGS) $(CPPFLAGS)

deviceadapter_LTLIBRARIES=libmmgr_dal_BaslerCamera.la
libmmgr_dal_BaslerCamera_la_CPPFLAGS=$(CPPFLAGS) $(PYLONCPPFLAGS)
libmmgr_dal_BaslerCamera_la_SOURCES=BaslerPylon5Camera.cpp BaslerPylon5Camera.h
libmmgr_dal_BaslerCamera_la_LIBADD=$(MMDEVAPI_LIBADD) $(PYLONLDLIBS)
libmmgr_dal_BaslerCamera_la_LDFLAGS=$(MMDEVAPI_LDFLAGS) $(PYLONLDLIBS) $(PYLONLDFLAGS)

EXTRA_DIST = 
