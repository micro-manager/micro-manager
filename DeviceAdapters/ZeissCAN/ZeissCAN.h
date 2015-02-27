///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissCAN.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN bus adapater
//   
// COPYRIGHT:     University of California, San Francisco, 2007
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
// AUTHOR:         Nico Stuurman, nico@cmp.ucsf.edu 5/14/2007
//                automatic device detection by Karl Hoover

#ifndef _ZEISSCAN_H_
#define _ZEISSCAN_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <vector>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004                                   
#define ERR_SET_POSITION_FAILED      10005                                   
#define ERR_INVALID_STEP_SIZE        10006                                   
#define ERR_INVALID_MODE             10008 
#define ERR_CANNOT_CHANGE_PROPERTY   10009 
#define ERR_UNEXPECTED_ANSWER        10010 
#define ERR_INVALID_TURRET           10011 
#define ERR_SCOPE_NOT_ACTIVE         10012 
#define ERR_INVALID_TURRET_POSITION  10013
#define ERR_MODULE_NOT_FOUND         10014
#define ERR_NO_FOCUS_DRIVE           10015
#define ERR_NO_XY_DRIVE              10016

class ZeissHub
{
   public:
      ZeissHub();
      ~ZeissHub();

      void SetPort(const char* port) {port_ = port; initialized_ = true;}
      int GetVersion(MM::Device& device, MM::Core& core, std::string& ver);
      int GetModelType(MM::Device& device, MM::Core& core, int& type);
      int ExecuteCommand(MM::Device& device, MM::Core& core, const char* command);
      int GetAnswer(MM::Device& device, MM::Core& core, std::string& answer); 
      //int ParseResponse(const char* cmd, string& value);

      static const int RCV_BUF_LENGTH = 1024;
      char rcvBuf_[RCV_BUF_LENGTH];
      void ClearRcvBuf();
      int ClearPort(MM::Device& device, MM::Core& core);
      std::string port_;
      std::string firmware_;
      double version_;

      bool initialized_;
};


class ZeissTurret
{
   public:
      ZeissTurret();
      ~ZeissTurret();

      int GetPosition(MM::Device& device, MM::Core& core, int turretNr, int& position);
      int SetPosition(MM::Device& device, MM::Core& core, int turretNr, int position);
      int GetMaxPosition(MM::Device& device, MM::Core& core, int turretNr, int& position);
      int GetBusy(MM::Device& device, MM::Core& core, int turretNr, bool& busy);
      int GetPresence(MM::Device& device, MM::Core& core,  int turretNr, bool& present);

   private:
      std::vector<int> groupOne;
      std::vector<int> groupTwo;
      std::map<int, int> bitPosition;
};


class ZeissScope : public HubBase<ZeissScope>
{
   public:
      ZeissScope();
      ~ZeissScope();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();
      void GetName(char* pszName) const;
      bool Busy();
      MM::DeviceDetectionStatus DetectDevice(void);

      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 

      // HUB interface
      // -------------
      int DetectInstalledDevices();

   private:
      bool initialized_;
      bool IsMCU28Present();
      int GetMCU28Version(std::string& ver);
      std::vector<std::string> peripherals_;
      std::map<int,std::string>* pTurretIDMap_;

      // this would better be a static member!!!!
      void CreateAndAddDevice(std::string deviceName);
      void GetDiscoverableDevice(int peripheralNum, char* peripheralName, unsigned int maxNameLen);
      int Query(std::string queryCode, std::string& answer);
      std::map<int,std::string>& turretIDMap();
};



class ZeissShutter : public CShutterBase<ZeissShutter>
{
public:
   ZeissShutter();
   ~ZeissShutter();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();
   unsigned long GetShutterNr() const {return shutterNr_;}

   // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExternal(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   unsigned shutterNr_;
   bool external_;
   bool state_;
   MM::MMTime changedTime_;
};

class ZeissShutterMF : public CShutterBase<ZeissShutterMF>
{
public:
   ZeissShutterMF();
   ~ZeissShutterMF();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();
   unsigned long GetShutterNr() const {return shutterNr_;}

   // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   unsigned shutterNr_;
   bool state_;
};

