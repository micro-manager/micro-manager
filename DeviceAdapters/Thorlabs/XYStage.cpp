///////////////////////////////////////////////////////////////////////////////
// FILE:          XYStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD Controller
//
// COPYRIGHT:     Thorlabs, 2011
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

#include <ModuleInterface.h>
#include "XYStage.h"
#include <sstream>

extern const char* g_XYStageDeviceName;

extern const char* g_SerialNumberProp;
extern const char* g_ModelNumberProp;
extern const char* g_SWVersionProp;

extern const char* g_StepSizeXProp;
extern const char* g_StepSizeYProp;
extern const char* g_MaxVelocityProp;
extern const char* g_AccelProp;
extern const char* g_MoveTimeoutProp;

using namespace std;

///////////
// fixed stage parameters
///////////
const long xAxisMaxSteps = 2200000L;   // maximum number of steps in X
const long yAxisMaxSteps = 1500000L;   // maximum number of steps in Y
const double stepSizeUm = 0.05;        // step size in microns
const double accelScale = 13.7438;     // scaling factor for acceleration
const double velocityScale = 134218.0; // scaling factor for velocity

///////////////////////////////////////////////////////////////////////////////
// CommandThread class
// (for executing move commands)
///////////////////////////////////////////////////////////////////////////////

class XYStage::CommandThread : public MMDeviceThreadBase
{
   public:
      CommandThread(XYStage* stage) :
         stop_(false), moving_(false), stage_(stage), errCode_(DEVICE_OK) {}

      virtual ~CommandThread() {}

