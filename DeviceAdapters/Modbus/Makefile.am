AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(MODBUS_CPPFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_Modbus.la
libmmgr_dal_Modbus_la_SOURCES = ModbusModule.cpp ModbusModule.h ../../MMDevice/MMDevice.h
libmmgr_dal_Modbus_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(MODBUS_LIBS)
libmmgr_dal_Modbus_la_LIBADD = $(MMDEVAPI_LIBADD)

EXTRA_DIST = Modbus.vcproj

