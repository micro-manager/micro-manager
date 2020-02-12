///////////////////////////////////////////////////////////////////////////////
// FILE:          MotorZStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: Motor Z Stage TST001
//
// COPYRIGHT:     Erich E. Hoover, 2012
//                Thorlabs, 2011
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
// AUTHOR:        Erich E. Hoover, ehoover@mines.edu, 2012
//

#ifdef WIN32
   #include <windows.h>
#endif
#include "FixSnprintf.h"

#include "MotorZStage.h"
#include "Thorlabs.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>

extern const char* g_MotorZStageDeviceName;

extern const char* g_SerialNumberProp;
extern const char* g_ModelNumberProp;
extern const char* g_SWVersionProp;

extern const char* g_StepSizeXProp;
extern const char* g_StepSizeYProp;
extern const char* g_MaxVelocityProp;
extern const char* g_AccelProp;
extern const char* g_MoveTimeoutProp;

const char* g_MotorProp = "AttachedMotor";

using namespace std;

///////////
// fixed stage parameters
///////////
const double stepSizeUmStepper = 0.002142; // microns/step
const double stepSizeUmServo   = 0.05;     // microns/(encoder count)

typedef enum {
   MOTOR_ZST6,
   MOTOR_ZST13,
   MOTOR_ZST25,
   MOTOR_T25X,
   MOTOR_MAX
} MOTOR_TYPE;

const char* g_SupportedMotors[MOTOR_MAX] = {
   "ZST6(B) Stepper Motor",
   "ZST13(B) Stepper Motor",
   "ZST25(B) Stepper Motor",
   "T25X Servo Motor"
};

// maximum motor steps (in steps)
const long motorMaxSteps[MOTOR_MAX] = {
   (long)( 6*1000/stepSizeUmStepper),
   (long)(13*1000/stepSizeUmStepper),
   (long)(25*1000/stepSizeUmStepper),
   (long)(25*1000/stepSizeUmServo)
};

///////////////////////////////////////////////////////////////////////////////
// CommandThread class
// (for executing move commands)
///////////////////////////////////////////////////////////////////////////////

class MotorZStage::CommandThread : public MMDeviceThreadBase
{
   public:
      CommandThread(MotorZStage* stage) :
         stop_(false), moving_(false), stage_(stage), errCode_(DEVICE_OK) {}

      virtual ~CommandThread() {}

      int svc()
      {
         if (cmd_ == MOVE)
         {
            moving_ = true;
            errCode_ = stage_->MoveBlocking(pos_);
            moving_ = false;
            ostringstream os;
            os << "Move finished with error code: " << errCode_;
            stage_->LogMessage(os.str().c_str(), true);
         }
         else if (cmd_ == MOVEREL)
         {
            moving_ = true;
            errCode_ = stage_->MoveBlocking(pos_, true); // relative move
            moving_ = false;
            ostringstream os;
            os << "Move finished with error code: " << errCode_;
            stage_->LogMessage(os.str().c_str(), true);
         }
         return 0;
      }
      void Stop() {stop_ = true;}
      bool GetStop() {return stop_;}
      int GetErrorCode() {return errCode_;}
      bool IsMoving()  {return moving_;}

      void StartMove(long pos)
      {
         Reset();
         pos_ = pos;
         cmd_ = MOVE;
         activate();
      }

      void StartMoveRel(long dpos)
      {
         Reset();
         pos_ = dpos;
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
      MotorZStage* stage_;
      long pos_;
      Command cmd_;
      int errCode_;
};

///////////////////////////////////////////////////////////////////////////////
// MotorZStage class
///////////////////////////////////////////////////////////////////////////////

MotorZStage::MotorZStage() :
   attachedMotor_(MOTOR_ZST6), // default to motor with smallest range
   port_("Undefined"),
   initialized_(false),
   answerTimeoutMs_(1000),
   moveTimeoutMs_(110000.0), // takes roughly 100 seconds to travel the whole range of ZST25
   zstage_(NULL),
   home_(false),
   cmdThread_(0)
{
   InitializeDefaultErrorMessages();

   // set device specific error messages
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Serial port can't be changed at run-time."
                                           " Use configuration utility or modify configuration file manually.");
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Invalid response from the device");
   SetErrorText(ERR_UNSPECIFIED_ERROR, "Unspecified error occured.");
   SetErrorText(ERR_HOME_REQUIRED, "Stage must be homed before sending MOVE commands.\n"
      "To home the stage use one of the following options:\n"
      "   Open Tools | XY List... dialog and press 'Calibrate' button\n"
      "       or\n"
      "   Open Scipt panel and execute this line: mmc.home(mmc.getXyStageDevice());");
   SetErrorText(ERR_INVALID_PACKET_LENGTH, "Invalid packet length.");
   SetErrorText(ERR_RESPONSE_TIMEOUT, "Device timed-out: no response received withing expected time interval.");
   SetErrorText(ERR_BUSY, "Device busy.");
   SetErrorText(ERR_UNRECOGNIZED_DEVICE, "Unsupported device model!");
 

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_MotorZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Thorlabs Motor Z Stage", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &MotorZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   cmdThread_ = new CommandThread(this);
}

