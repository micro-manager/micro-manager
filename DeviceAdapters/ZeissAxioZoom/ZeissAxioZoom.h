///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissAxioZoom.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN29 bus controller, see Zeiss CAN29 bus documentation
//                
// AUTHOR: Nico Stuurman, 6/18/2007 - 
//         Nenad Amodaj, 2015
//
// COPYRIGHT:     University of California, San Francisco, 2007, 2008, 2009
//                Exploratorium, 2015
//
// LICENSE:       Please note: This code could only be developed thanks to information 
//                provided by Zeiss under a non-disclosure agreement.  Subsequently, 
//                this code has been reviewed by Zeiss and we were permitted to release 
//                this under the LGPL on 1/16/2008 (permission re-granted on 7/3/2008, 7/1/2009
//                after changes to the code).
//                If you modify this code using information you obtained 
//                under a NDA with Zeiss, you will need to ask Zeiss whether you can release 
//                your modifications.  
//
//                This version contains changes not yet approved by Zeiss.  This code
//                can therefore not be made publicly available and the license below does not apply
//                
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


#ifndef _ZEISSCAN29_H_
#define _ZEISSCAN29_H_

#ifdef WIN32
#include <windows.h>
#include <winsock.h>
#define snprintf _snprintf 
#else
#include <netinet/in.h>
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <vector>
#include <map>
#include <algorithm>

////////////
// Typedefs for the following Zeiss datatypes:
// TEXT (max 20 ascii characters, not null terminated)
// BYTE - 1 byte
typedef char ZeissByte;
// UBYTE - 1 unsigned byte
typedef unsigned char ZeissUByte;
#define ZeissByteSize 1
// SHORT - 2 Byte Motorola format
typedef short ZeissShort;
// USHORT - 2 Byte Motorola format, unsigned
typedef unsigned short ZeissUShort;
#define ZeissShortSize 2
// LONG - 4 Byte Motorola format
typedef int  ZeissLong;
// ULONG - 4 Byte Motorola format, unsigned
typedef unsigned int  ZeissULong;
#define ZeissLongSize 4
// FLOAT - 4 Byte Float Motorola format
typedef float  ZeissFloat;
#define ZeissFloatSize 4
// DOUBLE - 8 Byte Double Motorola format
typedef double ZeissDouble;
#define ZeissDoubleSize 8
 
// The highest device number in the Zeiss Scope
#define MAXNUMBERDEVICES 0x80

// Target (CAN) addresses for various Zeiss CAN controllers:
#define AXIOOBSERVER 0x19
#define AXIOIMAGER 0x1B
#define COLIBRI 0x50
#define DEFINITEFOCUS 0x60

// axioZoom CAN nodes
#define EMS3 0x28 // controller unit
#define OPTICS 0x20
#define MOTOR_FOCUS 0x24
#define STAGEX 0x26
#define STAGEY 0x27
#define DL450 0x65
#define FLUO_TUBE 0x66

// AxioZoom devices
static const char* g_ZeissMotorFocus = "ZeissMotorFocus";
static const char* g_ZeissXYStage = "ZeissXYStage";
static const char* g_ZeissOpticsUnit = "OpticsUnit";
static const char* g_ZeissFluoTube = "FluoTube";
static const char* g_ZeissDL450 = "DL450";

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
#define ERR_ANSWER_TIMEOUT           10015
// Use the same value as used in the serial port adapter
#define ERR_PORT_NOT_OPEN            104
#define ERR_SHUTTER_POS_UNKNOWN      10017
#define ERR_UNKNOWN_OFFSET           10018
#define ERR_DEFINITE_FOCUS_NOT_LOCKED           10019
#define ERR_DEFINITE_FOCUS_NO_DATA   10020
#define ERR_DEFINITE_FOCUS_TIMEOUT   10021
#define ERR_NO_AUTOFOCUS_DEVICE_FOUND           10022
#define ERR_NO_AUTOFOCUS_DEVICE       10023

#define ERR_GET_NODE                  10500
#define ERR_GET_AXIOZOOM_ONLY         10501

enum HardwareStops {
   UPPER,
   LOWER
};

class ZeissMonitoringThread;

/**
 * Model that holds info about each device in the Zeiss scope
 */
