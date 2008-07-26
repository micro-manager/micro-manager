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

/*
 * Base class for all Leica Devices
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

protected:
   MM_THREAD_GUARD mutex_;
   int position_;
   bool busy_;
   int maxPosition_;
   int minPosition_;
};

/*
 * Model for cubes in Leica Reflector Turret
 */
class LeicaCubeModel
{
public:
   LeicaCubeModel();

   bool IsMethodAvailable(int methodId);

   bool apProtection;
   std::string name;
   std::vector<bool> cubeMethods_;
};

/*
 * Model for Leica Reflector Turret
 */
class LeicaILTurretModel : public LeicaDeviceModel
{
public:
   LeicaILTurretModel();
   std::vector<LeicaCubeModel> cube_;

   static const int maxNrCubes_ = 8;
};

/*
 * Model for Leica Objectives
 */
class LeicaObjectiveModel
{
public:
   LeicaObjectiveModel();

   bool IsMethodAvailable(int methodId);

   int magnification_;
   double NA_;
   int articleNumber_;
   std::vector<bool> methods_;
   std::string immersionType_;
   int parfocalityLeveling_;
   int lowerZ_;
   int immerseZ_;
   int zStepSize_;
};

/*
 * Model for Leica Objective Turret
 */
class LeicaObjectiveTurretModel : public LeicaDeviceModel
{
public:
   LeicaObjectiveTurretModel();

   std::vector<LeicaObjectiveModel> objective_;

   static const int maxNrObjectives_ = 7;
};

/*
 * Model for Leica Z drive
 */
class LeicaDriveModel : public LeicaDeviceModel
{
public:
   LeicaDriveModel();

   // Not Thread safe
   double GetStepSize() {return stepSize_;};
   void SetStepSize(double stepSize) {stepSize_ = stepSize;};
 
   // Thread safe
   int GetRamp(int& ramp);
   int SetRamp(int ramp);
   int GetSpeed(int& speed);
   int SetSpeed(int speed);
   int GetPosFocus(int& posFocus);
   int SetPosFocus(int posFocus);

private:
   double stepSize_; // size in micrometer of each step
   int ramp_;
   int speed_;
   int posFocus_;
};

/*
 * Abstract model of the Lecia DMI microscope
 * All get and set methods refer to the model, not to the actual microscope
 * No communication with the microscope takes place in the model, this is merely
 * a place where the program can internally keep track of the state of the microscoe
 */

class LeicaDMIModel
{
   friend class LeicaCubeModel;
   friend class LeicaObjectiveModel;

public:
   LeicaDMIModel();
   ~LeicaDMIModel();

   bool IsDeviceAvailable(int deviceID);
   void SetDeviceAvailable(int devId);
   bool IsMethodAvailable(int methodId);
   std::string GetMethod(int methodId);
   int GetMethodID(std::string method);
   bool IsMethodAvailable(std::string methodLabel);
   void SetMethodAvailable(int devId);

   // Not thread safe
   int GetStandType(std::string& standType) {standType = standType_; return DEVICE_OK;};
   int SetStandType(std::string standType) {standType_ = standType; return DEVICE_OK;};
   int GetStandVersion(std::string& standVersion) 
      {standVersion = standVersion_; return DEVICE_OK;};
   int SetStandVersion(std::string standVersion) 
      {standVersion_ = standVersion; return DEVICE_OK;};

   LeicaDeviceModel method_;   
   LeicaDeviceModel TLShutter_;
   LeicaDeviceModel ILShutter_;
   LeicaILTurretModel ILTurret_;
   LeicaObjectiveTurretModel ObjectiveTurret_;
   LeicaDriveModel ZDrive_;
   LeicaDriveModel XDrive_;
   LeicaDriveModel YDrive_;

private:
   std::vector<bool> availableDevices_;
   std::vector<bool> availableMethods_;
   std::vector<std::string> methodNames_;

   static const int maxNrDevices_ = 100;
   static const int maxNrMethods_ = 16;

   std::string standType_;
   std::string standVersion_;

};

#endif
