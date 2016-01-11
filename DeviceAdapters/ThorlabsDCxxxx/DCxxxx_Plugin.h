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
//							+ DC2100 - high power LED driver / both uses the DC2xxx class
//							+ DC3100 - FLIM LED driver
//							+ DC4100 - four channel LED driver
//
// COPYRIGHT:     Thorlabs GmbH
// LICENSE:       LGPL
// VERSION:			1.1.0
// DATE:				06-Oct-2009
// AUTHOR:        Olaf Wohlmann, owohlmann@thorlabs.com
//

#ifndef _DCxxxx_PLUGIN_H_
#define _DCxxxx_PLUGIN_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#include <string>
#include <map>

#include "DC2xxx.h"
#include "DC3100.h"
#include "DC4100.h"

#endif	// _DCxxxx_PLUGIN_H_
