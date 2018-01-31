///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissCAN29.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN29 bus controller, see Zeiss CAN29 bus documentation
//                
// AUTHOR: Nico Stuurman, 6/18/2007 - 
//
// COPYRIGHT:     University of California, San Francisco, 2007, 2008, 2009
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
      trajectoryVelocity = 0;
      trajectoryAcceleration = 0;
      hasTrajectoryInfo = false;
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
   ZeissLong trajectoryVelocity;
   ZeissLong trajectoryAcceleration;
   bool hasTrajectoryInfo;
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


/*
 * Helper class to store Definite Focus offsets
 */
class DFOffset
{
   public:
      DFOffset(ZeissUByte length) :
         length_ (length)
      {
         if (length > 0)
            data_ = new ZeissUByte[length_];
      };
      DFOffset(const DFOffset& original)
      {
         length_ = original.length_;
         if (length_ > 0) {
            data_ = new ZeissUByte[length_];
            for (ZeissUByte i=0; i<length_; i++)
               data_[i] = original.data_[i];
         }
      };
      DFOffset() : length_(0) {
      };
      ~DFOffset()
      {
         if (length_ != 0)
            delete[] (data_);
      };

      DFOffset& operator=(DFOffset x)
      {
         length_ = x.length_;
         data_ = new ZeissUByte[length_];
         for (ZeissUByte i=0; i<x.length_; i++) {
            data_[i] = x.data_[i];
         }
         return *this;
      }
      bool operator==(DFOffset x) 
      {
         if (x.length_ == length_) {
            for (ZeissUByte i=0; i<x.length_; i++) {
               if (x.data_[i] != data_[i])
                  return false;
            }
            return true;
         }
         return false;
      };
      ZeissUByte length_;
      ZeissUByte* data_;
};

/**
 * Model that holds current state of the Definite focus device
*/
class DefiniteFocusModel
{
   public:
      DefiniteFocusModel();

      int GetControlOnOff(ZeissByte& controlOnOff);
      int SetControlOnOff(ZeissByte controlOnOff); 
      int GetPeriod(ZeissULong& period);
      int SetPeriod(ZeissULong period);
      int GetStatus(ZeissUShort& status);
      int SetStatus(ZeissUShort status);
      int GetVersion(ZeissUShort& version);
      int SetVersion(ZeissUShort version);
      int GetError(ZeissUShort& error);
      int SetError(ZeissUShort error);
      int GetDeviation(ZeissLong& deviation);
      int SetDeviation(ZeissLong deviation);
      DFOffset GetData();
      int SetData(ZeissUByte dataLength, ZeissUByte* data);
      int GetBrightnessLED(ZeissByte& brightness);
      int SetBrightnessLED(ZeissByte brightness);
      int GetShutterFactor(ZeissShort& shutterFactor);
      int GetWorkingPosition(double& workingPosition);
      int SetWorkingPosition(double workingPosition);
      int SetShutterFactor(ZeissShort shutterFactor);
      bool GetWaitForStabilizationData();
      int SetWaitForStabilizationData(bool state);
      int GetBusy(bool& busy);
      int SetBusy(bool busy);

   private:
      MMThreadLock dfLock_;
      ZeissByte controlOnOff_;
      ZeissULong period_;
      ZeissUShort status_;
      ZeissUShort version_;
      ZeissUShort error_;
      ZeissLong deviation_;
      ZeissUByte dataLength_;
      ZeissUByte data_[UCHAR_MAX];
      ZeissByte brightnessLED_;
      ZeissShort shutterFactor_;
      double workingPosition_;
      bool waitForStabilizationData_;
      bool busy_;
};
   
/*
 * Helper class for Colibri
 */
class LEDInfo
{
   public:
      ZeissByte serialNumber_[8];
      char orderId_[15];
      int wavelengthNm_;
      int calRefValue_;
      char name_[21];
      int halfPowerBandwidth_;
      int nominalCurrent_;
};

/*
 * Helper class for Colibri
 */
class TriggerBufferEntry
{
   public:
      ZeissShort brightness_;
      ZeissULong duration_;
};

/*
 * Model and Access functions for Colibri
 * LEDS are Zeiss DevId 1-4.  States of various properties are stored in (zero-based) arrays.
 */
