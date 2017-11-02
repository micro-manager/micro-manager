///////////////////////////////////////////////////////////////////////////////
// FILE:          module.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   A serial port implementation that communicates over TCP/IP
//                Can be used to connect to devices on another computer over the network
//
// AUTHOR:        Lukas Lang
//
// COPYRIGHT:     2017 Lukas Lang
// LICENSE:       Licensed under the Apache License, Version 2.0 (the "License");
//                you may not use this file except in compliance with the License.
//                You may obtain a copy of the License at
//                
//                http://www.apache.org/licenses/LICENSE-2.0
//                
//                Unless required by applicable law or agreed to in writing, software
//                distributed under the License is distributed on an "AS IS" BASIS,
//                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//                See the License for the specific language governing permissions and
//                limitations under the License.

#include "../../MMDevice/ModuleInterface.h"

#include "TCPIPPort.h"

// Exported MMDevice API
MODULE_API void InitializeModuleData()
{
	TCPIPPort::RegisterNewPort();
}

MODULE_API MM::Device* CreateDevice(const char* name)
{
	if (name == 0)
		return 0;

	std::string deviceName_(name);

	if (deviceName_.find(deviceName) == 0)
	{
		size_t start = std::string(deviceName).size() + 2;

		int index = atoi(deviceName_.substr(start, deviceName_.find(')') - start).c_str());
		
		TCPIPPort* s = new TCPIPPort(index);
		return s;
	}
	
	return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}