MotorZStage::~MotorZStage()
{
   Shutdown();
}

void MotorZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_MotorZStageDeviceName);
}

const char* g_Handshaking_Off = "Off";
const char* g_Handshaking_Hardware = "Hardware";
const char* g_Handshaking_Software = "Software";

bool MotorZStage::RecognizedDevice(char *model)
{
   if(strcmp(model, "TST001") == 0)
   {
           stepSizeUm_ = stepSizeUmStepper;
      return true;
   }
   if(strcmp(model, "OST001") == 0)
   {
           stepSizeUm_ = stepSizeUmStepper;
      return true;
   }
   if(strcmp(model, "TDC001") == 0)
   {
           stepSizeUm_ = stepSizeUmServo;
      return true;
   }
   if(strcmp(model, "ODC001") == 0)
   {
           stepSizeUm_ = stepSizeUmServo;
      return true;
   }
   return false;
}

int MotorZStage::Initialize()
{
   int ret;

   // initialize stage device
   zstage_ = new MotorStage(this, GetCoreCallback(), port_, 0, answerTimeoutMs_, moveTimeoutMs_);

   // for some strange reason the device requires the flow control to be toggled before first communications
   GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, g_Handshaking_Hardware);
   GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, g_Handshaking_Off);

   // create hardware info properties
   ret = zstage_->Initialize(&info_);
   if (ret != DEVICE_OK)
      return ret;

   // confirm that the device is supported
   if (!RecognizedDevice(info_.szModelNum))
      return ERR_UNRECOGNIZED_DEVICE;

   CreateProperty(g_SerialNumberProp, CDeviceUtils::ConvertToString((int)info_.dwSerialNum), MM::String, true);
   CreateProperty(g_ModelNumberProp, info_.szModelNum, MM::String, true);
   CreateProperty(g_SWVersionProp, CDeviceUtils::ConvertToString((int)info_.dwSoftwareVersion), MM::String, true);

   // check if we are already homed
   DCMOTSTATUS stat;
   ret = zstage_->GetStatus(stat);
   if (ret != DEVICE_OK)
      return ret;

   if (stat.dwStatusBits & 0x00000400)
      home_ = true;

   // check if axis needs enabling
   if (!(stat.dwStatusBits & 0x80000000))
   {
      ret = zstage_->Enable();
      if (ret != DEVICE_OK)
         return ret;
   }

   // check if home operation needs to be performed
   if (!home_)
   {
      ret = Home();
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = zstage_->GetStatus(stat);
   if (ret != DEVICE_OK)
      return ret;

   ostringstream os;
   os << "Status X axis (hex): " << hex << stat.dwStatusBits;
   LogMessage(os.str(), true);

   // Step size
   CreateProperty(g_StepSizeXProp, CDeviceUtils::ConvertToString(stepSizeUm_), MM::Float, true);
   CreateProperty(g_StepSizeYProp, CDeviceUtils::ConvertToString(stepSizeUm_), MM::Float, true);

   // Max Speed
   CPropertyAction* pAct = new CPropertyAction (this, &MotorZStage::OnMaxVelocity);
   CreateProperty(g_MaxVelocityProp, "100.0", MM::Float, false, pAct);
   //SetPropertyLimits(g_MaxVelocityProp, 0.0, 31999.0);

   // Acceleration
   pAct = new CPropertyAction (this, &MotorZStage::OnAcceleration);
   CreateProperty(g_AccelProp, "100.0", MM::Float, false, pAct);
   //SetPropertyLimits("Acceleration", 0.0, 150);

   // Move timeout
   pAct = new CPropertyAction (this, &MotorZStage::OnMoveTimeout);
   CreateProperty(g_MoveTimeoutProp, "10000.0", MM::Float, false, pAct);
   //SetPropertyLimits("Acceleration", 0.0, 150);

   // Supported motors
   pAct = new CPropertyAction (this, &MotorZStage::OnChangeMotor);
   ret = CreateProperty(g_MotorProp, g_SupportedMotors[0], MM::String, false, pAct);//, true);
   for (int i=0; i<MOTOR_MAX; i++)
      AddAllowedValue(g_MotorProp, g_SupportedMotors[i], (long)i);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int MotorZStage::Shutdown()
{
   delete zstage_;
   zstage_ = NULL;

   if (cmdThread_ && cmdThread_->IsMoving())
   {
      cmdThread_->Stop();
      cmdThread_->wait();
   }

   delete cmdThread_;
   cmdThread_ = NULL;

   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/**
 * Performs homing of stage
 * (required after initialization)
 */
int MotorZStage::Home()
{
   int ret;

   // home stage (doesn't return a reply for this controller?)
   ret = zstage_->Home();
   if (ret != DEVICE_OK)
      return ret;
   home_ = true; // successfully homed

   // check status
   DCMOTSTATUS stat;
   ret = zstage_->GetStatus(stat);
   if (ret != DEVICE_OK)
      return ret;
   
   ostringstream os;
   os << "Status (hex): " << hex << stat.dwStatusBits;
   LogMessage(os.str(), true);

   return DEVICE_OK;
}

bool MotorZStage::Busy()
{
   return cmdThread_->IsMoving();
}

int MotorZStage::SetPositionSteps(long steps)
{
   if (!home_)
      return ERR_HOME_REQUIRED; 

   if (Busy())
      return ERR_BUSY;

   cmdThread_->StartMove(steps);
   CDeviceUtils::SleepMs(10); // to make sure that there is enough time for thread to get started

   return DEVICE_OK;
}

int MotorZStage::GetPositionSteps(long& steps)
{
   int ret;

   // if not homed just return default
   if (!home_)
   {
      steps = 0L;
      return DEVICE_OK;
   }

   // send command to the axis
   ret = zstage_->GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   ostringstream os;
   os << "GetPositionSteps(), pos=" << steps;
   LogMessage(os.str().c_str(), true);

   return DEVICE_OK;
}
  
int MotorZStage::SetPositionUm(double posUm)
{
   long steps = (long)(posUm / stepSizeUm_);
   int ret = SetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
  
int MotorZStage::GetPositionUm(double& posUm)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   posUm = (double)steps * stepSizeUm_;
   return DEVICE_OK;
}

int MotorZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int MotorZStage::GetLimits(double& min, double& max)
{
   min = 0.0;
   max = motorMaxSteps[attachedMotor_];

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int MotorZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      // initialize port properties to be correct by default
//      GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, CDeviceUtils::ConvertToString(115200));

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the maximum speed with which the stage travels
 */
int MotorZStage::OnMaxVelocity(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      // get parameters from the axis
      MOTVELPARAMS params;
      int ret = zstage_->GetVelocityProfile(params);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set((unsigned long)params.lMaxVel * stepSizeUm_);
   } 
   else if (eAct == MM::AfterSet) 
   {
      // first get current profile
      MOTVELPARAMS params;
      int ret = zstage_->GetVelocityProfile(params);
      if (ret != DEVICE_OK)
         return ret;
     
      // set desired velocity to axis
      double maxVel;
      pProp->Get(maxVel);
      params.lMaxVel = (unsigned int)(maxVel / stepSizeUm_);

      // apply profile
      ret = zstage_->SetVelocityProfile(params);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the Acceleration of the stage travels
 */
int MotorZStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      // get profile from the axis
      MOTVELPARAMS params;
      int ret = zstage_->GetVelocityProfile(params);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set((unsigned long)params.lAccn * stepSizeUm_);
   } 
   else if (eAct == MM::AfterSet) 
   {
      // first get current profile
      MOTVELPARAMS params;
      int ret = zstage_->GetVelocityProfile(params);
      if (ret != DEVICE_OK)
         return ret;
     
      // set desired acceleration to axes
      double accel;
      pProp->Get(accel);
      params.lAccn = (unsigned int)(accel / stepSizeUm_); 

      // apply profile
      ret = zstage_->SetVelocityProfile(params);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the Acceleration of the stage travels
 */
int MotorZStage::OnMoveTimeout(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      pProp->Set(moveTimeoutMs_);
   } 
   else if (eAct == MM::AfterSet) 
   {
      pProp->Get(moveTimeoutMs_);
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the Acceleration of the stage travels
 */
int MotorZStage::OnChangeMotor(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
       //SetProperty(g_MotorProp, g_SupportedMotors[attachedMotor_]);
       pProp->Set(g_SupportedMotors[attachedMotor_]);
   } 
   else if (eAct == MM::AfterSet) 
   {
       GetCurrentPropertyData(g_MotorProp, (long &)attachedMotor_);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// private methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends move command to axis and wait for response, blocking the calling thread.
 * If expected answer does not come within timeout interval, return with error.
 */
int MotorZStage::MoveBlocking(long pos, bool relative)
{
   if (!home_)
      return ERR_HOME_REQUIRED; 

   ClearPort(*this, *GetCoreCallback(), port_);
   // send command to axis
   return zstage_->MoveBlocking(pos, relative);
}