struct ZeissDeviceInfo {
   ZeissDeviceInfo(){
      lastUpdateTime = 0;
      lastRequestTime = 0;
      currentPos = 0;
      targetPos = 0;
      maxPos = 0;
      upperHardwareStop = 0;
      lowerHardwareStop = 0;
      status = 0;
      measuringOrigin = 0;
      busy = false;
      present = false;
   }
   ~ZeissDeviceInfo(){ 
   }

   MM::MMTime lastUpdateTime;
   MM::MMTime lastRequestTime;
   ZeissLong currentPos;
   ZeissLong targetPos;
   ZeissLong maxPos;
   ZeissLong upperHardwareStop;
   ZeissLong lowerHardwareStop;
   ZeissLong typeDeviation;
   ZeissLong maxDeviation;
   ZeissLong measuringOrigin;
   ZeissULong status;
   std::vector<std::string> deviceScalings;
   std::map<std::string, std::vector<ZeissLong> > nativeScale;
   std::map<std::string, std::vector<ZeissFloat> > scaledScale;
   bool busy;
   bool present;
   void print (MM::Device& device, MM::Core& core) {
      std::ostringstream os;
      for (size_t i = 0; i< deviceScalings.size(); i++) {
         os << "\nScale: " << deviceScalings[i].c_str() ;
         for (size_t j = 0; j< nativeScale[deviceScalings[i]].size(); j++) {
            os << "\nNative: " << nativeScale[deviceScalings[i]][j] << " non-Native: " << scaledScale[deviceScalings[i]][j]; 
         }
         core.LogMessage(&device, os.str().c_str(), false);
      }
   }
};

/**
 * Model that holds current state of the MOTOR FOCUS device
 */
class MotorFocusModel
{
   public:
      MotorFocusModel();
      ~MotorFocusModel(){}

      int GetState(ZeissUByte& status);
      int SetState(ZeissUByte status);
      int SetPosition(ZeissLong pos);
      int GetPosition(ZeissLong& pos);
      int GetBusy(bool& busy);
      int GetInitialized(bool& init);
      void MakeBusy();
      void setWaiting(bool state);
      bool isVaiting() {return waitingForAnswer;}

   private:
      static MMThreadLock mfLock_;
      ZeissLong position_;
      ZeissUByte state_;
      bool waitingForAnswer;
};

/**
 * Model that holds current state of the X or Y stage device
 */
class StageModel
{
   public:
      StageModel();
      ~StageModel(){}

      int GetState(ZeissULong& status);
      int SetState(ZeissULong status);
      int SetPosition(ZeissLong pos);
      int GetPosition(ZeissLong& pos);
      int GetBusy(bool& busy);
      int GetInitialized(bool& init);
      void MakeBusy();
      void setWaiting(bool state);
      bool isVaiting() {return waitingForAnswer;}

   private:
      static MMThreadLock sLock_;
      ZeissLong position_;
      ZeissULong state_;
      bool waitingForAnswer;
};

/**
 * Model that holds current state of the OPTICS device
 */
class OpticsUnitModel
{
   public:
      OpticsUnitModel();
      ~OpticsUnitModel(){}

      int GetState(ZeissUByte& status);
      int SetState(ZeissUByte status);
      int SetZoomLevel(ZeissUShort level);
      int GetZoomLevel(ZeissUShort& level);
      int GetAperture(ZeissByte& a);
      int SetAperture(ZeissByte a);
      int GetBusy(bool& busy);
      int GetInitialized(bool& init);
      void MakeBusy();
      void setWaiting(bool state);
      bool isVaiting() {return waitingForAnswer;}

   private:
      static MMThreadLock ouLock_;
      ZeissUShort zoomLevel;
      ZeissByte aperture;
      ZeissUByte state;
      bool waitingForAnswer;
};   

/**
 * Model that holds current state of the FLUO_TUBE device
 */
class FluoTubeModel
{
   public:
      FluoTubeModel();
      ~FluoTubeModel(){}

      int GetState(ZeissUShort& status);
      int SetState(ZeissUShort status);
      int GetShutterState(ZeissUShort& status);
      int SetShutterState(ZeissUShort status);
      int SetPosition(ZeissUShort p);
      int GetPosition(ZeissUShort& p);
      int SetShutterPosition(ZeissUShort p);
      int GetShutterPosition(ZeissUShort& p);
      int GetBusy(bool& busy);
      int GetShutterBusy(bool& busy);
      int GetInitialized(bool& init);
      void MakeBusy();
      void setWaiting(bool state);
      bool isVaiting() {return waitingForAnswer;}