class ColibriModel
{
   public:
      ColibriModel(); 
         
      int GetStatus(ZeissULong& status);
      int SetStatus(ZeissULong status);
      ZeissShort GetBrightness(int ledNr);
      ZeissShort GetCalibrationValue(int ledNr);
      std::string GetName(int ledNr);
      std::string GetInfo(int ledNr);
      int SetBrightness(int ledNr, ZeissShort brightness);

      bool GetOpen();

      // access function to actual onoff states of individual LEDs
      int GetOnOff(int ledNr);
      int SetOnOff(int ledNr, ZeissByte onOff);
      ZeissByte GetExternalShutterState();
      void SetExternalShutterState(ZeissByte externalShutterState);

      bool GetBusy();
      bool GetBusy(int ledNr);
      int SetBusy(int ledNr, bool busy);
      int SetBusyExternal(bool busy);
      LEDInfo GetLEDInfo(int ledNr);
      ZeissByte GetMode();
      void SetMode (ZeissByte mode);

      static const int NRLEDS = 4;
      LEDInfo info_[NRLEDS];
      std::string infoString_[NRLEDS];
      bool available_[NRLEDS];

   private:
      MMThreadLock dfLock_;
      ZeissShort calibrationValue_[NRLEDS];
      ZeissShort brightness_[NRLEDS];
      ZeissByte onOff_[NRLEDS];
      std::string name_[NRLEDS];
      ZeissByte operationMode_;
      ZeissULong status_;
      ZeissByte externalShutterState_;
      bool busy_[NRLEDS];
      bool busyExternal_;
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
      int GetModelTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& velocity);
      int HasModelTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, bool &hasTV);
      int GetModelTrajectoryAcceleration(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& acceleration);
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
      static DefiniteFocusModel definiteFocusModel_;
      static ColibriModel colibriModel_;

   private:
      static ZeissUByte commandGroup_[MAXNUMBERDEVICES];
      void ClearRcvBuf();
      int ClearPort(MM::Device& device, MM::Core& core);
      //void SetPort(const char* port) {port_ = port; portInitialized_ = true;}
      int Initialize(MM::Device& device, MM::Core& core);
      int GetVersion(MM::Device& device, MM::Core& core);
      int FindDevices(MM::Device& device, MM::Core& core);

      // These are used by MonitoringThread to set values in our microscope model
      int SetModelPosition(ZeissUByte devId, ZeissLong position);
      int SetUpperHardwareStop(ZeissUByte devId, ZeissLong position);
      int SetLowerHardwareStop(ZeissUByte devId, ZeissLong position);
      int SetModelStatus(ZeissUByte devId, ZeissULong status);
      int SetTrajectoryVelocity(ZeissUByte devId, ZeissLong velocity);
      int SetTrajectoryAcceleration(ZeissUByte devId, ZeissLong acceleration);

      // Helper function for GetAnswer
      bool signatureFound(unsigned char* answer, unsigned char* signature, unsigned long signatureStart, unsigned long signatureLength);

      // functions used only from initialize, they set values in our abstract microscope 'deviceInfo_', based on information obtained from the microscope
      int GetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissLong& position);
      int GetMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissLong& position);
      int GetDeviceScalings(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo);
      int GetScalingTable(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo, std::string unit);
      int GetMeasuringOrigin(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo);
      int GetTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo);
      int GetTrajectoryAcceleration(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo);
      int GetPermanentParameter(MM::Device& device, MM::Core& core, ZeissUShort descriptor, ZeissByte entry, ZeissUByte& dataType, unsigned char* data, unsigned char& dataLength);
      int GetReflectorLabels(MM::Device& device, MM::Core& core);
      int GetObjectiveLabels(MM::Device& device, MM::Core& core);
      int GetTubeLensLabels(MM::Device& device, MM::Core& core);
      int GetSidePortLabels(MM::Device& device, MM::Core& core);
      int GetCondenserLabels(MM::Device& device, MM::Core& core);
      int InitDev(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId);

      // functions used to initialize DefiniteFocus
      int InitDefiniteFocusEventMonitoring(MM::Device& device, MM::Core& core, bool start);
      int GetDefiniteFocusInfo(MM::Device& device, MM::Core& core);

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

      MMThreadLock mutex_;
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
      void interpretMessage(unsigned char* message);
      MM::Device& device_;
      MM::Core& core_;
      ZeissHub& hub_;
      bool debug_;
      bool stop_;
      long intervalUs_;
      ZeissMonitoringThread& operator=(ZeissMonitoringThread& /*rhs*/) {assert(false); return *this;}
};


