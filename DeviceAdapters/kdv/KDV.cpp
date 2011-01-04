///////////////////////////////////////////////////////////////////////////////
// FILE:          KDV.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Z-stage for Meiji Techno EMZ series
//
// AUTHOR:        Koen De Vleeschauwer, www.kdvelectronics.eu, 2010
//                Nenad Amodaj, nenad@amodaj.com, 08/28/2006
// COPYRIGHT:     University of California, San Francisco, 2006
//                KDV Electronics, 2010
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// CVS:           $Id$
//

/*
 * Test stage by opening a script panel and typing:
 * bsh % mmc.setRelativePosition(mmc.getFocusDevice(), 1000);
 * Stage has to move 1 mm towards sample. 
 * Make sure that the objective is far enough away from the sample before executing this command.
 */

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf
#endif

#include "KDV.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDevice.h"
#include <sstream>

const char* g_ZStageDeviceName = "KDVZStage";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ZStageDeviceName, "Focus drive for Meiji Techno EMZ");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
	  return 0;

   if (strcmp(deviceName, g_ZStageDeviceName) == 0)
   {
	  ZStage* s = new ZStage();
	  return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// General utility function:
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == (unsigned)bufSize)
   {
	  ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
	  if (ret != DEVICE_OK)
		 return ret;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ZStage

ZStage::ZStage()
   : port_("Undefined"),
	 initialized_(false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Port cannot be changed after device has been initialized");
   SetErrorText(ERR_STEPPER_FAIL, "Stepper command failed");
   SetErrorText(ERR_SPEED_ACCEL_OUT_OF_RANGE, "Stage speed and/or acceleration out of range");
   SetErrorText(ERR_POSITION_OUTSIDE_RANGE, "Position outside stage travel range");
   SetErrorText(ERR_SET_POSITION_FAILED, "Stage positioning timed out");

   TMCL_InitErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Focus drive for Meiji Techno", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int ZStage::Initialize()
{
   /* 
   * Step size.
   * Focus drive step size is 19.25 um/step.
   */

   double defaultStageStepSizeUm = 19.25;

   /*
    * Stage travel.
    * The Meiji EMZ "FC" coarse and fine focus block has 33mm travel.
    */

   double defaultStageTravelUm = 33000.0;

   /*
   * Stage timeout when positioning, in seconds.
   * Default value is 60.
   */

   long defaultStagePositioningTimeOutS = 60;

   /*
   * Stepper microstepping.
   * Number of microsteps per fullstep.
   * TMCL always uses 256 microsteps per step, even if the stepper hardware is only capable of 16 microsteps per step.
   */

   stepperMicroStepsPerStep_ = 256;

   /*
   * Stepper speed.
   * If you wish to add a new speed to the list, download the TMCL-IDE from www.trinamic.de, 
   * and use the Parameter Calculation Tool to obtain stepper drive settings.
   */

   const char* defaultStepperSpeed = "1 rps";

   speedParam spd;

   spd.descr = "2 rps";
   spd.velocity = 1678;
   spd.pulseDivisor = 4;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   spd.descr = "1 rps";
   spd.velocity = 1678;
   spd.pulseDivisor = 5;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   spd.descr = " 30 rpm";
   spd.velocity = 1678;
   spd.pulseDivisor = 6;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   spd.descr = " 20 rpm";
   spd.velocity = 1118;
   spd.pulseDivisor = 6;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   spd.descr = " 10 rpm";
   spd.velocity = 1118;
   spd.pulseDivisor = 7;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   spd.descr = "  3 rpm";
   spd.velocity = 671;
   spd.pulseDivisor = 8;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   spd.descr = "  2 rpm";
   spd.velocity = 447;
   spd.pulseDivisor = 8;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   spd.descr = "  1 rpm";
   spd.velocity = 224;
   spd.pulseDivisor = 8;
   spd.acceleration = 1000;
   spd.rampDivisor = 7;
   speedSetting_.push_back(spd);

   /*
   * Stepper idle timer, in seconds.
   * Stepper motor is switched off stepperIdleTimerS seconds after speed has dropped to zero.
   * Default value is 0, no timeout.
   */

   long defaultStepperIdleTimerS = 0;

   /*
   * Serial port timeout, in milliseconds.
   * When a command is sent to the stepper drive, 
   * the stepper drive has to acknowledge the command within portTimeoutMs_ milliseconds.
   */

   double defaultPortTimeoutMs = 1000;

   /*
   * Create the properties
   */
   int ret;

   // Step size
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnStageStepSizeUm);
   ret = CreateProperty("StageStepSizeUm", CDeviceUtils::ConvertToString(defaultStageStepSizeUm), MM::Float, false,
	  pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("StageStepSizeUm", 0.0, 50.0);
   stageStepSizeUm_ = defaultStageStepSizeUm;

   // Stage travel.
   pAct = new CPropertyAction (this, &ZStage::OnStageTravelUm);
   ret = CreateProperty("StageTravelUm", CDeviceUtils::ConvertToString(defaultStageTravelUm), MM::Float, false, pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("StageTravelUm", 0, 100000);
   stageTravelUm_ = defaultStageTravelUm;

   // Stage positioning timeout
   pAct = new CPropertyAction (this, &ZStage::OnStagePositioningTimeOutS);
   ret = CreateProperty("StagePositioningTimeOutS", CDeviceUtils::ConvertToString(defaultStagePositioningTimeOutS),
	  MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("StagePositioningTimeOutS", 1, 3600);
   stagePositioningTimeOutS_ = defaultStagePositioningTimeOutS;

   // Controller answer timeout
   pAct = new CPropertyAction (this, &ZStage::OnPortTimeoutMs);
   ret = CreateProperty("PortTimeoutMs", CDeviceUtils::ConvertToString(defaultPortTimeoutMs),
	  MM::Float, false, pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("PortTimeoutMs", 0.0, 10000.0);
   portTimeoutMs_ = defaultPortTimeoutMs;

   // Stepper speed
   pAct = new CPropertyAction (this, &ZStage::OnStepperSpeed);
   ret = CreateProperty("StepperSpeed", defaultStepperSpeed, MM::String, false, pAct);
   assert(ret == DEVICE_OK);
   stepperSpeed_ = defaultStepperSpeed;
   for (list<speedParam>::iterator i = speedSetting_.begin(); i != speedSetting_.end(); i++)
	  AddAllowedValue("StepperSpeed", (i->descr).c_str());

   // Stepper idle timer
   pAct = new CPropertyAction (this, &ZStage::OnStepperIdleTimerS);
   ret = CreateProperty("StepperIdleTimerS", CDeviceUtils::ConvertToString(defaultStepperIdleTimerS), MM::Integer,
	  false, pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("StepperIdleTimerS", 0, 600);
   stepperIdleTimerS_ = defaultStepperIdleTimerS;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
	  return ret;

   /*
   * Log firmware version for debugging purposes
   */
   ret = GetFirmwareVersion();
   if (ret != DEVICE_OK)
	  return ret;

   /*
   * Origin. 
   * We (arbitrarily) initialize axis origin to the current position on powerup.
   */
   ret = SetOrigin();
   if (ret != DEVICE_OK)
	  return ret;

   /*
    * Update drive speed/acceleration parameters
    */
   ret = SetStepperParams(stepperSpeed_, stepperIdleTimerS_);
   if (ret != DEVICE_OK)
	  return ret;

   stepperParamsChanged_ = false;

   initialized_ = true;
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   if (initialized_)
   {
	  initialized_ = false;
   }
   return DEVICE_OK;
}

bool ZStage::Busy()
{
   /* Stage is never busy because all commands are blocking */
   return false;
}

int ZStage::SetPositionUm(double pos)
{
   long steps = (long)(pos / stageStepSizeUm_ * stepperMicroStepsPerStep_ + 0.5);
   return SetPositionSteps(steps);
}

int ZStage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
	  return ret;
   pos = steps / stepperMicroStepsPerStep_ * stageStepSizeUm_;
   return DEVICE_OK;
}

int ZStage::GetLimits(double& min, double& max)
{
   /*
    * Initial position of the focus block is unknown. Travel could be up, or down.
    * We are generous and set stage limits at +stageTravelUm_ and -stageTravelUm_.
    */
   min = -stageTravelUm_;
   max = stageTravelUm_;
   return DEVICE_OK;
}

int ZStage::Home()
{
   int ret;
   // Move upward to zero position
   ret = SetPositionUm(-stageTravelUm_);
   if (ret != DEVICE_OK)
	  return ret;
   // Set current position as origin
   SetOrigin();
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Methods which interact with the stepper hardware
///////////////////////////////////////////////////////////////////////////////

int ZStage::GetFirmwareVersion()
{
   /* Get firmware version */
   int ret = TMCL_Execute(1, TMCL_GET_VERSION, 1, 0, 0);
   if (ret != DEVICE_OK)
	  return ret;

   /* Extract TMCL version and type numbers from reply */
   int versionLowByte = tmclReply_ & 0xff;
   int versionHighByte = (tmclReply_ >> 8) & 0xff;
   int typeLowByte = (tmclReply_ >> 16) & 0xff;
   int typeHighByte = (tmclReply_ >> 24) & 0xff;

   /* Log version and type numbers */
   ostringstream os;
   os << "Trinamic Pandrive type: " << typeHighByte << " " << typeLowByte;
   os << " Firmware version: " << versionHighByte << "." << versionLowByte;
   LogMessage(os.str().c_str(), false);

   return DEVICE_OK;
}

int ZStage::SetOrigin()
{
   /* Stop motor */
   int ret = TMCL_Execute(1, TMCL_MST, 0, 0, 0);
   if (ret != DEVICE_OK)
	  return ret;

   /* Set position to 0 */
   return TMCL_Execute(1, TMCL_SAP, SAP_ACTUAL_POS, 0, 0);
}

int ZStage::SetStepperParams(string speed, long idleTimerS)
{
   /* Make sure motor has stopped when we're updating speed and acceleration */
   int ret = TMCL_Execute(1, TMCL_MST, 0, 0, 0);
   if (ret != DEVICE_OK)
	  return ret;

   /* find drive settings */
   for (list<speedParam>::iterator i = speedSetting_.begin(); i != speedSetting_.end(); i++)
	  if (i->descr == speed)
	  {
		 /* Logging */
		 ostringstream os;
		 os << "Speed " << i->descr << " Velocity: " << i->velocity << " PulseDivisor: " << i->pulseDivisor;
		 os << " Acceleration: " << i->acceleration << " RampDivisor: " << i->rampDivisor;
		 LogMessage(os.str().c_str(), false);

		 /* Set pulse divisor */
		 ret = TMCL_Execute(1, TMCL_SAP, SAP_PULSE_DIVISOR, 0, i->pulseDivisor);
		 if (ret != DEVICE_OK)
			return ret;

		 /* Set ramp divisor */
		 ret = TMCL_Execute(1, TMCL_SAP, SAP_RAMP_DIVISOR, 0, i->rampDivisor);
		 if (ret != DEVICE_OK)
			return ret;

		 /* Set max. velocity */
		 ret = TMCL_Execute(1, TMCL_SAP, SAP_MAXIMUM_SPEED, 0, i->velocity);
		 if (ret != DEVICE_OK)
			return ret;

		 /* Set max. acceleration 
		 * Always set the acceleration parameter as the last parameter after you have changed the ramp divisior,
		 * pulse divisor or microstep resolution. Otherwise some internal ramping parameters do not get calculated correctly. 
		 */
		 ret = TMCL_Execute(1, TMCL_SAP, SAP_MAXIMUM_ACCELERATION, 0, i->acceleration);
		 if (ret != DEVICE_OK)
			return ret;

		 /* No need to look any further */
		 break;
	  }

   /* Freewheeling timeout, in tens of milliseconds */
   int freewheel = idleTimerS * 100;

   /* Set freewheeling timeout */
   ret = TMCL_Execute(1, TMCL_SAP, SAP_FREEWHEEL_TIMEOUT, 0, freewheel);
   if (ret != DEVICE_OK)
	  return ret;

   return DEVICE_OK;
}

int ZStage::GetPositionSteps(long& steps)
{
   /* Get actual position */
   int ret = TMCL_Execute(1, TMCL_GAP, GAP_ACTUAL_POS, 0, 0);
   if (ret != DEVICE_OK)
	  return ret;
   steps = tmclReply_;

   {
	  ostringstream os;
	  os << "Position: " << steps;
	  LogMessage(os.str().c_str(), true);
   }

   return DEVICE_OK;
}

int ZStage::SetPositionSteps(long pos)
{
   /*
    * Update the drive if speed and/or acceleration settings have changed.
    */

   if (stepperParamsChanged_)
   {
	  int ret = SetStepperParams(stepperSpeed_, stepperIdleTimerS_);
	  if (ret != DEVICE_OK)
		 return ret;
	  stepperParamsChanged_ = false;
   }

   /*
   * Check whether position within stage travel.
   */

   double stagePositionUm = pos / stepperMicroStepsPerStep_ * stageStepSizeUm_;
   if ((stageTravelUm_ != 0) && ((stagePositionUm > stageTravelUm_) || (stagePositionUm < -stageTravelUm_)))
   {
	  ostringstream os;
	  os << "Position outside stage travel range. Position: " << stagePositionUm << "um Range: " << -stageTravelUm_ <<
		 " ... " << stageTravelUm_ << " um";
	  LogMessage(os.str().c_str(), false);
	  return ERR_POSITION_OUTSIDE_RANGE;
   }

   /*
   * relative position of target.
   *
   * From the micro-manager wiki:
   *   Z-Stages (Focus stages) will have their lowest values away from the sample 
   *   (at the lowest point for inverted scopes and the highest point of upright scopes). 
   *   Movement in the positive direction will move the objective lens towards the sample. 
   *   This directionality should be guaranteed by the device adapter.
   */

   /*
   * From the TMCL manual: 
   *   Please note that the distance between the actual position and the new one should not be more than
   *   8388607 microsteps. Otherwise the motor will run in the wrong direction for taking a shorter way. If
   *   the value is exactly 8388608 the motor *maybe* stops.
   */

   /* Move to position 'pos' */
   int ret = TMCL_Execute(1, TMCL_MVP, MVP_ABS, 0, pos);
   if (ret != DEVICE_OK)
	  return ret;

   bool posReached = false;

   /* 
    * Timeout if stage positioning takes more than stagePositioningTimeOutS_ seconds.
    */
   MM::MMTime duration(stagePositioningTimeOutS_, 0);
   MM::MMTime startTime = GetCurrentMMTime();

   while (((GetCurrentMMTime() - startTime) < duration) && !posReached)
   {
	  /* Check whether target position reached */
	  PurgeComPort(port_.c_str());
	  ret = TMCL_Execute(1, TMCL_GAP, GAP_POS_REACHED, 0, pos);
	  posReached = (ret == DEVICE_OK) && (tmclStatus_ == TMCL_STATUS_CMD_OK) && (tmclReply_ == 1);

	  /* if target position has not been reached yet,
	   * request stepper to notify us when the target position has been reached.
	   * If we haven't been notified after one second, timeout.
	   */
	  if (!posReached)
	  {
		 TMCL_Execute(1, TMCL_WAIT_EVENT, 0, 0, 1);
		 TMCL_ReceiveReply(1000); // Wait for notification. No problem if this times out after one second.
	  }
   }

   if (!posReached)
   {
	  /* Stage timed out. Send "Motor Stop" command in case motor has stalled. */
	  LogMessage("Stage positioning timed out. Stopping motor.", false);
	  ret = TMCL_Execute(1, TMCL_MST, 0, 0, 0);
	  if (ret != DEVICE_OK)
		 return ret;
	  return ERR_SET_POSITION_FAILED;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int ZStage::OnStageStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(stageStepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(stageStepSizeUm_);
   }

   return DEVICE_OK;
}

int ZStage::OnStageTravelUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(stageTravelUm_);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(stageTravelUm_);
   }

   return DEVICE_OK;
}

int ZStage::OnStepperIdleTimerS(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(stepperIdleTimerS_);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(stepperIdleTimerS_);
	  stepperParamsChanged_ = true;
   }

   return DEVICE_OK;
}

int ZStage::OnStagePositioningTimeOutS(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(stagePositioningTimeOutS_);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(stagePositioningTimeOutS_);
   }

   return DEVICE_OK;
}

int ZStage::OnStepperSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(stepperSpeed_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(stepperSpeed_);
	  stepperParamsChanged_ = true;
   }

   return DEVICE_OK;
}

int ZStage::OnPortTimeoutMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(portTimeoutMs_);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(portTimeoutMs_);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// TMCL Commands
///////////////////////////////////////////////////////////////////////////////

//
// The manuals of Trinamic "Pandrive" stepper motors are available at www.trinamic.de
// Of interest are:
//   "PD-110-42 / TMCM-110-42 Manual (V1.17/2009-NOV-20)" and
//   "PDx-110-42 / TMCM-110 TMCL(TM) Firmware Manual (V1.00/2009-DEC-01)" 
//

int ZStage::TMCL_InitErrorMessages()
{
   /* add custom error messages */

   SetErrorText(ERR_TMCL_ANSWER_TIMEOUT, "Stepper drive: Timed out");
   SetErrorText(ERR_TMCL_RECV_CHECKSUM_ERROR, "Stepper drive: Wrong checksum received");
   SetErrorText(ERR_TMCL_VALUE_OUT_OF_RANGE, "Stepper drive: Value out of range");
   SetErrorText(ERR_TMCL_XMIT_CHECKSUM_ERROR, "Stepper drive: Drive received command with wrong checksum");
   SetErrorText(ERR_TMCL_INVALID_COMMAND, "Stepper drive: Invalid command");
   SetErrorText(ERR_TMCL_WRONG_TYPE, "Stepper drive: Command has wrong type");
   SetErrorText(ERR_TMCL_INVALID_VALUE, "Stepper drive: Command has invalid value");
   SetErrorText(ERR_TMCL_CONFIGURATION_LOCKED, "Stepper drive: Configuration locked");
   SetErrorText(ERR_TMCL_COMMAND_NOT_AVAIL, "Stepper drive: Unavailable command");
   SetErrorText(ERR_TMCL_UNKNOWN_STATUS_CODE, "Stepper drive: Unknown status code");

   return DEVICE_OK;
}

int ZStage::TMCL_Execute(unsigned int address, tmcl_cmd command, unsigned int type, unsigned int motor, int value)
{
   int ret = TMCL_SendCommand(address, command, type, motor, value);
   if (ret != DEVICE_OK)
      return ret;
   return TMCL_ReceiveReply(portTimeoutMs_);
}

int ZStage::TMCL_SendCommand(unsigned int address, tmcl_cmd command, unsigned int type, unsigned int motor, int value)
{
   /* Logging */    
   {
      ostringstream os;
      os << "TMCL " << address << " ";
      switch (command)
      {
      case TMCL_MST:
         os << "MST " << type;
         break;
      case TMCL_MVP:
         os << "MVP ";
         switch (type)
         {
         case MVP_ABS:
            os << "ABS";
            break;
         case MVP_REL:
            os << "REL";
            break;
         case MVP_COORD:
            os << "COORD";
            break;
         default:
            os << type;
            break;
         }
         break;
      case TMCL_SAP:
         os << "SAP ";
         switch (type)
         {
         case   SAP_ACTUAL_POS:
            os << "ACTUAL_POS";
            break;
         case  SAP_MAXIMUM_SPEED :
            os << "MAXIMUM_SPEED";
            break;
         case  SAP_MAXIMUM_ACCELERATION:
            os << "MAXIMUM_ACCELERATION";
            break;
         case  SAP_RAMP_DIVISOR :
            os << "RAMP_DIVISOR";
            break;
         case SAP_PULSE_DIVISOR :
            os << "PULSE_DIVISOR";
            break;
         case SAP_FREEWHEEL_TIMEOUT :
            os << "FREEWHEEL_TIMEOUT";
            break;
         default:
            os << type;
            break;
         }
         break;
      case TMCL_GAP:
         os << "GAP ";
         switch (type)
         {
         case GAP_ACTUAL_POS:
            os << "ACTUAL_POS";
            break;
         case GAP_POS_REACHED:
            os << "POS_REACHED";
            break;
         default:
            os << type;
            break;
         }
         break;
      case TMCL_GET_VERSION:
         os << "VERSION " << type;
         break;
      case TMCL_WAIT_EVENT:
         os << "WAIT " << type;
         break;
      default:
         os << command << " " << type;
         break;    
      }
      os << " " << motor << " " << value;
      LogMessage(os.str().c_str(), true);
   }

   /*
    * From the TMCL manual: 
    *   Please note that the distance between the actual position and the new one should not be more than
    *   8388607 microsteps. Otherwise the motor will run in the wrong direction for taking a shorter way. If
    *   the value is exactly 8388608 the motor *maybe* stops.
    */

   const long maxValue = 8388607l; /* 2**23 - 1 */

   if ((value > maxValue) || (value < -maxValue))
      return ERR_TMCL_VALUE_OUT_OF_RANGE;

   /* Clear serial port buffer */
   PurgeComPort(port_.c_str());

   /* Construct transmit buffer */
   unsigned char txBuffer[9];
   int i;

   txBuffer[0] = (unsigned char)address;
   txBuffer[1] = (unsigned char)command;
   txBuffer[2] = (unsigned char)type;
   txBuffer[3] = (unsigned char)motor;
   txBuffer[4] = (unsigned char)(value >> 24);
   txBuffer[5] = (unsigned char)(value >> 16);
   txBuffer[6] = (unsigned char)(value >> 8);
   txBuffer[7] = (unsigned char)(value & 0xff);
   txBuffer[8] = 0;
   for (i = 0; i < 8; i++)
      txBuffer[8] += txBuffer[i];

   /* send TMCL binary command to stepper */
   int ret = WriteToComPort(port_.c_str(), txBuffer, 9);

   if (ret != DEVICE_OK)
      LogMessage("Write to com port failed", true);
   return ret;
}

int ZStage::TMCL_ReceiveReply(double timeOutMs)
{
   /* Receive response from stepper */
   static const unsigned long rxBufSize = 9;
   unsigned char rxBuffer[9], checksum;
   int ret;

   /* read 9 bytes from serial port; timeout after timeoutMs_ (typically 1000 ms.) */
   MM::MMTime duration(timeOutMs * 1000);
   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long totalRead = 0;
   unsigned long read = 0;
   while (((GetCurrentMMTime() - startTime) < duration) && (totalRead < rxBufSize))
   {
      ret = ReadFromComPort(port_.c_str(), rxBuffer + totalRead, rxBufSize - totalRead, read);
      totalRead += read;
      CDeviceUtils::SleepMs(10);
   }

   if (totalRead != 9)
   {    
      LogMessage("Read from port timed out", true);
      return ERR_TMCL_ANSWER_TIMEOUT;
   }
        
   checksum = 0;
   for (int i = 0; i < 8; i++)
      checksum += rxBuffer[i];

   if (checksum != rxBuffer[8])
   {
      LogMessage("Checksum error", false);
      return ERR_TMCL_RECV_CHECKSUM_ERROR;
   }
    
   tmclStatus_ = rxBuffer[2];
   tmclReply_ = (rxBuffer[4] << 24) | (rxBuffer[5] << 16) | (rxBuffer[6] << 8) | rxBuffer[7];

   {
      ostringstream os;
      os << "TMCL Status: " << (unsigned int)tmclStatus_ << " Reply: " << tmclReply_;
      LogMessage(os.str().c_str(), true);
   }

   switch (tmclStatus_)
   {
   case TMCL_STATUS_CMD_OK :
      LogMessage("Successfully executed, no error", true);
      return DEVICE_OK;
      break;
   case TMCL_STATUS_EEPROM_OK:
      LogMessage("Command loaded into TMCL program EEPROM", true);
      return DEVICE_OK;
      break;
   case TMCL_STATUS_POS_OK:
      LogMessage("Motor reached target position", true);
      return DEVICE_OK;
      break;
   case TMCL_STATUS_CSUM_ERR:
      LogMessage("Wrong checksum", false);
      return ERR_TMCL_XMIT_CHECKSUM_ERROR;
      break;
   case TMCL_STATUS_CMD_ERR:
      LogMessage("Invalid command", false);
      return ERR_TMCL_INVALID_COMMAND;
      break;
   case TMCL_STATUS_TYPE_ERR:
      LogMessage("Wrong type", false);
      return ERR_TMCL_WRONG_TYPE;
      break;
   case TMCL_STATUS_VAL_ERR:
      LogMessage("Invalid value", false);
      return ERR_TMCL_INVALID_VALUE;
      break;
   case TMCL_STATUS_EEPROM_ERR:
      LogMessage("Configuration EEPROM locked", false);
      return ERR_TMCL_CONFIGURATION_LOCKED;
      break;
   case TMCL_STATUS_AVAIL_ERR:
      LogMessage("Command not available", false);
      return ERR_TMCL_COMMAND_NOT_AVAIL;
      break;
   default:
      LogMessage("Unknown status code", false);
      return ERR_TMCL_UNKNOWN_STATUS_CODE;
   }
}

// not truncated