   private:
      static MMThreadLock ftLock_;
      ZeissUShort position;
      ZeissUShort state;
      ZeissUShort shutterState;
      ZeissUShort shutterPos;
      bool waitingForAnswer;
};   

/**
 * Model that holds current state of the DL_450 device
 */
class DL450Model
{
   public:
      DL450Model();
      ~DL450Model(){}

      int GetState(ZeissUShort& status);
      int SetState(ZeissUShort status);
      int SetPosition(ZeissUShort p);
      int GetPosition(ZeissUShort& p);
      int GetBusy(bool& busy);
      int GetInitialized(bool& init);
      void MakeBusy();
      void setWaiting(bool state);
      bool isWaiting() {return waitingForAnswer;}

   private:
      static MMThreadLock dlLock_;
      ZeissUShort position;
      ZeissUShort state;
      bool waitingForAnswer;
};   

class ZeissHub
{
   friend class ZeissScope;
   friend class ZeissMonitoringThread;

   public:
      ZeissHub();
      ~ZeissHub();

      int ExecuteCommand(MM::Device& device, MM::Core& core, const unsigned char* command, int commandLength, unsigned char targetDevice = 0);
      int GetAnswer(MM::Device& device, MM::Core& core, unsigned char* answer, unsigned long &answerLength); 
      int GetAnswer(MM::Device& device, MM::Core& core, unsigned char* answer, unsigned long &answerLength, unsigned char* signature, unsigned long signatureStart, unsigned long signatureLength); 
      MM::MMTime GetTimeOutTime(){ return timeOutTime_;}
      void SetTimeOutTime(MM::MMTime timeOutTime) { timeOutTime_ = timeOutTime;}


      // Tells whether we have an AxioImager or AxioObserver
      unsigned char GetAxioType() {return targetDevice_;};

