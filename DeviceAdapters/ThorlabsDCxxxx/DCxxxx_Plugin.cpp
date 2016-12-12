///////////////////////////////////////////////////////////////////////////////
// FILE:          DCxxxx.h
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls the Thorlabs DCxxxx LED driver series through a
//						serial port. These devices are implemented as shutter devices,
//						although they are illumination devices. This makes the
//						synchronisation easier. So "Open" and "Close" means "On" or
//						"Off". "Fire" does nothing at all. All other commands are
//						realized as properties and differ from device to device.
//						Supported devices are:
//                   + DC2010 - universal LED driver	
//							+ DC2100 - high power LED driver - both uses the DC2xxx class
//							+ DC3100 - FLIM LED driver
//							+ DC4100 - four channel LED driver
//							+ DC2200 - Led driver
//
// COPYRIGHT:		Thorlabs GmbH
// LICENSE:			LGPL
// VERSION:			2.0.0
// DATE:			16-Dec-2015
// AUTHOR:			Olaf Wohlmann, owohlmann@thorlabs.com
//


#ifdef WIN32
	#include <windows.h>
	#define snprintf _snprintf
#endif
#include <string>
#include <math.h>
#include "DCxxxx_Plugin.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceUtils.h"

///////////////////////////////////////////////////////////////////////////////
//
// GLOBALS
///////////////////////////////////////////////////////////////////////////////
const char* g_DeviceDC2200Name = "Thorlabs DC2200";
const char* g_DeviceDC3100Name = "Thorlabs DC3100";
const char* g_DeviceDC4100Name = "Thorlabs DC4100";
const char* g_DeviceDC2xxxName = "Thorlabs DC2010/DC2100";

///////////////////////////////////////////////////////////////////////////////
//
// Exported MMDevice API
//
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 Initialize module data. It publishes the DCxxxx series names to µManager.
---------------------------------------------------------------------------*/
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_DeviceDC2xxxName, MM::ShutterDevice, "DC2010/DC2100 High Power LED Driver");
	RegisterDevice(g_DeviceDC3100Name, MM::ShutterDevice, "DC3100 FLIM LED Driver");
	RegisterDevice(g_DeviceDC4100Name, MM::ShutterDevice, "DC4100 Four channel LED Driver");
	RegisterDevice(g_DeviceDC2200Name, MM::ShutterDevice, "DC2200 High Power LED Driver");
}

/*---------------------------------------------------------------------------
 Creates and returns a device specified by the device name.
---------------------------------------------------------------------------*/
MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	// no name, no device
	if(deviceName == 0)	return 0;

	if(strcmp(deviceName, g_DeviceDC2xxxName) == 0)
	{
		return new DC2xxx(g_DeviceDC2xxxName);
	}
	else if(strcmp(deviceName, g_DeviceDC3100Name) == 0)
	{
		return new DC3100(g_DeviceDC3100Name);
	}
	else if(strcmp(deviceName, g_DeviceDC4100Name) == 0)
	{
		return new DC4100(g_DeviceDC4100Name);
	}
	else if(strcmp(deviceName, g_DeviceDC2200Name) == 0)
	{
		return new DC2200(g_DeviceDC2200Name);
	}

	return 0;
}

/*---------------------------------------------------------------------------
 Deletes a device pointed by pDevice.
---------------------------------------------------------------------------*/
MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
