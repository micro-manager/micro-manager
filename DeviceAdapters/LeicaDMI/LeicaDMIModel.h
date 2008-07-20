///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMIModel.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION: Model for the compositsion and state of the microscope.  
//   
// COPYRIGHT:     100xImaging, Inc., 2008
// LICENSE:        
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  

#ifndef _LEICADMIMODEL_H_
#define _LEICADMIMODEL_H_

#include <string>
#include <vector>
#include <map>

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceThreads.h"

/*
 * Abstract model of a device in the Lecia DMI microscope
 * All get and set methods refer to the model, not to the actual microscope
 * No communication with the microscope takes place in the model, this is merely
 * a place where the program can internally keep track of the state of the microscoe
 */

class LeicaDeviceModel
{
public:
   LeicaDeviceModel();
   ~LeicaDeviceModel();

   // Thread safe
   int GetPosition(int& position);
   int SetPosition(int position);
   int GetBusy(bool& busy);
   int SetBusy(bool busy);

   // Not thread safe
   int GetMaxPosition(int& maxPosition) {maxPosition = maxPosition_; return DEVICE_OK;};
   int SetMaxPosition(int maxPosition) {maxPosition_ = maxPosition; return DEVICE_OK;};
   int GetMinPosition(int& minPosition) {minPosition = minPosition_; return DEVICE_OK;};
   int SetMinPosition(int minPosition) {minPosition_ = minPosition; return DEVICE_OK;};

private:
   int position_;
   int maxPosition_;
   int minPosition_;
};


/*
 * Abstract model ofthe Lecia DMI microscope
 * All get and set methods refer to the model, not to the actual microscope
 * No communication with the microscope takes place in the model, this is merely
 * a place where the program can internally keep track of the state of the microscoe
 */

class LeicaDMIModel
{
public:
   LeicaDMIModel();
   ~LeicaDMIModel();

   bool IsDeviceAvailable(int deviceID);
   bool IsMethodAvailable(int methodId);
   bool IsMethodAvailable(std::string methodLabel);

   // Not thread safe
   int GetStandType(std::string& standType) {standType = standType_; return DEVICE_OK;};
   int SetStandType(std::string standType) {standType_ = standType; return DEVICE_OK;};
   int GetStandVersion(std::string& standVersion) 
      {standVersion = standVersion_; return DEVICE_OK;};
   int SetStandVersion(std::string standVersion) 
      {standVersion_ = standVersion; return DEVICE_OK;};

   LeicaDeviceModel method_;   
   LeicaDeviceModel TLShutter_;
   LeicaDeviceModel ReflectorTurret_;

private:
   std::vector<bool> availableDevices_;
   std::vector<bool> availableMethods;

   std::string standType_;
   std::string standVersion_;

};

#endif
