///////////////////////////////////////////////////////////////////////////////
// FILE:          Oasis.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of the Objective Imaging Oasis device adapter.
//                Includes OASIS-blue PCI/PCIe, OASIS-4i, and Glide Stages
//                
// AUTHOR:        Don Laferty, 4/9/12
//
// COPYRIGHT:     Objective Imaging, Ltd. Cambridge, UK, 2012
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
// vim: set autoindent tabstop=3 softtabstop=3 shiftwidth=3 expandtab textwidth=78:

#ifndef _OASIS_H_
#define _OASIS_H_

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

/*
 * TODO: Check whether we need any of these...
// MMCore name of serial port
std::string port_ = "";

const int nrShuttersPerDevice = 4;
const int nrWheelsPerDevice = 4;
const int nrDevicesPerController = 1;
const int nrTTLPerDevice= 4;
bool wheelsUsed [nrDevicesPerController][nrWheelsPerDevice] = { false };
bool shuttersUsed [nrShuttersPerDevice] = { false };
bool ttlUsed [nrTTLPerDevice] = { false };
*/

class Hub : public HubBase<Hub>
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
      int DetectInstalledDevices();

      // action interface
      // ---------------
      int OnConfig (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct);

      // device discovery
      bool SupportsDeviceDetection(void);
      MM::DeviceDetectionStatus DetectDevice(void);

      int GetNumberOfDiscoverableDevices();
      void GetDiscoverableDevice(int peripheralNum, char* peripheralName, unsigned int maxNameLen);

      int GetDiscoDeviceNumberOfProperties(int peripheralNum);
      void GetDiscoDeviceProperty(int peripheralNum, short propertyNumber ,char* propertyName, char* propValue, unsigned int maxValueLen);
      static MMThreadLock& GetLock() {return lock_;}
      int GetBoardID() {return boardId_;}
      bool isInitialized() {return initialized_;}


   private:
      void GetPeripheralInventory();
      std::vector<std::string> peripherals_;

      int boardId_;                 // Board identification number
      static MMThreadLock lock_;

      std::vector<std::string> discoverableDevices_;

      std::vector<short> inventoryDeviceAddresses_; // 1, 2, 17 etc.
      std::vector<char> inventoryDeviceIDs_; //  X Y S, etc.

      int QuerySerialNum(std::string& sn);
      int Hub::QueryDescription(std::string& desc);
      int QueryVersion(std::string& version);

      // Command exchange with MMCore
      std::string command_;
      int transmissionDelay_;
      bool initialized_;
      bool busy_;
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
      int SetPositionUm(double x, double y);
      int GetPositionUm(double& x, double& y);
      int SetRelativePositionUm(double dx, double dy);
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
       int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
       int OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct);
       int OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct);
       int OnRampX(MM::PropertyBase* pProp, MM::ActionType eAct);
       int OnRampY(MM::PropertyBase* pProp, MM::ActionType eAct);
       int OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      void GetOrientation(bool& mirrorX, bool& mirrorY);
   
      bool initialized_;
      double stepSizeUm_;
      double speedX_, speedY_;
      int rampX_, rampY_;
      int backlash_;
      double originX_;
      double originY_;
};


class ZStage : public CStageBase<ZStage>
{
   public:
      ZStage();
      ~ZStage();
  
      // Device API
      // ----------
      int Initialize();
      int Shutdown();
  
      void GetName(char* pszName) const;
      bool Busy();

      // Stage API
      // ---------
      int SetPositionUm(double pos);
      int SetRelativePositionUm(double d);
      int Move(double velocity);
      int SetAdapterOriginUm(double d);

      int GetPositionUm(double& pos);
      int SetPositionSteps(long steps);
      int GetPositionSteps(long& steps);
      int SetOrigin();
      int SetAdapterOrigin();
      int Stop();
      int GetLimits(double& min, double& max);

      int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
      bool IsContinuousFocusDrive() const {return false;}

      // action interface
      // ----------------
      int OnStepSize (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnAccel    (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnBacklash (MM::PropertyBase* pProp, MM::ActionType eAct);


   private:
      bool initialized_;
      bool range_measured_;
      double stepSizeUm_;
      double speedZ_;
      int accelZ_;
      int backlash_;
      double originZ_;
};

class Wheel : public CStateDeviceBase<Wheel>                         
{
   public:
      Wheel(int index);
      ~Wheel();
      // 
      // MMDevice API
      // ------------                                                           
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;
      bool Busy();  
      unsigned long GetNumberOfPositions()const {return numPos_;}
                 
      // action interface
      // ----------------                                                       
      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);                
      int OnID(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDeviceNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnWheelNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnWheelNrPos(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnWheelHomeTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);
                 
   private:         
      int SetWheelPosition(int position);
      int GetWheelPosition(int &position);
      int HomeWheel();
      std::string name_;
      long pos_;
      bool initialized_;
      unsigned wheelNumber_;
      double wheelHomeTimeoutS_;
      int numPos_;
};



class Shutter : public CShutterBase<Shutter>
{
   public:
      Shutter(int index);
      ~Shutter();

      bool Busy();
      void GetName(char* pszName) const;
      int Initialize();
      int Shutdown();
      
      // Shutter API
      int SetOpen(bool open = true);
      int GetOpen(bool& open);
      int Fire(double deltaT);

      // action interface
      // ----------------
      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
      //int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnID(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDeviceNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnShutterNumber(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      int SetShutterPosition(bool state);
      int GetShutterPosition(bool& state);
      std::string name_;
      unsigned shutterNumber_;
      bool initialized_;
      MM::MMTime changedTime_;
};


class TTL : public CShutterBase<TTL>
{
   public:
      TTL(int index);
      ~TTL();

      bool Busy();
      void GetName(char* pszName) const;
      int Initialize();
      int Shutdown();
      
      // Shutter API
      int SetOpen(bool open = true);
      int GetOpen(bool& open);
      int Fire(double deltaT);

      // action interface
      // ----------------
      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnTTLNumber(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      int SetTTL(bool state);
      int GetTTL(bool& state);

      std::string name_;
      unsigned ttlNumber_;
      bool initialized_;
};

class DAC : public CSignalIOBase<DAC>
{
   public:
      DAC(int index);
      ~DAC();

      // Device API
      int Initialize();
      int Shutdown();
      bool Busy();
      void GetName(char* pszName) const;

      // SignalIO API
      int SetGateOpen(bool open = true);
      int GetGateOpen(bool& open);
      int SetSignal(double volts);
      int GetSignal(double& volts);
      int GetLimits(double& minVolts, double& maxVolts) {minVolts = 0.0; maxVolts = 10.0; return DEVICE_OK;};

      int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

      // action interface
      int OnState  (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDACPort(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      bool initialized_;
      int  DACPort_;
      bool open_;
      double volts_;
      int GetCommand(const std::string& cmd, std::string& response);
      std::string name_;  
      double answerTimeoutMs_;
};



#endif //_OASIS_H_
