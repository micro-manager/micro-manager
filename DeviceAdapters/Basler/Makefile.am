# sma 2019 
# V 2.01

PYLON_ROOT=/opt/pylon5
PYLONLDFLAGS =
PLIBADD =
PLDFLAGS =
PYLONCPPFLAGS =
AM_CXXFLAGS=$(MMDEVAPI_CXXFLAGS) $(CPPFLAGS)
libmmgr_dal_BaslerCamera_la_SOURCES=BaslerPylon5Camera.cpp BaslerPylon5Camera.h
deviceadapter_LTLIBRARIES=libmmgr_dal_BaslerCamera.la



if PYLON64
   PYLONLDFLAGS +=-Wl,--enable-new-dtags -Wl,-rpath,$(PYLON_ROOT)/lib64,-L$(PYLON_ROOT)/lib64
else
   PYLONLDFLAGS +=-Wl,--enable-new-dtags -Wl,-rpath,$(PYLON_ROOT)/lib,-L$(PYLON_ROOT)/lib
endif


if BUILD_BASLER_LINUX

   PYLONCPPFLAGS +=-I$(PYLON_ROOT)/include
   PYLONLDLIBS =-lpylonbase -lpylonutility -lGenApi_gcc_v3_1_Basler_pylon -lGCBase_gcc_v3_1_Basler_pylon

   PLIBADD+=$(MMDEVAPI_LIBADD) $(PYLONLDLIBS)
   PLDFLAGS+=$(MMDEVAPI_LDFLAGS) $(PYLONLDLIBS) $(PYLONLDFLAGS)
endif

if BUILD_BASLER_MAC


PYLON_LIBRARY_DIR = /Library/Frameworks
PYLON_HEADER_MAC = -I${PYLON_LIBRARY_DIR}/pylon.framework/Headers/
GENICAM_HEADER_MAC= -I${PYLON_LIBRARY_DIR}/pylon.framework/Headers/GenICam/
PYLONFRAMEWORKS = "-framework pylon -Wl,-rpath,"
DYLD_LIBRARY_PATH =-F "/Library/Frameworks/pylon.framework/Libraries"
PYLONCPPFLAGSMAC = -F$(PYLON_LIBRARY_DIR)


AM_CXXFLAGS += $(PYLON_HEADER_MAC) $(GENICAM_HEADER_MAC)  $(PYLONCPPFLAGSMAC)
PYLONCPPFLAGS +=   $(PYLON_HEADER_MAC) $(GENICAM_HEADER_MAC)  $(PYLONCPPFLAGSMAC)

PLIBADD += $(MMDEVAPI_LIBADD) 
PLDFLAGS += $(MMDEVAPI_LDFLAGS)  $(PYLONFRAMEWORKS)  $(DYLD_LIBRARY_PATH)


endif

libmmgr_dal_BaslerCamera_la_CPPFLAGS=$(CPPFLAGS) $(PYLONCPPFLAGS)
libmmgr_dal_BaslerCamera_la_LIBADD=$(PLIBADD)
libmmgr_dal_BaslerCamera_la_LDFLAGS=$(PLDFLAGS)

EXTRA_DIST = 