///////////////// Zeiss Devices ////////////////////////
/**
 * Base class for all Zeiss Devices
 */
class ZeissDevice
{
   protected:
      ZeissDevice();
      virtual ~ZeissDevice();

      int GetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int SetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, int position);
      int GetStatus(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId);
      int GetTargetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int GetMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int GetStatus(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissULong& status);
      int SetLock(MM::Device& device, MM::Core& core, ZeissUByte devId, bool on);
      int GetBusy(MM::Device& device, MM::Core& core, ZeissUByte devId, bool& busy);
      int GetPresent(MM::Device& device, MM::Core& core, ZeissUByte devId, bool& present);

   private:
};


class ZeissChanger : public ZeissDevice
{
   public:
      ZeissChanger();
      ~ZeissChanger();

      int SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, int position);
      ZeissUByte devId_;

   private:
      const static ZeissUByte commandGroup_ = 0xA1;
};

class ZeissServo : public ZeissDevice
{
   public:
      ZeissServo();
      ~ZeissServo();

      int SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, int position);
      ZeissUByte devId_;

   private:
      const static ZeissUByte commandGroup_ = 0xA2;
 
};

class ZeissAxis : public ZeissDevice
{
   public:
      ZeissAxis();
      ~ZeissAxis();

      int SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long position, ZeissByte moveMode);
      int SetRelativePosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long increment, ZeissByte moveMode);
      int FindHardwareStop(MM::Device& device, MM::Core& core, ZeissUByte devId, HardwareStops stop);
      int StopMove(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissByte moveMode);
      int HasTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, bool& hasTV);
      int SetTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, long velocity);
      int SetTrajectoryAcceleration(MM::Device& device, MM::Core& core, ZeissUByte devId, long acceleration);
      int GetTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& velocity);
      int GetTrajectoryAcceleration(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& acceleration);

   private:
      const static ZeissUByte commandGroup_ = 0xA3;
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
      int OnVersionChange(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      bool initialized_;
      std::string port_;
};


///////////////// Micro-Manager Devices ////////////////////////
/**
 * Shutter
 */
class Shutter : public CShutterBase<Shutter>, public ZeissChanger
{
public:
   Shutter(ZeissUByte devId, std::string name, std::string description);
   ~Shutter();

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
   unsigned shutterNr_;
   std::string name_;
   std::string description_;
   bool state_;
};

class Turret : public CStateDeviceBase<Turret>,  public ZeissChanger
{
public:
   Turret(ZeissUByte devId, std::string name, std::string description);
   ~Turret();

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
   unsigned int numPos_;

private:
   bool initialized_;
   long pos_;
   std::string name_;
   std::string description_;
};
      
class ReflectorTurret : public Turret
{
public:
   ReflectorTurret(ZeissUByte devId, std::string name, std::string description);
   ~ReflectorTurret();

   // MMDevice API
   int Initialize();
};

class ObjectiveTurret : public Turret
{
public:
   ObjectiveTurret(ZeissUByte devId, std::string name, std::string description);
   ~ObjectiveTurret();

   // MMDevice API
   int Initialize();
};

class TubeLensTurret : public Turret
{
public:
   TubeLensTurret(ZeissUByte devId, std::string name, std::string description);
   ~TubeLensTurret();

   // MMDevice API
   int Initialize();
};

class SidePortTurret : public Turret
{
public:
   SidePortTurret(ZeissUByte devId, std::string name, std::string description);
   ~SidePortTurret();

   // MMDevice API
   int Initialize();
};

class CondenserTurret : public Turret
{
public:
   CondenserTurret(ZeissUByte devId, std::string name, std::string description);
   ~CondenserTurret();

   // MMDevice API
   int Initialize();
};

