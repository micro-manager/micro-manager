///////////////////////////////////////////////////////////////////////////////
// FILE:          PI_GCS_DLL.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS DLL Controller Driver
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 10/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// CVS:           $Id: PIGCSController.cpp,v 1.0, 2010-03-09 12:32:58Z, Steffen Rau$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "PIGCSController.h"
#include "PI_GCS_DLL.h"
#include "../../MMDevice/ModuleInterface.h"
#include <algorithm>


const char* PIGCSController::DeviceName_ = "PI_GCSController";


PIGCSController::PIGCSController()
:PIController("")
{
}

PIGCSController::~PIGCSController()
{
	Shutdown();
}

int PIGCSController::Initialize()
{
	return Connect();
}

int PIGCSController::Shutdown()
{
	return Close();
}

bool PIGCSController::Busy()
{
   return false;
}

void PIGCSController::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, DeviceName_);
}

int PIGCSController::Connect()
{
	return DEVICE_ERR;
}

int PIGCSController::Close()
{
	return DEVICE_ERR;
}

bool PIGCSController::qIDN(std::string& sIDN)
{
	return false;
}

bool PIGCSController::INI(const std::string& axes)
{
	return false;
}

bool PIGCSController::CST(const std::string& axes, const std::string& stages)
{
	return false;
}

bool PIGCSController::SVO(const std::string& axes, const BOOL* svoValues)
{
	return false;
};
