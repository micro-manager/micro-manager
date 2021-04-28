PVCAM_CPPFLAGS = -I$(PVCAM_INC_DIR)
PVCAM_LDFLAGS = -L$(PVCAM_LIB_DIR)
PVCAM_LIBS = -lpvcam

# To allow building with outdated versions of Boost (present by default on many
# Linux distributions), use the older Boost.Thread interface, even though it
# differs more from C++11. Another way around this might be to use G++'s C++11
# libraries where possible.
AM_CPPFLAGS = $(BOOST_CPPFLAGS) -DBOOST_THREAD_VERSION=2 $(PVCAM_CPPFLAGS)

AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)

deviceadapter_LTLIBRARIES = libmmgr_dal_PVCAM.la

libmmgr_dal_PVCAM_la_SOURCES = \
                            AcqConfig.cpp \
                            AcqConfig.h \
                            AcqThread.cpp \
                            AcqThread.h \
                            Event.cpp \
                            Event.h \
                            NotificationEntry.cpp \
                            NotificationEntry.h \
                            NotificationThread.cpp \
                            NotificationThread.h \
                            PollingThread.cpp \
                            PollingThread.h \
                            PpParam.cpp \
                            PpParam.h \
                            PVCAMAdapter.cpp \
                            PVCAMAdapter.h \
                            PVCAMIncludes.h \
                            PVCAMParam.cpp \
                            PVCAMParam.h \
                            PVCAMUniversal.cpp \
                            PvCircularBuffer.cpp \
                            PvCircularBuffer.h \
                            PvDebayer.cpp \
                            PvDebayer.h \
                            PvFrameInfo.cpp \
                            PvFrameInfo.h \
                            PvRoi.h \
                            PvRoiCollection.cpp \
                            PvRoiCollection.h \
                            Semaphore.cpp \
                            Semaphore.h \
                            StreamWriter.cpp \
                            StreamWriter.h \
                            Task.cpp \
                            Task.h \
                            TaskSet.cpp \
                            TaskSet.h \
                            TaskSet_CopyMemory.cpp \
                            TaskSet_CopyMemory.h \
                            ThreadPool.cpp \
                            ThreadPool.h \
                            Version.h

libmmgr_dal_PVCAM_la_LIBADD = $(MMDEVAPI_LIBADD) \
                            $(BOOST_THREAD_LIB) \
                            $(BOOST_SYSTEM_LIB) \
                            $(BOOST_FILESYSTEM_LIB) \
                            $(PVCAM_LIBS)

libmmgr_dal_PVCAM_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) \
                            $(BOOST_LDFLAGS) \
                            $(PVCAM_LDFLAGS)

EXTRA_DIST = PVCAM.vcproj license.txt
