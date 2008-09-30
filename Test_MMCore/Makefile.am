## Process this file with automake to produce Makefile.in
bin_PROGRAMS = mm_testCore
mm_testCore_LDADD = ../MMCore/libMMCore.a ../MMDevice/.libs/libMMDevice.a $(LIBACE) $(LPTHREAD)
mm_testCore_SOURCES = Test_MMCore_Mac.cpp 

EXTRA_DIST = Test_MMCore.vcproj Test_MMCore.sln Test_MMCore.cpp

#install:
#	cp mm_testCore $(IJPATH)/mm_testCore