class Servo : public CGenericBase<Servo>,  public ZeissServo
{
public:
   Servo(ZeissUByte devId, std::string name, std::string description);
   ~Servo();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ---------------
   unsigned long GetNumberOfPositions()const {return numPos_;};
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   double minPosScaled_, maxPosScaled_;
   ZeissLong minPosNative_, maxPosNative_;
   bool initialized_;
   unsigned int numPos_;
   std::string name_;
   std::string description_;
   std::string unit_;
};


class Axis : public CStageBase<Axis>, public ZeissAxis
{
public:
   Axis(ZeissUByte devId, std::string name, std::string description);
   ~Axis();

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
   ZeissUByte devId_;
   //int GetUpperLimit();
   //int GetLowerLimit();
   double stepSize_um_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   long moveMode_;
   long velocity_;
   std::string name_;
   std::string description_;
   std::string direct_, uni_, biSup_, biAlways_, default_, fast_, smooth_;
   long busyCounter_;

};

class XYStage : public CXYStageBase<XYStage>, public ZeissAxis
{
public:
   XYStage();
   ~XYStage();

   // Device API
   // ---------
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API                                                            
   // -----------
  int SetPositionSteps(long x, long y);
  int SetRelativePositionSteps(long x, long y);
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
   int OnTrajectoryVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrajectoryAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double stepSize_um_;
   bool initialized_;
   long moveMode_;
   long velocity_;
   std::string name_;
   std::string description_;
   std::string direct_, uni_, biSup_, biAlways_, default_, fast_, smooth_;
};

class DefiniteFocus : public CAutoFocusBase<DefiniteFocus>
{
   public:
      DefiniteFocus();
      ~DefiniteFocus();

      // MMDevice API
      bool Busy();
      void GetName(char* pszName) const;

      int Initialize();
      int Shutdown();

      // AutoFocus API
      virtual int SetContinuousFocusing(bool state);
      virtual int GetContinuousFocusing(bool& state);
      bool isContinuousFocusingAvailable() {return true;};
      virtual int FullFocus();
      virtual int IncrementalFocus();
      virtual int GetLastFocusScore(double& /*score*/) {return DEVICE_UNSUPPORTED_COMMAND;}
      virtual int GetCurrentFocusScore(double& score) { score = 0.0; return DEVICE_OK;}
      virtual bool IsContinuousFocusLocked();
      int GetOffset(double& offset);
      int SetOffset(double offset);
      int OnPeriod(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnDFWorkingPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnFocusMethod(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      int FocusControlOnOff(bool state);
      int GetStabilizedPosition();
      int IlluminationFeatures();
      int Wait();
      int WaitForStabilizationData();
      int WaitForBusy();
      int StabilizeThisPosition(ZeissUByte dataLength, ZeissUByte* data = 0);
      int StabilizeThisPosition(DFOffset position);
      int StabilizeLastPosition();

      std::vector<DFOffset> offsets_;
      DFOffset currentOffset_;
      std::string focusMethod_;
      
      bool initialized_;
};

/**
 * Treats the Definite Focus Offset as a Drive.
 * Can be used to make the DF offset appear in the position list
 */
class DFOffsetStage : public CStageBase<DFOffsetStage>
{
public:
   DFOffsetStage();
   ~DFOffsetStage();
  
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

  int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
  bool IsContinuousFocusDrive() const {return true;}

   // action interface
   // ----------------
   int OnAutoFocusDevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableAutoFocusDevices_;
   MM::AutoFocus* autoFocusDevice_;
   std::string autoFocusDeviceName_;
   bool initialized_;
};

class Colibri : public CShutterBase<Colibri>
{
   public:
      Colibri();
      ~Colibri();

      // MMDevice API
      int Initialize();
      int Shutdown();
      bool Busy();
      void GetName(char* pszName) const;

      // action interface
      int OnExternalShutter(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
      int OnName(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
      int OnInfo(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
      int OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct);

      // Shutter API
      int SetOpen(bool open = true);
      int GetOpen(bool& open);
      int Fire(double /* deltaT */) {return DEVICE_UNSUPPORTED_COMMAND;};

   private:
      int OnOff(int ledNr, bool state);

      bool initialized_;
      bool useExternalShutter_;
};

#endif // _ZeissCAN29_H_
