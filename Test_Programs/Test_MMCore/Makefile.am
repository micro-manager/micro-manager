## Process this file with automake to produce Makefile.in
bin_PROGRAMS = mm_testCore
mm_testCore_SOURCES = Test_MMCore_Mac.cpp 
mm_testCore_LDADD = ../../MMCore/libMMCore.a ../../MMDevice/.libs/libMMDevice.a $(LPTHREAD) -ldl $(BOOST_LDFLAGS) $(BOOST_DATE_TIME_LIB) -ltiff 

EXTRA_DIST = Test_MMCore.vcproj Test_MMCore.sln Test_MMCore.cpp

install:
	echo "Nothing to do"
#	cp mm_testCore $(IJPATH)/mm_testCore