class HalogenLamp : public CShutterBase<HalogenLamp>
{
public:
   HalogenLamp();
   ~HalogenLamp();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLightManager(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetLM(bool on = true);
   int GetLM(bool& on);
   int SetIntensity(long intensity);
   int GetIntensity(long &intensity);
   bool initialized_;
   std::string name_;
   MM::MMTime changedTime_;
   bool state_;
};

class ReflectorTurret : public CStateDeviceBase<ReflectorTurret>
{
public:
   ReflectorTurret();
   ~ReflectorTurret();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool GetPresence(bool& present);
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};
      
class SidePortTurret : public CStateDeviceBase<SidePortTurret>
{
public:
   SidePortTurret();
   ~SidePortTurret();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};

class BasePortSlider : public CStateDeviceBase<BasePortSlider>
{
public:
   BasePortSlider();
   ~BasePortSlider();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};

class ObjectiveTurret : public CStateDeviceBase<ObjectiveTurret>
{
public:
   ObjectiveTurret();
   ~ObjectiveTurret();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool GetPresence(bool& present);
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};

class CondenserTurret : public CStateDeviceBase<CondenserTurret>
{
public:
   CondenserTurret();
   ~CondenserTurret();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAperture(MM::PropertyBase *pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool GetPresence(bool& present);

   int SetAperture(long aperture);
   int GetAperture(long &aperture);
   bool BusyChangingAperture();

   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
   
   MM::MMTime apertureChangingTimeout_;
};

class OptovarTurret : public CStateDeviceBase<OptovarTurret>
{
public:
   OptovarTurret();
   ~OptovarTurret();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool GetPresence(bool& present);
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};

class TubelensTurret : public CStateDeviceBase<TubelensTurret>
{
public:
   TubelensTurret();
   ~TubelensTurret();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool GetPresence(bool& present);
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};

class LampMirror : public CStateDeviceBase<LampMirror>
{
public:
   LampMirror();
   ~LampMirror();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool GetPresence(bool& present);
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};


class FocusStage : public CStageBase<FocusStage>
{
public:
   FocusStage();
   ~FocusStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
   virtual int SetPositionUm(double pos);
   virtual int GetPositionUm(double& pos);
   virtual double GetStepSize() const {return stepSize_um_;}
   virtual int SetPositionSteps(long steps) ;
   virtual int GetPositionSteps(long& steps);
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLoadSample(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int GetFocusFirmwareVersion();
   int GetUpperLimit();
   int GetLowerLimit();
   double stepSize_um_;
   std::string focusFirmware_;
   std::string firmware_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   typedef enum {
      ZMSF_MOVING = 0x0002, // trajectory is in progress
      ZMSF_SETTLE = 0x0004  // settling after movement
   } ZmStatFlags;

};


class FilterWheel : public CStateDeviceBase<FilterWheel>
{
public:
   FilterWheel(int wheelNr);
   ~FilterWheel();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   bool GetPresence(bool& present);
   int wheelNr_;
   bool initialized_;
   std::string name_;
   long pos_;
   int numPos_;
   int turretId_;
};

// ZEISS XY stage with MCU28 controller
class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // XYStage API
   // -----------
   int SetPositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
   int Home();
   int Stop();
   int SetOrigin();
   int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   double GetStepSizeXUm();
   double GetStepSizeYUm();
   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetXYFirmwareVersion();
   std::string xyFirmware_;
   std::string firmware_;
   bool initialized_;
   double stepSizeUm_;
};

// Axioskope 2 Z stage
//
class ZStage : public CStageBase<ZStage>
{
public:
   ZStage();
   ~ZStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
   virtual int SetPositionUm(double pos);
   virtual int GetPositionUm(double& pos);
   virtual double GetStepSize() const {return stepSize_um_;}
   virtual int SetPositionSteps(long steps) ;
   virtual int GetPositionSteps(long& steps);
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLoadSample(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Sequence functions (unimplemented)
   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   int GetStageSequenceMaxLength(long& nrEvents) const  {nrEvents = 0; return DEVICE_OK;}
   int StartStageSequence() {return DEVICE_OK;}
   int StopStageSequence() {return DEVICE_OK;}
   int ClearStageSequence() {return DEVICE_OK;}
   int AddToStageSequence(double /*position*/) {return DEVICE_OK;}
   int SendStageSequence() {return DEVICE_OK;}

private:
   int GetFocusFirmwareVersion();
   int GetUpperLimit();
   int GetLowerLimit();
   double stepSize_um_;
   std::string focusFirmware_;
   std::string firmware_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   typedef enum {
      ZMSF_MOVING = 0x0002, // trajectory is in progress
      ZMSF_SETTLE = 0x0004  // settling after movement
   } ZmStatFlags;

};

// Axiophot2 Photomodule

class PhotoModule : public CStateDeviceBase<PhotoModule>
{
public:
   PhotoModule();
   ~PhotoModule();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUpperPrism(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetTurretPosition(int position);
   int GetTurretPosition(int &position);
   int SetUpperPrism(int position);
   bool initialized_;
   std::string name_;
   long pos_;
   long upperPrismPos_;
   int numPos_;
};
      
#endif // _ZeissCAN_H_
