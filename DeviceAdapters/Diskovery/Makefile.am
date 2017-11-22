AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_Diskovery.la

libmmgr_dal_Diskovery_la_SOURCES = \
				   BlockingQueue.h \
				   Diskovery.cpp \
				   Diskovery.h \
				   DiskoveryCommander.cpp \
				   DiskoveryListener.cpp \
				   DiskoveryModel.cpp \
				   DiskoveryModel.h \
				   MessageSender.cpp

libmmgr_dal_Diskovery_la_LIBADD = $(MMDEVAPI_LIBADD) $(BOOST_SYSTEM_LIB) $(BOOST_THREAD_LIB)
libmmgr_dal_Diskovery_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = license.txt
