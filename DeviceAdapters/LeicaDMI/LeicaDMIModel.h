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
   MMThreadLock mutex_;
   int position_;
   int minPosition_;
   int maxPosition_;
   bool busy_;
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
 * Model for Leica Condensor
 */
class LeicaCondensorModel : public LeicaDeviceModel
{
public:
   LeicaCondensorModel();
   std::vector<std::string> filter_;

   static const int maxNrFilters_ = 7;
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
 * Model for Leica Fast Filter Wheel
 */
class LeicaFastFilterWheelModel : public LeicaDeviceModel
{
public:
   LeicaFastFilterWheelModel();
   std::map<int, std::string> positionLabels_;

   static const int maxNrFilters_ = 5;

   int SetPositionLabel(int position, std::string label);
   std::string GetPositionLabel(int position);
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

   // Thread safe
   int GetImmersion(char& method);
   int SetImmersion(char method);

protected:
   char method_;
};

/*
 * Model for Leica Z drive
 */
class LeicaDriveModel : public LeicaDeviceModel
{
public:
   friend class LeicaScopeInterface;

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

   // Not Thread safe:
   int GetMinRamp() {return minRamp_;};
   int GetMaxRamp() {return maxRamp_;};
   int GetMinSpeed() {return minSpeed_;};
   int GetMaxSpeed() {return maxSpeed_;};

private:
   double stepSize_; // size in micrometer of each step
   int ramp_;
   int minRamp_;
   int maxRamp_;
   int speed_;
   int minSpeed_;
   int maxSpeed_;
   int posFocus_;
};

/*
 * Model for Leica Motorized Magnification Changer
 */
class LeicaMagChangerModel : public LeicaDeviceModel
{
public:
   LeicaMagChangerModel();

   int GetMagnification(int pos, double& mag);
   int SetMagnification(int pos, double mag);
private:
   std::vector<double> magnification_;
   const static int maxMags_ = 4;
};

/*
 * Model for Leica DIC Prism changer
 */
class LeicaDICPrismTurretModel : public LeicaDeviceModel
{
public:
   LeicaDICPrismTurretModel();

   std::vector<std::string> prismName_;

   // Thread safe
   int GetFinePosition(int& finePosition);
   int SetFinePosition(int finePosition);

   // Not Thread safe:
   bool isEncoded () {return !motorized_;};
   bool isMotorized () {return motorized_;};
   void SetMotorized (bool motorized) {motorized_ = motorized;};
   int GetMinFinePosition() {return minFinePosition_;};
   int GetMaxFinePosition() {return maxFinePosition_;};
   void SetMinFinePosition(int minFinePosition) {minFinePosition_ = minFinePosition;};
   void SetMaxFinePosition(int maxFinePosition) {maxFinePosition_ = maxFinePosition;};

private:
   static const int maxNrPrisms_ = 4;
   bool motorized_;
   int finePosition_;
   int minFinePosition_;
   int maxFinePosition_;
};

/*
 * Model for Leica Motorized Magnification Changer
 */
class LeicaAFCModel : public LeicaDeviceModel
{
public:
   LeicaAFCModel();

   int GetOffset(double& offset); // Set point
   int SetOffset(double offset);  // Measured value
   int GetMode(bool& mode);
   int SetMode(bool mode);
   int GetScore(double& score);
   int SetScore(double score);
   int GetEdgePosition(double& edgeposition);
   int SetEdgePosition(double edgeposition);
   int GetLEDColors(int& topColor, int& bottomColor);
   int SetLEDColors(int topColor, int bottomColor);
   int GetLEDIntensity(int& LEDintensity);
   int SetLEDIntensity(int LEDintensity);
private:
   double edgeposition_;
   double offset_;
   double score_;
   bool mode_;
   int topLEDColor_;
   int bottomLEDColor_;
   int LEDintensity_;
};


/*
 * Model for Leica Transmitted Light lamp
 */
class LeicaTransmittedLightModel : public LeicaDeviceModel
{
public:
   LeicaTransmittedLightModel();

      // Thread safe
   int GetManual(int& position);
   int SetManual(int position);

protected:
   int manual_;
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
   bool UsesMethods();
   void SetUsesMethods(bool use);
   bool IsMethodAvailable(std::string methodLabel);
   void SetMethodAvailable(int devId);

   // Not thread safe
   int GetStandType(std::string& standType) {standType = standType_; return DEVICE_OK;};
   int SetStandType(std::string standType) {standType_ = standType; return DEVICE_OK;};
   int GetStandFamily(int& standFamily) {standFamily = standFamily_; return DEVICE_OK;};
   int SetStandFamily(int standFamily) {standFamily_ = standFamily; return DEVICE_OK;};
   int GetStandVersion(std::string& standVersion) 
      {standVersion = standVersion_; return DEVICE_OK;};
   int SetStandVersion(std::string standVersion) 
      {standVersion_ = standVersion; return DEVICE_OK;};

   LeicaDeviceModel method_;   
   LeicaDeviceModel TLShutter_;
   LeicaDeviceModel ILShutter_;
   LeicaTransmittedLightModel TransmittedLight_;
   LeicaILTurretModel ILTurret_;
   LeicaCondensorModel Condensor_;
   LeicaObjectiveTurretModel ObjectiveTurret_;
   LeicaFastFilterWheelModel FastFilterWheel_[4];
   LeicaDriveModel ZDrive_;
   LeicaDriveModel XDrive_;
   LeicaDriveModel YDrive_;
   LeicaDeviceModel fieldDiaphragmTL_;
   LeicaDeviceModel apertureDiaphragmTL_;
   LeicaDeviceModel fieldDiaphragmIL_;
   LeicaDeviceModel apertureDiaphragmIL_;
   LeicaDICPrismTurretModel dicTurret_;
   LeicaDeviceModel tlPolarizer_;
   LeicaMagChangerModel magChanger_;
   LeicaAFCModel afc_;

	LeicaDeviceModel sidePort_;

private:
   bool usesMethods_;
   std::vector<bool> availableDevices_;
   std::vector<bool> availableMethods_;
   std::vector<std::string> methodNames_;

   static const int maxNrDevices_ = 100;
   static const int maxNrMethods_ = 16;

   std::string standType_;
   int standFamily_;
   std::string standVersion_;

};

#endif