      int svc()
      {
         if (cmd_ == MOVE)
         {
            moving_ = true;
            errCode_ = stage_->MoveBlocking(x_, y_);
            moving_ = false;
            ostringstream os;
            os << "Move finished with error code: " << errCode_;
            stage_->LogMessage(os.str().c_str(), true);
         }
         else if (cmd_ == MOVEREL)
         {
            moving_ = true;
            errCode_ = stage_->MoveBlocking(x_, y_, true); // relative move
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

///////////////////////////////////////////////////////////////////////////////
// XYStage class
///////////////////////////////////////////////////////////////////////////////

XYStage::XYStage() :
   CXYStageBase<XYStage>(),
   initialized_(false),
   home_(false),
   port_("Undefined"), 
   answerTimeoutMs_(1000.0),
   moveTimeoutMs_(10000.0),
   xstage_(NULL),
   ystage_(NULL),
   cmdThread_(0)
{
   // set default error messages
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
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Thorlabs BBD102 XY stage adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   cmdThread_ = new CommandThread(this);
}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XY Stage API
// required device interface implementation
///////////////////////////////////////////////////////////////////////////////
void XYStage::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_XYStageDeviceName);
}

int XYStage::Initialize()
{
   // initialize individual stages
   xstage_ = new MotorStage(this, GetCoreCallback(), port_, 0, answerTimeoutMs_, moveTimeoutMs_);
   ystage_ = new MotorStage(this, GetCoreCallback(), port_, 1, answerTimeoutMs_, moveTimeoutMs_);

   // initialize device and get hardware information
   int ret = xstage_->Initialize(&info_);
   if (ret != DEVICE_OK)
      return ret;
   ret = ystage_->Initialize(NULL);
   if (ret != DEVICE_OK)
      return ret;

   // confirm that the device is supported
   if (strcmp(info_.szModelNum, "BBD102") != 0 && strcmp(info_.szModelNum, "BBD103") != 0 &&
			strcmp(info_.szModelNum, "BBD202") != 0 && strcmp(info_.szModelNum, "BBD203") != 0)
      return ERR_UNRECOGNIZED_DEVICE;

   CreateProperty(g_SerialNumberProp, CDeviceUtils::ConvertToString((int)info_.dwSerialNum), MM::String, true);
   CreateProperty(g_ModelNumberProp, info_.szModelNum, MM::String, true);
   CreateProperty(g_SWVersionProp, CDeviceUtils::ConvertToString((int)info_.dwSoftwareVersion), MM::String, true);

   // check if we are already homed
   DCMOTSTATUS stat;
   ret = GetStatus(stat, X);
   if (ret != DEVICE_OK)
      return ret;

   if (stat.dwStatusBits & 0x00000400)
      home_ = true;

   // check if axes need enabling
   if (!(stat.dwStatusBits & 0x80000000))
   {
      // enable X
      ret = xstage_->Enable();
      if (ret != DEVICE_OK)
         return ret;

      // enable Y
      ret = ystage_->Enable();
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = GetStatus(stat, X);
   if (ret != DEVICE_OK)
      return ret;

   ostringstream os;
   os << "Status X axis (hex): " << hex << stat.dwStatusBits;
   LogMessage(os.str(), true);

   // Step size
   CreateProperty(g_StepSizeXProp, CDeviceUtils::ConvertToString(stepSizeUm), MM::Float, true);
   CreateProperty(g_StepSizeYProp, CDeviceUtils::ConvertToString(stepSizeUm), MM::Float, true);

   // Max Speed
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnMaxVelocity);
   CreateProperty(g_MaxVelocityProp, "100.0", MM::Float, false, pAct);
   //SetPropertyLimits(g_MaxVelocityProp, 0.0, 31999.0);

   // Acceleration
   pAct = new CPropertyAction (this, &XYStage::OnAcceleration);
   CreateProperty(g_AccelProp, "100.0", MM::Float, false, pAct);
   //SetPropertyLimits("Acceleration", 0.0, 150);

   // Move timeout
   pAct = new CPropertyAction (this, &XYStage::OnMoveTimeout);
   CreateProperty(g_MoveTimeoutProp, "10000.0", MM::Float, false, pAct);
   //SetPropertyLimits("Acceleration", 0.0, 150);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   delete xstage_;
   delete ystage_;
   xstage_ = NULL;
   ystage_ = NULL;

   if (cmdThread_ && cmdThread_->IsMoving())
   {
      cmdThread_->Stop();
      cmdThread_->wait();
   }

   delete cmdThread_;
   cmdThread_ = 0;

   if (initialized_)
      initialized_ = false;

   return DEVICE_OK;
}

bool XYStage::Busy()
{
   return cmdThread_->IsMoving();
}
 
double XYStage::GetStepSizeXUm()
{
   return stepSizeUm;
}

double XYStage::GetStepSizeYUm()
{
   return stepSizeUm;
}

int XYStage::SetPositionSteps(long x, long y)
{
   if (!home_)
      return ERR_HOME_REQUIRED; 

   if (Busy())
      return ERR_BUSY;

   if (x <= xAxisMaxSteps && y <= yAxisMaxSteps)
   {
      cmdThread_->StartMove(x, y);
      CDeviceUtils::SleepMs(10); // to make sure that there is enough time for thread to get started
   }

   return DEVICE_OK;   
}
 
int XYStage::SetRelativePositionSteps(long x, long y)
{
   if (!home_)
      return ERR_HOME_REQUIRED; 

   if (Busy())
      return ERR_BUSY;

   cmdThread_->StartMoveRel(x, y);

   return DEVICE_OK;
}

int XYStage::GetPositionSteps(long& x, long& y)
{
   int ret;

   // if not homed just return default
   if (!home_)
   {
      x = 0L;
      y = 0L;
      return DEVICE_OK;
   }

   // send command to X axis
   ret = xstage_->GetPositionSteps(x);
   if (ret != DEVICE_OK)
      return ret;
   CDeviceUtils::SleepMs(10);
   // send command to Y axis
   ret = ystage_->GetPositionSteps(y);
   if (ret != DEVICE_OK)
      return ret;

   ostringstream os;
   os << "GetPositionSteps(), X=" << x << ", Y=" << y;
   LogMessage(os.str().c_str(), true);

   return DEVICE_OK;
}

/**
 * Performs homing for both axes
 * (required after initialization)
 */
int XYStage::Home()
{
   int ret;

   // home X axis
   ret = xstage_->Home();
   if (ret != DEVICE_OK)
      return ret;
   // home Y axis
   ret = ystage_->Home();
   if (ret != DEVICE_OK)
      return ret;

   home_ = true; // successfully homed

   // check status
   DCMOTSTATUS stat;
   ret = GetStatus(stat, X);
   if (ret != DEVICE_OK)
      return ret;
   
   ostringstream os;
   os << "Status X axis (hex): " << hex << stat.dwStatusBits;
   LogMessage(os.str(), true);

   // check status
   ret = GetStatus(stat, Y);
   if (ret != DEVICE_OK)
      return ret;
   
   os << "Status Y axis (hex): " << hex << stat.dwStatusBits;
   LogMessage(os.str(), true);

   return DEVICE_OK;
}

/**
 * Stops XY stage immediately. Blocks until done.
 */
int XYStage::Stop()
{
   int ret;

   // send command to X axis
   ret = xstage_->Stop();
   if (ret != DEVICE_OK)
      return ret;
   // send command to Y axis
   return ystage_->Stop();
}

/**
 * This is supposed to set the origin (0,0) at whatever is the current position.
 * Our stage does not support setting the origin (it is fixed). The base class
 * (XYStageBase) provides the default implementation SetAdapterOriginUm(double x, double y)
 * but we are not going to use since it would affect absolute coordinates during "Calibrate"
 * command in micro-manager.
 */
int XYStage::SetOrigin()
{
   // commnted oout since we do not really want to support setting the origin
   // int ret = SetAdapterOriginUm(0.0, 0.0);
   return DEVICE_OK; 
}
 
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   xMin = 0.0;
   yMin = 0.0;
   xMax = xAxisMaxSteps * stepSizeUm;
   yMax = yAxisMaxSteps * stepSizeUm;

