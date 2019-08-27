# sma 26.05.2019 
# File Version  2.11


PYLONLDFLAGS =
PLIBADD =
PLDFLAGS =
PYLONCPPFLAGS =
AM_CXXFLAGS=$(MMDEVAPI_CXXFLAGS) 
libmmgr_dal_BaslerCamera_la_SOURCES=BaslerPylon5Camera.cpp BaslerPylon5Camera.h
deviceadapter_LTLIBRARIES=libmmgr_dal_BaslerCamera.la


if BUILD_BASLER_LINUX
   PYLON_ROOT=/opt/pylon5

if PYLON64
   PYLONLDFLAGS +=-Wl,--enable-new-dtags -Wl,-rpath,$(PYLON_ROOT)/lib64,-L$(PYLON_ROOT)/lib64
else
   PYLONLDFLAGS +=-Wl,--enable-new-dtags -Wl,-rpath,$(PYLON_ROOT)/lib,-L$(PYLON_ROOT)/lib
endif
    
   PYLONCPPFLAGS +=-I$(PYLON_ROOT)/include
   PYLONLDLIBS =-lpylonbase -lpylonutility -lGenApi_gcc_v3_1_Basler_pylon -lGCBase_gcc_v3_1_Basler_pylon

   PLIBADD+=$(MMDEVAPI_LIBADD) $(PYLONLDLIBS)
   PLDFLAGS+=$(MMDEVAPI_LDFLAGS) $(PYLONLDLIBS) $(PYLONLDFLAGS)

else
if BUILD_BASLER_MAC

   LIBRARY_DIR =/Library/Frameworks
   PYLON_LIBRARY_DIR = -F${LIBRARY_DIR}  
   
   GENICAM_HEADER_MAC= -I${LIBRARY_DIR}/pylon.framework/Headers/GenICam/
   PYLON_FRAMEWORKS = -framework pylon
   PYLON_RPATH=$(PYLON_LIBRARY_DIR)  $(PYLON_FRAMEWORKS)  -Wl,-rpath,$(LIBRARY_DIR)
   PLIBADD += $(MMDEVAPI_LIBADD)  

  PYLONCPPFLAGS +=   $(GENICAM_HEADER_MAC)  $(PYLON_LIBRARY_DIR)  $(PYLON_FRAMEWORKS)
  PLDFLAGS += $(MMDEVAPI_LDFLAGS)   $(PYLON_RPATH) 
endif
endif

libmmgr_dal_BaslerCamera_la_CPPFLAGS= $(PYLONCPPFLAGS)
libmmgr_dal_BaslerCamera_la_LIBADD=$(PLIBADD)  
libmmgr_dal_BaslerCamera_la_LDFLAGS=$(PLDFLAGS)

EXTRA_DIST = 




