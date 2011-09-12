///////////////////////////////////////////////////////////////////////////////
// FILE:          Thorlabs.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD102 Controller
//
// COPYRIGHT:     Thorlabs Inc, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 2011
//

#ifndef _THORLABS_H_
#define _THORLABS_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_UNSPECIFIED_ERROR        10010
#define ERR_HOME_REQUIRED            10011
#define ERR_INVALID_PACKET_LENGTH    10012
#define ERR_RESPONSE_TIMEOUT         10013
#define ERR_BUSY                     10014
#define ERR_STEPS_OUT_OF_RANGE       10015

// utility functions
int ClearPort(MM::Device& device, MM::Core& core);
unsigned const char* GenerateYCommand(const unsigned char* xCmd);

/////////////////////////////////////////////////////////////////////////////
// Device specific data structures
// Declarations copied from Thorlabs documentation 
/////////////////////////////////////////////////////////////////////////////
typedef struct _HWINFO
{
  unsigned long dwSerialNum;		   // Unique 8 digit serial number.
  char szModelNum[8];		         // Alphanumeric model number.
  unsigned short wHWType;		      // Hardware type ident (see #defines below).
  unsigned long dwSoftwareVersion;	// Software version
  char szNotes[64];		            // Arbitrary alphanumeric info string.
  unsigned short wNumChannels;		// Number of channels of operation
} HWINFO;

// velocity and acceleration parameters
typedef struct _MOTVELPARAMS
{
  unsigned short wChannel;	// Channel ident (see #defines earlier).
  long lMinVel;	         // Minimum (start) velocity
  long lAccn;		         // Acceleration in position pos. steps/sec*sec 
  long lMaxVel;	         // Maximum (final) velocity in pos. steps/sec 
} MOTVELPARAMS;

// motor status parameters
typedef struct _DCMOTSTATUS
{
   unsigned short wChannel;   // Channel ident.
	long lPosition;			   // Position in encoder counts. 
	unsigned short wVelocity;  // Velocity in encoder counts/sec.
	unsigned short wReserved;	// Controller specific use 
	unsigned long dwStatusBits;// Status bits (see #defines below).
} DCMOTSTATUS;

///////////////////////////////////////////////////////////////////////////////
// fixed stage parameters
///////////////////////////////////////////////////////////////////////////////
const int cmdLength = 6;               // command block length
const long xAxisMaxSteps = 2200000L;   // maximum number of steps in X
const long yAxisMaxSteps = 1500000L;   // maximum number of steps in Y
const double stepSizeUm = 0.05;        // step size in microns
const double accelScale = 13.7438;     // scaling factor for acceleration
const double velocityScale = 134218.0; // scaling factor for velocity

//////////////////////////////////////////////////////////////////////////////
// global utility
int ClearPort(MM::Device& device, MM::Core& core, std::string port);

//////////////////////////////////////////////////////////////////////////////
// XYStage class
// (device adapter)
//////////////////////////////////////////////////////////////////////////////
class CommandThread;

class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();

   friend class CommandThread;
  
   // Device API
   // ----------
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
   double GetStepSizeXUm() {return stepSizeUm;}
   double GetStepSizeYUm() {return stepSizeUm;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMoveTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   
   enum Axis {X, Y};

   int MoveBlocking(long x, long y, bool relative = false);
   int SetCommand(const unsigned char* command, unsigned cmdLength);
   int GetCommand(unsigned char* answer, unsigned answerLength, double TimeoutMs);
   int SetVelocityProfile(const MOTVELPARAMS& params, Axis a);
   int GetVelocityProfile(MOTVELPARAMS& params, Axis a);
   int FillHardwareInfo();
   int ParseStatus(const unsigned char* buf, int bufLen, DCMOTSTATUS& stat);
   int ParseVelocityProfile(const unsigned char* buf, int bufLen, MOTVELPARAMS& params);
   int GetStatus(DCMOTSTATUS& stat, Axis a);

   bool initialized_;            // true if the device is intitalized
   bool home_;                   // true if stage is homed
   std::string port_;            // com port name
   double answerTimeoutMs_;      // max wait for the device to answer
   double moveTimeoutMs_;        // max wait for stage to finish moving
   HWINFO info_;                 // hardware information
   CommandThread* cmdThread_;    // thread used to execute move commands
};

///////////////////////////////////////////////////////////////////////////////
// CommandThread class
// (for executing move commands)
///////////////////////////////////////////////////////////////////////////////

class CommandThread : public MMDeviceThreadBase
{
   public:
      CommandThread(XYStage* stage) :
         stop_(false), moving_(false), stage_(stage), errCode_(DEVICE_OK) {}

      virtual ~CommandThread() {}

      int svc();
      void Stop() {stop_ = true;}
      bool GetStop() {return stop_;}
      int GetErrorCode() {return errCode_;}
      bool IsMoving()  {return moving_;}

      void StartMove(long x, long y)
      {
         Reset();
         x_ = x;
         y_ = y;
         cmd_ = MOVE;
         activate();
      }

      void StartMoveRel(long dx, long dy)
      {
         Reset();
         x_ = dx;
         y_ = dy;
         cmd_ = MOVEREL;
         activate();
      }

      void StartHome()
      {
         Reset();
         cmd_ = HOME;
         activate();
      }

   private:
      void Reset() {stop_ = false; errCode_ = DEVICE_OK; moving_ = false;}
      enum Command {MOVE, MOVEREL, HOME};
      bool stop_;
      bool moving_;
      XYStage* stage_;
      long x_;
      long y_;
      Command cmd_;
      int errCode_;
};


//class PiezoZStage : public CStageBase<PiezoZStage>
//{
//public:
//   PiezoZStage();
//   ~PiezoZStage();
//  
//   // Device API
//   // ----------
//   int Initialize();
//   int Shutdown();
//  
//   void GetName(char* pszName) const;
//   bool Busy();
//
//   // Stage API
//   // ---------
//   int SetPositionUm(double pos);
//   int GetPositionUm(double& pos);
//   int SetPositionSteps(long steps);
//   int GetPositionSteps(long& steps);
//   int SetOrigin();
//   int GetLimits(double& min, double& max);
//
//   bool IsContinuousFocusDrive() const {return false;}
//
//   // action interface
//   // ----------------
//   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
//   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
//
//   // Sequence functions - empty stubs
//   // (not supporting sequences)
//   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
//   int GetStageSequenceMaxLength(long& nrEvents) const  {nrEvents = 0; return DEVICE_OK;}
//   int StartStageSequence() const {return DEVICE_OK;}
//   int StopStageSequence() const {return DEVICE_OK;}
//   int ClearStageSequence() {return DEVICE_OK;}
//   int AddToStageSequence(double /*position*/) {return DEVICE_OK;}
//   int SendStageSequence() const {return DEVICE_OK;}
//
//private:
//   int SetCommand(const unsigned char* command, unsigned length);
//   int GetCommand(unsigned char* response, unsigned length, double timeoutMs);
//   bool GetValue(std::string& sMessage, double& pos);
//   int SetMaxTravel();
//   double GetTravelTimeMs(long steps);
//
//   std::string port_;
//   double stepSizeUm_;
//   bool initialized_;
//   double answerTimeoutMs_;
//   double maxTravelUm_;
//   double curPosUm_; // cached current position
//};

#endif //_THORLABS_H_
