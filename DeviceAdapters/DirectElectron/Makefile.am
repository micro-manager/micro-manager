AM_CXXFLAGS = $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_DECamera.la
libmmgr_dal_DECamera_la_SOURCES=src/DEMessaging/DEConstants.h src/DEMessaging/DEExceptions.h src/DEMessaging/DEServer.pb.cc src/DEMessaging/DEServer.pb.h src/DEMessaging/PacketCreator.cpp src/DEMessaging/PacketCreator.h src/DEMessaging/PacketParser.cpp src/DEMessaging/PacketParser.h src/DEMessaging/PacketUtility.cpp src/DEMessaging/PacketUtility.h src/DEMessaging/VariableBuffer.cpp src/DEMessaging/VariableBuffer.h src/DEClientLib/DENetwork.cpp src/DEClientLib/DENetwork.h src/DEClientLib/DEProperty.h src/DEClientLib/DEProtoProxy.cpp src/DEClientLib/DEProtoProxy.h src/DEClientLib/DEProxyInterface.h src/DEClientLib/PropertyHelper.cpp src/DEClientLib/PropertyHelper.h src/DECamera/DECamera.cpp src/DECamera/DECamera.h
libmmgr_dal_DECamera_la_CPPFLAGS = -I./src/DEMessaging -I./src/DEClientLib -I../../ $(AM_CPPFLAGS)
libmmgr_dal_DECamera_la_LIBADD = /usr/local/lib/libprotobuf.a ../../MMDevice/.libs/libMMDevice.a /usr/local/lib/libboost_system.a /usr/local/lib/libboost_date_time.a /usr/local/lib/libboost_thread.a /usr/local/lib/libboost_signals.a
libmmgr_dal_DECamera_la_LDFLAGS = -module 
