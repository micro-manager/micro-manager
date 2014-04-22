
AUTOMAKE_OPTIONS = foreign subdir-objects

AM_CPPFLAGS = $(BOOST_CPPFLAGS) $(ZLIB_CPPFLAGS)
AM_CXXFLAGS = $(ZLIB_CFLAGS)
AM_LDFLAGS = $(BOOST_LDFLAGS) $(ZLIB_LDFLAGS) $(MMCORE_APPLEHOST_LDFLAGS)

noinst_LTLIBRARIES = libMMCore.la

# libMMCore currently requires libMMDevice, due to use of CDeviceUtils,
# ImgBuffer, and FrameBuffer. This dependency should probably be removed.
libMMCore_la_LIBADD = $(BOOST_SYSTEM_LIB) $(BOOST_DATE_TIME_LIB) $(ZLIB_LIBS) ../MMDevice/libMMDevice.la

libMMCore_la_SOURCES = \
       ../MMDevice/MMDevice.h \
       ../MMDevice/MMDeviceConstants.h \
       ../MMDevice/ModuleInterface.h \
       AppleHost.h \
       CircularBuffer.cpp \
       CircularBuffer.h \
       Compressor.h \
       ConfigGroup.h \
       Configuration.cpp \
       Configuration.h \
       CoreCallback.cpp \
       CoreCallback.h \
       CoreProperty.cpp \
       CoreProperty.h \
       CoreUtils.h \
       Error.cpp \
       Error.h \
       ErrorCodes.h \
       FastLogger.cpp \
       FastLogger.h \
       Host.cpp \
       Host.h \
       IMMLogger.h \
       LibraryInfo/LibraryPaths.h \
       LibraryInfo/LibraryPathsUnix.cpp \
       LoadableModules/LoadedDeviceAdapter.cpp \
       LoadableModules/LoadedDeviceAdapter.h \
       LoadableModules/LoadedModule.cpp \
       LoadableModules/LoadedModule.h \
       LoadableModules/LoadedModuleImpl.cpp \
       LoadableModules/LoadedModuleImpl.h \
       LoadableModules/LoadedModuleImplUnix.cpp \
       LoadableModules/LoadedModuleImplUnix.h \
       MMCore.cpp \
       MMCore.h \
       PluginManager.cpp \
       PluginManager.h

EXTRA_DIST = license.txt
