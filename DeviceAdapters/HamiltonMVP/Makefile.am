AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_HamiltonMVP.la
libmmgr_dal_HamiltonMVP_la_SOURCES = HamiltonMVP.cpp \
				     MVPCommands.h \
				     MVPValves.h
libmmgr_dal_HamiltonMVP_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_HamiltonMVP_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

if BUILD_CPP_TESTS
UNITTESTS = unittest
endif

SUBDIRS = . $(UNITTESTS)
