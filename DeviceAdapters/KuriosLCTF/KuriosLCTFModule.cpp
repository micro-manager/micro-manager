///////////////////////////////////////////////////////////////////////////////
// FILE:          KuriosLCTFModule.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Kurios LCTF
// COPYRIGHT:     2019 Backman Biophotonics Lab, Northwestern University
//                All rights reserved.
//
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.
//
// AUTHOR:        Nick Anthony

#include "KuriosLCTF.h"


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_LCTFName, MM::GenericDevice, g_LCTFName);
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0) {
	return 0;
	}
	else if (strcmp(deviceName, g_LCTFName) == 0) {
		KuriosLCTF* lctf = new KuriosLCTF();
		return lctf;
	} else {
		return 0;
	}
}