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
//							+ DC2010 - universal LED driver	
//							+ DC2100 - high power LED driver - both uses the DC2xxx class
//							+ DC2200 - high power LED driver ( uses USBTMC, not serial com port)
//							+ DC3100 - FLIM LED driver
//							+ DC4100 - four channel LED driver
//
// COPYRIGHT:   Thorlabs GmbH
// LICENSE:     LGPL
// VERSION:		2.0.0
// DATE:		08-Jan-2016
// AUTHOR:      Olaf Wohlmann, owohlmann@thorlabs.com
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
// Exported MMDevice API
//
///////////////////////////////////////////////////////////////////////////////
/*---------------------------------------------------------------------------
 Initialize module data. It publishes the DCxxxx series names to µManager.
---------------------------------------------------------------------------*/
MODULE_API void InitializeModuleData()
{
	//RegisterDevice(DC2200::DeviceName(), MM::ShutterDevice, "DC2200 High Power LED Driver");
	RegisterDevice(DC2xxx::DeviceName(), MM::ShutterDevice, "DC2010/DC2100 High Power LED Driver");
	RegisterDevice(DC3100::DeviceName(), MM::ShutterDevice, "DC3100 FLIM LED Driver");
	RegisterDevice(DC4100::DeviceName(), MM::ShutterDevice, "DC4100 Four channel LED Driver");
}

/*---------------------------------------------------------------------------
 Creates and returns a device specified by the device name.
---------------------------------------------------------------------------*/
MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	// no name, no device
	if(deviceName == 0)	return 0;

	if(strcmp(deviceName, DC2xxx::DeviceName()) == 0)
	{
		return new DC2xxx;
	}
	//else if(strcmp(deviceName, DC2200::DeviceName()) == 0)
	//{
	//	return new DC2200;
	//}
	else if(strcmp(deviceName, DC3100::DeviceName()) == 0)
	{
		return new DC3100;
	}
	else if(strcmp(deviceName, DC4100::DeviceName()) == 0)
	{
		return new DC4100;
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