      // access functions for device info from microscope model (use lock)
      int GetVersion(MM::Device& device, MM::Core& core, std::string& ver);
      int GetModelPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int GetModelMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& maxPosition);
      int GetModelStatus(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissULong& status);
      int GetModelPresent(MM::Device& device, MM::Core& core, ZeissUByte devId, bool &present);
      int GetModelBusy(MM::Device& device, MM::Core& core, ZeissUByte devId, bool &busy);
      int GetUpperHardwareStop(ZeissUByte devId, ZeissLong& position);
      int GetLowerHardwareStop(ZeissUByte devId, ZeissLong& position);
      // a few classes can set devices in the microscope model to be busy
      int SetModelBusy( ZeissUByte devId, bool busy);

      // Switch LEDS in Colibri on or off.  Needs to be in hub as the monitoringThread can access this too
      int ColibriOnOff(MM::Device& device, MM::Core& core, ZeissByte ledNr, bool state);
      int ColibriExternalShutter(MM::Device& device, MM::Core& core, bool state);
      int ColibriOperationMode(MM::Device& device, MM::Core& core, ZeissByte mode);
      int ColibriBrightness(MM::Device& device, MM::Core& core, int ledNr, ZeissShort brightness);

      ZeissUByte GetCommandGroup(ZeissUByte devId) {return commandGroup_[devId];};

      static std::string reflectorList_[];
      static std::string objectiveList_[];
      static std::string tubeLensList_[];
      static std::string sidePortList_[];
      static std::string condenserList_[];
      static const bool debug_ = true;
      std::string port_;
      bool portInitialized_;
      static const int RCV_BUF_LENGTH = 1024;
      unsigned char rcvBuf_[RCV_BUF_LENGTH];
      static ZeissDeviceInfo deviceInfo_[];
      MotorFocusModel motorFocusModel_;
      StageModel stageModelX_;
      StageModel stageModelY_;
      OpticsUnitModel opticsUnitModel_;
      FluoTubeModel fluoTubeModel_;
      DL450Model dl450Model_;

   private:
      static ZeissUByte commandGroup_[MAXNUMBERDEVICES];
      void ClearRcvBuf();
      int ClearPort(MM::Device& device, MM::Core& core);
      //void SetPort(const char* port) {port_ = port; portInitialized_ = true;}
      int Initialize(MM::Device& device, MM::Core& core);
      int GetVersion(MM::Device& device, MM::Core& core);
      int FindDevices(MM::Device& device, MM::Core& core);
	  int GetCANAdrress(MM::Device& device, MM::Core& core);

      // These are used by MonitoringThread to set values in our microscope model
      int SetModelPosition(ZeissUByte devId, ZeissLong position);
      int SetUpperHardwareStop(ZeissUByte devId, ZeissLong position);
      int SetLowerHardwareStop(ZeissUByte devId, ZeissLong position);
      int SetModelStatus(ZeissUByte devId, ZeissULong status);

      // Helper function for GetAnswer
      bool signatureFound(unsigned char* answer, unsigned char* signature, unsigned long signatureStart, unsigned long signatureLength);

      // functions used only from initialize, they set values in our abstract microscope 'deviceInfo_', based on information obtained from the microscope
      int GetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissLong& position);
      int GetMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissLong& position);
      int GetDeviceScalings(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo);
      int GetScalingTable(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo, std::string unit);
      int GetMeasuringOrigin(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo);
      int GetPermanentParameter(MM::Device& device, MM::Core& core, ZeissUShort descriptor, ZeissByte entry, ZeissUByte& dataType, unsigned char* data, unsigned char& dataLength);
      int GetReflectorLabels(MM::Device& device, MM::Core& core);
      int GetObjectiveLabels(MM::Device& device, MM::Core& core);
      int GetTubeLensLabels(MM::Device& device, MM::Core& core);
      int GetSidePortLabels(MM::Device& device, MM::Core& core);
      int GetCondenserLabels(MM::Device& device, MM::Core& core);
      int InitDev(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId);
      int InitDevX(MM::Device& device, MM::Core& core, ZeissUByte devId);

      // functions used to initialize Focus Motor
      int InitMotorFocusEventMonitoring(MM::Device& device, MM::Core& core, bool start);
      int GetMotorFocusInfo(MM::Device& device, MM::Core& core);

      // functions used to initialize stages
      int InitStageEventMonitoring(MM::Device& device, MM::Core& core, bool start, unsigned char devNodeId);
      int GetStageInfo(MM::Device& device, MM::Core& core, unsigned char id);

      // functions used to initialize optics unit
      int InitOpticsEventMonitoring(MM::Device& device, MM::Core& core, bool start);
      int GetOpticsInfo(MM::Device& device, MM::Core& core);

      // functions used to initialize fluo tube unit
      int InitFluoTubeEventMonitoring(MM::Device& device, MM::Core& core);
      int GetFluoTubeInfo(MM::Device& device, MM::Core& core);

      // functions used to initialize DL450
      int InitDL450EventMonitoring(MM::Device& device, MM::Core& core);
      int GetDL450Info(MM::Device& device, MM::Core& core);


      // functions for  Colibri
      int GetLEDInfo(MM::Device& device, MM::Core& core, int ledNr);
      int GetColibriInfo(MM::Device& device, MM::Core& core);
      int InitColibriEventMonitoring(MM::Device& device, MM::Core& core, bool start);
      int RequestColibriBrightness(MM::Device& device, MM::Core& core, ZeissByte ledNr);
      int RequestColibriOnOff(MM::Device& device, MM::Core& core, ZeissByte ledNr);
      int RequestColibriOperationMode(MM::Device& device, MM::Core& core);
      int RequestColibriExternalOnOff(MM::Device& device, MM::Core& core);


      // BIOS level functions
      // Figure out which CAN nodes are present in this system
      int GetCANNodes(MM::Device& device, MM::Core& core);
      // Do software reset.  Currently only called from Definite Focus module
      int GetBiosInfo(MM::Device& device, MM::Core& core, ZeissUByte canNode, ZeissUByte infoType, std::string& info);
      // vector to store CAN nodes present in the system
      std::vector<ZeissByte> canNodes_;

      MMThreadLock mutex;
      MMThreadLock executeLock_;
      std::vector<ZeissUByte > availableDevices_;
      std::string version_;

      unsigned char targetDevice_;
      ZeissMonitoringThread* monitoringThread_;
      MM::MMTime timeOutTime_;
      bool scopeInitialized_;
};


/*
 * ZeissMessageParser: Takes a stream containing CAN29 messages and
 * splits this stream into individual messages.
 * Also removes escaped characters (like 0x10 0x10) 
 */
