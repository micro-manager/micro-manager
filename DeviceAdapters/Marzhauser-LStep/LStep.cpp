///////////////////////////////////////////////////////////////////////////////
// FILE:          LStep.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser L-Step Controller Driver
//                XY Stage
//                Z Stage support added
//
// AUTHORS:			Original Marzhauser Tango adapter code by Falk Dettmar, falk.dettmar@marzhauser-st.de, 09/04/2009
//					Modifications for Marzhauser L-Step controller by Gilles Courtand, gilles.courtand@u-bordeaux.fr, 
//					and Brice Bonheur brice.bonheur@u-bordeaux.fr 08/03/2012
//
// COPYRIGHT:     Marzhauser SensoTech GmbH, Wetzlar, 2010
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

#ifdef WIN32
#pragma warning(disable: 4355)
#endif
#include "FixSnprintf.h"

#include "LStep.h"
#include <string>
//#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


const char* g_XYStageDeviceName = "XYStage";
const char* g_ZStageDeviceName  = "ZAxis";



using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "LStep XY Stage");
   RegisterDevice(g_ZStageDeviceName,  MM::StageDevice,   "LStep Z Axis");
}                                                                            

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0) return 0;
   if (strcmp(deviceName, g_XYStageDeviceName) == 0) return new XYStage();                                     
   if (strcmp(deviceName, g_ZStageDeviceName)  == 0) return new ZStage();

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// LStepBase (convenience parent class)
//
LStepBase::LStepBase(MM::Device *device) :
   initialized_(false),
   Configuration_(0),
   port_("Undefined"),
   device_(device),
   core_(0)
{
}

LStepBase::~LStepBase()
{
}


