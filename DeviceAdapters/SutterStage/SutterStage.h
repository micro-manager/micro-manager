///////////////////////////////////////////////////////////////////////////////
// FILE:          SutterStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of the Ludl device adapter.
//                Should also work with Sutter devices.
//                
// AUTHOR:        Nico Stuurman, 4/12/07.  XYStage and ZStage by Nenad Amodaj, nenad@amodaj.com, 10/27/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
//
// LICENSE:       This library is free software; you can redistribute it and/or
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
//
//

#ifndef _SUTTERSTAGE_H_
#define _SUTTERSTAGE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_LOW_LEVEL_MODE_FAILED    10007
#define ERR_INVALID_MODE             10008
#define ERR_INVALID_ID               10009
#define ERR_UNRECOGNIZED_ANSWER      10010
#define ERR_INVALID_SHUTTER_STATE    10011
#define ERR_INVALID_SHUTTER_NUMBER   10012
#define ERR_INVALID_COMMAND_LEVEL    10013
#define ERR_MODULE_NOT_FOUND         10014
#define ERR_INVALID_WHEEL_NUMBER     10015
#define ERR_INVALID_WHEEL_POSITION   10016
#define ERR_NO_ANSWER                10017
#define ERR_WHEEL_HOME_FAILED        10018
#define ERR_WHEEL_POSITION_FAILED    10019
#define ERR_SHUTTER_COMMAND_FAILED   10020
#define ERR_COMMAND_FAILED           10021
#define ERR_INVALID_DEVICE_NUMBER    10023
#define ERR_DEVICE_CHANGE_NOT_ALLOWED 10024
#define ERR_SHUTTER_USED             10025
#define ERR_WHEEL_USED               10026
#define ERR_NO_CONTROLLER            10027

// MMCore name of serial port
std::string port_ = "";

int clearPort(MM::Device& device, MM::Core& core, const char* port);
int getResult(MM::Device& device, MM::Core& core, const char* port);
int changeCommandLevel(MM::Device& device, MM::Core& core, const char* commandLevel);
const int nrShuttersPerDevice = 3;
const int nrWheelsPerDevice = 2;
const int nrDevicesPerController = 5;

// Rely on zero initialization
bool shuttersUsed [nrDevicesPerController][nrShuttersPerDevice];
bool wheelsUsed [nrDevicesPerController][nrWheelsPerDevice];

class Hub : public CGenericBase<Hub>
{
   public:
      Hub();
      ~Hub();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;
      bool Busy();

      // action interface
      // ---------------
      int OnPort (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnConfig (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnReset (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnTransmissionDelay (MM::PropertyBase* pProp, MM::ActionType eAct);

      // device discovery
      bool SupportsDeviceDetection(void);
      MM::DeviceDetectionStatus DetectDevice(void);

     int GetNumberOfDiscoverableDevices();
     void GetDiscoverableDevice(int peripheralNum, char* peripheralName, unsigned int maxNameLen);

     int GetDiscoDeviceNumberOfProperties(int peripheralNum);
     void GetDiscoDeviceProperty(int peripheralNum, short propertyNumber ,char* propertyName, char* propValue, unsigned int maxValueLen);


   private:
      void QueryPeripheralInventory();

      std::vector<std::string> discoverableDevices_;

      std::vector<short> inventoryDeviceAddresses_; // 1, 2, 17 etc.
      std::vector<char> inventoryDeviceIDs_; //  X Y S, etc.

      int QueryVersion(std::string& version);

      // Command exchange with MMCore
      std::string command_;
      int transmissionDelay_;
      bool initialized_;
};

class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API
   // -----------
  int SetPositionSteps(long x, long y);
  int GetPositionSteps(long& x, long& y);
  int SetRelativePositionSteps(long x, long y);
  int SetOrigin();
  int SetAdapterOrigin();
  int Home();
  int Stop();
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  double GetStepSizeXUm() {return stepSizeUm_;}
  double GetStepSizeYUm() {return stepSizeUm_;}
  int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnIDX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIDY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStartSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool AxisBusy(const char* axis);
   int ExecuteCommand(const std::string& cmd, std::string& response);
   
   bool initialized_;
   double stepSizeUm_;
   double speed_, startSpeed_;
   long accel_;
   unsigned idX_;
   unsigned idY_;
   double originX_;
   double originY_;
};

class Stage : public CStageBase<Stage>
{
public:
   Stage();
   ~Stage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);

  bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnID(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct);
   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);
   int Autofocus(long param);

   bool initialized_;
   double stepSizeUm_;
   double answerTimeoutMs_;
   std::string id_;
};

#endif //_SUTTERSTAGE_H_