   return DEVICE_OK;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   xMin = 0L;
   yMin = 0L;
   xMax = xAxisMaxSteps;
   yMax = yAxisMaxSteps;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
 * Gets/Sets serial port name
 * Works only before the device is initialized
 */
int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the maximum speed with which the stage travels
 */
int XYStage::OnMaxVelocity(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      // get parameters from the x axis (we assume y is the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set((unsigned long)params.lMaxVel / velocityScale);
   } 
   else if (eAct == MM::AfterSet) 
   {
      // first get current profile
      // (from x axis only - y should be the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;
     
      // set desired velocity to both axes
      double maxVel;
      pProp->Get(maxVel);
      params.lMaxVel = (unsigned int)(maxVel * velocityScale); 

      // apply X profile
      ret = SetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      // apply the same for Y
      ret = SetVelocityProfile(params, Y);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the Acceleration of the stage travels
 */
int XYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      // get profile from x axis (we assume y is the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set((unsigned long)params.lAccn / accelScale);
   } 
   else if (eAct == MM::AfterSet) 
   {
      // first get current profile
      // (from x stage only because we assume y stage is the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;
     
      // set desired acceleration to both axes
      double accel;
      pProp->Get(accel);
      params.lAccn = (unsigned int)(accel * accelScale); 

      // apply X profile
      ret = SetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      // apply the same for Y
      ret = SetVelocityProfile(params, Y);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the Acceleration of the stage travels
 */
int XYStage::OnMoveTimeout(MM::PropertyBase* pProp, MM::ActionType eAct) 
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

///////////////////////////////////////////////////////////////////////////////
// private methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sets max velocity and acceleration parameters
 */
int XYStage::SetVelocityProfile(const MOTVELPARAMS& params, Axis a)
{
   MotorStage *axis;

   if (a == X)
      axis = xstage_;
   else
      axis = ystage_;
   return axis->SetVelocityProfile(params);
}

/**
 * Obtains max velocity and acceleration parameters
 */
int XYStage::GetVelocityProfile(MOTVELPARAMS& params, Axis a)
{
   MotorStage *axis;

   if (a == X)
      axis = xstage_;
   else
      axis = ystage_;
   return axis->GetVelocityProfile(params);
}

/**
 * Obtain status information for the given axis
 */
int XYStage::GetStatus(DCMOTSTATUS& stat, Axis a)
{
   MotorStage *axis;

   if (a == X)
      axis = xstage_;
   else
      axis = ystage_;
   return axis->GetStatus(stat);
}

/**
 * Sends move command to both axes and waits for responses, blocking the calling thread.
 * If expected answers do not come within timeout interval, returns with error.
 */
int XYStage::MoveBlocking(long x, long y, bool relative)
{
   int ret;

   if (!home_)
      return ERR_HOME_REQUIRED; 

   // send command to X axis
   ret = xstage_->MoveBlocking(x, relative);
   if (ret != DEVICE_OK)
      return ret;
   // send command to Y axis
   return ystage_->MoveBlocking(y, relative);
}