class ZeissMessageParser{
   public:
      ZeissMessageParser(unsigned char* inputStream, long inputStreamLength);
      ~ZeissMessageParser(){};
      int GetNextMessage(unsigned char* nextMessage, int& nextMessageLength);
      static const int messageMaxLength_ = 64;

   private:
      unsigned char* inputStream_;
      long inputStreamLength_;
      long index_;
};

class ZeissMonitoringThread : public MMDeviceThreadBase
{
   public:
      ZeissMonitoringThread(MM::Device& device, MM::Core& core, ZeissHub& hub, ZeissDeviceInfo* deviceInfo, bool debug); 
      ~ZeissMonitoringThread(); 
      int svc();
      int open (void*) { return 0;}
      int close(unsigned long) {return 0;}

      void Start();
      void Stop() {stop_ = true;}

   private:
      ZeissDeviceInfo * deviceInfo_;
      //MM_THREAD_HANDLE thread_;
      MM::Device& device_;
      MM::Core& core_;
      ZeissHub& hub_;
      bool debug_;
      bool stop_;
      long intervalUs_;
      ZeissMonitoringThread& operator=(ZeissMonitoringThread& /*rhs*/) {assert(false); return *this;}
      void interpretMessageAZ(unsigned char* message);
};

class ZeissScope : public CGenericBase<ZeissScope>
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
      
      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnAnswerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      bool initialized_;
      std::string port_;
      double answerTimeoutMs_;
};


///////////////// Micro-Manager Devices ////////////////////////


class XYStageX : public CXYStageBase<XYStageX>
{
public:
   XYStageX();
   ~XYStageX();

   // Device API
   // ---------
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API                                                            
   // -----------
  int SetPositionSteps(long x, long y);
  int GetPositionSteps(long& x, long& y);
  int Home();
  int Stop();
  int SetOrigin();
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  double GetStepSizeXUm() {return stepSize_um_;}
  double GetStepSizeYUm() {return stepSize_um_;}
  int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetPositionStepsDirect(long& x, long& y);
   double stepSize_um_;
   bool busy_;
   bool initialized_;
   double lowerLimitX_;
   double upperLimitX_;
   double lowerLimitY_;
   double upperLimitY_;
   long moveMode_;
   long velocity_;
   std::string name_;
   std::string description_;
   std::string direct_, uni_, biSup_, biAlways_, fast_, smooth_;
};

class MotorFocus : public CStageBase<MotorFocus>
{
public:
   MotorFocus(std::string name, std::string description);
   ~MotorFocus();

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
   int OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetPositionStepsDirect(long& steps);
   double stepSize_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   long moveMode_;
   long velocity_;
   std::string name_;
   std::string description_;
   std::string direct_, uni_, biSup_, biAlways_, fast_, smooth_;
   long busyCounter_;
   ZeissByte devId_;
};

class OpticsUnit : public CGenericBase<OpticsUnit>
{
public:
   OpticsUnit(std::string name, std::string description);
   ~OpticsUnit();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // action interface
   // ----------------
   int OnZoomLevel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAperture(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetZoomLevelDirect(long& level);
   int GetZoomLevel(long& level);
   int SetZoomLevel(long level);
   int GetApertureDirect(long& a);
   int GetAperture(long& a);
   int SetAperture(long a);

   bool busy;
   bool initialized;
   std::string name;
   std::string description;
   ZeissByte devId;
};

class FluoTube : public CStateDeviceBase<FluoTube>
{
public:
   FluoTube();
   ~FluoTube();

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
   int OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   static const unsigned int numPos_ = 5;

private:
   int SetPosition(unsigned short pos);
   int GetPosition(unsigned short& pos);
   int GetPositionDirect(unsigned short& pos);

   int SetShutterPos(unsigned short pos);
   int GetShutterPosDirect(unsigned short& pos);
   int GetShutterPos(unsigned short& pos);
   int GetStatusDirect(unsigned short& status);

   bool initialized_;
   unsigned short pos_;
   unsigned short shutterPos_;
};

class LampDL450 : public CStateDeviceBase<LampDL450>
{
public:
   LampDL450();
   ~LampDL450();

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

protected:
   static const unsigned int numPos_ = 5;

private:
   int SetPosition(unsigned short pos);
   int GetPosition(unsigned short& pos);
   int GetPositionDirect(unsigned short& pos);
   int GetStatusDirect(unsigned short& status);

   bool initialized_;
   unsigned short pos_;
};
#endif // _ZeissCAN29_H_
