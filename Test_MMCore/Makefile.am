## Process this file with automake to produce Makefile.in
bin_PROGRAMS = testCore
testCore_LDADD = $(LIBACE)
testCore_SOURCES = Test_MMCore_Mac.cpp \
   $(top_srcdir)/MMDevice/DeviceUtils.cpp \
   $(top_srcdir)/MMDevice/ImgBuffer.cpp \
   $(top_srcdir)/MMDevice/ModuleInterface.cpp \
   $(top_srcdir)/MMDevice/Property.cpp \
   $(top_srcdir)/MMCore/Configuration.cpp \
   $(top_srcdir)/MMCore/CoreProperty.cpp \
   $(top_srcdir)/MMCore/MMCore.cpp \
   $(top_srcdir)/MMCore/PluginManager.cpp

EXTRA_DIST = Test_MMCore.vcproj Test_MMCore_vc8.vcproj Test_MMCore.sln Test_MMCore_vc8.sln  Test_MMCore.cpp

install:
	cp testCore $(IJPATH)/testCore

