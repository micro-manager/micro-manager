///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissCAN.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN bus adapater
//   
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       Please note: This code could only be developed thanks to information 
//                provided by Zeiss under a non-disclosure agreement.  Subsequently, 
//                this code has been reviewed by Zeiss and we were permitted to release 
//                this under the LGPL on 1/16/2008 (and again on 7/3/2208 after changes 
//                to the code).  If you modify this code using information you obtained 
//                under a NDA with Zeiss, you will need to ask Zeiss whether you can release 
//                your modifications.  
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
// AUTOR:         Nico Stuurman, nico@cmp.ucsf.edu 5/14/2007
// REVISIONS:     Added Win32 compatibilty, N.A. 11/20/2007

#ifndef _ZEISSCAN29_H_
#define _ZEISSCAN29_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <vector>
#include <map>

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

// Target addresses for various Zeiss CAN controllers:
#define AXIOOBSERVER 0x19

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
#define ERR_PORT_NOT_OPEN            10016

enum HardwareStops {
   UPPER,
   LOWER
};

class ZeissMonitoringThread;

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
      printf ("Found %d scalings\n", (int) deviceScalings.size());
      for (size_t i = 0; i< deviceScalings.size(); i++) {
         os << "\nScale: " << deviceScalings[i].c_str() ;
         //printf("Scale: %s\n", deviceScalings[i].c_str());
         //printf ("Found %d nativeScales\n", (int) nativeScale[deviceScalings[i]].size());
         for (size_t j = 0; j< nativeScale[deviceScalings[i]].size(); j++) {
            os << "\nNative: " << nativeScale[deviceScalings[i]][j] << " non-Native: " << scaledScale[deviceScalings[i]][j]; 
            //printf("Native: %d, non-native: %f\n", nativeScale[deviceScalings[i]][j], scaledScale[deviceScalings[i]][j]);
         }

         core.LogMessage(&device, os.str().c_str(), false);
      }
   }
};

   
class ZeissHub
{
   friend class ZeissScope;
   friend class ZeissMonitoringThread;

   public:
      ZeissHub();
      ~ZeissHub();

      int ExecuteCommand(MM::Device& device, MM::Core& core, const unsigned char* command, int commandLength, unsigned char targetDevice = AXIOOBSERVER);
      int GetAnswer(MM::Device& device, MM::Core& core, unsigned char* answer, unsigned long &answerLength); 
      int GetAnswer(MM::Device& device, MM::Core& core, unsigned char* answer, unsigned long &answerLength, unsigned char* signature, unsigned long signatureStart, unsigned long signatureLength); 
      MM::MMTime GetTimeOutTime(){ return timeOutTime_;}
      void SetTimeOutTime(MM::MMTime timeOutTime) { timeOutTime_ = timeOutTime;}

      // access functions for device info from microscope model (use lock)
      int GetVersion(MM::Device& device, MM::Core& core, std::string& ver);
      int GetModelPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int GetModelMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& maxPosition);
      int GetModelStatus(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissULong& status);
      int GetModelPresent(MM::Device& device, MM::Core& core, ZeissUByte devId, bool &present);
      int GetModelBusy(MM::Device& device, MM::Core& core, ZeissUByte devId, bool &busy);
      // a few classes can set devices in the microscope model to be busy
      int SetModelBusy( ZeissUByte devId, bool busy);

      static std::string reflectorList_[10];
      static std::string objectiveList_[7];
      static std::string tubeLensList_[5];
      static std::string sidePortList_[3];
      static std::string condenserList_[7];
      std::string port_;
      bool portInitialized_;
      static const int RCV_BUF_LENGTH = 1024;
      unsigned char rcvBuf_[RCV_BUF_LENGTH];

   private:
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

      ZeissMonitoringThread* monitoringThread_;
      MM::MMTime timeOutTime_;
      //static ZeissDeviceInfo deviceInfo_[MAXNUMBERDEVICES];
      std::vector<ZeissUByte > availableDevices_;
      //static std::vector<ZeissUByte > commandGroup_; // relates device to commandgroup, initialized in constructor
      std::string version_;
      bool scopeInitialized_;
};

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

class ZeissMonitoringThread
{
   public:
      ZeissMonitoringThread(MM::Device& device, MM::Core& core); 
      ~ZeissMonitoringThread(); 
      static MM_THREAD_FUNC_DECL svc(void *arg);
      int open (void*) { return 0;}
      int close(unsigned long) {return 0;}

      void Start();
      void Stop() {stop_ = true;}
      void wait() {MM_THREAD_JOIN(thread_);}

   private:
      MM_THREAD_HANDLE thread_;
      void interpretMessage(unsigned char* message);
      MM::Device& device_;
      MM::Core& core_;
      bool stop_;
      long intervalUs_;
      ZeissMonitoringThread& operator=(ZeissMonitoringThread& /*rhs*/) {assert(false); return *this;}
};


class ZeissDevice
{
   protected:
      ZeissDevice();
      virtual ~ZeissDevice();

      int GetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int SetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, int position, unsigned char targetDevice = AXIOOBSERVER);
      int GetTargetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int GetMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position);
      int GetStatus(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissULong& status);
      int SetLock(MM::Device& device, MM::Core& core, ZeissUByte devId, bool on, unsigned char targetDevice = AXIOOBSERVER);
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

      int SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long position, ZeissByte moveMode, unsigned char targetDevice = AXIOOBSERVER);
      int SetRelativePosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long increment, ZeissByte moveMode, unsigned char targetDevice = AXIOOBSERVER);
      int FindHardwareStop(MM::Device& device, MM::Core& core, ZeissUByte devId, HardwareStops stop, unsigned char targetDevice = AXIOOBSERVER);
      int StopMove(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissByte moveMode, unsigned char targetDevice = AXIOOBSERVER);
      ZeissUByte devId_;

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

   private:
      bool initialized_;
      std::string port_;
      double answerTimeoutMs_;
};


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

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   //int GetUpperLimit();
   //int GetLowerLimit();
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
  int SetPositionUm(double x, double y);
  int SetRelativePositionUm(double x, double y);
  int GetPositionUm(double& x, double& y);
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

   // action interface
   // ----------------
   int OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   ZeissAxis xAxis_;
   ZeissAxis yAxis_;
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

#endif // _ZeissCAN29_H_
