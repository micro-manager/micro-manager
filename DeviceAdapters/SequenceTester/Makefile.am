# To allow building with outdated versions of Boost (present by default on many
# Linux distributions), use the older Boost.Thread interface, even though it
# differs more from C++11. Another way around this might be to use G++'s C++11
# libraries where possible.
AM_CPPFLAGS = $(BOOST_CPPFLAGS) -DBOOST_THREAD_VERSION=2 $(MSGPACK_CPPFLAGS)

AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(MSGPACK_CXXFLAGS)

deviceadapter_LTLIBRARIES = libmmgr_dal_SequenceTester.la

libmmgr_dal_SequenceTester_la_SOURCES = \
					InterDevice.cpp \
					InterDevice.h \
					LoggedSetting.cpp \
					LoggedSetting.h \
					SequenceTester.cpp \
					SequenceTester.h \
					SequenceTesterImpl.h \
					SettingLogger.cpp \
					SettingLogger.h \
					TextImage.cpp \
					TextImage.h \
					TriggerInput.cpp \
					TriggerInput.h

libmmgr_dal_SequenceTester_la_LIBADD = $(MMDEVAPI_LIBADD) \
				       $(BOOST_THREAD_LIB) \
				       $(BOOST_SYSTEM_LIB) \
				       $(MSGPACK_LIBS)
libmmgr_dal_SequenceTester_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) \
					$(BOOST_LDFLAGS) \
					$(MSGPACK_LDFLAGS)