// Communication "clear buffer" utility function:
int LStepBase::ClearPort(void)
{
   // Clear contents of serial port
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while ((int) read == bufSize)
   {
      ret = core_->ReadFromSerial(device_, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;                                                           
} 


// Communication "send" utility function:
int LStepBase::SendCommand(const char *command) const
{
   const char* g_TxTerm = "\r"; //unique termination from MM to LStep communication
   int ret;

   std::string base_command = "";
   base_command += command;
   // send command
   ret = core_->SetSerialCommand(device_, port_.c_str(), base_command.c_str(), g_TxTerm);
   return ret;
}


// Communication "send & receive" utility function:
int LStepBase::QueryCommand(const char *command, std::string &answer) const
{
   const char* g_RxTerm = "\r"; //unique termination from LStep to MM communication   std::string base_command = "";
   int ret;

   // send command
   ret = SendCommand(command);

   if (ret != DEVICE_OK)
      return ret;
   // block/wait for acknowledge (or until we time out)
   const size_t BUFSIZE = 2048;
   char buf[BUFSIZE] = {'\0'};
   ret = core_->GetSerialAnswer(device_, port_.c_str(), BUFSIZE, buf, g_RxTerm);
   answer = buf;
   return ret;
}


int LStepBase::CheckDeviceStatus(void)
{
  int ret = ClearPort();
  if (ret != DEVICE_OK) return ret;

  // LStep Version
  string resp;
  ret = QueryCommand("?ver",resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;
  //expected response starts either with "Vers:LS" or "Vers:LP"
  if (resp.find("Vers:L") == string::npos) return DEVICE_NOT_CONNECTED;


  ret = SendCommand("!autostatus 0"); //diasable autostatus
  if (ret != DEVICE_OK) return ret;

  ret = QueryCommand("?det", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
  Configuration_ = atoi(resp.c_str());

  initialized_ = true;
  return DEVICE_OK;
}





///////////////////////////////////////////////////////////////////////////////
// XYStage
//
/*
* XYStage - two axis stage device.
* Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
* system with the X and Y axis going the 'Micro-Manager standard' direction
* Then, there is the Tango native system.  All functions using 'steps' use the Tango system
* All functions using [um] use the Micro-Manager coordinate system
*/

XYStage::XYStage() :
   LStepBase(this),
   range_measured_(false),

   stepSizeXUm_(0.02), //=1000*pitch[mm]/gear/(motorsteps*250)  (assume gear=1 motorsteps=200 => stepsize=pitch/50.0)
   stepSizeYUm_(0.02),
 
   speedX_(20.0), //[mm/s]
   speedY_(20.0), //[mm/s]
   accelX_(0.2), //[m/s²]
   accelY_(0.2),
   originX_(0),
   originY_(0),
   pitchX_(1), //spindle pitch [mm]
   pitchY_(1)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   // NOTE: pre-initialization properties contain parameters which must be defined fo
   // proper startup

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
	CreateProperty(MM::g_Keyword_Description, "L-Step XY stage driver adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}


/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   int NumberOfAxes = (Configuration_ >> 4) &0x0f;
   if (NumberOfAxes < 2) return DEVICE_NOT_CONNECTED; 


   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   // get current step size from the controller
   string resp;
   ret = QueryCommand("?pitch x", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   float pitch;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   pitchX_ = pitch;
   stepSizeXUm_ = pitchX_/50.0; //step size in micrometer
   ret = CreateProperty("StepSizeX [um]", CDeviceUtils::ConvertToString(stepSizeXUm_), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;

   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   // get current step size from the controller
   ret = QueryCommand("?pitch y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   pitchY_ = pitch;
   stepSizeYUm_ = pitchY_/50.0;
   ret = CreateProperty("StepSizeY [um]", CDeviceUtils::ConvertToString(stepSizeYUm_), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;

//********************************************************************************************************************************************speed
   // Speed (in mm/s)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnSpeedX);

   // switch to mm/s
	ret = SendCommand("!dim x 2");	//velocity (vel) in rev/s (motor revolution)
    if (ret != DEVICE_OK) return ret;

   //conversion revolution/s to mm/s
   ret = QueryCommand("?vel x", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   float rev_per_s;
   char iBufString[256];
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f\r", &rev_per_s);

   velocityXmm_ = rev_per_s * pitchX_; //motor revolution = 2mm
   ret = CreateProperty("SpeedX [mm/s]", CDeviceUtils::ConvertToString(velocityXmm_), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedX [mm/s]", 0.01, 20*pitch); // mm/s

   pAct = new CPropertyAction (this, &XYStage::OnSpeedY);

   // switch to mm/s
  ret = SendCommand("!dim y 2"); //velocity (vel) in rev/s (motor revolution)
   if (ret != DEVICE_OK) return ret;

//conversion revolution/s to mm/s
   ret = QueryCommand("?vel y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f\r", &rev_per_s);

   velocityYmm_ = rev_per_s * pitchY_;
   ret = CreateProperty("SpeedY [mm/s]", CDeviceUtils::ConvertToString(velocityYmm_), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedY [mm/s]", 0.01, 20*pitch); // mm/s
//******************************************************************************************************************************************

   // Accel (Acceleration (in m/s²)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnAccelX);


   ret = QueryCommand("?accel x", resp); //Lstep : 0,01 to 20,00 m/s²
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("Acceleration X [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration X [m/s^2]", 0.01, 2.0);
   
   pAct = new CPropertyAction (this, &XYStage::OnAccelY);


   ret = QueryCommand("?accel y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("Acceleration Y [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration Y [m/s^2]", 0.01, 2.0);


   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   initialized_    = false;
   range_measured_ = false;
   return DEVICE_OK;
}

/**
 * Returns true if any axis (X or Y) is still moving.
 */
bool XYStage::Busy()
{
   string resp;
   int ret;

   // send command
   ret = QueryCommand("?statusaxis", resp);
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in XYStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   if ((resp[0] == 'M') || (resp[1] == 'M')) return true;
   else return false;
}



/**
 * Returns current position in µm.
 */
int XYStage::GetPositionUm(double& x, double& y)
{
int ret;
   // switch to µm
   ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?pos", resp);

   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float xx, yy;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f %f\r", &xx, &yy);
   x = xx;
   y = yy;
   return DEVICE_OK;
}


/**
 * Sets position in µm
 */
int XYStage::SetPositionUm(double x, double y)
{
   ostringstream os;
   os << "XYStage::SetPositionUm() " << x << " " << y;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}
  

/**
 * Sets relative position in µm
 */
int XYStage::SetRelativePositionUm(double dx, double dy)
{
   ostringstream os;
   os << "XYStage::SetPositionUm() " << dx << " " << dy;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor "<< dx << " " << dy;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}
  

/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long x, long y)
{
   // switch to steps
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;
   ret = SendCommand(cmd.str().c_str());
   if (ret !=DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}
  
/**
 * Sets relative position in steps.
 */
int XYStage::SetRelativePositionSteps(long x, long y)
{
   // switch to steps
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor "<< x << " " << y;

   ret = SendCommand(cmd.str().c_str());
   return ret;
}

/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
   // switch to steps
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = QueryCommand("?pos", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is(resp);

   is >> x;
   is >> y;
   return DEVICE_OK;
}



/**
 * Defines current position as origin (0,0) coordinate of the controller.
 */
int XYStage::SetOrigin()
{
   int ret = SendCommand("!pos 0 0");
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return SetAdapterOrigin();
}



/**
 * Defines current position as origin (0,0) coordinate of our coordinate system
 * Get the current (stage-native) XY position
 * This is going to be the origin in our coordinate system
 */
int XYStage::SetAdapterOrigin()
{
   double xx, yy;
   int ret = GetPositionUm(xx, yy);
   if (ret != DEVICE_OK) return ret;
   originX_ = xx;
   originY_ = yy;

   return DEVICE_OK;
}


/**
 * Defines current position as (x,y) coordinate of the controller.
 */
int XYStage::SetAdapterOriginUm(double x, double y)
{
   // switch to steps
   int ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!pos "<< x << " " << y;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



int XYStage::Home()
{
   const char* cmd;
   range_measured_ = false;

   // format the command
   cmd = "!cal x";
   int ret = SendCommand(cmd);
   if (ret != DEVICE_OK) return ret;

   cmd = "!cal y";
   ret = SendCommand(cmd);
   if (ret != DEVICE_OK) return ret;

//ATTENTION! using !cal without argument would also cal Z axis unintentionally

   bool status;
   int numTries=0, maxTries=400;
   long pollIntervalMs = 100;

   this->LogMessage("Starting read in XY-Stage HOME\n", true);

   do
   {
      status = Busy();
	  numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(status && (numTries < maxTries)); // keep trying up to maxTries * pollIntervalMs ( = 20 sec)--->40s?

   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << status;
   this->LogMessage(os.str().c_str());



   // additional range measure to provide GetLimitsUm()

   cmd = "!rm x";
   ret = SendCommand(cmd);
   if (ret != DEVICE_OK) return ret;

   cmd = "!rm y";
   ret = SendCommand(cmd);
   if (ret != DEVICE_OK) return ret;

//ATTENTION! using !rm without argument would also rm Z axis unintentionally

   this->LogMessage("Starting read in XY-Stage HOME\n", true);

   do
   {
      status = Busy();
	  numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(status && (numTries < maxTries)); // keep trying up to maxTries * pollIntervalMs ( = 20 sec)

   os << "Tried reading "<< numTries << " times, and finally read " << status;
   this->LogMessage(os.str().c_str());

   if (status) return DEVICE_ERR;


   range_measured_ = true;
   return DEVICE_OK;
}


int XYStage::Stop()
{
   int ret = SendCommand("a"); //LStep will also stop Z (sorry)
   return ret;
}


/**
 * Returns the stage position limits in um.
 */
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
  if (!range_measured_) return DEVICE_UNKNOWN_POSITION;

  // switch to µm
  int ret = SendCommand("!dim 1 1");
  if (ret != DEVICE_OK) return ret;

  string resp;
  ret = QueryCommand("?lim x", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  float lower, upper;
  char iBuf[256];
  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  xMin = lower;
  xMax = upper;

  ret = QueryCommand("?lim y", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  yMin = lower;
  yMax = upper;

  return DEVICE_OK;
}



int XYStage::Move(double vx, double vy)
{
   // switch LStep to rev/s
   int ret = SendCommand("!dim 2 2");
   if (ret != DEVICE_OK) return ret;

   // convert vx and vy [mm/s] to LStep [rev/s]
   double vx_ = vx / pitchX_;
   double vy_ = vy / pitchY_;

   // format the command
   ostringstream cmd;
   cmd << "!speed "<< vx_ << " " << vy_;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   // switch to steps
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?lim x", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   long lower, upper;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%ld %ld\r", &lower, &upper);
   xMin = lower;
   xMax = upper;

   ret = QueryCommand("?lim y", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%ld %ld\r", &lower, &upper);
   yMin = lower;
   yMax = upper;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

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


int XYStage::OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
     // get current step size from the controller
     string resp;
     int ret = QueryCommand("?pitch x", resp);
     if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
     float pitch;
     char iBuf[256];
     strcpy(iBuf,resp.c_str());
     sscanf(iBuf, "%f\r", &pitch);
     double stepSizeXUm = pitch/50.0;
     pProp->Set(stepSizeXUm);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <= 0.0)
      {
         pProp->Set(stepSizeXUm_);
         return DEVICE_INVALID_INPUT_PARAM;
      }
      stepSizeXUm_ = stepSize;
   }

   return DEVICE_OK;
}


int XYStage::OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
     // get current step size from the controller
     string resp;
     int ret = QueryCommand("?pitch y", resp);
     if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
     float pitch;
     char iBuf[256];
     strcpy(iBuf,resp.c_str());
     sscanf(iBuf, "%f\r", &pitch);
     double stepSizeYUm = pitch/50.0;
     pProp->Set(stepSizeYUm);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <= 0.0)
      {
         pProp->Set(stepSizeYUm_);
         return DEVICE_INVALID_INPUT_PARAM;
      }
      stepSizeYUm_ = stepSize;
   }

   return DEVICE_OK;
}


/*
 * Speed as returned by device is in mm/s
 * We convert that to um per second using the factor 1000
 */
int XYStage::OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // switch to rev/s
	 int ret = SendCommand("!dim x 2");
     if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = QueryCommand("?vel x", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double speedX = atof(tokens.at(0).c_str()) * pitchX_;
      speedX_ = speedX;
      pProp->Set(speedX_);
   }
   else if (eAct == MM::AfterSet)
   {
      // switch to rev/s
      int ret = SendCommand("!dim x 2");
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed / pitchX_;

      ostringstream cmd;
      cmd << "!vel x " << speed;
	  ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;

      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;


      speedX_ = speed;
   }
   return DEVICE_OK;
}


int XYStage::OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // switch to mm/s
      int ret = SendCommand("!dim y 2");
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = QueryCommand("?vel y", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double speedY = atof(tokens.at(0).c_str()) * pitchY_;
      speedY_ = speedY;
      pProp->Set(speedY_);
   }
   else if (eAct == MM::AfterSet)
   {
      // switch to rev/s
	 int ret = SendCommand("!dim y 2");
	 if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed / pitchY_;

      ostringstream cmd;
      cmd << "!vel y " << speed;
	  ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      speedY_ = speed;
   }
   return DEVICE_OK;
}


int XYStage::OnAccelX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?accel x", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double accelX = atof(tokens.at(0).c_str());
      accelX_ = accelX;
      pProp->Set(accelX_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

	  ostringstream cmd;
      cmd << "!accel x " << accel;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      accelX_ = accel;
   }
   return DEVICE_OK;
}


int XYStage::OnAccelY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?accel y", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double accelY = atof(tokens.at(0).c_str());
      accelY_ = accelY;
      pProp->Set(accelY_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

	  ostringstream cmd;
      cmd << "!accel y " << accel;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      accelY_ = accel;
   }
   return DEVICE_OK;
}





///////////////////////////////////////////////////////////////////////////////
// Z - Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
ZStage::ZStage() :
   LStepBase(this),
   range_measured_(false),
   stepSizeUm_(0.1),
   speedZ_(20.0), //[mm/s]
   accelZ_(0.2), //[m/s²]
   originZ_(0),
   pitchZ_(1)



{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "L-Step Z axis driver", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}



int ZStage::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   int NumberOfAxes = (Configuration_ >> 4) &0x0f;
   if (NumberOfAxes < 3) return DEVICE_NOT_CONNECTED; // Controller hardware without Z axis


   // set property list
   // -----------------
   
   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnStepSize);
   // get current step size from the controller
   string resp;
   ret = QueryCommand("?pitch z", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   float pitch;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   pitchZ_ = pitch;
   stepSizeUm_ = pitch/50.0;
   ret = CreateProperty("StepSize [um]", CDeviceUtils::ConvertToString(stepSizeUm_), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;

   // switch to mm/s
   ret = SendCommand("!dim z 2");
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?vel z", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   float rev_per_s;
   char iBufString[256];
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f\r", &rev_per_s);
   velocityZmm_ = rev_per_s * pitchZ_;

   pAct = new CPropertyAction (this, &ZStage::OnSpeed);
   ret = CreateProperty("SpeedZ [mm/s]", CDeviceUtils::ConvertToString(velocityZmm_), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedZ [mm/s]", 0.001, 20*pitchZ_); // mm/s


   // Accel (Acceleration (in m/s²)
   // -----
   ret = QueryCommand("?accel z", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   pAct = new CPropertyAction (this, &ZStage::OnAccel);
   ret = CreateProperty("Acceleration Z [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration Z [m/s^2]", 0.01, 2.0);
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   initialized_    = false;
   range_measured_ = false;
   return DEVICE_OK;
}

bool ZStage::Busy()
{
  string resp;
  int ret;

   // send command
   ret = QueryCommand("?statusaxis", resp);
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in ZStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   if (resp[2] == 'M') return true;
   else return false;
}


int ZStage::Stop()
{
   int ret;
   ret = SendCommand("a"); //LStep abort also XY (sorry)
   return ret;
}


int ZStage::SetPositionUm(double pos)
{
   ostringstream os;
   os << "ZStage::SetPositionUm() " << pos;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa z " << pos;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK)
     return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}



int ZStage::SetRelativePositionUm(double d)
{
   // switch to µm
   int ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor z " << d;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}



int ZStage::GetPositionUm(double& pos)
{
int ret;
   // switch to µm
   ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = QueryCommand("?pos z", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float zz;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &zz);
   pos = zz;

   ostringstream os;
   os << "ZStage::GetPositionUm() " << pos;
   this->LogMessage(os.str().c_str());
   return DEVICE_OK;
}
  
int ZStage::SetPositionSteps(long pos)
{
int ret;
   // switch to steps
   ret = SendCommand("!dim z 0");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "MOA Z " << pos;

   string resp;
   ret = QueryCommand(cmd.str().c_str(), resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}
  
int ZStage::GetPositionSteps(long& steps)
{
int ret;
   // switch to steps
   ret = SendCommand("!dim z 0");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = QueryCommand("?pos z", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is(resp);
   is >> steps;
   return DEVICE_OK;
}
  
int ZStage::SetAdapterOrigin()
{
   double zz;
   int ret = GetPositionUm(zz);
   if (ret != DEVICE_OK)
      return ret;
   originZ_ = zz;

   return DEVICE_OK;
}


int ZStage::SetOrigin()
{
   int ret = SendCommand("!pos z 0");
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = QueryCommand("?status", resp);
   if (ret != DEVICE_OK) return ret;

   if (resp.compare("OK...") != 0)
   {
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   return SetAdapterOrigin();
}


/**
 * Defines current position as (d) coordinate of the controller.
 */
int ZStage::SetAdapterOriginUm(double d)
{
   // switch to steps
   int ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!pos z "<< d;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}




int ZStage::Move(double v)
{
   // switch to mm/s
   int ret = SendCommand("!dim z 2");
   if (ret != DEVICE_OK) return ret;

   // if v is not in mm/s then please convert here to mm/s
   double rev_s = v / pitchZ_;

   // format the command
   ostringstream cmd;
   cmd << "!speed z "<< rev_s;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



/**
 * Returns the stage position limits in um.
 */
int ZStage::GetLimits(double& min, double& max)
{
  if (!range_measured_) return DEVICE_UNKNOWN_POSITION;

  // switch to µm
  int ret = SendCommand("!dim z 1");
  if (ret != DEVICE_OK) return ret;

  string resp;
  ret = QueryCommand("?lim z", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  float lower, upper;
  char iBuf[256];
  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  min = lower;
  max = upper;

  return DEVICE_OK;
}





///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
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

int ZStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // switch to mm/s
      int ret = SendCommand("!dim z 2");
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = QueryCommand("?vel z", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double speedZ = atof(tokens.at(0).c_str()) * pitchZ_;
      speedZ_ = speedZ;
      pProp->Set(speedZ_);
   }
   else if (eAct == MM::AfterSet)
   {
      // switch to mm/s
      int ret = SendCommand("!dim z 2");
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed / pitchZ_;

      ostringstream cmd;
      cmd << "!vel z " << speed;
	  ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      speedZ_ = speed;
   }
   return DEVICE_OK;
}


int ZStage::OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?accel z", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double accel = atof(tokens.at(0).c_str());
      accelZ_ = accel;
      pProp->Set(accelZ_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

	  ostringstream cmd;
      cmd << "!accel z " << accel;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      accelZ_ = accel;
   }
   return DEVICE_OK;
}







///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <= 0.0)
      {
         pProp->Set(stepSizeUm_);
         return DEVICE_INVALID_INPUT_PARAM;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}





